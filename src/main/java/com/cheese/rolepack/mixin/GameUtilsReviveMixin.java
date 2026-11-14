package com.cheese.rolepack.mixin;

import com.cheese.rolepack.role.GreenRole;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 复活监听：把被复活的玩家从格林的「本局死亡名单」里移出。
 *
 * <p>为什么需要它：SRE 没有通用的「当前是否死亡」标记
 * （{@code DefibrillatorComponent#isDead} 只在会议/除颤器流程里写，不是通用死亡态），
 * 训练场机制又会把亡者改成冒险模式，因此无法从状态反推生死。于是改为事件驱动：
 * 死亡用 {@code OnPlayerDeath} 记录，复活用本 mixin 在 SRE 的复活入口处移除。</p>
 *
 * <p>注入点（SRE 的 {@code io.wifi.starrailexpress.game.GameUtils}）：</p>
 * <ul>
 *   <li>{@code revivePlayerToItsRoom(ServerPlayer)} —— 486 修饰符等「回自己房间复活」走这条；</li>
 *   <li>{@code revivePlayer(ServerPlayer, double, double, double)} —— 指定坐标复活（含管理命令）。</li>
 * </ul>
 *
 * <p>这两个都是 SRE 自己的静态方法，方法名不会被映射改动，因此生产环境同样命中。</p>
 */
@Mixin(io.wifi.starrailexpress.game.GameUtils.class)
public class GameUtilsReviveMixin {

    @Inject(method = "revivePlayerToItsRoom", at = @At("HEAD"))
    private static void cheeserolepack$onReviveToRoom(ServerPlayer player, CallbackInfo ci) {
        GreenRole.markRevived(player);
    }

    @Inject(method = "revivePlayer", at = @At("HEAD"))
    private static void cheeserolepack$onRevive(ServerPlayer player, double x, double y, double z, CallbackInfo ci) {
        GreenRole.markRevived(player);
    }
}
