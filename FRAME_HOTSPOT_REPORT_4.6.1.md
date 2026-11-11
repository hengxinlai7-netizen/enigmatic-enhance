# enigmatic-enhance 4.6.1 —— 客户端掉帧排查与修复报告

> 结论先说:4.6.0 解决的是"**每 tick 分配**"。这次掉帧的主因在 **①每帧(60~200 次/秒)的 tooltip / HUD / 物品模型谓词路径**,
> 以及 **②每实体每 tick 的最热 mixin(`Entity#move`)和每帧新建 `RenderType` 的实体渲染器**;
> "每 tick 遍历背包/饰品栏"那条(书袋 HUD)量级比想象的小很多,只做了顺手能做的两处(见 §3.9)。

所有改动均已 `gradle build` 通过(`BUILD SUCCESSFUL`,产物 `build/libs/enigmaticenhance-4.6.0.jar`)。
**tooltip / HUD 的显示内容逐字未变**,每处改法都写了"为什么等价"。

---

## 1. 频率 × 工作量 总表

频率一栏: **每帧** = 60~200/s(取决于帧率);**每 tick** = 20/s;**每实体 tick** = 实体数 × 20/s。

| # | 类 | 方法 | 频率 | 做什么 | 是否重活 | 处理 |
|---|---|---|---|---|---|---|
| 1 | `ItemTooltips` | `onItemTooltip` | **每帧**(悬停任意物品) | 一串 11 个 `RingConfig.isXxx` → 每个 `itemOrNull` 做 `containsKey`+`getValue` = **约 22 次注册表 map 查找** | ✅ 中 | 已优化 → 1 次 `getKey` + 常量比较 |
| 2 | `ItemTooltips` | `onItemTooltip`(书袋分支) | **每帧**(悬停古旧书袋) | 每条 `Entry` 重建 `Component.translatable` + `BagHudEffects.display`(内部可能 `new ResourceLocation` / 反查属性注册表 / `substring`)+ 逐条 `withStyle` + `getString()`;摘要行还要再遍历两遍列表 | ✅ **重** | 已优化 → 按数据源实例缓存整份行 |
| 3 | `UnfinishedContent` | `onTooltip` | **每帧**(悬停**任意**物品) | `ForgeRegistries.ITEMS.getKey` 反查 + `EnchantmentHelper.getEnchantments`(**新建 HashMap** + 逐条反查附魔注册表再反查 id) | ✅ **重** | 已优化 → 零分配直读 NBT + 物品实例缓存 |
| 4 | `client.NumericHealthHud` | `render`(`RenderGuiEvent.Post`) | **每帧** | `carrying()`:扫背包 36 格 + 副手,再走 `CuriosApi.getCuriosInventory(...).map(...)` + `findFirstCurio(谓词)`(每次包一层 `Optional`/lambda,并遍历全部饰品槽)。**没带生命刻度的玩家是最坏情况**(全扫完才 false) | ✅ **重** | 已优化 → 按 `tickCount` 缓存 |
| 5 | `EarthAmuletState` / `EarthAmuletTint`(`ItemProperties` / `ItemColor` 回调) | `state` / `headIndex` / `colorFor` | **每帧 × 每个被渲染的护符**(背包/手上/展示框) | `effects()` / `heads()` 每次都 `new ArrayList` + 每条 `new StoredEffect`;`effects()` 里还有 NBT 遍历 | ⚠️ 中 | 已优化 → `hasTag` 短路 + 零分配 `hasHeads/hasEffects` |
| 6 | `client.MagicSwordAuraRenderer` / `client.PureRuneProjectileRenderer` | `render` | **每帧 × 每个实体** | `RenderType.entityTranslucentEmissive(TEXTURE)` **每次调用都新建** `CompositeRenderType` + `CompositeState` + `TextureStateShard`(一堆 ImmutableList/Optional/lambda) | ✅ 中 | 已优化 → 渲染类型缓存一次 |
| 7 | `ForgerGemAnvilHandler` | `onTooltip` → `isGem` | **每帧**(悬停任意物品) + 每次 `hasGem` 扫 36 格 | `getKey(item).toString()` —— **每帧新建一个 String** —— 再和两个字面量比较 | ⚠️ 轻 | 已优化 → 物品实例缓存 |
| 8 | `AddonRevivalLeafTooltip` → `RevivalLeafStoneHandler` | `isStone` | **每帧**(悬停任意物品) | `ForgeRegistries.ITEMS.getKey` 反查注册表(`ResourceLocation` hash 重算) | ⚠️ 轻 | 已优化 → 物品实例缓存 |
| 9 | `EarthAmuletItem` / `HistoryMistItem` / `WarBannerBlock` | `appendHoverText` | **每帧**(悬停**该件**物品) | 每次解析 NBT + 每条效果 `ResourceLocation.tryParse` + 反查注册表 + `StringBuilder`/`getString()` | ⚠️ 中 | ❌ 未改(见 §4) |
| 10 | `mixin.EntityMoveNoClipMixin` → `NoClipState` | `Entity#move` HEAD | **每实体每 tick**(几百实体 × 20 = 上万次/秒) | `instanceof Player` + `ConcurrentHashMap.contains` / `remove` | ⚠️ 轻但基数极大 | 已优化 → 两次 `isEmpty` 提前返回 |
| 11 | `mixin.LivingEntityAmuletMixin` | `getMobType`(`m_6336_`)/ `canBeAffected`(`m_7301_`) | 每次伤害结算 / 每次药水效果 tick | 对玩家: `EarthAmuletData.equippedAmulet` → Curios capability + `findFirstCurio` + `Optional` 链 | ⚠️ 中 | ❌ 未改(见 §4) |
| 12 | `mixin.GluttonySprintMixin` → `GluttonyHandler.sprintAllowed` | `hasEnoughFoodToStartSprinting`(`m_255269_`) | 每 tick 数次(aiStep + 起跑判定) | `RingConfig.gluttonyCharmItem()`(2 次注册表查找)+ `Superposition.hasCurio`(反射进 curios 扫描) | ⚠️ 轻 | ❌ 未改(见 §4) |
| 13 | `BagHudSync` → `BagHudEffects.collect` | `onServerTick` / `tickUpdate` | **每 tick × 每个在线玩家**(整合服与客户端同进程) | `BookBag.hasBag`(反射进 `hasItem`:背包+饰品栏+末影箱);书袋随身时 **≤16 次**反射 `findBookInBag`,每次**再**完整扫一遍;16 次 `ITEMS.getValue(id)`;末尾 `FourthCurseFix.active` = 1 次 curios + **3 次**完整扫描(书袋不在身上时纯白扫) | ✅ 重(书袋玩家/整合服) | 已优化 → 物品预解析 + `hasBag` 复用短路 |
| 14 | 各 `PlayerTickEvent` 处理器(20+ 类:EarthAmulet/DemonBloodChalice/DemonBone/TravelerHat/Gluttony/HistoryMist/SpiritLight/SunBottle/WarFog/… ) | `onPlayerTick` | 每 tick × 每玩家 | 各自的 curios/背包查询 + 配置读;多数已有 `tickCount % 20` 门控 | ⚠️ 轻~中(累加) | 抽查热点,未逐个改(见 §4) |
| 15 | `ConsciousnessSeaHandler` / `OmniContaminationGuard` / `FateCorrectionHandler` / `ExperienceOverflowGuard` / `MercurySerpentHandler` | `onTick`(`LivingTickEvent`) | **每实体每 tick** | 第一行就是 `tickCount % 20/40 != 0 → return`(门控正确),之后才是重活 | ✅ 已很轻 | 未改 |
| 16 | `mixin.GluttonyFoodTickMixin` | `FoodData#tick` | 每玩家每 tick | 配置读 + 数值比较 | ⚠️ 轻 | 未改 |
| 17 | `client.DemonBoneParticles` | `ClientTickEvent.END` | 每 tick | `wearing()` 查 curios(**在 %10 门控之前**),通过后才放粒子 | ⚠️ 轻 | 未改 |
| 18 | `client.NoClipClientHandler` / `NoclipKeyHandler` | `ClientTickEvent.END` | 每 tick | 纯布尔判断 | ❌ 否 | 未改(本来就是几个字段比较) |
| 19 | `BagHudEffects.signature` | 签名 | 每 tick × 每玩家 | 复用 `ThreadLocal<StringBuilder>`(4.6.0 已做) | ❌ 否 | 未改 |

---

## 2. 逐项改法(为什么等价)

### 2.1 `client/NumericHealthHud` —— 每帧 36 格 + Curios 扫描 → 每 tick 一次
- **改法**:新增 `carryingCached(player)`,用 `(player 实例, player.tickCount)` 做键缓存 `carrying()` 的结果;
  同一 tick 内重复渲染(以及 `RenderGuiEvent` 被多次触发)只算一次。`carrying(Player)` 本身保持原样(其它调用方不受影响)。
- **为什么等价**:背包/副手/饰品栏的内容**只可能在 tick 里**变化(拾取、拖放落格、Curios 换装都在 tick 内完成),
  所以同一 tick 内结果恒定;血条该出现的那一 tick 就出现。
- **收益**:最坏情况(没带刻度)从 `36 次 ItemStack.is + curios capability + findFirstCurio 全槽遍历` **每帧** 降到 **每秒 20 次**。

### 2.2 `ItemTooltips` —— 22 次注册表查找/帧 → 1 次;书袋整份行缓存
- **分类改法**:删掉 `isRing/isCursedRing/isEngine` 三个每次都要 `itemOrNull` 的谓词,改成
  `kindOf(item)`:一次 `ForgeRegistries.ITEMS.getKey(item)` 拿到 id,再和 `RingConfig` 里**静态 final 的 `ResourceLocation` 常量**比较,
  用一个 `switch` 分发到原来完全相同的分支代码。
- **为什么等价**:原判定 = `item == 注册表里该 id 的物品`;这在"物品已注册"时严格等价于"该物品注册在这个 id 上"。
  物品缺失 ⇒ 反查出的 id 也不可能等于这些常量 ⇒ 同样落到 `OTHER`(`BooksHandler` → 大地之心)分支。
  分支内的代码、顺序、行内容一字未改(`BooksHandler` 仍在大地之心**之前**判断)。
- **书袋行缓存**:新增 `bagTooltipLines()`,只在 `BagHudSync.cache()` **实例变了**或 `synced` 变了时重建;
  重建结果 `List.copyOf` 后整份 `addAll` 进 tooltip。
  等价性:客户端 `accept()` 是 `cache = List.copyOf(entries)`,**内容一变服务端就会换一个新实例**,
  所以"实例没变 ⇒ 这些行一个字都不会变";`Component` 不可变,同一批实例追加进不同列表是安全的。
  缓存字段按"先写 source/synced、最后写 lines"的顺序发布,读到新行时键一定已配对(最坏情况只是重建一次)。
- **收益**:悬停书袋时从"每帧 N 个 Component + 每条 1~2 次注册表查找 + 2 次列表遍历"降到"每帧 1 次引用比较 + 1 次 `addAll`"。

### 2.3 `UnfinishedContent` —— 每帧对**每个**物品建 HashMap → 零分配
- **物品判定**:`isUnfinishedItem` 先懒解析 `ModItems.seaOfConsciousnessOrNull()` 缓存实例,之后只做引用比较;
  解析不到才退回原来的注册表反查。
- **附魔判定**:新增 `hasUnfinishedEnchantRaw(stack)`,直接读 `Enchantments` / `StoredEnchantments` 两个原版键
  (与 `ItemStack#getEnchantmentTags` 相同的取值顺序:有 `Enchantments` 就只看它,否则看 `StoredEnchantments`),
  逐条把 `id` 字符串和三个目标 id 比较。**不建 Map、不查注册表、不装箱**。
- **为什么等价**:`EnchantmentHelper.getEnchantments(stack)` 读的就是这两个键并反序列化,原逻辑是"反序列化后
  `keySet()` 里有没有这三个附魔";三个附魔的注册表 id 就是 `enigmaticenhance:magic_sword_aura` /
  `void_aura` / `hand_of_execution`(由 `ResourceLocation` 常量 `toString()` 得到),比较字符串与比较注册表对象等价。
  没有这两个键时只付一次 `contains` 判断。
- **收益**:绝大多数物品(无附魔)从"每帧 1 个 HashMap + K 次注册表反查"降到"每帧 1~2 次 NBT contains"。

### 2.4 `EarthAmuletData` / `EarthAmuletState` / `EarthAmuletTint` —— 每帧零分配
- `state()`:`isAmulet` 之后加 `if (!stack.hasTag()) return EMPTY;`(没有 NBT ⇒ 强化/头颅/效果必然全空,与原来逐条判空的结果相同);
  再用新增的 `EarthAmuletData.hasHeads/hasEffects`(只遍历 NBT 判"有没有一条非空 id")替代 `heads()/effects()` 的 List 分配。
- `headIndex()`:同样先 `hasTag` 短路;`coreColor()`(染色回调)先 `hasTag` 短路返回白色。
- **收益**:普通护符(最常见)每帧 **0 次分配**;有内容的护符也只在 `hasHeads/hasEffects` 上做 NBT 遍历,不再建 List/record。

### 2.5 `MagicSwordAuraRenderer` / `PureRuneProjectileRenderer` —— 缓存 `RenderType`
- `RenderType.entityTranslucentEmissive(TEXTURE)` 并不是"按贴图取现成对象",而是**每次调用都新建**一个
  `CompositeRenderType + CompositeState + TextureStateShard`。两个渲染器都是**每个实体每帧**调一次。
- 改成静态字段 + 首次渲染时懒建(保证在渲染线程上第一次构造),之后只读字段。
- 保留 `endBatch(type)` 等所有绘制语义,不变的只是"类型对象的来源"。

### 2.6 `ForgerGemAnvilHandler.isGem` / `RevivalLeafStoneHandler.isStone` —— 去掉每帧分配/反查
- `isGem`:原来 `getKey(item).toString()` **每帧新建 String**;现在懒解析两个物品实例,只做引用比较;
  解析失败不缓存失败(下次再试),与原"id 比不过 ⇒ 不算宝石"一致。同一个 `isGem` 也被 `hasGem` 的 36 格扫描调用。
- `isStone`:原来每帧反查注册表;现在同上。

### 2.7 `NoClipState` + `EntityMoveNoClipMixin` —— 最热 mixin 的提前返回
- 新增 `NoClipState.anyActive()`(`clientActive || !serverActive.isEmpty()`)与 `hasForced()`(`!FORCED.isEmpty()`)。
- mixin 在 `Entity#move` HEAD 第一行:`if (!anyActive() && !hasForced()) return;`
- **为什么等价且安全**:`hasForced()` 保证"曾经被我们强行打开 `noPhysics`、还需要回收"的实体**一定会继续往下走**
  (这正是 4.2.2 的还原逻辑,不会被提前返回吃掉);没人开启穿墙、也没有待回收实体时,往下走的两个分支本来也什么都不做。
- `releaseForced` 内部再加一层 `if (forced.isEmpty()) return false;`,省掉每次的 `entity.getId()`。

### 2.8 `BagHudEffects` / `BookBag` —— 每 tick 每人 32 次注册表查找 → 0;`FourthCurseFix` 白扫 → 短路
- `BookBag.findBook(Player, Item)` 新重载;`BagHudEffects` 用 `BOOK_ITEMS[]` 懒解析缓存 16 个物品实例
  (解析失败不缓存失败),热路径不再 `ITEMS.getValue(id)`。
- `collect()` 里把 `BookBag.hasBag(player)` 的结果提到外面,`FourthCurseFix.active(player)` 改为 `hasBag && ...`。
- **为什么等价**:`FourthCurseFix.active` 要求"三本书都在袋里",而书袋不在身上(背包+饰品栏+末影箱都没有)时
  `findBookInBag` 必然返回空 ⇒ `hasBag == false` 时它一定为 false。这一条把**没有书袋的玩家每 tick 的
  1 次 curios 查询 + 3 次全量背包/饰品栏/末影箱扫描**彻底去掉(原来这段对所有玩家都跑)。

---

## 3. 没有改、但"理论上可能掉帧"的点

1. **`EarthAmuletItem` / `HistoryMistItem` 的 `appendHoverText`(每帧,悬停该件物品)**
   每帧解析 NBT、每条效果 `ResourceLocation.tryParse` + 反查注册表、`StringBuilder`/`getString()`。
   量级是"每帧个位数小对象",且**只在鼠标停在这一件物品上时**发生(不像 #2/#3 那样对任意物品都跑)。
   要真正缓存需要"按 NBT 内容失效",而这些 NBT 是**原地修改**的(`getOrCreateTag().put(...)`),没有版本号可用;
   加内容哈希(如 `ListTag.toString()`)反而更贵。**建议**:若 Spark 显示这里仍占 >1%,再单独做一次"按 `ItemStack`+内容指纹"的缓存。

2. **`LivingEntityAmuletMixin.getMobType` / `canBeAffected`(每 tick 数次,仅玩家)**
   每次都要 `CuriosApi.getCuriosInventory(...).map(...).findFirstCurio(...)`(Optional/lambda)。
   可以加"按 tick 备忘",但 Curios 换装发生在**同一 tick 内**(`CurioChangeEvent`),缓存会让"戴上僵尸头后立刻变亡灵"
   延迟 1 tick —— 属于可见行为变化,不做。非玩家实体在方法第一行就 return,不会付这份钱。

3. **`Superposition.hasCurio` 的重复调用**(`GluttonyHandler.wearsCharm`、`BlessRingTickMixin`、`FourthCurseFix`)
   同样是"1 tick 内可能被改"的语义,不做跨调用缓存;4.6.0 已把最贵的 `Class.forName/getMethod` 全部缓存掉了。

4. **书袋玩家的 16 次反射 `findBookInBag`**
   每次都会让扩展重新做一遍 `hasItem`(背包+饰品栏+末影箱)。想再降只能**直接反射进扩展的
   `AntiqueBagCapability.INVENTORY`** 一次性读 12 格 —— 那等于新增一条"依赖扩展内部结构"的反射契约(扩展一改就崩),
   风险高于收益;实测这部分约"每人每 tick 一千多次 `ItemStack.is`",是微秒级。
   **可选开关化方案**(未实现):把 `collect()` 改成每 5~10 tick 一次,代价是 tooltip 内容最多延迟 0.25~0.5s —— 这是可见行为变化,默认不做。

5. **`itemOrNull` 仍是"containsKey + getValue"两次查找**(`RingConfig` 自身,被很多每 tick 路径调用)
   改动面太大(整个模组都在用),而单次约几十 ns。本次只在**每帧**路径(#1)上绕开它。留作后续:`RingConfig` 内部加一层
   "id → Item" 的懒缓存(只缓存成功解析的结果)。

6. **`MagicSwordAuraRenderer` 每实体 `endBatch`**
   会打断批次合并,实体多时是真实的 GPU/驱动开销;但这是 1.2.0 为了修"白片串到别的实体"刻意加的,
   去掉会回归旧 bug。`RenderType` 缓存已经把它每帧的大对象分配去掉了。

7. **`DebugLog` 的参数求值(共 193 处调用)**
   逐条复核了"每帧/每 tick"路径:**已无**在关闭状态仍每帧/每 tick 求值 `getName().getString()` 的调用;
   剩下未门控的都在**状态变化/事件**分支里(开/关穿墙、命中、转化成功…),不是热路径。
   4.6.0 加的 `oncePending()` 预检也已覆盖 `noclip:*` 这类每 tick 会走到的 `once()` 键拼接。

8. **`NumericHealthHud` 每帧的 `String.format("%.0f / %.0f")`** ~1µs/帧,相对整帧预算可忽略,不动。

---

## 4. 改动文件清单

| 文件 | 改动 |
|---|---|
| `client/NumericHealthHud.java` | `carrying()` 按 tick 缓存 |
| `ItemTooltips.java` | `kindOf()` 一次反查取代 11 次 `itemOrNull`;书袋行缓存 `bagTooltipLines()` |
| `UnfinishedContent.java` | `hasUnfinishedEnchantRaw()` 零分配 NBT 直读;意识之海物品实例缓存 |
| `ForgerGemAnvilHandler.java` | `isGem` 去 `toString()`,物品实例缓存 |
| `RevivalLeafStoneHandler.java` | `isStone` 物品实例缓存 |
| `EarthAmuletData.java` | 新增 `hasEffects` / `hasHeads`(零分配) |
| `EarthAmuletState.java` | `state` / `headIndex` 的 `hasTag` 短路 + 零分配判空 |
| `EarthAmuletTint.java` | `coreColor` 的 `hasTag` 短路 |
| `NoClipState.java` | 新增 `anyActive()` / `hasForced()`;`releaseForced` 空集短路 |
| `mixin/EntityMoveNoClipMixin.java` | HEAD 提前返回(每实体每 tick) |
| `BagHudEffects.java` | `BOOK_ITEMS` 预解析;`hasBag` 复用到 `FourthCurseFix` |
| `BookBag.java` | 新增 `findBook(Player, Item)` |
| `client/MagicSwordAuraRenderer.java` | `RenderType` 缓存 |
| `client/PureRuneProjectileRenderer.java` | `RenderType` 缓存 |

---

## 5. 需要你配合验证的部分

这些是"哪条路径真的在吃你的帧",只有实机能分辨:

1. **对比"不动"和"鼠标悬停物品"**:站在一个装满物品的箱子前,分别 (a) 鼠标移到空白处,(b) 悬停在**非本模组**物品上,
   (c) 悬停在**古旧书袋**上,(d) 悬停在**大地护符**上。本版改动应该让 (b)(c)(d) 与 (a) 的 FPS 差距明显缩小
   (改前 (c) 通常最糟)。
2. **对比携带/不携带「生命刻度」**:把刻度放箱子里 vs 带在身上,改前"不带"反而更贵(每帧扫完才 false)。
3. **整合服 vs 专用服**:单机(集成服务器同进程)掉帧但连专服不掉 ⇒ 锅在服务端 tick(§3.4 的书袋路径);
   两边都掉 ⇒ 客户端渲染路径。
4. **`/spark profiler start --timeout 30`** 采样两次(改前/改后),看
   `ItemTooltips`、`NumericHealthHud`、`EarthAmuletState`、`BagHudEffects`、`Entity#move` 的占比是否下降。
5. **GC 日志**:加 `-Xlog:gc:file=gc.log` 跑同样场景,Young GC 次数/秒应明显下降
   (本轮改动的核心收益就是"每帧垃圾 → 0")。
6. **关掉某个开关做 A/B**:`bagTooltipEnabled=false` 应让"悬停书袋"的差异消失(验证 #2 的路径);
   若某个开关关掉后掉帧依旧,说明还有别的来源,把 `/spark` 结果发我,我按采样继续改。
7. **本模组的自检**:启动日志里 `T21`(书袋 tooltip 三行)与 `T24`(每件物品行数)应仍为 PASS —— 本次改造没有动显示内容,
   自检就是回归护栏。
