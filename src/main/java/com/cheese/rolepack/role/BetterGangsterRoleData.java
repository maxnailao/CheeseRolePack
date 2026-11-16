package com.cheese.rolepack.role;

import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 「更好的悍匪」的职业数据：场上还活着的两颗地雷数量。
 *
 * <p>客户端右下角 HUD 要显示 {@code 阔剑地雷数量 x/1}、{@code 反人员地雷数量 y/2}，
 * 但客户端**不能自己数实体**（实体跟踪范围只有 64 格，跑远一点就数不到），
 * 所以这里在服务端统计好、只同步给数据拥有者本人。</p>
 *
 * <p><b>怎么保证准</b>：计数不是「加一减一」算出来的，而是<b>从场上实体重新数一遍</b>
 * （{@link #reconcile()}），下面这些时机都会触发一次：</p>
 * <ul>
 *   <li>布设成功（物品里调用 {@link #refresh(Player)}）；</li>
 *   <li>拆除 / 引爆（实体在 {@code discard()} 前调用 {@link #refreshOwner(ServerLevel, UUID)}）；</li>
 *   <li>每局清场（{@code ModGameEvents} 清完再逐个刷新）；</li>
 *   <li><b>兜底</b>：{@link #serverTick()} 每 20 tick 重数一次 —— 即使有哪条移除路径漏了通知
 *       （区块卸载、/kill、其它模组删实体……），HUD 最多 1 秒后也会自动纠回正确值。</li>
 * </ul>
 */
public class BetterGangsterRoleData extends SimpleRoleData {

    /** 阔剑地雷的场上上限（与 {@code ClaymoreMineEntity.hasMine} 的判定一致）。 */
    public static final int MAX_CLAYMORES = 1;

    /** 反人员地雷的场上上限。 */
    public static final int MAX_AP_MINES = 2;

    private static final String TAG_KNIFE_REWARD = "psychoKnifeReward";
    private static final String TAG_CLAYMORES = "claymores";
    private static final String TAG_AP_MINES = "apMines";

    /** 兜底重算间隔（tick）。 */
    /** 瘋狂模式期間小刀冷卻上限：10 秒（200 tick）。 */
    public static final int KNIFE_PSYCHO_COOLDOWN_TICKS = 200;

    private static final int RECONCILE_INTERVAL = 20;

    /** 本地玩家场上还活着的阔剑地雷数量。 */
    private int claymoresPlaced;

    /** 本地玩家场上还活着的反人员地雷数量。 */
    private int apMinesPlaced;

    /** 兜底重算计时器。 */
    private int reconcileTimer;

    /** 上一 tick 小刀是否处于冷却中（用来检测「刚挥刀」这个跳变，只钳制一次）。 */
    private boolean knifeCooldownSeen;

    /** 本次疯狂模式是否已经发过「刀杀奖励火箭弹」（每次疯狂模式只给一次）。 */
    private boolean psychoKnifeRewardGranted;

    public BetterGangsterRoleData(RoleDataContext context) {
        super(context);
    }

    public int getClaymoresPlaced() {
        return claymoresPlaced;
    }

    public int getApMinesPlaced() {
        return apMinesPlaced;
    }

    /** 写入两个计数（值没变就不发包）。 */
    public void setCounts(int claymores, int apMines) {
        int clampedClaymores = Math.max(0, claymores);
        int clampedAp = Math.max(0, apMines);
        if (this.claymoresPlaced == clampedClaymores && this.apMinesPlaced == clampedAp) {
            return;
        }
        this.claymoresPlaced = clampedClaymores;
        this.apMinesPlaced = clampedAp;
        sync();
    }

    /** 从场上实体重新数一遍并写回（服务端调用）。 */
    public void reconcile() {
        if (this.player == null || this.player.level().isClientSide) {
            return;
        }
        setCounts(ClaymoreMineEntity.countOwned(this.player), AntiPersonnelMineEntity.countOwned(this.player));
    }

    /** 每局重置。 */
    @Override
    public void init() {
        this.claymoresPlaced = 0;
        this.apMinesPlaced = 0;
        this.reconcileTimer = 0;
    }

    /** 服务端每 tick：每 {@link #RECONCILE_INTERVAL} tick 兜底重数一次。 */
    @Override
    public void serverTick() {
        // 疯狂模式期间：小刀冷却上限 10 秒（每 tick 检查，早于下面的兜底计时器）
        clampKnifeCooldownInPsycho();

        if (++reconcileTimer < RECONCILE_INTERVAL) {
            return;
        }
        reconcileTimer = 0;
        reconcile();
    }

    /** 只同步给本人（别人的地雷数量与他无关）。 */
    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean(TAG_KNIFE_REWARD, this.psychoKnifeRewardGranted);
        tag.putInt(TAG_CLAYMORES, this.claymoresPlaced);
        tag.putInt(TAG_AP_MINES, this.apMinesPlaced);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 恢复「本次疯狂模式刀杀奖励已发」标记：否则重登/重连后能再领一次
        this.psychoKnifeRewardGranted = tag.getBoolean(TAG_KNIFE_REWARD);
        this.claymoresPlaced = Math.max(0, tag.getInt(TAG_CLAYMORES));
        this.apMinesPlaced = Math.max(0, tag.getInt(TAG_AP_MINES));
    }

    // === 静态快捷入口（给物品 / 实体 / 清场用）===

    /** 取该玩家的职业数据；不是更好的悍匪（或还没绑数据）时返回 {@code null}。 */
    public static @Nullable BetterGangsterRoleData get(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return RoleData.getNullable(BetterGangsterRoleData.class, player);
    }

    /** 立即重数并同步该玩家的 HUD 计数。 */
    public static void refresh(@Nullable Player player) {
        BetterGangsterRoleData data = get(player);
        if (data != null) {
            data.reconcile();
        }
    }

    /** 按布设者 UUID 刷新（地雷被拆除 / 引爆时用；布设者不在线则跳过）。 */
    public static void refreshOwner(ServerLevel level, @Nullable UUID owner) {
        if (owner == null || level == null) {
            return;
        }
        Player ownerPlayer = level.getPlayerByUUID(owner);
        if (ownerPlayer != null) {
            refresh(ownerPlayer);
        }
    }

    /**
     * 疯狂模式期间把「小刀冷却」压到最多 {@value #KNIFE_PSYCHO_COOLDOWN_TICKS} tick（10 秒）。
     *
     * <p><b>为什么用「钳制」而不是改配置</b>：SRE 的刀冷却来自
     * {@code GameConstants.ITEM_COOLDOWNS}（默认 {@code SREConfig.knifeCooldown * 20}，即 30 秒），
     * 那是<b>全局静态表</b>，直接改会影响所有玩家。
     * 这里改成：玩家挥刀后 SRE 先把冷却设成 30 秒，我们在每 tick 检查一次，
     * 发现剩余超过 10 秒就重新设为 10 秒 —— 只作用于处于疯狂模式的本人。</p>
     *
     * <p>剩余时长 = 剩余比例 × 该次冷却的总时长（{@code ItemCooldowns} 只暴露比例）。</p>
     */
    private void clampKnifeCooldownInPsycho() {
        try {
            if (!(this.player instanceof ServerPlayer sp)) {
                return;
            }
            io.wifi.starrailexpress.cca.SREPlayerPsychoComponent psycho =
                    io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.get(sp);
            net.minecraft.world.item.Item knife = io.wifi.starrailexpress.index.TMMItems.KNIFE;
            boolean onCooldown = sp.getCooldowns().isOnCooldown(knife);

            // 只在「疯狂模式 + 刚挥了一刀」这个跳变上钳制一次。
            //
            // 不能每 tick 都钳：ItemCooldowns 只暴露「占本次冷却总时长的比例」，
            // 我们把冷却改成 200 tick 之后，这条记录的总时长也变成 200，
            // 再用全局的 600 去乘比例就会算回 ~600，于是每 tick 都重新钳成 10 秒 ——
            // 冷却永远走不完（表现就是「卡在 10 秒不动」）。
            if (psycho != null && psycho.psychoTicks > 0 && onCooldown && !knifeCooldownSeen) {
                sp.getCooldowns().addCooldown(knife, KNIFE_PSYCHO_COOLDOWN_TICKS);
            }
            knifeCooldownSeen = onCooldown;
        } catch (Throwable ignored) {
            // 冷却钳制失败绝不影响对局
        }
    }

    /** 本次疯狂模式的刀杀奖励是否已经发过（每次疯狂模式只给一次）。 */
    public boolean isPsychoKnifeRewardGranted() {
        return this.psychoKnifeRewardGranted;
    }

    /** 标记「本次疯狂模式的刀杀奖励已发」。 */
    public void setPsychoKnifeRewardGranted(boolean granted) {
        this.psychoKnifeRewardGranted = granted;
    }
}
