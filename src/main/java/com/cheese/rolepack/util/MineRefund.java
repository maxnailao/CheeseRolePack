package com.cheese.rolepack.util;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 地雷「空炸」（爆炸了但一个人都没炸到）时的金币返还。
 *
 * <p><b>规则</b>：</p>
 * <ul>
 *   <li><b>没人被炸到</b> → 返还该地雷售价的 {@value #REFUND_PERCENT}%（阔剑地雷 50 → 45，反人员地雷 80 → 72）；</li>
 *   <li><b>炸到人了</b>（不管死没死）→ 不返还。</li>
 * </ul>
 *
 * <p><b>「炸到人」怎么判</b>：只要爆炸体积内有玩家 —— 哪怕他带着护盾 / 处于疯狂模式的护盾，
 * 没被炸死 —— 也算「炸到人」，<b>不算空炸</b>。所以调用方传进来的必须是
 * <b>命中爆炸体积的玩家数（hits）</b>，而不是<b>击杀数（kills）</b>：
 * 用击杀数会把「炸到带盾玩家但没炸死」误判成空炸。</p>
 *
 * <p><b>与是否远程引爆无关</b>：踩雷、远程引爆、掉落物压住后解压引爆……只要空炸都返还。</p>
 */
public final class MineRefund {

    /** 空炸时返还的百分比。 */
    public static final int REFUND_PERCENT = 90;

    private MineRefund() {
    }

    /**
     * 空炸（爆炸体积内没有任何玩家）时，把 {@code price * 90%} 返还给地雷所有者。
     *
     * @param level     爆炸发生的服务端世界
     * @param ownerUuid 地雷所有者的 UUID（可能为 null，例如清场产生的雷）
     * @param price     该种地雷的售价（金币）
     * @param hits      本次爆炸<b>命中爆炸体积的玩家数</b>（不是击杀数）
     */
    public static void refundIfEmptyBlast(ServerLevel level, @Nullable UUID ownerUuid, int price, int hits) {
        if (hits > 0 || ownerUuid == null || level == null) {
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
            // 所有者离线：钱是给在场玩家的，离线时无处可进
            return;
        }
        try {
            SREPlayerShopComponent.KEY.get(owner).addToBalance(amount);
            owner.displayClientMessage(
                    Component.translatable("message.cheeserolepack.mine.empty_blast_refund", amount), true);
        } catch (Throwable ignored) {
            // 拿不到商店组件就算了，绝不影响爆炸流程本身
        }
    }
}
