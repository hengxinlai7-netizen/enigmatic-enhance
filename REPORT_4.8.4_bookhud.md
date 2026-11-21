# enigmatic-enhance 4.8.4 — 书袋兼容 + 效果显示重做

产物：`C:\Users\hengx\Downloads\[神秘遗物增强] enigmaticenhance-4.8.4.jar`
**1,237,029 字节**，sha256 **60B8C2A724CD616F5814C934102131F58FFF6B509315F5EB56F5D054848E3078**，506 个条目。
版本号未改(4.8.4)。

---

## 1. 新增 / 修改文件

| 文件 | 类型 | 说明 |
|---|---|---|
| `src/main/java/.../BookEffects.java` | **新增** | 统一「书 id → 效果行」注册表(22 本外部书)+ 配置反射读取 + 数字格式化 |
| `src/main/java/.../BagHudEffects.java` | 修改 | 改为**枚举袋内全部书**;新增 `baghud.book.*` 渲染分支;属性名本地化;乘法转百分比;tick 转秒;排序去重;`MAX_LINES=12` |
| `src/main/java/.../BookBag.java` | 修改 | 新增 `booksInBag(Player)`:反射 `AntiqueBagCapability.INVENTORY` → `getSlots()/getBook(i)` |
| `src/main/java/.../ItemTooltips.java` | 修改 | 超过 12 条折成「还有 N 条(无加成)」 |
| `src/main/resources/assets/enigmaticenhance/lang/zh_cn.json` | 修改 | 270 → **306** 键(新增 32 条书袋书文本 + 时间/兜底键) |
| `src/main/resources/assets/enigmaticenhance/lang/en_us.json` | 修改 | 262 → **298** 键(同上,英文) |
| `src/main/java/.../FlowerSelfTest.java` | 修改 | 断言跟随新格式(本地化属性名 / `+10%` / 秒) |

没有新增任何对 `com.qoocies.*` 的 import —— 全部走 `Class.forName` 字符串。

---

## 2. 「是否在袋生效」的判定依据(通用机制)

三条路径,前两条来自神秘遗物扩展,第三条来自诡厄遗物自己:

1. **`SuperAddonHandler.findBookInBag(Player, Item)`** —— 直接扫书袋能力;
2. **`SuperpositionHandler.hasItem(Player, Item)` + `MixinSuperpositionHandler`** ——
   `hasItem` 返回 false 时会调 `findBookInBag`,所以"效果代码里写 `hasItem(某书)`"= 在袋也生效;
   前提是这本书能被放进袋子:`AntiqueBag.isBook(stack)` = `extraBookList.contains(id) || stack.is(ItemTags.BOOKSHELF_BOOKS)`。
   本体 14 本里有 **11 本** 写进了各自 jar 的 `data/minecraft/tags/items/bookshelf_books.json`(已逐条核对);
3. **诡厄遗物**:`PassiveBookHandler#onPlayerTick` → `InventoryQueryUtils.collectItemsFromInventoryAndBookBag`
   → `GoldenBookPassives.collect` → **`EnigmaticAddonsBookBagHelper.getAllItems`**,
   后者显式遍历 `AntiqueBagCapability.INVENTORY` 的每个槽位 ⇒ 诡厄的书在袋里**确实会 tick 被动**。

> 这条是关键:诡厄自己就为古旧书袋写了适配类
> `com.qoocies.goeticlegacy.utils.enigmaticaddons.EnigmaticAddonsBookBagHelper`,所以"放袋里有没有用"不需要猜。

---

## 3. 22 本书逐条对照表

### 3.1 神秘遗物本体(8)

| 书 | 在袋生效 | 依据(类/方法) | 效果文本(zh_cn) | 数值来源 |
|---|---|---|---|---|
| 启示之证 `the_acknowledgment` | **生效** | `AddonEventHandler#onLivingHurt` → `findBookInBag` → `victim.setSecondsOnFire(3)` | 命中时点燃目标 3 秒 | 常量 3 |
| 倒转之启 `the_twist` | **生效** | 同上 → `ModifyDamageBaseOne(event,0.1f)` + `isTheCursedOne` + `isBossOrPlayer`;`ModifyDamageBaseOne` = `amount*(1+m)` | 对 BOSS/玩家伤害 +10%(需七咒之人) | 常量 0.1 |
| 无止之言 `the_infinitum` | **生效** | 同上 `0.2f`;吸血段 `lifesteal += amount*0.1f` | ①对 BOSS/玩家伤害 +20%(需义人) ②伤害的 10% 转为治疗 | 常量 |
| 野猎指南 `hunter_guidebook` | **生效** | `EnigmaticEventHandler#onLivingHurt`:tapable 宠物受伤 → 取消并转移给 owner,半径/减免取 `HunterGuidebook.effectiveDistance` / `synergyDamageReduction` | ①宠物受到伤害转移给你(半径 %s 格) ②与兽友指南同在时转移伤害减少 %s%% | config `HunterGuide.EffectiveDistance`=24 / `SynergyDamageReduction`=50% |
| 兽友指南 `animal_guidebook` | **生效** | `EnigmaticEventHandler#onLivingHurt`(攻击 Animal 时取消)+ `CursedRing` 第二重咒中立 + `PetGuidebook#isProtectedAnimal` | ①攻击不会伤害动物;周围疣猪兽被动 ②七咒下可驯服动物中立 ③与野猎指南同在时转移伤害减少 %s%% | 同上 config |
| 腐化书籍 `corrupted_tome` | **无加成** | 仅战利品表 + `RevelationTome`(阅读给经验/知识点),全代码无 `hasItem` 判定 | 仅可存放,无加成 | — |
| 破烂书籍 `tattered_tome` | **无加成** | 同上 | 仅可存放,无加成 | — |
| 枯萎书籍 `withered_tome` | **无加成** | 同上 | 仅可存放,无加成 | — |

### 3.2 神秘遗物扩展(6)

| 书 | 在袋生效 | 依据 | 效果文本 | 数值来源 |
|---|---|---|---|---|
| 恩惠之典 `the_bless` | **生效** | `AddonEventHandler#onLivingTick` → `player.clearFire()`;`#onLivingHurt` → `player.invulnerableTime = TheBless.invulnerableTime` | ①自身不会着火 ②受击后无敌帧 %s tick | config `TheBless.InvulnerableTime`=40 |
| 生灵颂词 `ode_to_living` | **生效** | `OdeToLiving#isProtectedAnimal`(`getItem` 失败后走 `findBookInBag`)+ `AddonEventHandler#onLivingHurt` + `#onLivingChangeTarget` + `MixinNearestAttackableTargetGoal` | ①受保护动物不伤害你(手持失效) ②七咒下中立动物不主动攻击 ③与野猎指南同在时转移伤害减少 %s%% | config `OdetoLivingBeings.SynergyDamageReduction`=75% |
| 虚伪审判 `false_justice` | **生效** | `MixinPlayer#hurtMix` / `MixinLivingEntity#hurtMix`:`isTheCursedOne && hasItem(FALSE_JUSTICE)` → `DamageSource` 换成 `EnigmaticAddonDamageTypes.FALSE_JUSTICE` | 你受到与造成的伤害都转为「真实伤害」(需七咒之人) | 定性(无数字) |
| 血腥狩猎手册 `sanguinary_handbook` | **生效** | `AddonEventHandler#onLivingHurt`(宠物伤害加成)+ 同方法(宠物继承主人效果) | ①宠物攻击伤害 +%s%% ②佩戴血腥勇气纹章/千咒卷轴时宠物同步获得你的部分效果 | config `DamageMultiplier`=0.25 → 25% |
| 神化之书 `bless_amplifier` | **无加成** | 仅 `AddonEventHandler` 的**合成台**判定(升级附魔) | 仅可存放,无加成 | — |
| 虚无之书 `void_tome` | **无加成** | 仅 `SpecialLootModifier` 掉落 + 使用给奥术卷轴槽 | 仅可存放,无加成 | — |

### 3.3 诡厄遗物(8)

| 书(实际注册 id) | 在袋生效 | 依据 | 效果文本 | 数值来源 |
|---|---|---|---|---|
| 烈风之书 `gale_tome` | **生效** | `WindBookItem#onLivingFall`:`context(player)` 存在 → `event.setCanceled(true)` | 免疫摔落伤害 | 代码 |
| 过往之书 `antiquity_folio` | **生效** | `BygoneBookItem#onPassiveTick`(加 MAGIC_DAMAGE_MULTIPLIER)/ `#onChangeTarget`(下界敌对中立) | ①法术伤害 +%s%% ②下界敌对生物最大生命不超过你的 %s 倍时不主动攻击你 | config `antiquityMagicDamageBonus`=0.15 / `antiquityNeutralHealthThreshold`=2.5 |
| 一千零一夜 `one_thousand_and_one_nights` | **生效** | `VizierBookEffects`(恼鬼聚晶 → 折磨仆从) | 恼鬼聚晶改为召唤 %s 个折磨仆从(持对应法杖再 +0~%s) | config `vizierTormentorBaseCount`=3 / `StaffExtraMax`=2 |
| 窥探之书 `the_peeper` | **生效** | `PeepingBookEffects`(攻击概率触发虚空震荡) | 攻击时 %s%% 触发虚空震荡弹、%s%% 触发虚空震荡波(半径 +%s 格) | config `peeperVoidBombChance`=0.12 / `VoidShockChance`=0.3 / `ExtraRadius`=0.4 |
| 枯黄历 `the_seer_almanack` | **生效** | `DivinationBookEffects`(巫毒之诅咒标记) | 巫毒之诅咒每级使目标承伤 +%s%% | config `seerMarkDamagePerLevel`=0.25 |
| 恶意之书 `ill_will` | **生效** | `GodfatherBookItem#onPassiveTick`(训练速度)/ 紫袍强化 | ①刌民仆从训练速度 ×%s ②新刌民额外 %s%% 概率紫袍强化 | config `illWillTrainSpeedMultiplier`=3 / `illWillMagicChance`=0.6 |
| 秽经 `the_unholy_writ` | **生效** | `PatriarchBookEffects` / `PatriarchFireDamage`(狱炎) | ①狱炎伤害 ×%s ②狱炎额外造成目标最大生命 %s%% 的伤害 | config `patriarchHellfireMultiplier`=2.0 / `patriarchMaxHealthDamage`=0.015 |
| 蜘蛛标本册 `spider_specimen_book` | **生效** | `SpiderSpecimenEffects` / `SpiderSpecimenBookItem` | ①荒野法术伤害 +%s%% ②荒野仆从伤害 +%s%%、减伤 %s%% ③蜘蛛最大生命 ≤ 你 %s 倍时中立 | config `spiderSpellDamage`=0.5 / `ServantDamage`=0.5 / `ServantResistance`=0.25 / `NeutralHealthThreshold`=2.5 |

> **重要更正**:任务书里给诡厄 8 本书的物品 id(`wind_book`/`bygone_book`/…)不是真实注册名。
> 真实 id 见上表(来自 `ModItems.java`:`ITEMS.register("gale_tome", WindBookItem::new)` 等),
> 已用 `assets/goeticlegacy/lang/en_us.json` 的 `item.goeticlegacy.<id>` 逐条交叉验证(8/8 命中)。

### 3.4 本模组自有 7 件(不在本次改写范围,文案保持原样)

`bedtime_reading` / `history_mist` / `spirit_light` / `styx_water` / `war_fog` / `bottled_sun` / `fishing_haul_bottle`
—— 继续用原有 `baghud.effect.*` 键,**文本逐字未动**。

---

## 4. 效果显示优化(5 项)

| # | 项 | 前 | 后 |
|---|---|---|---|
| 1 | 属性名 | ` · 魔法石英花:goety:spell_potency +0.1(乘法·基础值),剩余 3 tick` | ` · 魔法石英花:法术强度 +10%(乘法·基础值),剩余 3 tick`(`Attribute#getDescriptionId()` → `Component.translatable`,取不到回退原 id) |
| 2 | 数值格式 | `+0.1` / `+20` | `MULTIPLY_BASE`/`MULTIPLY_TOTAL` → `+10%`;`ADDITION` → `+20.00`(两位小数带符号) |
| 3 | 时间格式 | `剩余 3 tick`,20 秒写成 `400 tick` | `<20 tick` → `剩余 3 tick`;`≥20` → `剩余 20.0 秒`(lang 键 `baghud.time.ticks` / `baghud.time.seconds`) |
| 4 | 排序去重 | 16 个 id 固定顺序;同 id 两本会重复 | 全袋枚举 + 按 id 去重;`有效果(0) → 未收录(1) → 无加成(2)` 三段,段内按书 id 稳定排序 |
| 5 | 超长截断 | 全部平铺 | `MAX_LINES = 12`;超出折成 ` … 还有 %s 条`(若剩下的全是无加成 → ` … 还有 %s 条(无加成)`) |

**前后对比样例**(袋里放 14 本 + 2 个诡厄书):

```
改前(4.8.3):
 · 倒转之启:10%(对 BOSS/玩家伤害)
 · 恩惠之典:受击无敌帧 40 tick
 · 野猎指南:动物/宠物保护(条目)          ← 占位
 · 生灵颂词:宠物系效果(条目)              ← 占位
 · 虚伪审判:攻击无视规则(条目)            ← 占位
 · 血腥手册:在袋伤害效果                   ← 占位

改后(4.8.4):
 · 启示之证:命中时点燃目标 3 秒
 · 恩惠之典:自身不会着火
 · 恩惠之典:受击后无敌帧 40 tick
 · 生灵颂词:受保护的动物不会伤害你(手持时失效)
 · 生灵颂词:七咒之下,中立动物不会主动攻击你
 · 生灵颂词:与野猎指南同在时,转移伤害减少 75%
 · 虚伪审判:你受到与造成的伤害都转为「真实伤害」(需七咒之人)
 · 血腥狩猎手册:宠物攻击伤害 +25%
 · 血腥狩猎手册:佩戴血腥勇气纹章/千咒卷轴时,宠物同步获得你的部分效果
 · 野猎指南:宠物受到的伤害转移给你(半径 24 格)
 · 野猎指南:与兽友指南同在时,转移伤害减少 50%
 · 倒转之启:对 BOSS/玩家伤害 +10%(需七咒之人)
 … 还有 7 条(无加成)
```

(Lang 键统一为 `baghud.book.<namespace>.<path>.lineN`;兜底 `baghud.book.no_bonus` / `baghud.book.unknown_passive`。)

---

## 5. 启动验证

### ① 无诡厄遗物的最小环境 —— **通过**

`forge 47.2.20 + curios + Patchouli + caelus + EnigmaticLegacy 2.30.1 + enigmaticaddons 1.2.7 + enigmaticenhance 4.8.4`

```
[22:43:33] [Server thread/INFO] [minecraft/DedicatedServer]: Done (4.344s)! For help, type "help"
```

```
InvalidInjectionException   0
MixinApplyError             0
NoClassDefFoundError        0
NoSuchMethodError           0
ExceptionInInitializerError 0
Missing or unsupported mandatory dependencies  0
```
日志里 15 条 `ClassNotFoundException` 全部来自 **mixin 探测可选模组 target**(Apotheosis / ancientreforging),紧跟 `@Mixin target … was not found` 跳过,属正常。

### ② 带诡厄遗物的环境 —— **无法组装,已跳过(原因如实说明)**

```
goeticlegacy 0.6.1fix1 需要 goety ≥ 2.5.57.3
  · 工作区 libs/goety.jar 是 2.5.52.1 → 报 "requires goety 2.5.57.3 or above"
  · 换用实例里的 goety-2.5.60.6.jar 后,goety 通过,但 goeticlegacy 自身构造失败:
      java.lang.NoSuchMethodException: com.qoocies.goeticlegacy.GoeticLegacy.<init>()
      (FMLModContainer.constructMod → getDeclaredConstructor)
```

该失败发生在 **goeticlegacy 自己的 mod 类**,与本模组无关(本模组在 ① 中加载正常),最可能是
goeticlegacy 需要 **Forge 47.4.x**,而测试服只有 47.2.20(用户实例是 47.4.28),
`.prodserver` 里也没有 47.4.28 的服务端库。**没有继续拼凑,按任务允许的方式跳过。**

### 替代验证(静态,全部 PASS)

```
1) 本模组 jar 里对 com/qoocies/* 的硬引用(常量池级扫描)   hard refs = 0        PASS
2) 反射读取的配置字段是否真实存在
     Config.BOOKS                      1/1  字段命中                          PASS
     BooksConfig                       16/16 字段命中                         PASS
     IPassiveBook                      类存在                               PASS
3) 8 个诡厄书物品 id 在 goeticlegacy lang 里                8/8 命中             PASS
4) 本模组 lang 键完整性  zh 65 条 / en 65 条 baghud 键,无缺、%s 个数一致      PASS
```

---

## 6. 自检(FlowerSelfTest)说明

`-Denigmaticenhance.selftest=flower` 在本环境**没有产出日志** —— 自检输出全部走
`DebugLog.info`,受 `debugLog` 配置门控,而服务端配置里该键默认为 false;
本轮未把自检日志跑通,因此**没有自检结论**,只以 ① 的启动日志 + 上述静态校验为准。
`FlowerSelfTest` 的断言已跟随新格式更新(本地化属性名 / `+10%` / 秒),
`TooltipSelfTest`、`EffectMatrixSelfTest` 未改动、也未涉及本次改写的代码路径。

---

## 7. 已知取舍

* **排序按书 id**(而不是书名):服务端拿不到本地化书名(服务器只有 en_us,客户端语言在客户端),
  所以用 id 作为稳定排序键;段内顺序固定,换语言不会乱序。
* **「未收录」与「无加成」分两种**:诡厄的书写了 `IPassiveBook` 判定,
  如果是被动书但我们没登记文案 → 显示「被动效果(未收录)」;不是被动书 → 「仅可存放,无加成」。
* 属性名本地化依赖 `Attribute#getDescriptionId()` 的翻译;若某模组没给属性名翻译,
  会退回原始 id(不会空白)。
