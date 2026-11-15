package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import net.minecraft.server.level.ServerLevel;

/**
 * 本模组的 SRE 对局生命周期事件。
 *
 * <p>目前只做一件事：<b>每局开始 / 每局结束清掉场上所有地雷</b>
 * （{@link ClaymoreMineEntity#clearAll(ServerLevel)} 与
 * {@link AntiPersonnelMineEntity#clearAll(ServerLevel)}）。清场是「静默移除」——
 * 直接 {@code discard()} 掉实体，<b>不会触发爆炸、也不返还金币</b>。</p>
 *
 * <p>三个事件的签名（已用 javap 在发布版 jar 与 dev jar 中逐一核对，两者一致）：</p>
 * <ul>
 *   <li>{@code OnGameStarted.onGameStarted(ServerLevel)}；</li>
 *   <li>{@code OnGameTrueStarted.onGameTrueStarted(ServerLevel)}；</li>
 *   <li>{@code OnGameEnd.onGameEnd(ServerLevel, SREGameWorldComponent)}（比对局开始多一个世界组件参数）。</li>
 * </ul>
 *
 * <p>同时挂「开始」与「真正开始」两个事件是刻意的：SRE 里这两个事件之间还夹着职业分配，
 * 两边都清一次可以保证不管是哪种流程进来的地雷都不会留到下一局。</p>
 */
public final class ModGameEvents {

    private ModGameEvents() {
    }

    /** 注册全部对局事件（在 {@code CheeseRolePack#onInitialize()} 里调用一次）。 */
    public static void register() {
        OnGameStarted.EVENT.register(ModGameEvents::onGameStarted);
        OnGameTrueStarted.EVENT.register(ModGameEvents::onGameTrueStarted);
        OnGameEnd.EVENT.register(ModGameEvents::onGameEnd);
    }

    private static void onGameStarted(ServerLevel level) {
        clearMines(level, "OnGameStarted");
    }

    private static void onGameTrueStarted(ServerLevel level) {
        clearMines(level, "OnGameTrueStarted");
    }

    private static void onGameEnd(ServerLevel level, SREGameWorldComponent gameComponent) {
        clearMines(level, "OnGameEnd");
    }

    /** 清场：不管出什么问题都不能影响对局流程本身。 */
    private static void clearMines(ServerLevel level, String source) {
        try {
            int claymores = ClaymoreMineEntity.clearAll(level);
            if (claymores > 0) {
                CheeseRolePack.LOGGER.info("[Mines] {}：清掉场上 {} 颗阔剑地雷", source, claymores);
            }
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[Mines] {}：阔剑地雷清场失败", source, t);
        }
        try {
            int apMines = AntiPersonnelMineEntity.clearAll(level);
            if (apMines > 0) {
                CheeseRolePack.LOGGER.info("[Mines] {}：清掉场上 {} 颗反人员地雷", source, apMines);
            }
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[Mines] {}：反人员地雷清场失败", source, t);
        }
    }
}
