package com.cheese.rolepack.client.hud;

import com.cheese.rolepack.role.GreenRole;
import com.cheese.rolepack.role.GreenRoleData;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;

/**
 * 格林的技能 HUD（右下角）：「全场返回房间(剩余次数:1)」。
 *
 * <p>注册机制与 SRE 内置职业一致：{@code RoleHudRenderCallback.EVENT.register(职业 id, 渲染回调)}，
 * 渲染器只会对「当前客户端玩家就是该职业」的情况回调，因此这里不需要再判断阵营。
 * 剩余次数来自 {@link GreenRoleData}（服务端 → 客户端同步）。</p>
 */
public final class GreenHud {

    private GreenHud() {
    }

    /** 注册 HUD（在客户端入口调用一次）。 */
    public static void register() {
        RoleHudRenderCallback.EVENT.register(GreenRole.ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()) {
                return;
            }

            // 还没同步到时按「本局还没用过」处理（=1）
            GreenRoleData data = RoleData.getNullable(GreenRoleData.class, client.player);
            int returnsLeft = data != null ? data.getReturnsLeft() : GreenRoleData.USES_PER_ROUND;

            Font font = client.font;
            Component text = Component.translatable("hud.cheeserolepack.green.returns_left", returnsLeft);
            int right = context.guiWidth() - 8;
            int y = context.guiHeight() - 40;
            int color = returnsLeft > 0 ? 0xFF7CFFB2 : 0xFF808080;
            context.drawString(font, text, right - font.width(text), y, color);
        });
    }
}
