package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.BloodbathHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.7.8:「血战沙场之证·锁血」写死到 setHealth 入口。
 *
 * <p>原来的 clamp 只在玩家 tick 末尾夹一次,任何在它之后发生的治疗(再生/吸血/瞬间治疗/其它模组
 * 直接 setHealth 回血)都能让血量短暂超过锁值。这里在 {@code LivingEntity#setHealth} 上直接拦:</p>
 * <ul>
 *   <li>佩戴勋章且锁血开启的玩家,任何"把血量写超过锁值"的写入都改成锁值并取消原写入;</li>
 *   <li>写低(受伤)完全放行;内部回写通过旁路避免递归。</li>
 * </ul>
 *
 * <p>这样血量在服务端恒为锁值(默认 1),客户端血条看到的也恒为 1 —— 等于一根"固定 1 的假血条",
 * 绝大多数回血在写入前就被吃掉。</p>
 */
@Mixin(LivingEntity.class)
public class BloodbathHealthLockMixin {

    private static final ThreadLocal<Boolean> ALLOW_WRITE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "m_21152_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$lockHealthIncrease(float health, CallbackInfo ci) {
        try {
            if (ALLOW_WRITE.get().booleanValue()) {
                return;
            }
            if (!((Object) this instanceof ServerPlayer player)) {
                return;
            }
            if (!player.isAlive() || !BloodbathHandler.lockActive(player)) {
                return;
            }
            float lock = BloodbathHandler.lockValue();
            if (health > lock) {
                ALLOW_WRITE.set(Boolean.TRUE);
                try {
                    player.setHealth(lock);
                }
                finally {
                    ALLOW_WRITE.set(Boolean.FALSE);
                }
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
