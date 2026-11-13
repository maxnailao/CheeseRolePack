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
            // 技能键不能写死：格林走 SRE 的「统一技能」通道，触发键就是 abilityBind 这个 KeyMapping
            // （nextAbilityBind 只负责在多个技能之间切换槽位，格林只有一个技能，用不到它）。
            // getTranslatedKeyMessage() 每帧读取当前绑定，玩家在设置里改键后提示会自动跟着变。
            Component keyName = getSkillKeyName();
            Component text = Component.translatable("hud.cheeserolepack.green.returns_left", keyName, returnsLeft);
            int right = context.guiWidth() - 8;
            int y = context.guiHeight() - 40;
            int color = returnsLeft > 0 ? 0xFF7CFFB2 : 0xFF808080;
            context.drawString(font, text, right - font.width(text), y, color);
        });
    }

    /**
     * 取当前绑定的技能键显示名（如 {@code X}、{@code 鼠标左键}）。
     *
     * <p>直接读 SRE 的 {@code NoellesrolesClient.abilityBind}，因此玩家改键后 HUD 会立即跟着变；
     * 万一该字段拿不到（SRE 版本差异），退回显示一个占位符，绝不让 HUD 崩掉。</p>
     */
    private static Component getSkillKeyName() {
        try {
            net.minecraft.client.KeyMapping abilityBind = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind;
            if (abilityBind != null) {
                return abilityBind.getTranslatedKeyMessage();
            }
        } catch (Throwable ignored) {
            // 忽略：拿不到就用占位符
        }
        return Component.translatable("hud.cheeserolepack.green.key_unknown");
    }
}
