# enigmatic-enhance —— 第二轮内存/分配优化报告(4.6.1 → 未发布)

> 结论:本轮四个方向的落点与量级差得很远 ——
> **① 缓存 `Component` 是真正的大头**(悬停物品时每秒上千个短命对象 → 0),
> **③ 去掉热路径 `Optional` 次之**(每次伤害/每 tick 省 2~3 个对象),
> **④ 装箱改原始类型最小**(每次伤害/每 tick 省 1 个装箱对象,收益在"不再有"而不是"省很多"),
> **② 热路径 `Stream` 实际为 0 处**(全仓 18 个 `.stream()` 全在自检类 + 一处玩家手动触发的回溯,见 §4)。
> 另外顺手做了 **⑤ 热路径 `DebugLog.once` 的参数预求值门控**(每次伤害省 2~6 个对象,输出逐字不变)。
>
> 全部改动 `gradle build` 通过(`BUILD SUCCESSFUL`,产物 `build/libs/enigmaticenhance-4.6.1.jar`)。
> **版本号、配置项、消息文案、事件顺序/优先级、tooltip 文字全部未动。**

---

## 1. 改动总表(按类)

| 类 | 处数 | 文件:行号(改后行号) |
|---|---|---|
| ① 常量 `Component` 缓存 | **94 行 / 26 个文件** | 见下表 |
| ③ 去热路径 `Optional` | **10 处** | `EarthAmuletData:427`、`DemonPactData:30`、`DemonBloodChaliceHandler:144`、`DemonBoneHandler:119`、`TravelerHatHandler:72`、`SharpshooterHandler:41`、`PearlUtil:43`、`BloodCalamityHandler:102`、`client/NumericHealthHud:126`、`ForgerGemAnvilHandler:46` |
| ④ 装箱 → 原始类型 | **3 张表** | `EarthAmuletHandler:92`(`Map<UUID,Integer>`→`Map<UUID,int[]>`)、`DemonBoneHandler:95`(`Map<UUID,Long>`→`Object2LongOpenHashMap`)、`HandOfExecutionHandler:20`(`Map<UUID,Integer>`→`Map<UUID,int[]>`;`169` 读侧) |
| ⑤ `DebugLog.once` 参数门控 | **7 处** | `HandOfExecutionHandler:60 / 104 / 116`、`BloodCalamityHandler:192 / 208`、`ElementalSpellCastHandler:144`、`PlayerCritHandler:113` |
| ② 热路径 `Stream` | **0 处**(审计结论见 §4) | — |

### ① 常量 `Component` 缓存明细(每行 = 1 个 `translatable/literal` + `withStyle`)

| 文件 | 缓存行数 | 行号 |
|---|---|---|
| `DemonHandBoneItem` | 8 | 36–60 |
| `ItemTooltips`(`addFixed` + `line()` 缓存) | 7 个 key | 302–352 |
| `SharpshooterCharmItem` | 6 | 34–50 |
| `DemonBloodChaliceItem` | 5 | 60–75 |
| `DemonPactItem` | 5 | 72–88 |
| `AddonRevivalLeafTooltip` | 5 | 28–55 |
| `BedtimeReadingItem` / `ConsciousnessSeaItem` / `HistoryMistItem` / `MirrorShadowItem` / `PureRuneItem` / `SpiritLightItem` / `StyxWaterItem` / `FishingHaulBottleItem` | 各 4 | 37 / 24 / 55 / 72 / 53 / 34 / 32 / 31 |
| `LifeMeterItem` / `SunBottleItem` / `WarFogItem` / `EarthAmuletItem` | 各 3 | 31 / 31 / 32 / 86 |
| `EarthBottleItem` / `FakeLedgerItem` / `GluttonyBookItem` / `TravelerHatItem` / `UncertainFogItem` / `WarBannerBlock` | 各 2 | 33 / 56 / 347 / 65 / 25 / 32 |
| `UnfinishedContent` / `ForgerGemAnvilHandler` | 各 1 | 215 / 70 |

`EarthAmuletItem` 额外把"头颅行"(key 带参数,但 lang path 是有限集合)按 path 缓存进
`HEAD_LINES`(`ConcurrentHashMap<String, Component>`),每帧省掉字符串拼接 + 2 个对象。

---

## 2. 逐类"为什么等价"

### 2.1 缓存 `Component`(①)

- **`Component` 是不可变的**,而且 tooltip 列表里的对象**只被读**(渲染)——
  全仓只有 `lines.add(...)`、`tooltip.get(i).getString()`、`lines.set(0, lines.get(0).copy()...)`
  (后者自己 `copy()`),没有任何代码对拿到的行做原地 `append`/`setStyle`。所以把同一个实例
  反复放进不同 tooltip 列表是安全的。4.6.1 的 `ItemTooltips#bagTooltipLines()` 已经是同一个做法。
- **语言切换不受影响**:`TranslatableComponent` 的文字是**渲染时**才按当前
  `LanguageManager` 解析的,缓存的是"key + 参数 + 样式",不是解析结果。
- **类加载时机没问题**:这些常量在物品注册(类初始化)时构造,`Component.translatable(...)`
  不触碰语言表 / 注册表,服务端也一样安全(服务端本来就不会渲染这些文字)。
- `ItemTooltips#addFixed`:原来 `add(lines, line("add.xxx"))` 每帧要做
  拼 `"tooltip.enigmaticenhance." + key` 的 String、`new TranslatableComponent`、
  `body.getString()`(StringBuilder + String)、`new Object[1]`(varargs)、
  再 `translatable("...added", body)`、再 `withStyle` 复制。
  `addFixed` 把这些**一次性做好并整行缓存**。
  原来 `add()` 里的"正文翻译为空就不加"的检查,在建缓存的那一刻做同一次判断(空则记 `SKIP_LINE`,
  以后同样跳过),对同一份语言文件行为逐字一致。

### 2.2 去 `Optional`(③)

原来:`CuriosApi.getCuriosInventory(p).map(h -> h.findFirstCurio(...).isPresent()).orElse(false)`

- `getCuriosInventory` 返回 `LazyOptional`;`LazyOptional#map` **每次调用都新建一个 `LazyOptional`**,
  并且那个 lambda 捕获了局部变量 ⇒ **每次再新建一个 lambda 实例**。
- 现在:`LazyOptional#orElse(null)` 直接返回内部引用(不分配),再判 `null`。
  语义严格等价:`map(f).orElse(d)` ≡ `v == null ? d : f(v)`,而我们的 `f` 从不返回 `null`。
- 四个 `equippedXxx`(护符/契约/血杯/手骨/帽子)还额外省掉内层
  `findFirstCurio(...).map(SlotResult::stack).orElse(EMPTY)` 里的**第二个 `Optional`**,
  改成 `orElse(null)` + `found.stack()`。
- ⚠️ **没有用 `resolve()`**:本工程 Forge 的 `LazyOptional#resolve()` 返回 `java.util.Optional`
  (第一次编译就踩到了,见 `_b1.log`),反而多建一个对象 —— 所以统一用 `orElse(null)`。
- **不改 API**:`findFirstCurio` 自己返回的 `Optional` 保留(见 §4-7)。

### 2.3 装箱 → 原始类型(④)

1. `EarthAmuletHandler.LAST_FLYING_TICK`:`Map<UUID, Integer>` → `Map<UUID, int[]>`(长度 1)。
   `player.tickCount` 早就超出 `Integer` 缓存区(-128..127),原来**每 tick** put 一次就是
   **一个新 `Integer`**。现在 `computeIfAbsent(id, k -> new int[1])[0] = tick` ——
   第一次建数组,之后只写下标 0。读侧拿到同一个数组只读 `[0]`,比较的仍然是 tickCount 的整数值。
2. `DemonBoneHandler.HEAL_BLOCK`:`Map<UUID, Long>` → FastUtil `Object2LongOpenHashMap<UUID>`
   (`defaultReturnValue(NO_BLOCK)`,省掉 null 判断)。每次命中一个 `Long` → 0 个。
   它**原来就是非并发 `HashMap`**,写入/读取全在服务端线程(受击/治疗/死亡/离开世界),
   **并发语义没有变化**(这也是没换成 `int[]` 而用 FastUtil 的原因:`int[]` 每次 put 要新建数组,
   反而比装箱还贵)。
3. `HandOfExecutionHandler.LAST_TICK`:`Map<UUID, Integer>` → `Map<UUID, int[]>`,仍是
   `ConcurrentHashMap`(并发语义不变)。`computeIfAbsent` 命中时零分配;`NO_TICK` 哨兵保证
   "第一次见到这个目标"不会被误判成"同一 tick 已处理"。

### 2.4 `DebugLog.once` 门控(⑤)

`DebugLog.once(key, ...)` 的第一件事是 `if (ONCE.size() >= 32 || !ONCE.add(key)) return;` ——
但 `key`(`"exec:noench:" + id + ":" + "p"`、`"ratiox" + ratio`、`"bcTaken:" + debuffs` …)
在**调用前**就已经拼好了,`varargs` 的 `Object[]` 也已建好。

- 门控写成 `if (DebugLog.oncePending()) { DebugLog.once(...); }`,
  而 `oncePending()` 就是 `ONCE.size() < 32`。
- **等价性是严格的**:`ONCE` 一旦满 32 条,`once()` 对**任何** key 都立刻返回不输出,
  所以 `oncePending()==false` 时跳过调用不可能改变任何输出;`oncePending()==true` 时
  走的分支与原来完全一样。
- 实机里 `ONCE` 通常开局几十秒就被这 32 条诊断占满 ⇒ 之后这条路径彻底零分配
  (每击少 1~2 个 String + 1~2 个 `Object[]`,还省掉 `ForgeRegistries.ITEMS.getKey` 的反查)。

---

## 3. 量化估算(基于实际调用频率推算)

前提说明(不是实测 profiler 数据,是按调用点推的):
- 悬停类 tooltip:**每帧一次**(`ItemTooltipEvent` 由 `ItemStack#getTooltipLines` 触发,
  这是 4.6.1 报告已经确认过的事实),取 60 / 144 fps 两个档。
- 每缓存一行原来至少建 **3 个对象**:`TranslatableComponent` + 它的
  `TranslatableContents` + `withStyle` 的副本(1.20.1 的 `withStyle` 返回**副本**);
  `Component.literal(...)` 是 2 个。`ItemTooltips` 的 `addFixed` 行还要再加
  1 个拼接 String、2 个 `getString()` 对象、1 个 `Object[]`。
- 战斗按 **10 次伤害结算/秒**、单人、1 名玩家。

| 场景 | 改前 | 改后 | 每秒减少 |
|---|---|---|---|
| 悬停「恶魔的手骨」(8 行) | 24 对象/帧 | 0 | **1440 /s (60fps) ~ 3456 /s (144fps)** |
| 悬停「神射手护符」(6 行) | 18 对象/帧 | 0 | 1080 ~ 2592 /s |
| 悬停一瓶「灵界之光/冥河之水/渔获瓶」(4 行) | 12 对象/帧 | 0 | 720 ~ 1728 /s |
| 悬停任意一件本模组带 3 行说明的物品 | 9 对象/帧 | 0 | 540 ~ 1296 /s |
| 悬停铁砧相关物品的锻造者宝石行 | 3 对象/帧 | 0 | 180 ~ 432 /s |
| `ItemTooltips` 常量行(引擎/书/大地之心等,7 个 key) | ≈9 对象/帧·行 | 0 | 约 540 ~ 1300 /s(单行) |
| curios 查询(③,每次 2~3 个对象) | 每 tick 约 2 次 + 每次伤害约 3 次 | 0 | 单人日常 ≈ **80 /s**,战斗 ≈ **200 /s** |
| `LAST_FLYING_TICK` 装箱(④) | 20 个 Integer/s(戴龙首且在飞时) | 0 | **20 /s** |
| `HEAL_BLOCK` / `LAST_TICK` 装箱(④) | 约 20 个 Long+Integer/s(战斗) | 0 | **≈20 /s** |
| `DebugLog.once` 参数(⑤) | 每次伤害 2~6 个对象 | 0 | 战斗 ≈ **20 ~ 60 /s** |

**汇总(单人)**:
- 挂机/看背包(频繁悬停):**每秒减少约 1000~3000 次分配**,这是本轮的主要收益;
- 战斗(不看 tooltip):**每秒减少约 250~300 次分配**;
- 多人整合服:③④⑤ 三条按**在线玩家数 × 5** 线性放大(龙首/手骨/血杯这些只在"戴着且战斗"时才有量)。

> 注意:① 的收益是"峰值型"的 —— 只在鼠标停在物品上时发生,但那一刻正是客户端最敏感的时候
> (tooltip 每帧重建 + 渲染),所以它同时也是 4.6.1 那条"悬停掉帧"的延续修复。

---

## 4. 没有改的,以及为什么

1. **② 热路径 `Stream`:全仓 0 处。**(逐个文件核对了 `Stream/stream()/Arrays.stream/IntStream`)
   18 个 `.stream()` 里:
   - 15 个在 `*SelfTest` 自检类(一次性,冷路径,按硬性要求 2 不动);
   - 1 个 `MirrorShadowHandler:235`(`restore()`,玩家右键 1 秒才触发一次,一个 list + 一次 `forEach`,
     属于"20 tick 都不一定调一次"的量级,不动);
   - 2 个是 `getResourceAsStream`(读资源文件,不是集合 stream)。
   **结论:这一轮 ② 没有可改的热点,不是"没做",是"没有"。**
2. **`EarthAmuletHandler.NOCLIP_SENT: Map<UUID, Boolean>` 不改成 `Set<UUID>`**:
   `Boolean.valueOf(true/false)` 返回的是**缓存常量**,`put` 本来就不分配;
   而且这张表编码了**三态**(`null` = 从没发过 / `true` / `false`),
   `lastNoClipSent()` 被自检用来区分"没发过"和"发过 false",单个 `Set` 表达不了
   (要两个 set 才等价)。收益 ≈ 0,风险 ≠ 0,**不改**。
3. **`EarthAmuletHandler.APPLIED_HEALTH_BONUS: Map<UUID, Double>` 不改**:
   它只在"数值真的变了"时才 `put`(`Math.abs(applied - bonus) < 1e-4` 就 return),
   稳态每 tick 只有 1 次 `get` + 拆箱,**零分配**。换 FastUtil 得把 `ConcurrentHashMap`
   降级成非并发 Map,风险大于收益。
4. **`GluttonyBookCleanup.DRIVING: Map<UUID, Integer>` 不改**:只有"正被贪食之书死亡驱动"的玩家
   才会 `merge`(每次死亡最多 100 tick,5 秒),`Integer::sum` 最多装 100 个箱,量级可忽略。
5. **`ElementalSpellCastHandler.LAST_CAST: Map<UUID, Integer>` 不改**:只有**施法成功**才 `put`
   (≈1 次/秒);读侧(每次伤害)是纯 `get`,不分配。
6. **`BlessRingCurioKeepMixin:81` 的 `getStacksHandler("ring").orElse(null)` 不改**:
   在"饰品卸下"事件里,冷路径;且 Curios 这个 API 只给 `Optional`。
7. **`findFirstCurio(...)` 内部的 `Optional` 保留**:Curios 只提供 `Optional` 返回值的查询,
   要彻底去掉只能自己遍历 `handler.getCurios()` 并复刻它的槽位顺序/空判断 ——
   属于"依赖扩展内部结构"的改动,扩展一升级就可能行为漂移。**不改 API、只去外层**。
8. **动态 tooltip 行保持每帧重建**(需要它):
   - `TravelerHatItem` 的速度/群系/维度三行(数值来自 `RingConfig`,游戏内可改);
   - `EarthAmuletItem` 的 `potion` 行、`HistoryMistItem` 的 `recorded` + 每条记录行
     (内容来自 NBT,而 NBT 是**原地改**的,没有版本号可做失效);
   - `ItemTooltips` 里带 `body(key, args)` 的正文(配置值 + 逐件开关状态);
   - `HistoryMistItem` 的 `· ` 前缀:代码是在它上面 `.append(...)` 的,缓存后必须 `copy()`,
     与原来一样开一次,没有净收益 —— 不动。
9. **`LAST_TICK` 这张表本身不回收**(4.6.1 之前就存在):它是"目标 UUID → 最后命中 tick",
   没有清理点。本轮把它从 `Integer` 换成 `int[]`,**每条多 8B**(24B vs 16B),
   但**每次命中少一个 `Integer`**;表的大小上限 = "被带执行之手的人打过的不同实体数"
   (几百~几千 ⇒ 几十 KB)。要彻底修需要挂 `LivingDeathEvent`/`EntityLeaveLevelEvent`,
   属于行为面扩张,留给下一轮。
10. **`AutoSmeltItemWatcher.PROCESSED` / `MagicSwordAuraEntity.hitEntities`**:已经是
    `Set<UUID>` 的正确语义(不是 `Map<UUID,Boolean>`),元素数也很小,不动。

---

## 5. 风险与自查

1. **缓存 `Component` 后是否有人依赖对象身份?**
   已核对:**没有**。`Component` 不重写 `equals`,`TooltipSelfTest`/`BedtimeReadingSelfTest`/
   `DemonBloodChaliceSelfTest`/`PureRuneSelfTest` 全部走 `tooltip.get(i).getString()` 比字符串;
   代码里唯一的 tooltip 变换是 `ItemTooltips:154` 的 `lines.set(0, lines.get(0).copy()...)`(自己 copy)。
   ⇒ **不存在"靠身份做 equals"的地方**。
2. **唯一的理论风险**:别的模组（或未来本模组的代码）若对 tooltip 列表里已有的行**原地** `append`,
   会把缓存行越改越长。mitigation:真出问题把 `tooltip.add(X)` 换成
   `tooltip.add(Component.empty().append(X))` 即可(每个行多 1 个对象)。当前整合包里的
   `ItemTooltipEvent` 使用者都是"往列表加行"或"读 `getString()`"。
3. **语言切换**:缓存的是 key/args/style,渲染时才解析 ⇒ 切语言后 tooltip 立刻跟着变。
4. **`addFixed` 的空白检查**:判断结果被缓存(建缓存时算一次)。只有"运行期把某个翻译热改成空串"
   才会与原代码有差异;语言文件只在资源包重载时变化,而那时 tooltip 会重建。
5. **`orElse(null)`**:`LazyOptional`/`Optional` 的 `orElse` 都不会分配;
   我们所有 lambda 都返回非 null,`map(...).orElse(d)` 与"判空 + 直接取"严格等价。
6. **FastUtil `Object2LongOpenHashMap`**:原本就是非并发 `HashMap`(同一批服务端线程访问),
   换成 FastUtil 不改变并发语义;`defaultReturnValue(NO_BLOCK)` 保证 `getLong` 不会把
   "没有记录"误当成"到期 tick 0"。
7. **`int[]` 格子**:数组元素写入不是原子操作,但这些表只在服务端 tick / 伤害结算里读写,
   跨线程最坏情况是读到"上一 tick 的值",与原来 put/get 的可见性行为同级;
   `ConcurrentHashMap` 仍然保证数组引用的安全发布。
8. **自检**:`TooltipSelfTest` 的 T21(书袋三行)/T24(每件物品行数预算)、
   `LangSelfTest`(key 齐全)、各 item 的 tooltip 自检**全部依赖 `getString()`,文字一个字节没改**,
   预期仍然 PASS(需要你进游戏跑一次确认)。

---

## 6. 改动文件清单(39 个 Java 文件,`git diff --shortstat` = 591 insertions / 228 deletions)

`AddonRevivalLeafTooltip`、`BedtimeReadingItem`、`BloodCalamityHandler`、`ConsciousnessSeaItem`、
`DemonBloodChaliceHandler`、`DemonBloodChaliceItem`、`DemonBoneHandler`、`DemonHandBoneItem`、
`DemonPactData`、`DemonPactItem`、`EarthAmuletData`、`EarthAmuletHandler`、`EarthAmuletItem`、
`EarthBottleItem`、`ElementalSpellCastHandler`、`FakeLedgerItem`、`FishingHaulBottleItem`、
`ForgerGemAnvilHandler`、`GluttonyBookItem`、`HandOfExecutionHandler`、`HistoryMistItem`、
`ItemTooltips`、`LifeMeterItem`、`MirrorShadowItem`、`PearlUtil`、`PlayerCritHandler`、`PureRuneItem`、
`SharpshooterCharmItem`、`SharpshooterHandler`、`SpiritLightItem`、`StyxWaterItem`、`SunBottleItem`、
`TravelerHatHandler`、`TravelerHatItem`、`UncertainFogItem`、`UnfinishedContent`、`WarBannerBlock`、
`WarFogItem`、`client/NumericHealthHud`。

未提交 git、未装实例、未改 `build.gradle`、未改 `gradle.properties`(版本号仍是 4.6.1)、未动其它模组。

## 7. 建议的实机验证

1. `/spark profiler start --timeout 30`,对比悬停「恶魔的手骨 / 神射手护符 / 大地护符」时的
   `ItemTooltips` / `appendHoverText` 占比;
2. `-Xlog:gc` 看 Young GC 次数:悬停物品时的 GC 频率应明显下降;
3. 战斗场景(带执行之手 + 三件套)看 `HandOfExecutionHandler` / `DemonBoneHandler` 的分配;
4. 进游戏跑一次自检,确认 T21/T24 与 Lang 自检仍 PASS。
