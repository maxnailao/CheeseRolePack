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

    /** 开局安全时间的默认冷却：30 秒（600 tick），与 C4 的开局冷却一致。 */
    private static final int DEFAULT_SAFE_TIME_COOLDOWN_TICKS = 20 * 30;

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
        applySafeTimeItemCooldowns(level);
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

    /**
     * 开局安全时间：给本模组的主动物品上<b>和 C4 同款的冷却</b>。
     *
     * <p><b>为什么必须自己加</b>：SRE 的 {@code GameUtils.addItemCooldowns} 只对
     * 命名空间为 {@code starrailexpress} / {@code stupidexpress} / {@code noellesroles}
     * 的物品上冷却（C4 属于 noellesroles，所以开局自动 30 秒冷却）；
     * 我们 {@code cheeserolepack} 不在名单里，不补这一步的话地雷/RPG 在安全时间内可以直接用。</p>
     *
     * <p>时长直接读 SRE 的 {@code safeTimeCooldown} 配置（× 20 = tick），
     * 所以和 C4 完全一致，改配置时也会一起变。</p>
     */
    private static void applySafeTimeItemCooldowns(ServerLevel level) {
        try {
            net.minecraft.world.item.Item[] items = {
                    ModItems.CLAYMORE_MINE,
                    ModItems.ANTI_PERSONNEL_MINE,
                    ModItems.RPG7
            };
            int applied = 0;
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                // 优先用玩家身上的安全时间剩余时长（这样和 C4 的冷却同步结束）；
                // 拿不到效果就用默认 30 秒（= C4 的开局冷却）
                int ticks = DEFAULT_SAFE_TIME_COOLDOWN_TICKS;
                var effect = player.getEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME);
                if (effect != null) {
                    ticks = Math.max(20, effect.getDuration());
                }
                for (net.minecraft.world.item.Item item : items) {
                    if (item != null) {
                        player.getCooldowns().addCooldown(item, ticks);
                    }
                }
                applied = ticks;
            }
            CheeseRolePack.LOGGER.info("[安全时间] 已给地雷/RPG-7 上 {} tick 开局冷却（与 C4 同款）", applied);
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[安全时间] 上开局冷却失败（已忽略，不影响开局）", t);
        }
    }
}
