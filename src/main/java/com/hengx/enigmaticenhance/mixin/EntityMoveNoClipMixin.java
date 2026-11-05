package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.NoClipState;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.2.13g:龙首「飞行穿方块」的真正落点。
 *
 * <h2>为什么必须在 {@code Entity#move} 里做</h2>
 * {@code Player#tick()} 第一句是 {@code this.noPhysics = this.isSpectator();},
 * 而玩家移动/碰撞就发生在同一 tick 的 {@code Entity#move(MoverType, Vec3)} 里。
 * 在 tick 之外(ClientTickEvent.END / PlayerTickEvent)设置的 {@code noPhysics}
 * 永远会被"下一次 tick 开头"的重置覆盖掉,赶不上 move 的判定 —— 这就是
 * 1.1.25b 到 1.2.13f 一直没有效果的原因。
 *
 * <p>这里在 move 的 HEAD 直接按共享状态把 {@code noPhysics} 置 true,
 * 原版方法紧接着就会走 {@code if (this.noPhysics) { setPos(...); return; }} 分支,
 * 完全跳过碰撞。状态清掉后下一 tick 自动恢复碰撞(服务端/客户端各自维护)。</p>
 *
 * <p>目标方法按项目约定用 <b>SRG + {@code remap = false}</b> 书写;
 * 注入 {@code require = 0},目标改名时只是不再生效,不会崩。</p>
 */
@Mixin(Entity.class)
public class EntityMoveNoClipMixin {

    @Inject(method = "m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$allowPhase(CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            if (NoClipState.isActive(self)) {
                if (!self.noPhysics) {
                    self.noPhysics = true;
                }
                // 记下"这个 noPhysics 是我们打开的",关闭时才能安全还原
                NoClipState.markForced(self);
            }
            else if (NoClipState.releaseForced(self)) {
                // 4.2.2:关闭穿墙后必须把 noPhysics 还原,
                // 否则会一直穿方块(而且因为人在方块里,还会持续窒息)。
                self.noPhysics = false;
            }
        }
        catch (Throwable ignored) {
            // 判定失败 ⇒ 保持原版碰撞
        }
    }
}
