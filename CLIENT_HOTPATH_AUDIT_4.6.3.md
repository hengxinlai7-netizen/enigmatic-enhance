# enigmatic-enhance 客户端热路径全量审计(4.6.2 基线 → 4.6.3)

> 审计范围:整个模组(不是 4.6.x 相对回归)。方法:把**每一个"每帧 / 每实体 tick / 每 tick / 每实体 tick × 每效果"**的入口点
> 全部列出来,逐个读方法体,判断"第一行是否廉价""最坏情况做多少工作",再按量级排序修掉前三。
>
> 所有改动 `gradle build` 通过,产物 `build/libs/enigmaticenhance-4.6.2.jar`(jar 时间晚于所有源文件)。

---

## 0. 入口点普查(硬数字)

| 事件 | 我们注册的 handler 数 | 频率 |
|---|---|---|
| `LivingHurtEvent` | **20** | 每次伤害结算(所有实体) |
| `TickEvent.PlayerTickEvent` | **16** | 每 tick × 每玩家 |
| `LivingDeathEvent` | 10 | 每次死亡 |
| `PlayerInteractEvent.RightClickBlock` | 8 | 每次右键方块 |
| `LivingEvent.LivingTickEvent` | **6** | **每实体每 tick** |
| `LivingDamageEvent` | 5 | 每次伤害 |
| `ItemTooltipEvent` | **4** | **每帧**(悬停物品时) |
| `TickEvent.ClientTickEvent` | **3** | 每 tick(客户端) |
| `RenderGuiEvent.Post` | **1** | **每帧** |
| `TickEvent.ServerTickEvent` | 1 | 每 tick |
| 全模组 `@SubscribeEvent` 合计 | **154** | — |

每帧还会走:2 个 `ItemProperties` 谓词 + 1 个 `ItemColor` 回调(大地护符)、3 个实体渲染器、1 处粒子生成。

---

## 1. 全量表(客户端热路径)

### 1.1 每帧(60~200 次/秒)—— 最贵的一组

| 类 / 方法 | 频率 | 每帧工作量 | 是否可优化 | 状态 |
|---|---|---|---|---|
| `client.NumericHealthHud.render` (`RenderGuiEvent.Post`) | 每帧 | 携带刻度时:`String.format` ×1~2 + 字符串拼接 + **`font.width(text)`(逐字形测量,1~3µs)** + 2~4 次 `fill` + `drawString`;不携带时:一次 tick 缓存比对 | ✅ 文本/宽度可按数值缓存 | **已修(本轮 T1)** |
| `EarthAmuletState.state` / `headIndex` (`ItemProperties`) | 每帧 × 每个被渲染的护符 | 4.6.1 后普通护符已零分配;有头颅时仍 `new ArrayList` + `indexOf` | ✅ 直接遍历 NBT | **已修(本轮 T2)** |
| `EarthAmuletTint.colorFor` (`ItemColor`) | 每帧 × 每层 × 每个护符 | `effects()`(ArrayList + 每条 record)+ `ResourceLocation.tryParse`(**每次新建对象**)+ 2 次注册表查找 | ✅ 直接遍历 NBT + id→颜色缓存 | **已修(本轮 T2)** |
| `ItemTooltips.onItemTooltip` | 每帧(悬停任意物品) | 1 次 `ITEMS.getKey` + ≤11 次 `ResourceLocation.equals`;书袋分支:1 次引用比较 + `addAll` | 已很轻 | 4.6.1 已修 |
| `ItemTooltips` 常量行(`line`/`addFixed`) | 每帧(命中行时) | 每行 1 次 `ConcurrentHashMap<String,Component>.get`(键是**字面量**,`hashCode` 已缓存,无分配) | 可再降到 0 次查找(静态 final 持有类),收益 ~20ns/行 | 4.6.2 现状:**不是回归**(见 §3.2) |
| `UnfinishedContent.onTooltip` | 每帧(悬停任意物品) | 1 次物品实例比较 + 1~2 次 NBT `contains`(无附魔物品到此为止) | 已很轻 | 4.6.1 已修 |
| `AddonRevivalLeafTooltip.onTooltip` | 每帧(悬停任意物品) | 物品实例比较(1 次引用比较) | 已很轻 | 4.6.1 已修 |
| `ForgerGemAnvilHandler.onTooltip` | 每帧(悬停任意物品) | 1 次配置读 + 2 次物品实例比较 | 已很轻 | 4.6.1 已修 |
| `EarthAmuletItem.appendHoverText` | 每帧(悬停护符) | 4.6.2 已缓存常量行;残留:效果行每帧重建(ArrayList + N records + 每个效果 `getString()` + StringBuilder + Component) | ✅ 按 NBT 内容指纹缓存 | **已修(本轮 T3)** |
| `MagicSwordAuraRenderer.render` / `PureRuneProjectileRenderer.render` | 每帧 × 每实体 | 4.6.1 已缓存 `RenderType`;`MagicSwordAura` 仍每实体 `endBatch(type)`(会打断批次合并,GPU 侧开销) | ⚠️ 有语义风险,见 §4 | 保留 |
| `client.DemonBoneParticles` | 每 tick | `wearing()`(curios 查询)在 %10 门控**之前** → 每 tick 都查 | ✅ 门控提前 | **已修(本轮 bonus)** |
| `HistoryMistItem.appendHoverText` | 每帧(悬停迷雾) | `readRecords` 每帧 new `LinkedHashMap` + 每个属性 `ResourceLocation.tryParse` + `String.format` | ✅ 同样可按指纹缓存 | 未修(见 §4) |
| `client.FakeLedgerScreen` / `ValueEditorScreen` | 每帧(仅打开界面时) | 自己画的 GUI | — | 不涉及 |

### 1.2 每实体每 tick(实体数 × 20/秒)

| 入口 | 频率 | 第一行 / 最坏工作量 | 状态 |
|---|---|---|---|
| `EntityMoveNoClipMixin` → `Entity#move` | 上万次/秒 | 4.6.1 起第一行是 `!anyActive() && !hasForced()` → 两次 `isEmpty` | 已优化 |
| `MobEffectSealMixin` ×4 → `MobEffect#applyEffectTick/applyInstantenousEffect/add-removeAttributeModifiers` | 每实体 × 每效果 × 每 tick | `shouldSeal` → `isSealed`:ThreadLocal 读 + `instanceof Player` + **按 tick 缓存的** curios 结果 | 已很轻 |
| `MobEffectInstanceSealMixin` → `MobEffectInstance#applyEffect` | 同上 | 同上 | 已很轻 |
| `LivingEntityAmuletMixin` → `getMobType(m_6336_)` / `canBeAffected(m_7301_)` | 每次伤害 / 每次效果施加 | 第一行 `instanceof Player`(非玩家直接返回);玩家:`equippedAmulet` = Curios capability + `findFirstCurio` + `Optional` | ⚠️ 玩家侧未缓存,见 §4 |
| `GluttonyFoodTickMixin` → `FoodData#tick` | 每玩家每 tick | 配置 + 数值比较 | 轻 |
| `ConsciousnessSeaHandler.onMobTick` | 每实体每 tick | 第一行 `instanceof Mob` + `isClientSide`,随后 `tickCount % 20` 门控 | 已很轻 |
| `OmniContaminationGuard.onTick` | 每实体每 tick | `instanceof LivingEntity` + `isClientSide` + `% 40` 门控 | 已很轻 |
| `FateCorrectionHandler.onTick` | 每实体每 tick | `isClientSide` + `% 20` 门控 | 已很轻 |
| `ExperienceOverflowGuard.onTick` | 每实体每 tick | `instanceof Player` + `% 20` 门控(之后才扫背包) | 已很轻 |
| `MercurySerpentHandler.onLivingTick` | 每实体每 tick | `isClientSide` → `has(entity)`:配置读 + `MobEffects.xxx.get()` + `hasEffect` | ⚠️ 每实体每 tick 一次配置读,可把 effect 实例缓存 |
| `GluttonyBookCleanup.deathTickDriver` | 每实体每 tick | `instanceof Player` 直接返回(非玩家零成本) | 已很轻 |
| `WitherSkullMixin`(`tick`/`hurt`) | 每个凋灵之首每 tick | 少量字段判断 | 轻 |

### 1.3 每 tick(20/秒,服务端线程;单机 = 客户端主线程)

| 入口 | 频率 | 工作量 | 状态 |
|---|---|---|---|
| `BagHudSync.onServerTick` → `BagHudEffects.collect` | 每 tick × 每在线玩家 | `BookBag.hasBag`(背包+饰品栏+末影箱扫描);书袋随身时 ≤16 次反射 `findBookInBag`(每次**再**扫一遍);16 次 `ITEMS.getValue` | 4.6.1 起物品预解析 + `hasBag` 复用;16 次扫描仍在(§4) |
| `BagHudSync.sync` 签名 | 每 tick × 每玩家 | `ThreadLocal<StringBuilder>` 复用 | 4.6.0 已优化 |
| **16 个 `PlayerTickEvent` handler** | 每 tick × 每玩家 | 逐个做自己的 curios / 背包扫描:`TravelerHatHandler`(ensureHeadSlot + equippedHat + applyBonuses + syncIfChanged)、`EarthAmuletHandler`、`DemonBloodChaliceHandler`、`DemonBoneHandler`、`BloodCalamityHandler.wearingBerserkCharm`(36 格 + curios)、`GluttonyHandler`、`HistoryMistHandler`(1/秒)、`SpiritLightHandler.hasSpiritLight`(全背包)、`SunBottleHandler.hasSunBottle`(全背包)、`WarFogHandler.hasWarFog`(全背包)、`StyxSoulHandler`、`UnbreakableHandler.sweep`(全背包)、`OmniAttributeApplier`、`BloodbathHandler`、`MercurySerpentHandler.onTick`、`AbyssalEarthHeartHandler` | ⚠️ **累计最大的"长期存在"的每 tick 开销**:每 tick 每人约 6~10 次背包/饰品扫描;未逐项改(§4) |
| `NumericHealthHud.carrying` | 每 **tick**(4.6.1 起;原来每帧) | 36 格 + 副手 + curios | 已优化 |
| `DemonBoneParticles` | 每 tick | 现在 10 tick 才查一次佩戴 | 本轮已修 |

### 1.4 每帧的 4 个 `ItemTooltipEvent` 处理器(单项明细)

| # | 处理器 | 第一行 | 最坏情况 |
|---|---|---|---|
| 1 | `ItemTooltips.onItemTooltip` | `stack.isEmpty()` | 1 次 `getKey` + ≤11 次 `RL.equals`;书袋 = 缓存命中 + `addAll`;雷霆卷轴 = `Component.translatable(...).getString()` + `removeIf`(只在悬停该物时) |
| 2 | `AddonRevivalLeafTooltip.onTooltip` | `try {` 后立即 `isStone(stack)`(物品实例比较) | 命中时 `add` 4 个**预建** Component(4.6.2) |
| 3 | `UnfinishedContent.onTooltip` | `stack.isEmpty()` | 物品实例比较 + 2 次 NBT `contains`;命中时遍历已有行做去重(`getString()`) |
| 4 | `ForgerGemAnvilHandler.onTooltip` | 配置读 | 2 次物品实例比较;命中时 add 1 个预建行 |

**要不要合并成 1 个 handler?不建议(本轮没做)**:
- 4 个处理器合起来最坏 ~0.3µs/帧;合并只省 3 次事件分发(~0.2µs/帧),对帧时间(16ms)影响是 0.002%。
- 合并会改变**行顺序语义**:现在顺序由"自动注册(ItemTooltips)在前、手动 register 在后"决定;当一件物品同时命中两个处理器(例如"带三个未完成附魔之一的大地之心")时,先后顺序变化会改变 tooltip 排版。
- 真正值得做的是"共享一次物品分类",而 4 个处理器用的分类维度互不相同(注册表 id / 类层次 / NBT 附魔 / 物品实例),没有重复劳动可省。

---

## 2. 本轮修掉的 Top 3(按量级)

### T1 `NumericHealthHud.render` —— 每帧 `String.format` + `font.width()` → 只在数字变化时重算
- **改法**:用 `(font 实例, round(health), round(max), round(absorption))` 做缓存键,存下文本与 `font.width(text)`;
  命中时直接 `drawString`。`font` 进键是为了资源包/语言重载后宽度能重算。
- **等价性**:`%.0f` 对非负数就是四舍五入,三个取整值相等 ⇒ 格式化结果**逐字节相同**;`drawString` 的 `x` 仍用同一个 `textWidth`。
- **收益**:`font.width` 是逐字形测量(1~3µs),`String.format` 各 ~1µs;跑图/站桩时血量不变,每帧从 ~3µs 降到 ~0.1µs。

### T2 大地护符的 `ItemProperties` / `ItemColor` 回调 —— 每帧零分配
- `EarthAmuletTint.coreColor`:不再 `effects()`(ArrayList + record),改为**直接遍历 NBT 列表**;`ResourceLocation.tryParse` + 注册表查找改为
  "效果 id → 颜色"缓存(`HashMap`,容量封顶 64,键是 NBT 里的原 String)。判定仍是"第一条能解析出的效果的颜色,否则白"。
- `EarthAmuletState.headIndex`:不再 `heads()`(ArrayList),直接遍历 `Heads` 列表;取第一个命中的头颅。
- `state()`:4.6.1 已有 `hasTag` 短路 + `hasHeads/hasEffects` 零分配判空。
- **收益**:带 2 个效果的护符从"每帧 2 次对象新建 + 2~4 次注册表查找 + 1 个 ArrayList"降到"每帧 2 次 HashMap 查找、0 分配"。

### T3 `EarthAmuletItem.appendHoverText` —— 效果行按 NBT 内容指纹缓存
- 新增 `contentKey(stack)`:只读 NBT(effects 的 id/lvl、heads、empowered)算一个 64 位指纹,**不分配**;
  指纹或语言实例变化时重建"药水效果行",否则复用。
- 头颅行改为直接遍历 NBT(不再 new `List<String>`)。
- **收益**:悬停一个有 2 条效果的护符,从每帧 ~8 个对象(ArrayList + 2 record + 2 String + StringBuilder + Component)降到 1 次指纹计算(几十纳秒)。

### bonus 1 粒子门控提前(`DemonBoneParticles`)
`%10` 计数器提到 `wearing()` 之前 → 每 10 tick 才做一次 curios 查询(原来每 tick 一次)。
唯一差别:计数器不再"只在佩戴时前进",戴上后第一缕烟可能早 0~9 tick 出现,之后节奏不变。

### bonus 2 缓存的语言失效(正确性,顺带)
4.6.2 的 `BODY_LINES`/`FIXED_LINES` 与 4.6.1 的书袋行缓存都可能跨语言重载保留旧文本
(`addFixed` 里做过一次 `getString()` 判定"该行是否为空")。现在用 `Language.getInstance()` 的**实例身份**做一代比对
(静态字段读,纳秒级),变化即清空/重建。

---

## 3. 逐条回答"缓存是不是反而变慢"

### 3.1 4.6.2 的 `ConcurrentHashMap<String, Component>` —— **净收益为正,不该改回去**
- 键全部是**字面量**(`line("add.engine.lightning")` 等),`String.hashCode` 第一次算完就缓存,`get()` 只是
  `spread(hash)` + 桶内一两次引用比较 ≈ 20~30ns,**零分配**。
- 它替掉的是:`"tooltip.enigmaticenhance." + key`(新建 String)+ `TranslatableComponent`(`add()` 里再
  `getString()`(StringBuilder + String)+ varargs 数组 + 新 Component + `withStyle` 复制)≈ 6 个对象/行/帧。
- 结论:`map.get` 比原来**快一个量级**。若还要再压,唯一空间是"改成静态 final 字段、连 hash 都不要"
  (省 ~20ns/行/帧,但会引入"类初始化早于语言加载"的风险,必须用嵌套 holder 类懒初始化;收益不足以抵消风险)。

### 3.2 4.6.1 的书袋行缓存 —— **命中率接近 100%,不会"每帧既建对象又塞 map"**
- 键是 `BagHudSync.cache()` 的**列表实例身份** + `synced` + 语言实例;没有 map、没有 `IdentityHashMap`、命中时零分配。
- `BagHudSync.cache` 只在**服务端发来新内容**时被 `accept()` 换成 `List.copyOf(...)` 的新实例,
  所以"悬停书袋时每帧都变"的情况不存在;唯一"每帧新实例"的假设不成立。
- 没有内存泄漏:缓存持有的就是客户端本来就在用的那个列表,不额外保留历史快照。

### 3.3 4.6.1 的 `kindOf()` —— **确实更快**
- 原来 11 个 `itemOrNull` = 11 × (`containsKey` + `getValue`) = **22 次 map 查找**,每次都要重算
  `ResourceLocation.hashCode`(namespace.hashCode + path.hashCode)。
- 现在 1 次 `ITEMS.getKey(item)`(反查表)+ 最多 11 次 `RL.equals`(先比引用、再比 hash);
  非目标物品通常在第 1~3 次比较就结束。**每帧少 21 次 map 查找 + 21 次 hash 计算**。

### 3.4 `NumericHealthHud` 的 tick 缓存 —— **不是白缓存**
缓存的**只是** `carrying()`(那是 36 格背包 + 副手 + Curios 遍历),不是渲染结果;渲染照常每帧画。
4.6.1 前:每帧 36 格扫描 + curios;现在:每 tick 一次。省掉的正是"每帧扫背包"这件事。

### 3.5 `EntityMoveNoClipMixin` —— **没有 `stream().anyMatch()`**
第一行就是 `if (!NoClipState.anyActive() && !NoClipState.hasForced()) return;`,
即一次 volatile 读 + 两次 `ConcurrentHashMap.isEmpty()`;`releaseForced` 里也先 `isEmpty()` 再算 `entity.getId()`。

### 3.6 每帧路径上现存的哈希查找(全部列出来,便于复核)
| 位置 | 次数/帧 | 键 | 说明 |
|---|---|---|---|
| `ItemTooltips` 常量行 | 0~2 | 字面量 String | 命中时 20~30ns/次 |
| `ItemTooltips` 书袋行 | 0 | — | 引用比较,无哈希 |
| `EarthAmuletTint` 颜色 | 0~N(护符效果条数) | NBT 里的 String | 20ns/次 |
| `EarthAmuletItem` 指纹 | 0 | — | 无哈希表 |
| `ItemTooltips.kindOf` | 1 次反查 | `Item` | 注册表反查 |
| `EarthAmuletItem/host` 头颅行 | 0~1 | lang path | 仅悬停护符且有头颅 |

---

## 4. 没改、但要明确交代的(以及为什么)

1. **16 个 `PlayerTickEvent` 处理器各自的背包/饰品扫描(每 tick 每人 6~10 次)**
   这是本模组**长期存在**的最大后台开销,单机时直接吃主线程。但它们分属 16 个功能,合并成"每 tick 一次背包+饰品快照"需要重写每个功能的判定入口(改造面 ≈ 半个模组),
   且单次扫描只有"36 次 `ItemStack.is` + 1 次 curios capability",总量约 1~3µs/tick —— **不足以造成可见掉帧**(0.02% 帧预算)。
   建议先按 §6 做 A/B 定位,不要盲改。

2. **书袋随身玩家的 16 次反射 `findBookInBag`**
   要减少只能①反射进扩展的 `AntiqueBagCapability`(新增"依赖扩展内部结构"的契约,风险高),
   或②把 `collect()` 降到每 5~10 tick 一次(tooltip 内容最多延迟 0.5s,属可见行为变化)。两者都需要你确认取舍。

3. **`LivingEntityAmuletMixin.getMobType` / `Superposition.hasCurio` 的按 tick 缓存**
   Curios 换装发生在同一 tick 内(`CurioChangeEvent`),加缓存会让"戴上僵尸头立刻变亡灵"延迟 1 tick。宁可不动。

4. **`MagicSwordAuraRenderer` 每实体 `endBatch`**
   这是**最像"能造成明显掉帧"的客户端代码**:它会把渲染批次提前刷掉,剑气越多、draw call 越碎。
   但它是 1.2.0 为修"白片串到别的实体"故意加的,去掉会回归旧 bug;`RenderType` 缓存已经把每帧的大对象分配去掉了。
   如果要进一步验证:在战斗场景里对比"有剑气 / 无剑气"(剑气来自未完成附魔,只有创造或 `unfinished.allowAcquisition=true` 时能拿到)。

5. **`HistoryMistItem.appendHoverText` 的 `readRecords`(每帧一次 NBT→Map)**
   同一套指纹缓存可以套用;但只在悬停该瓶时发生,优先级低于护符(护符是核心物品,出现频率高得多)。

6. **`MercurySerpentHandler.onLivingTick` 每实体每 tick 读一次配置**
   可把效果实例缓存成静态字段、先 `hasEffect` 再读配置,省一次配置读;量级 ~50ns × 实体数 × 20。留作后续。

---

## 5. 结论:**没有找到能解释"大幅掉帧"的每帧热点**

修复后,本模组在"不悬停物品、不携带刻度"时的每帧固定开销已接近 0:
- HUD:`carryingCached` 一次字段比较(1 次/帧)≈ 0;
- tooltip:仅在悬停时,< 0.3µs/帧;
- 护符回调:仅渲染护符时,< 0.2µs/帧;
- 渲染器:0(渲染类型已缓存,只付 GPU 本该付的绘制)。

即使按最坏值全加上,也只有 **~1µs/帧 ≈ 0.006% 的 16ms 帧预算**。所以如果掉帧幅度很大(例如 60→30fps),
原因更可能在下列三类,而不在 Java 侧每帧分配:

1. **GPU / 渲染管线**:每实体 `endBatch`(剑气)、`PureRuneFlashRenderer` 的 `Tesselator` 状态切换、粒子量;
   ——spark 的 CPU 侧可能看不到,建议同时看 F3 的 `C`(chunk update)/`E`(实体数)与帧时间分解。
2. **特定整合包交互**:某个第三方模组在我们的 4 个 tooltip 处理器之后又做重活(它每帧排队)、
   或我们的 mixin 目标方法被其它模组高频调用。
3. **内存/GC**:虽然本轮把每帧分配压到接近 0,但 `-Xlog:gc` 能直接证伪/证实这一条。

**下一步(强烈建议)**:用 spark 采样 30s,把 `enigmaticenhance` 包的占比截图发我;如果 spark 里本模组 <2%,
基本可以判定"不是 mod 的 Java 侧每帧开销",转向 1/2。

---

## 6. 用配置项隔离(你自己的 A/B 清单)

配置文件:`config/enigmaticenhance-common.toml`(键名即下表路径,改完 `/reload` 或重启)。

| 想验证什么 | 改哪个 | 期望 |
|---|---|---|
| 书袋 tooltip + 服务端每 tick 同步 | `bagHud.bagTooltipEnabled = false` | 悬停书袋不再有额外行,服务端每 tick 的 `collect()` 直接返回 |
| 锻造者宝石 tooltip | `forgerGem.tooltipEnabled = false` | 少一个 tooltip 处理器分支 |
| 冒险者之证/绝望者证章 tooltip | `books.miningTooltip = false` | 少两行常量行 |
| 书本逐件开关 tooltip | `books.enabled = false` | 书本类物品不再走 `BooksHandler.isTargetBook` |
| 救赎之戒的假耐久条谓词(每帧 × 渲染物品) | `ring.hideBetrayalBar = false` | 该 mixin 直接 no-op |
| 日志参数求值 | `debugLog = false`(默认) | 确认你没打开;打开时每 tick 会有字符串拼接 |
| 生命刻度 HUD | 把「生命刻度」放箱子 | HUD 路径完全不执行 |
| 恶魔手骨粒子 | 不佩戴即可 | 顺带验证粒子路径 |
| 每 tick 处理器群(服务端线程) | 依次关 `enhancements.spiritLightEnabled` / `sunBottleEnabled` / `warFogEnabled` / `styxWaterEnabled` / `unbreakableEnchantEnabled` / `mercurySerpentEnabled` / `bloodCalamityEnabled` / `abyssalEarthHeart.enabled` | 若某一项关掉后掉帧明显缓解,再针对性优化(告诉我即可) |
| 剑气渲染 | 不用魔法剑气附魔(未完成内容,默认拿不到) | 验证 `endBatch` 那条 GPU 嫌疑 |

如果方便,请再给两条信息,能直接缩小范围:
1. **单机(集成服务器)掉帧 vs 连专用服务器不掉帧?** —— 前者指向服务端 tick(§1.3),后者指向渲染。
2. **"静止不动" / "跑图" / "悬停物品" / "战斗"** 哪种场景掉得最狠 —— 直接对应到 §1 的不同行。
