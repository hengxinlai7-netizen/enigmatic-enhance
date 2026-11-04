# 神秘遗物增强 (Enigmatic Enhance)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.2.20%2B-orange.svg)](https://files.minecraftforge.net/)
[![Version](https://img.shields.io/badge/Version-2.11.26-blue.svg)](#)

针对 **神秘遗物（Enigmatic Legacy）** 与 **神秘遗物扩展（Enigmatic Addons）** 的增强附属模组，
补齐、增强并修正了一批玩法内容。

> 适用于 Minecraft **1.20.1** + Forge **47.2.20+**

---

## 目录

- [功能一览](#功能一览)
- [依赖](#依赖)
- [安装](#安装)
- [分扩展](#分扩展)
- [配置](#配置)
- [从源码构建](#从源码构建)
- [已知问题](#已知问题)
- [开源协议](#开源协议)

---

## 功能一览

### 执行之手（附魔）

`enchantment.enigmaticenhance.hand_of_execution`

- 限 **1 级**，无附魔限制（任何物品都能附）
- 造成伤害时把伤害转为**真实伤害**，绕过事件层限伤、免伤与无敌帧
- 真实伤害被拦截时自动退回普通伤害
- **无任何获取途径**，仅创造模式物品栏中的附魔书可取得

为以下 Boss 的「死亡保护 / 死亡动画免疫」做了破防：

| 模组 | Boss |
|---|---|
| 诡厄巫法 | 使徒 / 灾厄宰相 |
| 诡厄巫法：觉醒 | 无名者 / 蘑菇巨兽 |
| 传奇怪物 | 堕落圣骑 / 浮云傀儡 / 湮灭构筑体 |

### 信标增强

把神秘遗物、神秘遗物扩展与本模组的效果接入信标可选列表，按信标等级解锁：

| 信标等级 | 可选效果 |
|---|---|
| 1 级 | 不止饥饿、不止血欲 |
| 2 级 | 炽烈之力、熔融之心、瞬时气息 |
| 3 级 | 灵液腐蚀、冻结之心、镜碎、血色之灾 |
| 4 级 | 根源之祸、**纯化抗性**（恒定 I 级） |

- **纯化抗性**：只有 4 级信标能选到，且只能是 I 级
- 已排除：水银之蛇、永暗之寂（过强）

### 瓶装意识之海

- 手持时**敌对生物不会注意你，也不会把你设为目标**
- 已锁定的仇恨会被持续清除
- 只影响「注意 / 仇恨」，不影响伤害
- **获取**：手持「大地之瓶」睡觉，醒来后获得（消耗 1 个大地之瓶）
- 无合成配方，JEI 信息页内写明获取方式

### 瓶装冥河之水

- **获取**：大地之瓶 + 不死图腾，无序合成
- 效果：持有期间每天可免疫一次致死伤害，跨天自动恢复

### 大地护符

- 支持灌注任意带效果的物品（原版药水 / 神秘遗物 AdvancedPotion / 铁魔法精灵药）
- **生命提升改为常驻属性加成**：不再反复重刷 buff，避免 `max_health` 反复重算导致的满血抖动与掉血
- 卸下护符时加成自动清零
- 灌注读取依次兼容：
  - `PotionUtils`（原版）
  - `PotionHelper`（神秘遗物本体）
  - `PotionAddonHelper`（神秘遗物扩展）
  - `SimpleElixir#getMobEffect`（铁魔法）

### 龙首穿墙

- 佩戴「龙首」护符并创造飞行时可穿墙
- **默认关闭**，需在「控制 → 按键绑定」里自行绑定 `key.enigmaticenhance.noclip_toggle`
- 只有「创造模式飞行」或「龙首授予的飞行」才生效，其它模组的飞行不会穿墙

### 复苏之叶术石

- Curios `charm` 槽常驻饰品
- `Ctrl + 左键` 切换「龙首是否授予创造飞行」
- tooltip 实时显示当前状态（绿色 = 开 / 红色 = 关）

### 其它

- **地狱刃片护符保底**：战利品表含该物品时，玩家身上没有就必得一份
- **附魔师珍珠灌注**：神化附魔台 Eterna ≥ 15 时，大地之心可灌注成附魔师珍珠
- **Lootr 兼容**：大地之心的战利品箱刷新支持 Lootr（使用玩家专属库存）
- **宿命的修正**：距离类属性（reach / range / distance / interaction）不参与削减
- **战斗无敌帧**：受伤后短时间免疫，可配置时长与白名单
- **血战沙场之证**：假血条系统（伤害扣假血、回血无效、假血归零才死亡）

---

## 依赖

| 模组 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | |
| Forge | 47.2.20+ | |
| 神秘遗物 (Enigmatic Legacy) | 2.30.1+ | **必需** |
| Curios | 5.14.1+ | **必需** |
| 神秘遗物扩展 (Enigmatic Addons) | 1.2.5+ | 可选，提供部分效果 |
| 铁魔法 (Iron's Spellbooks) | 3.x | 可选，药水灌注兼容 |
| 神化 (Apotheosis) | 7.4.8+ | 可选，附魔师珍珠灌注 |
| Lootr | 0.7.x | 可选，战利品箱刷新兼容 |
| JEI | 15.x | 可选，信息页 |

---

## 安装

1. 安装对应版本的 Forge；
2. 把 `[神秘遗物增强] enigmaticenhance-<版本>.jar` 放进 `mods/` 目录；
3. 确保**神秘遗物本体**与 **Curios** 也已安装；
4. 启动游戏。

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

## 已知问题

- 信标「无视距离」尚未生效（`applyEffects` 的 SRG 方法名匹配失败）
- 信标里的「暴食」效果施加后没有实际作用
- Boss 击杀后的掉落偶有缺失（`LivingEntityDropInvoker` Mixin 未挂上）

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
