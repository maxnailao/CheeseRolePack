package com.cheese.rolepack.client.hud;

import com.cheese.rolepack.role.BetterGangsterRole;
import com.cheese.rolepack.role.BetterGangsterRoleData;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;

/**
 * 「更好的悍匪」的 HUD。
 *
 * <p><b>右下角两行</b>（场上还活着的两颗地雷数量）：</p>
 *
 * <pre>
 * 阔剑地雷数量 0/1
 * 反人员地雷数量 0/2
 * </pre>
 *
 * <p><b>准星提示</b>：当准星对准任意一颗地雷时，在准星下方显示
 * 「对准对应地雷按下 X 主动引爆」—— 按键名用 {@code abilityBind} 动态读取，
 * 玩家改键后提示会立即跟着变（与 {@link GreenHud} 的做法一致）。</p>
 *
 * <p>写法照 {@link GreenHud}：{@code RoleHudRenderCallback.EVENT.register(职业 id, 回调)}，
 * 回调只会在「本地玩家就是该职业」时触发。数字来自服务端同步的
 * {@link BetterGangsterRoleData}（客户端无法自己可靠地数实体 —— 实体跟踪范围只有 64 格）。</p>
 */
public final class BetterGangsterHud {

    /** 准星射线的最远距离（与服务端校验的 64 格一致）。 */
    private static final double PICK_DISTANCE = 64.0D;

    private BetterGangsterHud() {
    }

    /** 注册 HUD（在客户端入口调用一次）。 */
    public static void register() {
        RoleHudRenderCallback.EVENT.register(BetterGangsterRole.ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()) {
                return;
            }

            // 还没同步到时按 0 显示
            BetterGangsterRoleData data = RoleData.getNullable(BetterGangsterRoleData.class, client.player);
            int claymores = data != null ? data.getClaymoresPlaced() : 0;
            int apMines = data != null ? data.getApMinesPlaced() : 0;

            Font font = client.font;
            int right = context.guiWidth() - 8;

            // 三行靠右下角，从上到下：阔剑地雷 / 反人员地雷 / 主动引爆按键提示
            Component claymoreText = Component.translatable(
                    "hud.cheeserolepack.gangster.claymore_count",
                    claymores, BetterGangsterRoleData.MAX_CLAYMORES);
            Component apMineText = Component.translatable(
                    "hud.cheeserolepack.gangster.ap_mine_count",
                    apMines, BetterGangsterRoleData.MAX_AP_MINES);
            // 常驻提示：最下面一栏，按键名动态读取
            Component keyHintText = Component.translatable(
                    "hud.cheeserolepack.gangster.remote_detonate_key", skillKeyName());

            int y3 = context.guiHeight() - 29;   // 最下面一栏：主动引爆提示
            int y2 = y3 - 11;                    // 反人员地雷
            int y1 = y2 - 11;                    // 阔剑地雷

            // 到上限时置灰，方便一眼看出「还能不能再放」
            int colorClaymore = claymores >= BetterGangsterRoleData.MAX_CLAYMORES ? 0xFFFF8080 : 0xFFFFD27F;
            int colorApMine = apMines >= BetterGangsterRoleData.MAX_AP_MINES ? 0xFFFF8080 : 0xFFFFD27F;
            // 准星正对着地雷时把按键提示高亮，表示「现在按就能引爆」
            int colorKeyHint = (isAimingAtMine(client)) ? 0xFFFF8080 : 0xFFAAAAAA;

            context.drawString(font, claymoreText, right - font.width(claymoreText), y1, colorClaymore);
            context.drawString(font, apMineText, right - font.width(apMineText), y2, colorApMine);
            context.drawString(font, keyHintText, right - font.width(keyHintText), y3, colorKeyHint);

            // —— 准星对准地雷时，在准星下方提示「按下 X 主动引爆」——
            if (isAimingAtMine(client)) {
                Component hint = Component.translatable(
                        "hud.cheeserolepack.gangster.remote_hint", skillKeyName());
                int centerX = context.guiWidth() / 2;
                int hintY = context.guiHeight() / 2 + 14;
                context.drawString(font, hint, centerX - font.width(hint) / 2, hintY, 0xFFFFD27F);
            }
        });
    }

    /** 准星当前是否指着任意一种地雷。 */
    private static boolean isAimingAtMine(Minecraft client) {
        // 与服务端的 RemoteDetonateTargeting 使用同一套射线算法与参数，
        // 所以「出现提示」⟺「按技能键能选到同一颗雷」。
        net.minecraft.world.entity.Entity target =
                com.cheese.rolepack.client.MineTargeting.pickMine(client.player);
        // 未布防的地雷无法远程引爆，所以不显示提示（提示必须与服务端行为一致）
        if (target instanceof com.cheese.rolepack.content.entity.ClaymoreMineEntity claymore) {
            return claymore.isArmed();
        }
        if (target instanceof com.cheese.rolepack.content.entity.AntiPersonnelMineEntity apMine) {
            return apMine.isArmed();
        }
        return false;
    }

    /**
     * 取当前绑定的技能键显示名（如 {@code X}、{@code 鼠标左键}）。
     *
     * <p>直接读 SRE 的 {@code NoellesrolesClient.abilityBind}，因此玩家改键后提示会立即跟着变；
     * 万一该字段拿不到（SRE 版本差异），退回一个占位符，绝不让 HUD 崩掉。</p>
     */
    private static Component skillKeyName() {
        try {
            KeyMapping abilityBind = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind;
            if (abilityBind != null) {
                return abilityBind.getTranslatedKeyMessage();
            }
        } catch (Throwable ignored) {
            // 忽略：拿不到就用占位符
        }
        return Component.translatable("hud.cheeserolepack.green.key_unknown");
    }
}
