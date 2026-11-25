# 神秘遗物增强 (Enigmatic Enhance)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.2.20%2B-orange.svg)](https://files.minecraftforge.net/)
[![Version](https://img.shields.io/badge/Version-4.9.0-blue.svg)](#)
[![CurseForge](https://img.shields.io/badge/CurseForge-Enigmatic%20Enhance-f16436.svg)](https://www.curseforge.com/minecraft/mc-mods/enigmatic-enhance)

对 **神秘遗物（Enigmatic Legacy）** 与 **神秘遗物扩展（Enigmatic Addons）** 的改动与扩展，
并新增一批饰品与工具。

> 适用于 Minecraft **1.20.1** + Forge **47.2.20+**

---

## 目录

- [对神秘遗物的改动](#对神秘遗物的改动)
- [新增的内容](#新增的内容)
- [配套机制](#配套机制)
- [依赖](#依赖)
- [安装](#安装)
- [分扩展](#分扩展)
- [配置](#配置)
- [从源码构建](#从源码构建)
- [已知问题](#已知问题)
- [开源协议](#开源协议)

---

## 对神秘遗物的改动

### 饰品效果

| 内容 | 改动 |
|---|---|
| **万钧之护卷轴** | 横扫改为雷霆一击；伤害改为直接增伤（全伤害类型）；雷击可逐件开关 |
| **复苏之叶术石** | 修正飞行开关的生效范围，不再影响其它飞行来源 |
| **血战沙场之证** | 假血条系统（伤害扣假血、回血无效、假血归零才死亡）；血量锁定重新接管 |
| **救赎之戒** | 去除惩罚条、不再破碎，可与七咒之戒同时佩戴 |
| **恩惠之典 · 第四诅咒** | 修正与倒转之启 / 无止之言 / 恩惠之典的组合逻辑 |
| **古旧书袋** | 新增 HUD 同步与工具提示，袋内效果直接可见 |
| **磁吸戒指** | 吸取距离可配置 |
| **术石** | 掉落权重可配置 |
| **无知诅咒卷轴** | 添加上限，防止经验溢出 |
| **无止暴食之证** | 饥饿值锁定为 0、饱和度锁定为 1 |

### 附魔与锻造

- **执行之手**：限 1 级，无附魔限制；造成伤害时转为真实伤害，绕过事件层限伤、免伤与无敌帧；被拦截时自动退回普通伤害。**无获取途径**，仅创造模式附魔书可取得
- **锻造者宝石**：支持多词条附魔书，可将同等级附魔升到上限
- **附魔师珍珠**：神化附魔台 Eterna ≥ 15 时可灌注

为下列 Boss 的「死亡保护 / 死亡动画免疫」做了破防：

| 模组 | Boss |
|---|---|
| 诡厄巫法 | 使徒 / 灾厄宰相 |
| 诡厄巫法：觉醒 | 无名者 / 蘑菇巨兽 |
| 传奇怪物 | 堕落圣骑 / 浮云傀儡 / 湮灭构筑体 |

### 战利品与获取

- **地狱刃片**：保底掉落 —— 战利品表含该物品且玩家没有时必得一份
- **术石类**：掉落权重可配置
- **大地之心**：掉落处理与重投逻辑调整，支持 Lootr 玩家专属库存
- **堡垒遗迹宝箱**：新增投递逻辑

### 界面与显示

- **饰品栏槽位**：自动注册所需栏位
- **物品提示**：多件饰品的提示文本与状态显示重做
- **信标增强**：把神秘遗物 / 扩展 / 本模组的效果接入信标可选列表

| 信标等级 | 可选效果 |
|---|---|
| 1 级 | 不止饥饿、不止血欲 |
| 2 级 | 炽烈之力、熔融之心、瞬时气息 |
| 3 级 | 灵液腐蚀、冻结之心、镜碎、血色之灾 |
| 4 级 | 根源之祸、**纯化抗性**（恒定 I 级） |

> 已排除：水银之蛇、永暗之寂（过强）

---

## 新增的内容

本模组另注册了一批饰品与工具，主要包括：

- **护符与胸饰**：大地护符（灌注药水 / 收纳元素 / 穿墙与飞行）、恶魔契约、恶魔血杯、恶魔的手骨、神射手护符、旅行家帽子
- **战斗饰品**：战争之旗、生命刻度
- **瓶子系列**：大地之瓶、渔获瓶、瓶装历史迷雾、瓶装灵界之光、瓶装冥河之水、瓶装战争迷雾、瓶装太阳、瓶装不定之雾、瓶装镜中之影、瓶装意识之海
- **实用组件**：数值编辑器、假账生成器、贪食之书、睡前读物、纯净符文

### 几个值得单独说明的

**大地护符**

- 支持灌注任意带效果的物品（原版药水 / 神秘遗物 AdvancedPotion / 铁魔法精灵药）
- **生命提升改为常驻属性加成**：不再反复重刷 buff，避免 `max_health` 反复重算导致的满血抖动与掉血
- 卸下护符时加成自动清零
- 灌注读取依次兼容：`PotionUtils`（原版）→ `PotionHelper`（本体）→ `PotionAddonHelper`（扩展）→ `SimpleElixir#getMobEffect`（铁魔法）

**渔获瓶**

- 按倍数放大多倍渔获；幸运加成是**真实属性**而非局部变量
- 可放入古旧书袋，堆叠上限 1

**瓶装意识之海**

- 手持时敌对生物不会注意你，也不会把你设为目标；已锁定的仇恨会被持续清除
- 只影响「注意 / 仇恨」，不影响伤害
- **获取**：手持「大地之瓶」睡觉，醒来后获得（消耗 1 个大地之瓶）

**瓶装冥河之水**

- **获取**：大地之瓶 + 不死图腾，无序合成
- 持有期间每天可免疫一次致死伤害，跨天自动恢复

**龙首穿墙**

- 佩戴「龙首」护符并创造飞行时可穿墙
- **默认关闭**，需在「控制 → 按键绑定」里绑定 `key.enigmaticenhance.noclip_toggle`
- 只有「创造模式飞行」或「龙首授予的飞行」生效，其它模组的飞行不会穿墙

**复苏之叶术石**

- Curios `charm` 槽常驻饰品
- `Ctrl + 左键` 切换「龙首是否授予创造飞行」
- tooltip 实时显示当前状态（绿色 = 开 / 红色 = 关）

**水银之蛇（回满血 + 次数盾）**

- 生命降到 **50%** 时回满血并获得次数盾，每层盾抵挡一次任意伤害并给 10 tick 无敌
- **次数与 buff 等级已解耦**：触发不再降低 buff 等级，次数记在 `mercury_triggers_left`
  （键不存在时按「buff 等级」初始化），次数用尽只移除效果、**已获得的层数仍然可用**
- **不依赖 buff 存在**：层数 > 0 就受保护；buff 被外部移除（例如石英花对同一效果的 `DENY`）时
  会自动补回并同步客户端 HUD
- 每次抵消都会打日志（「剩余 N 层」），不再只打第一次

**执行之手**

- 造成伤害时转为 `enigmaticenhance:execution` 真实伤害，绕过事件层的限伤、免伤与无敌帧；
  被拦截时自动退回普通伤害
- **不再针对任何特定生物**：强度完全来自 execution 的 bypass 标签 + 事件层「记账 → 还原被削减数值 →
  驳回取消」的通用链路，对所有目标一致生效（各 Boss 的死亡动画免疫不再单独打补丁）

**全属性（`enigmaticenhance:omni`）**

- 数值即全局加成（`0.2` = 所有属性 +20%），作为 `MULTIPLY_TOTAL` 独立乘区挂到每个属性上
- **兼容三种修饰符操作**：`ADDITION`（+0.2）、`MULTIPLY_BASE`（+12%）、`MULTIPLY_TOTAL`（+12%）
  都会正确折算成本次加成（内部以 1.0 为基数展平），因此**魔法石英花提供的百分比全属性终于生效**
- 神化 / L2 等第三方属性面板读到的也是折算后的值

---

## 配套机制

- **数据自检**：启动时检查物品标签与配方冲突，冲突会明确列在日志里，不再静默丢内容
- **配置项**：上述改动均提供单独开关与数值调整
- **日志**：正常游玩仅输出一条启动信息（`debugLog` 打开后才输出诊断细节）
- **战斗无敌帧**：受伤后短时间免疫，可配置时长与白名单
- **宿命的修正**：距离类属性（reach / range / distance / interaction）不参与削减

---

## 依赖

| 模组 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | |
| Forge | 47.2.20+ | |
| 神秘遗物 (Enigmatic Legacy) | 2.30.1+ | **必需** |
| Curios | 5.14.1+ | **必需** |
| 神秘遗物扩展 (Enigmatic Addons) | 1.2.5+ | 可选，提供部分效果与联动 |
| 铁魔法 (Iron's Spellbooks) | 3.x | 可选，药水灌注兼容 |
| 神化 (Apotheosis) | 7.4.8+ | 可选，附魔师珍珠灌注 |
| Lootr | 0.7.x | 可选，战利品箱刷新兼容 |
| JEI | 15.x | 可选，信息页 |

> 所有可选模组缺失时，对应功能自动跳过，**不会影响启动**。

---

## 安装

1. 安装对应版本的 Forge；
2. 把 `[神秘遗物增强] enigmaticenhance-<版本>.jar` 放进 `mods/` 目录；
3. 确保**神秘遗物本体**与 **Curios** 也已安装；
4. 启动游戏。

**客户端与服务端都需安装。**

---

## 分扩展

Boss 增强与宿命的试炼已拆分为独立模组，均**强制依赖**本模组（`>= 2.0.0`）：

| 扩展 | 内容 |
|---|---|
| `enhanceboss` | 监守者 / 凋零 / 末影龙强化 |
| `enhancetrial` | 宿命的试炼（含「宿命选项」界面） |

主模组内**不再包含**这两个模块的代码与界面。

---

## 配置

配置文件：`config/enigmaticenhance-common.toml`

```toml
[enigmaticenhance.combatInvuln]
  combatInvulnEnabled = true
  combatInvulnTicks = 12
  combatInvulnOutput = 1
  combatInvulnWhitelist = [ "tacz:bullet", "terra_entity:summon" ]

[enigmaticenhance.lootGuarantee]
  hellBladeGuaranteed = true
  pearlInfusionEnabled = true

[enigmaticenhance.bloodbath]
  bloodbathLockEnabled = true
  bloodbathLockValue = 1.0
```

---

## 从源码构建

```powershell
# 需要 JDK 21
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot"
./gradlew --offline build
```

产物：`build/libs/enigmaticenhance-<版本>.jar`

> ⚠️ **重要**：本仓库**不包含** `libs/` 目录（约 198 MB 的第三方依赖 jar，受各自版权保护）。
>
> 编译前请自备以下 jar 放入 `libs/`：
>
> ```
> Enigmaticlegacy-2.30.1-1.jar
> enigmaticaddons-1.2.6.jar
> curios-forge-5.14.1+1.20.1.jar
> Apotheosis-1.20.1-7.4.8.jar
> Placebo-1.20.1-8.6.3.jar
> jei-1.20.1-forge-15.20.0.129.jar
> tacz-1.20.1-1.1.8-hotfix.jar
> ancientreforging-1.3.jar
> caelus-forge-3.2.0+1.20.1.jar
>
> # 破防 Mixin 需要（可选，缺失时相关功能静默失效）
> goety.jar              # 诡厄巫法本体
> goetyawaken.jar        # 诡厄巫法：觉醒
> legendarymonsters.jar  # 传奇怪物
> ```

---

## 更新记录

### 4.11.0

**水银之蛇：次数与 buff 等级解耦**

* 触发时**不再降低 buff 等级**、不再 `forceRemove` 后按等级写回 —— 旧写法在等级降到 -1 时会把效果摘掉，
  而石英花对同一效果设了 `MobEffectEvent.Applicable → DENY`，导致**buff 再也装不回来**、保护整条失效。
* 次数改记在 `mercury_triggers_left`（首次按 `amplifier + 1` 初始化），用尽只移除效果、**层数保留可用**。
* 保护判定改为 `charges > 0`（不再要求带 buff）；buff 丢失时每 tick 自动补回并同步 `ClientboundUpdateMobEffectPacket`。
* 抵消日志从 `once` 改为每次都打。

**执行之手：去掉 7 个逐 Boss 特判**

* 删除 `GoetyNamelessOne / GoetyVizier / GoetyApostle / GoetyMushroomMonstrosity /
  LegendaryPaladin / LegendaryObliterator / LegendaryCloudGolem` 七个 mixin 及其 mixin 配置条目。
* 强度统一由 `execution` 的 bypass 标签 + `LivingHurt/LivingDamage` 的「HIGHEST 记账 → LOWEST 还原 → 驳回取消」通用链路承担。

**全属性：兼容三种修饰符操作**

* `omni` 基础值为 0，`MULTIPLY_BASE` 系数的结果恒为 0 —— 石英花给的「+12% 全属性」从来没生效。
* 新增 `OmniAttribute.effectiveBonus(...)`：以 1.0 为基数展平 `ADDITION / MULTIPLY_BASE / MULTIPLY_TOTAL`，返回 `v - 1`。
* `OmniAttributeApplier` 与 `AttributeInstance#getValue`（新增 `OmniAttributeValueMixin`）都改用它，面板同步可见。
### 4.9.0

**书袋效果显示重构**

* 新增 `BookEffects` 注册表：**22 本书**的效果文本（诡厄遗物 8 + 神秘遗物本体 8 + 扩展 6），
  lang 键统一为 `baghud.book.<namespace>.<itemid>.lineN`，`collect()` 里不再有散落的 if。
* 袋内枚举改为**遍历书袋全部槽位**（反射 `AntiqueBagCapability.INVENTORY`）：
  以前只查白名单，白名单外的书完全不显示；现在袋里有什么就列什么。
* 未收录 / 判定为无效果的书显示「**仅可存放，无加成**」，不再静默消失
  （隐藏会让玩家分不清「本来没效果」和「HUD 坏了」）。
* 效果文本一律从代码 / 运行时配置取值（`HunterGuidebook.*`、`TheBless.invulnerableTime`、
  `OdeToLiving.*`、`SanguinaryHandbook.DamageMultiplier`、诡厄 `Config.BOOKS.*`），读不到才用默认值。
* 显示优化：属性名本地化（`goety:spell_potency` → 「法术强度」）、乘法转百分比（`+0.1` → `+10%`）、
  `≥20 tick` 转秒、排序去重（有效果的在前、无加成的在后）、超 12 条折成「… 还有 N 条（无加成）」。

**无知诅咒卷轴：吸满即停，不再把人吸到 0 级**

* 新增 `CursedXpScrollCapMixin`：`@Redirect` 把每 tick 份额夹到「距上限还剩多少」，
  并在玩家经验不足一份时把 `getPlayerXP` 退化为 `0` —— 不 drain、不改 `XPStored`，
  但方法继续执行，**卷轴吸地上经验球的行为保留**。
* 修复扩展 `curioTick` 里 `else if (getPlayerXP > 0) { drain(全部) }` 分支导致
  **等级被清空到 0** 的问题。
* 上限口径 = 存储经验折算的等级 ≥ `ScrollofIgnoranceCurseXPLevelUpperLimit`（默认 1000 级 ≈ 4,339,720 点），
  与玩家当前等级无关；取回模式 100% 原样，取回到上限以下后下一次 tick 自动恢复吸收。
* `debugLog=true` 时输出诊断行：`[无知诅咒] stored/cap/limit/portion/playerXp/level/skip`（5 秒节流）。
* 纯数值部分拆到零 MC 依赖的 `CursedXpMath`，配套 **35 条离线自检全部通过**。

## 已知问题

- 信标「无视距离」尚未生效
- 信标里的「暴食」效果施加后没有实际作用
- Boss 击杀后的掉落偶有缺失

---

## 开源协议

本项目采用 **MIT License** 开源。

### 你可以

- ✅ 自由使用、复制、修改、合并、发布、再授权、销售
- ✅ 用于整合包分发
- ✅ 在此基础上开发闭源的附属模组

### 你需要

- 📄 在所有副本或重要部分中**保留原始版权声明与本许可声明**

### 你不需要

- ❌ 公开你的修改
- ❌ 标注作者（虽然欢迎）
- ❌ 承担任何担保责任

### 免责

本软件按「原样」提供，不附带任何明示或暗示的担保。作者不对使用本软件造成的任何损失负责。

完整条款见 [LICENSE](LICENSE)。

### 关于第三方内容

本仓库**不包含**任何第三方模组的 jar 文件。`libs/` 目录下的依赖均为各自作者的作品，
受其自身许可保护，请自行前往对应发布页获取。

Minecraft 是 Mojang Studios 的商标，本项目与 Mojang 无关，也未获其背书。

---

## 致谢

- [神秘遗物 Enigmatic Legacy](https://www.curseforge.com/minecraft/mc-mods/enigmatic-legacy) —— 本模组的基础
- 神秘遗物扩展、Curios、神化、铁魔法等模组的作者
- 所有反馈问题与参与测试的玩家

### 与「七咒之戒 + 救赎之戒」同戴相关的修复

原版扩展在这两枚戒指同时佩戴时会进入「惩罚态」，其中一条副作用是**每帧覆盖原版光照贴图(lightmap)**。
在装有现代化 UI（ModernUI）的环境里，这会让字体图集与实体/方块纹理无法正常采样，
表现为**界面文字全部消失、方块与实体纹理变黑**。

本模组在允许两戒同戴时，一并将 `SuperAddonHandler#isPunishedOne` 的判定抑制为 `false`，
使上述副作用整条链路不再触发（惩罚执行本身本来就是被本模组拦截的）。
可用配置 `compat.suppressAddonPunishedState = false` 关闭该行为，回到扩展原判定。
