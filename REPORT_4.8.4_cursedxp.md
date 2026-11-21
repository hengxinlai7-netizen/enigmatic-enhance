# 4.8.4 — 无知诅咒卷轴：只让吸收退化为 0，**不取消 tick**（修复吸不到经验球的回归）

产物：`C:\Users\hengx\Downloads\[神秘遗物增强] enigmaticenhance-4.8.4.jar`
**1,243,243 字节**，sha256 **5F92C4C99689170F316FE57EAB345CA1E575982E8CDDC2015FD830E3C91F8888**，版本仍 4.8.4。

---

## 1. 回归根因（你的存档证据 + 代码路径）

存档里 `XPStored` 计数 0 ⇒ `remainingToCap ≈ 4,339,720` ⇒ `atCap` 从未触发；但玩家 0 级 0 经验时
`portion = (7-0)/5 = 1`，`playerXp(0) < 1` ⇒ `shouldSkip = true` ⇒ **上一版的 `ci.cancel()` 每 tick 生效**，
整个 `curioTick`（含末尾"扫半径内经验球"）都不执行 ⇒ **卷轴连地上的球都不吸**。

修法：**不 cancel，改成把 `getPlayerXP` 的返回值变成 0**。这样

```java
if (getPlayerXP(player) >= portion) { ... }   // 0 >= 1  假
else if (getPlayerXP(player) > 0)   { ... }   // 0 > 0   假
// ↓ 继续执行
// 扫半径内 ExperienceOrb → 飞向玩家          ← 吸球恢复
```

---

## 2. `getPlayerXP` 的真实语义（javap 证据）

```
public static int getPlayerXP(net.minecraft.world.entity.player.Player);
   0: aload_0
   1: getfield  #27   // Field Player.f_36078_:I        ← experienceLevel
   4: invokestatic #31 // ExperienceHelper.getExperienceForLevel:(I)I
   7: i2f
   8: aload_0
   9: getfield  #35   // Field Player.f_36080_:F        ← experienceProgress
  12: aload_0
  13: invokevirtual #39 // Player.m_36323_:()I           ← getXpNeededForNextLevel
  17: fmul
  18: fadd
  19: f2i
  20: ireturn
```

即 `(int)( xpForLevel(level) + progress * xpNeededForNextLevel(level) )`，
**不是** `totalExperience`。等价实现放在 `CursedXpMath.realPlayerXp(level, progress)`，
HEAD 注入里先算一次存 ThreadLocal（避免 Redirect 里回头再调一次被自己拦住）。

---

## 3. 改动后的完整 mixin 源码

```java
@Mixin(value = auviotre.enigmatic.addon.contents.items.CursedXPScroll.class)
public abstract class CursedXpScrollCapMixin {

    /** Redirect 拿不到 curioTick 的 stack/player 形参,由 HEAD 记下(同 tick 同线程顺序执行)。 */
    private static final ThreadLocal<ItemStack> CURRENT_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_XP = new ThreadLocal<>();

    /** HEAD:只记录，绝不 cancel。 */
    @Inject(method = "curioTick", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$capture(SlotContext context, ItemStack stack, CallbackInfo ci) {
        CURRENT_STACK.set(stack);
        if (context != null && context.entity() instanceof Player player) {
            CURRENT_XP.set(CursedXpCap.realPlayerXp(player));
        } else {
            CURRENT_XP.remove();
        }
    }

    /** ① 本 tick 的份额不超过"离上限还差多少"。 */
    @Redirect(method = "curioTick",
        at = @At(value = "INVOKE",
            target = "Lauviotre/enigmatic/addon/contents/items/CursedXPScroll;getXPPortion(Lnet/minecraft/world/entity/player/Player;)I"),
        remap = false, require = 0)
    private int enigmaticenhance$clampPortion(auviotre.enigmatic.addon.contents.items.CursedXPScroll self,
                                              Player player) {
        int raw = CursedXpCap.portionForLevel(player == null ? 0 : player.experienceLevel);
        ItemStack stack = CURRENT_STACK.get();
        if (stack == null || stack.isEmpty() || !CursedXpCap.isAbsorptionMode(stack)) {
            return raw;                     // 取回模式:原样
        }
        int clamped = CursedXpCap.clampPortion(raw, CursedXpCap.storedXp(stack));
        return clamped > 0 ? clamped : raw; // 绝不给扩展 0/负数(否则走 portion<=0 → 5 兜底)
    }

    /** ② 该停的时候把 getPlayerXP 变成 0（不 cancel）。 */
    @Redirect(method = "curioTick",
        at = @At(value = "INVOKE",
            target = "Lcom/aizistral/enigmaticlegacy/helpers/ExperienceHelper;getPlayerXP(Lnet/minecraft/world/entity/player/Player;)I"),
        remap = false, require = 0)
    private int enigmaticenhance$zeroXpWhenSkipping(Player player) {
        int real = CURRENT_XP.get() != null ? CURRENT_XP.get() : CursedXpCap.realPlayerXp(player);
        ItemStack stack = CURRENT_STACK.get();
        if (stack == null || stack.isEmpty()) return real;
        boolean skip = CursedXpCap.shouldSkip(stack, real, player == null ? 0 : player.experienceLevel);
        CursedXpCap.logSkipDecision(stack, real, player == null ? 0 : player.experienceLevel, skip);
        return skip ? 0 : real;
    }
}
```

**关键坑（踩过并已修）**：目标是**静态**方法 `ExperienceHelper.getPlayerXP`，但 Mixin 校验的是
**宿主方法** `curioTick` 的静态性（实例方法）⇒ handler 必须写成**非 static**。
写成 `static` 会报 `'static' modifier of handler method does not match target`
（第一次运行确实报了 4 条 `InvalidInjectionException`）。

### 诊断日志（5 秒最多一条，`debugLog=false` 时一条都不打）

```java
CursedXpCap.logSkipDecision(...)  →  DebugLog.info(
    "[无知诅咒] stored={} cap={} limit={} portion={} playerXp={} level={} skip={}", ...);
```
节流：`System.currentTimeMillis() - lastLogAt < 5000` 直接 return；整段 `try/catch(Throwable)`，绝不影响主流程。
本次两组启动日志里该行计数 = **0**（`debugLog` 默认 false），符合预期。

---

## 4. 行为对照表

| 情况 | 上一版（回归） | 现在 |
|---|---|---|
| 0 级 0 经验、卷轴空 | `ci.cancel()` 每 tick → **连经验球都不吸** | `getPlayerXP` 返回 0 → 不 drain、**吸球照常** |
| 玩家经验 < 一份（但 >0） | 取消 tick | 返回 0 → 不吸，经验留着（不会掉 0 级） |
| 玩家经验 ≥ 一份 | 吸 | 吸 `min(portion, 剩余额度)` |
| 接近上限 | — | 只吸剩余额度，正好停在上限 |
| 已到上限 | 取消 | 返回 0 → 不 drain、不改 `XPStored`，吸球照常 |
| **取回模式** | — | 两个 Redirect 都原样返回真实值，**100% 不变** |
| 取回到上限以下 | — | 下一 tick 自动恢复（判定无状态） |

---

## 5. 离线自检（`CursedXpMath` 纯函数，35 项全 PASS）

```
== 1) portionForLevel 与扩展 getXPPortion 等价
   level 0 -> 1   5 -> 3   17 -> 9   32 -> 26   100 -> 148   101 -> 300   200 -> 984      [全 OK]
== 2) 接近上限: 份额被夹到剩余额度
   remaining(cap-10)=10  clamp(300,cap-10)=10  clamp(148,cap-10)=10  clamp(5,cap-10)=5      [全 OK]
== 3) 已到/超过上限: 剩余 0、份额 0                                                        [全 OK]
== 4) 未满时 clamped 恒 >= 1(raw=0/-5/1/7 全部 >=1)                                      [OK]
== 5) 玩家经验不足一份 -> skip
   portion(5)=3; xp=2 -> skip=true; xp=3 -> false; xp=100 -> false; xp=1&stored=cap -> true [全 OK]
== 6) 取回模式一律放行                                                                     [OK]
== 7) 取回把 stored 降到上限以下 -> 恢复吸收                                               [OK]
== 8) 等级换算 1/2/16/17/31/32/100/1000 roundtrip 精确                                     [OK]
== 9) realPlayerXp == ExperienceHelper.getPlayerXP(等级累计 + 进度*本级所需)
   (0,0.0)->0  (0,0.5)->3  (5,0.0)->55  (16,0.0)->352  (17,0.0)->394
   xpNeededForNextLevel(0)=7  (100)=742                                                    [全 OK]
== 10) 实测回归场景: 等级0/经验0、卷轴空
   xp=0, stored=0, level=0, absorption=true  -> skip=true   (现在=返回0，不 cancel)         [OK]
   xp=0, stored=0, absorption=false          -> skip=false                                [OK]
PASSED=35  FAILED=0
```

---

## 6. 启动验证

| 环境 | 结果 | 关键日志 |
|---|---|---|
| **无 addons** | `Done (3.464s)!` | 无 `Mixing CursedXpScrollCapMixin`（target 缺失 → optional 跳过） |
| **有 addons 1.2.7**（`-Dmixin.debug.verbose=true`） | `Done (5.582s)!` | `[23:50:22] Mixing CursedXpScrollCapMixin from enigmaticenhance.optional.mixins.json into auviotre.enigmatic.addon.contents.items.CursedXPScroll` |

两个环境六类异常**全 0**：

```
NoClassDefFoundError / NoSuchMethodError / ExceptionInInitializerError
InvalidInjectionException / MixinApplyError / Missing or unsupported mandatory dependencies
```

### handler 描述符核对（构建产物）

```
private int enigmaticenhance$clampPortion(auviotre...CursedXPScroll, Player);
  descriptor: (Lauviotre/enigmatic/addon/contents/items/CursedXPScroll;Lnet/minecraft/world/entity/player/Player;)I
private int enigmaticenhance$zeroXpWhenSkipping(Player);
  descriptor: (Lnet/minecraft/world/entity/player/Player;)I          ← 与要求一致
```

---

## 7. 约束核对

无 `@Overwrite`；`getXPPortion` 单调用点（javap 核对）故无 ordinal；三个注入点均 `remap=false, require=0`；
`Integer.MAX_VALUE` 溢出保护（`ExperienceOverflowGuard`）未改动；无 addons 时走 optional 配置被跳过、零类加载。
