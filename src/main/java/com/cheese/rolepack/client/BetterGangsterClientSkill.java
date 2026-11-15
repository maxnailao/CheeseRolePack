package com.cheese.rolepack.client;

import com.cheese.rolepack.content.item.RemoteDetonatePayload;
import com.cheese.rolepack.role.BetterGangsterRole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.GKeyRoleSkill;

/**
 * 「更好的悍匪」的主动技能 —— 客户端半边：按技能键时用<b>准星射线</b>挑一颗地雷，
 * 把它的 entity id 发给服务端（{@link RemoteDetonatePayload}）。
 *
 * <p>为什么要在客户端选？服务端只知道玩家的朝向，而准星是客户端的东西；
 * 用 {@code player.pick(...)} 能拿到与玩家所见完全一致的目标。</p>
 *
 * <p>接线方式：{@code GKeyRoleSkill.register(职业, beforeRhapsody=false, handler)} ——
 * 这是 SRE/Noellesroles 给「职业专属技能键」准备的客户端钩子。
 * 它会在 SRE 发送通用 {@code AbilityC2SPacket} 之前被调用，<b>返回 true 就代表「这个按键由我处理」</b>，
 * 于是通用技能链路被短路；所以真正的逻辑全部由我们的 C2S 包 + 服务端校验完成。</p>
 */
public final class BetterGangsterClientSkill {

    /** 准星射线距离（格）：比 50 格档再远一点，覆盖最远一档前摇。 */
    public static final double REMOTE_DETONATE_RANGE = 64.0D;

    private BetterGangsterClientSkill() {
    }

    /** 注册客户端技能键（在客户端入口调用一次；此时职业实例已在主入口注册好）。 */
    public static void register() {
        if (BetterGangsterRole.role() == null) {
            // 主入口还没跑（理论上不会发生），跳过即可，不影响其它功能
            return;
        }
        GKeyRoleSkill.register(BetterGangsterRole.role(), false, (client, gameComponent) -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return false;
            }
            Entity target = pickMine(player);
            com.cheese.rolepack.CheeseRolePack.LOGGER.info(
                    "[远程引爆] 技能键触发，准星命中={} 玩家={}",
                    target == null ? "无" : target.getClass().getSimpleName(),
                    player.getName().getString());
            if (target == null) {
                player.displayClientMessage(
                        Component.translatable("message.cheeserolepack.remote_detonate.no_target"), true);
                // 按键由本技能消费，避免落到 SRE 的通用技能链路
                return true;
            }
            ClientPlayNetworking.send(new RemoteDetonatePayload(target.getId()));
            return true;
        });
    }

    /**
     * 用准星射线（{@link LocalPlayer#pick(double, float, boolean)}）找地雷实体。
     *
     * @return 命中的地雷实体；没对准地雷时返回 {@code null}
     */
    private static Entity pickMine(LocalPlayer player) {
        // 注意：不能用 player.pick(...) —— 那个只做方块射线，永远拿不到 EntityHitResult
        return MineTargeting.pickMine(player);
    }

    /** 供调试/其它客户端代码取当前选中的地雷（未使用也可以留着）。 */
    public static Entity pickMine(Minecraft client) {
        return client.player == null ? null : pickMine(client.player);
    }
}
