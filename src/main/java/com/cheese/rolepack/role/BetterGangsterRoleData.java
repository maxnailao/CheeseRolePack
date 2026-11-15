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

    private static final String TAG_CLAYMORES = "claymores";
    private static final String TAG_AP_MINES = "apMines";

    /** 兜底重算间隔（tick）。 */
    private static final int RECONCILE_INTERVAL = 20;

    /** 本地玩家场上还活着的阔剑地雷数量。 */
    private int claymoresPlaced;

    /** 本地玩家场上还活着的反人员地雷数量。 */
    private int apMinesPlaced;

    /** 兜底重算计时器。 */
    private int reconcileTimer;

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
        tag.putInt(TAG_CLAYMORES, this.claymoresPlaced);
        tag.putInt(TAG_AP_MINES, this.apMinesPlaced);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
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
}
