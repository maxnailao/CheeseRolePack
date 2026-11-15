package com.cheese.rolepack.util;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 地雷「远程引爆没炸到人」时的金币返还。
 *
 * <p><b>规则</b>（用户需求）：远程引爆后</p>
 * <ul>
 *   <li><b>没炸到任何玩家</b> → 返还该地雷售价的 {@value #REFUND_PERCENT}%（阔剑地雷 50 → 40，反人员地雷 100 → 80）；</li>
 *   <li><b>炸到人了</b> → 不返还。</li>
 * </ul>
 *
 * <p><b>只对远程引爆生效</b>：踩雷触发、钳子拆除、每局清场都不走这里。
 * 是否远程引爆由实体自己记（{@code igniteRemoteFuse} 时置位），
 * 因为 fuse 到点后走的还是同一个 {@code explode(...)}，
 * 只能用这个标记区分「遥控引爆」与「被踩爆」。</p>
 */
public final class MineRefund {

    /** 没炸到人时返还的百分比。 */
    public static final int REFUND_PERCENT = 80;

    private MineRefund() {
    }

    /**
     * 远程引爆且没炸到人时，把 {@code price * 80%} 返还给地雷所有者。
     *
     * @param level           爆炸发生的服务端世界
     * @param ownerUuid       地雷所有者的 UUID（可能为 null，例如清场产生的雷）
     * @param price           该种地雷的售价（金币）
     * @param remoteDetonated 这次爆炸是否由远程引爆触发
     * @param kills           本次爆炸实际击杀的玩家数量
     */
    public static void refundIfRemoteMissed(ServerLevel level, @Nullable UUID ownerUuid, int price,
            boolean remoteDetonated, int kills) {
        if (!remoteDetonated || kills > 0 || ownerUuid == null || level == null) {
            return;
        }
        int amount = price * REFUND_PERCENT / 100;
        if (amount <= 0) {
            return;
        }
        ServerPlayer owner = level.getServer() == null
                ? null
                : level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            // 所有者离线：不返还（钱是给在场玩家的，离线时无处可进）
            return;
        }
        try {
            SREPlayerShopComponent.KEY.get(owner).addToBalance(amount);
            owner.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.refund", amount), true);
        } catch (Throwable ignored) {
            // 拿不到商店组件就算了，绝不影响爆炸流程本身
        }
    }
}
