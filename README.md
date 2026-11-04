神秘遗物增强（enigmatic-enhance）
==================================

针对**神秘遗物（Enigmatic Legacy）** / **神秘遗物扩展（enigmaticaddons）**的增强。

已实现（1.0.0）
---------------
1. **新属性【全属性】** `enigmaticenhance:omni`（`OmniAttribute`）
   - 数值即全局加成：`0.2` = 所有属性 +20%，可负；默认只挂到玩家身上（不放大怪物）；
   - **独立乘区**（`AttributeMapMixin` 注入 `AttributeMap#getValue`，SRG `m_22181_`）：
     ```
     最终值 = 原版/其它模组算完并裁剪后的值 × (1 + 全属性)
     ```
     不参与其它属性的加成计算，也不受它们的上限裁剪；读取 omni 自身时用标记短路，不会递归；
   - 语言文件：`attribute.enigmaticenhance.omni` = 「全属性」。
2. **救赎之戒自带 +20% 全属性**（`CurioCompat`，走 Curios 的 `CurioAttributeModifierEvent`，纯事件不注入）
   - 配置 `ring.omniBonus`（默认 `0.2`）。
3. **救赎之戒自带无法破坏**（`BlessRingHelperMixin`）
   - 反编译：破损流程在 `BlessRing.curioTick` → `BlessRing$Helper.setBlessAttribute(stack, "BlessDuration", 当前值 + 1)`
     （破损时会播声音并提示 `message.enigmaticaddons.blessRingBreak`）；
   - 本 mixin 在 `setBlessAttribute` 入口拦下 `BlessDuration` 的自增，使戒指永不进入破损状态；
   - 配置 `ring.unbreakable`（默认 `true`）。

未完成 / 待办
-------------
- **取消失落引擎的雷击清除所有物品**：已定位到失落引擎（`enigmaticaddons:lost_engine`）的雷击创建位置在
  `AddonEventHandler.onTick` / `onCursed` / `onConfirmedDeath`（`LightningBolt` + `setDamage`），
  且该模组自带 `MixinLightningBolt`（带计分板标签 `HarmlessThunder` 的雷击会取消点燃）。
  「清除所有物品」那一步尚未定位到，需要继续查 `AddonEventHandler` 里与 `ItemEntity` 相关的那段。

基本信息
--------
- modId：`enigmaticenhance`
- 显示名：神秘遗物增强 (Enigmatic Legacy Enhance)
- 版本：1.0.0
- 环境：Minecraft 1.20.1 / Forge（47.2.20 编译，运行需 47.2.0+）
- 硬依赖：无（除 Forge / Minecraft）

构建
----
```powershell
$env:JAVA_HOME  = 'C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'
$env:GRADLE_USER_HOME = 'C:\Users\hengx\Documents\deepseek-harness\default-workspace\.gradle-home'
cd C:\Users\hengx\Documents\deepseek-harness\default-workspace\enigmatic-enhance
& C:\Users\hengx\Documents\deepseek-harness\default-workspace\.tools\gradle-8.5\bin\gradle.bat build --no-daemon --offline
```
产物：`build/libs/enigmaticenhance-1.0.0.jar`

目录
----
```
enigmatic-enhance/
├─ build.gradle / gradle.properties / settings.gradle / gradlew(.bat) + gradle/wrapper/
├─ libs/                  # 已放好 enigmaticlegacy / curios 等 jar,供后续加 compileOnly
├─ README.md
└─ src/main/
   ├─ java/com/hengx/enigmaticenhance/EnigmaticEnhance.java   # @Mod 骨架 + 加载日志
   └─ resources/
      ├─ META-INF/mods.toml            # displayName 已填「神秘遗物增强 (Enigmatic Legacy Enhance)」
      ├─ pack.mcmeta
      ├─ enigmaticenhance.mixins.json  # mixins 列表为空,等着往里登记
      └─ enigmaticenhance.refmap.json
```

后续实现的约定（与工作区其它模组一致）
--------------------------------------
1. **优先用 Forge 事件**（`LivingHurtEvent` / `CriticalHitEvent` / `ItemAttributeModifierEvent` / `Curios` 相关事件等），
   不注入就不用抢代码路径，兼容性最好；
2. 必须注入时：在 `src/main/java/com/hengx/enigmaticenhance/mixin/` 新建类并登记到
   `enigmaticenhance.mixins.json`；目标是原版方法 → SRG 名 + `remap = false`；
   目标是神秘遗物自己的方法 → 用其自身名称 + `remap = false`；模组缺失时用 `required=false` 兜底；
3. **联动神秘遗物**：`libs/` 里已有 jar，`build.gradle` 加 `compileOnly`，代码里用
   `ModList.get().isLoaded("enigmaticlegacy")` 守卫，所有直接引用放进单独的 compat 类；
4. **配置项**：`ModLoadingContext.get().registerConfig(...)` + `ForgeConfigSpec`，
   文件名形如 `config/enigmaticenhance-common.toml`；
5. **验证习惯**：改完先 `build`，再放进 `.prodserver` 起一次专用服务器看日志
   （`Done (` 且无 `InjectionError`）；需要一次性行为的自检时，临时加一个由 `-D` 开关控制的类，
   验证完删除并复查包内无残留。
