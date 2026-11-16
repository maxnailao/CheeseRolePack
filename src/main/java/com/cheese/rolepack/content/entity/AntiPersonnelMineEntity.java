package com.cheese.rolepack.content.entity;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.init.ModEntities;
import com.cheese.rolepack.role.BetterGangsterRoleData;
import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ricord（RKD）的<b>反人员地雷</b>实体 —— 原版行为照搬自
 * {@code org.agmas.noellesroles.content.block_entity.LandmineBlockEntity}，
 * 但承载从「方块 + 方块实体」换成了实体（和我们的阔剑地雷同一套架构）。
 *
 * <p><b>注意</b>：这是<b>第二个</b>地雷，与 {@link ClaymoreMineEntity}（阔剑地雷）并存，互不影响。</p>
 *
 * <p>原版行为（严格照搬）：</p>
 * <ul>
 *   <li>布设后<b>布设者必须先离开布设位置</b>（阶段一）；</li>
 *   <li>布设者离开后再等 {@link #ARM_DELAY_TICKS} tick（3 秒）<b>自动布防</b>（阶段二）——
 *       不需要右键，这点和阔剑地雷不同；</li>
 *   <li>待发后有人站到雷所在的方块上 → 记下踩雷者并播放按钮音效（阶段四）；
 *       该玩家离开踩踏方块 → 引爆（阶段三）；</li>
 *   <li>引爆：以雷心为球心、半径 {@link #EXPLOSION_RADIUS} 格，范围内所有玩家被炸死，
 *       击杀者记为布设者。</li>
 * </ul>
 *
 * <p><b>方块 → 实体的对应关系</b>：原版所有判定都基于方块坐标，
 * 这里统一用 {@code this.blockPosition()}（地雷实体所在的那一格）代替原来的 {@code pos}，
 * 语义完全一致；「先移除方块再爆炸」对应成 {@code discard()}。</p>
 */
public class AntiPersonnelMineEntity extends Entity {

    /** 布设者离开后的布防延迟（3 秒 = 60 tick），与 RKD 的 {@code ARM_DELAY_TICKS} 一致。 */
    public static final int ARM_DELAY_TICKS = 60;

    /** 爆炸半径（格），与 RKD 的 {@code EXPLOSION_RADIUS} 一致。 */
    public static final float EXPLOSION_RADIUS = 2.5F;

    /** 每个玩家场上最多几颗（本模组新增的限制）。 */
    public static final int MAX_PER_PLAYER = 2;

    /** 用钳子拆除所需时长（3 秒 = 60 tick，与 SRE 的 {@code PliersDefuseManager} 一致）。 */
    public static final int PLIERS_DEFUSE_TICKS = 60;

    /** 被钳子拆除后返还给所有者的金币（售价 100 的一半）。 */
    public static final int PLIERS_DEFUSE_REFUND = 50;

    /** 钳子拆除时每多少 tick 刷一次 action bar 进度。 */
    public static final int PLIERS_DEFUSE_PROGRESS_INTERVAL = 10;

    /** 钳子拆除者离地雷的最远距离（格），超过即中断。 */
    public static final double PLIERS_DEFUSE_MAX_DISTANCE = 2.5D;

    /** 同步数据索引 0：是否已布防（客户端据此决定要不要画透视轮廓）。 */
    private static final EntityDataAccessor<Boolean> DATA_ARMED = SynchedEntityData.defineId(
            AntiPersonnelMineEntity.class, EntityDataSerializers.BOOLEAN);

    /** 布设者 UUID。 */
    private @Nullable UUID owner;

    /** 布设者是否已离开布设位置。 */
    private boolean ownerLeft;

    /** 布防倒计时。 */
    private int armTimer;

    /** 当前踩雷玩家 UUID。 */
    private @Nullable UUID victim;

    /** 踩雷玩家踩踏时的方块坐标。 */
    private @Nullable BlockPos victimStepPos;

    /** 正在用钳子拆除这颗雷的玩家 UUID（没人拆时为 {@code null}）。 */
    private @Nullable UUID defusingPlayer;

    /** 钳子拆除进度（tick）。 */
    private int defuseTicks;

    /**
     * 远程引爆前摇剩余 tick（服务端字段，不同步）。
     *
     * <p>由「更好的悍匪」的远程引爆技能设置，归零时执行正常的 {@link #explode(ServerLevel)}。
     * 前摇最长 3 秒，既不写 NBT 也不同步。</p>
     */
    private int remoteFuseTicks;

    /** 这次爆炸是不是「远程引爆」触发的（只有远程引爆没炸到人才返还金币）。 */
    private boolean remoteDetonated;

    public AntiPersonnelMineEntity(EntityType<? extends AntiPersonnelMineEntity> entityType, Level level) {
        super(entityType, level);
        // 地雷不动、也不该被方块碰撞推来推去
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** 布设用构造：直接落在给定坐标（玩家脚下）。 */
    public AntiPersonnelMineEntity(Level level, double x, double y, double z) {
        this(ModEntities.ANTI_PERSONNEL_MINE, level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ARMED, false);
    }

    // === 状态 ===

    public boolean isArmed() {
        return this.entityData.get(DATA_ARMED);
    }

    /** 只改同步数据（读档 / 布防时用）。 */
    public void setArmed(boolean armed) {
        this.entityData.set(DATA_ARMED, armed);
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    public void setOwner(@Nullable Player player) {
        setOwner(player == null ? null : player.getUUID());
    }

    // === tick（照搬 RKD 的四阶段状态机 + 本模组的钳子拆除 / 远程前摇）===

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 远程引爆前摇：到点直接引爆
        if (remoteFuseTicks > 0) {
            if (--remoteFuseTicks <= 0) {
                explode(serverLevel);
                return;
            }
        }

        // 钳子拆除：与下面的踩雷状态机互不影响（拆到一半有人踩照样会炸）
        tickPliersDefuse(serverLevel);

        // 原版的 pos 是方块坐标；实体形态用「地雷所在的那一格」等价替代
        BlockPos pos = this.blockPosition();

        // 阶段一：布设者尚未离开布设位置，不进行任何判定
        if (!ownerLeft) {
            Player ownerPlayer = owner != null ? serverLevel.getPlayerByUUID(owner) : null;
            if (ownerPlayer == null || !ownerPlayer.blockPosition().equals(pos)) {
                ownerLeft = true;
            } else {
                return;
            }
        }

        // 阶段二：布设者离开后延迟 3 秒才自动布防
        if (!isArmed()) {
            armTimer++;
            if (armTimer < ARM_DELAY_TICKS) {
                return;
            }
            setArmed(true);
        }

        // 阶段三：已有玩家踩雷
        // 新增：有掉落物压在雷上时不触发（玩家可以把物品丢在雷上作为反制/标记）
        if (hasItemEntityOnTop(serverLevel)) {
            // 顺手清掉已记录的踩踏状态，避免物品被捡走后立刻引爆
            if (victim != null) {
                victim = null;
                victimStepPos = null;
            }
            return;
        }

        if (victim != null) {
            Player victimPlayer = serverLevel.getPlayerByUUID(victim);
            if (victimPlayer == null || victimPlayer.isRemoved()
                    || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(victimPlayer)) {
                // 踩雷者离线或已死亡，重置踩踏状态
                victim = null;
                victimStepPos = null;
                return;
            }
            // 踩雷者离开其踩踏坐标 → 引爆
            if (!victimPlayer.blockPosition().equals(victimStepPos)) {
                explode(serverLevel);
            }
            return;
        }

        // 阶段四：检测是否有玩家踩上地雷
        for (Player player : serverLevel.players()) {
            if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                continue;
            }
            if (player.blockPosition().equals(pos)) {
                victim = player.getUUID();
                victimStepPos = player.blockPosition().immutable();
                // 踩雷时发出按按钮的声音
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.8F);
                break;
            }
        }
    }

    /**
     * 引爆：照搬 RKD {@code LandmineBlockEntity#explode} 的原版实现。
     *
     * <p>与原版的唯一差异：</p>
     * <ol>
     *   <li>「先 {@code setBlock(AIR)} 避免重复引爆」→ 实体形态改成 {@link #discard()}；</li>
     *   <li>原版对 {@code PuppeteerBodyEntity}（noellesroles 独有类，SRE 4.3.0 里不存在）
     *       也调了一次 {@code playerHurt}，这里<b>只保留 Player 分支</b>；</li>
     *   <li>死亡原因 id 从 {@code noellesroles:landmine_explosion} 改成
     *       {@code cheeserolepack:anti_personnel_mine_explosion}。</li>
     * </ol>
     * <p>其余（半径 2.5、五层 y 偏移采样、{@link GrenadeEntity#getPlayersAffectedByExplosion}、
     * {@code GENERIC_EXPLODE} 音效 + EXPLOSION_EMITTER/SMOKE 粒子、击杀者记布设者）完全一致。</p>
     */
    /**
     * 雷上是否压着掉落物。
     *
     * <p><b>玩法</b>：只要有一颗掉落物（{@link ItemEntity}）压在反人员地雷上，
     * 地雷就<b>不会</b>因踩踏而引爆 —— 玩家可以丢一件物品在雷上把它「压住」，
     * 作为发现地雷后的临时反制/标记手段（普通走路不会被炸）。</p>
     *
     * <p>判定范围：以雷心为中心、水平 ±0.75 格、竖直 −0.25 ~ +1.25 格的盒子，
     * 覆盖「贴在雷上」「落在雷上方一格内」这两种常见情况。</p>
     *
     * <p><b>注意</b>：只影响<b>踩踏触发</b>；远程引爆与钳子拆除不受掉落物影响
     * （那是刻意的引爆/排雷动作）。</p>
     */
    private boolean hasItemEntityOnTop(ServerLevel level) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                this.getX() - 0.75D, this.getY() - 0.25D, this.getZ() - 0.75D,
                this.getX() + 0.75D, this.getY() + 1.25D, this.getZ() + 0.75D);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                item -> item.isAlive() && !item.isRemoved()).isEmpty();
    }

    private void explode(ServerLevel level) {
        broadcastExploded(level);
        double cx = this.getX();
        double cy = this.getY() + 0.1D;
        double cz = this.getZ();

        // 先移除地雷本身，避免重复引爆（对应原版的 setBlock(AIR)），随后刷新布设者的 HUD 计数
        this.discard();
        BetterGangsterRoleData.refreshOwner(level, owner);

        // 爆炸音效与粒子
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 1.0F);
        // —— 粒子效果与杀伤半径匹配（EXPLOSION_RADIUS = 2.5 格）——
        // 原来的 EXPLOSION_EMITTER 是原版「大爆炸」那一团（TNT/苦力怕那种），视觉直径约 4~6 格，
        // 比 2.5 格的杀伤范围夸张一倍多；而 SMOKE 的散布只有 0.6 格，烟团又小得像个屁。
        // 改成：小的 EXPLOSION 多点散布 + 按半径铺开的烟/云，让「看到的范围」≈「炸到的范围」。
        //
        // sendParticles(type, x, y, z, count, xDist, yDist, zDist, speed) 里的 *Dist 是「半幅」：
        // 粒子会落在 ±Dist 的盒子里，所以写 1.8 ≈ 直径 3.6，配合半径 2.5 的边缘刚好。
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy + 0.3D, cz, 7, 1.8D, 0.6D, 1.8D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, cx, cy + 0.2D, cz, 70, 2.2D, 0.9D, 2.2D, 0.02D);
        level.sendParticles(ParticleTypes.CLOUD, cx, cy + 0.25D, cz, 24, 2.2D, 0.7D, 2.2D, 0.06D);

        // 击杀者记为布设该地雷的玩家
        Player killer = owner != null ? level.getPlayerByUUID(owner) : null;

        // 空间多层采样检测爆炸范围内的玩家（复用手雷的判定逻辑）
        Set<Entity> hit = new HashSet<>();
        double[] yOffsets = { 0.0D, 0.5D, -0.5D, 1.0D, -1.0D };
        for (double yOff : yOffsets) {
            hit.addAll(GrenadeEntity.getPlayersAffectedByExplosion(level, cx, cy + yOff, cz, EXPLOSION_RADIUS));
        }
        int kills = 0;
        for (Entity entity : hit) {
            // RKD 里还有 PuppeteerBodyEntity#playerHurt 分支（noellesroles 独有类），本模组不移植
            if (entity instanceof Player player) {
                GameUtils.killPlayer(player, true, killer,
                        CheeseRolePack.id("anti_personnel_mine_explosion"));
                kills++;
            }
        }

        // 远程引爆且一个人都没炸到 → 返还售价的 80%（踩雷触发不返还）
        com.cheese.rolepack.util.MineRefund.refundIfRemoteMissed(
                level, owner, com.cheese.rolepack.role.BetterGangsterRole.AP_MINE_PRICE,
                remoteDetonated, kills);
    }

    // === 钳子拆除（本模组新增，参考 SRE 的 PliersDefuseManager）===

    public @Nullable UUID getDefusingPlayer() {
        return defusingPlayer;
    }

    public int getDefuseTicks() {
        return defuseTicks;
    }

    /** 拆除进度百分比（0~100）。 */
    public int getDefusePercent() {
        return Math.min(100, defuseTicks * 100 / PLIERS_DEFUSE_TICKS);
    }

    /** 远程前摇是否正在倒计时。 */
    public boolean isRemoteFuseLit() {
        return remoteFuseTicks > 0;
    }

    /**
     * 点燃远程引爆前摇。
     *
     * @param ticks 前摇 tick（≥1）；到 0 时由 {@link #tick()} 执行 {@link #explode(ServerLevel)}
     */
    public void igniteRemoteFuse(int ticks) {
        this.remoteFuseTicks = Math.max(1, ticks);
        // 记住「这次是远程引爆」，fuse 到点后 explode() 里据此决定要不要返还金币
        this.remoteDetonated = true;
    }

    /**
     * 开始用钳子拆除（「主手拿钳子 + 右键」时调用）。
     *
     * @return {@code true} 表示这次点击有效（新开始，或就是同一个人在继续）；{@code false} 表示已有别人在拆
     */
    public boolean startDefusing(ServerPlayer player) {
        if (defusingPlayer != null) {
            return defusingPlayer.equals(player.getUUID());
        }
        this.defusingPlayer = player.getUUID();
        this.defuseTicks = 0;
        player.displayClientMessage(
                Component.translatable("message.cheeserolepack.anti_personnel_mine.defuse_start"), true);
        return true;
    }

    /**
     * 中断拆除并清空进度。
     *
     * @param player 收到中断提示的玩家，可为 {@code null}（例如离线 / 开局清场）
     */
    public void abortDefusing(@Nullable ServerPlayer player) {
        if (defusingPlayer == null) {
            return;
        }
        this.defusingPlayer = null;
        this.defuseTicks = 0;
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.anti_personnel_mine.defuse_aborted"), true);
        }
    }

    /**
     * 钳子拆除进度：每 tick 校验「拆除者仍在本维度、活着、仍手持钳子、距雷 ≤ 3 格」，
     * 任一不满足立即中断；满 {@link #PLIERS_DEFUSE_TICKS} tick 直接 {@link #discard()}
     * （不爆炸、不返还金币）；每 10 tick 刷一次 action bar。
     */
    private void tickPliersDefuse(ServerLevel level) {
        if (defusingPlayer == null) {
            return;
        }
        ServerPlayer defuser = level.getServer().getPlayerList().getPlayer(defusingPlayer);
        boolean valid = defuser != null
                && defuser.level() == level
                && !defuser.isRemoved()
                && defuser.isAlive()
                && isHoldingPliers(defuser)
                && defuser.distanceToSqr(this) <= PLIERS_DEFUSE_MAX_DISTANCE * PLIERS_DEFUSE_MAX_DISTANCE;
        if (!valid) {
            abortDefusing(defuser);
            return;
        }
        defuseTicks++;
        if (defuseTicks % PLIERS_DEFUSE_PROGRESS_INTERVAL == 0) {
            defuser.displayClientMessage(Component.translatable(
                    "message.cheeserolepack.anti_personnel_mine.defusing", getDefusePercent()), true);
        }
        if (defuseTicks >= PLIERS_DEFUSE_TICKS) {
            // 拆除成功：静默移除（不爆炸），并把 50 金返还给地雷所有者
            this.discard();
            BetterGangsterRoleData.refreshOwner(level, owner);
            refundOwnerOnDefuse(level);
            broadcastDefused(defuser);
            defuser.displayClientMessage(
                    Component.translatable("message.cheeserolepack.anti_personnel_mine.defuse_done"), false);
        }
    }

    /** 主手或副手是否拿着钳子（与 SRE 的 {@code isStillHoldingPliers} 同一判据）。 */
    /**
     * 被钳子拆除后把 {@value #PLIERS_DEFUSE_REFUND} 金返还给地雷所有者，并通知他。
     */
    private void refundOwnerOnDefuse(ServerLevel level) {
        if (owner == null || level.getServer() == null) {
            return;
        }
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null) {
            return;
        }
        try {
            io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(ownerPlayer)
                    .addToBalance(PLIERS_DEFUSE_REFUND);
            ownerPlayer.displayClientMessage(Component.translatable(
                    "message.cheeserolepack.anti_personnel_mine.defused_refund", PLIERS_DEFUSE_REFUND), false);
        } catch (Throwable ignored) {
            // 拿不到商店组件就算了，绝不影响拆除流程
        }
    }

    public static boolean isHoldingPliers(Player player) {
        return player.getMainHandItem().is(org.agmas.noellesroles.init.ModItems.PLIERS)
                || player.getOffhandItem().is(org.agmas.noellesroles.init.ModItems.PLIERS);
    }

    /**
     * 只有「主手拿钳子」才会开始拆除（右键交互用）。
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).is(org.agmas.noellesroles.init.ModItems.PLIERS)) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return startDefusing(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    // === 实体属性 ===


    /**
     * 放大原版准星的拾取半径。
     *
     * <p>地雷的碰撞箱只有 0.8×0.3，{@code Minecraft.hitResult} 用严格包围盒求交，
     * 站着瞄脚边那颗小雷几乎选不中 —— 而统一技能系统正是靠 {@code client.hitResult}
     * 取目标的（{@code ClientAbilityHandler#findTarget}）。
     * 返回 0.5 让准星对此类实体宽容一些，配合 HUD 提示与技能触发。</p>
     */
    @Override
    public float getPickRadius() {
        return 0.5F;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;  // 玩家可以直接走过去踩雷
    }

    // === 存档（照搬 RKD 的 NBT 键名） ===

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putBoolean("OwnerLeft", ownerLeft);
        tag.putInt("ArmTimer", armTimer);
        tag.putBoolean("Armed", isArmed());
        if (victim != null) {
            tag.putUUID("Victim", victim);
        }
        if (victimStepPos != null) {
            tag.putInt("VictimStepX", victimStepPos.getX());
            tag.putInt("VictimStepY", victimStepPos.getY());
            tag.putInt("VictimStepZ", victimStepPos.getZ());
        }
        // 钳子拆除进度（服务器重启后还能接着拆）
        if (defusingPlayer != null) {
            tag.putUUID("DefusingPlayer", defusingPlayer);
        }
        tag.putInt("DefuseTicks", defuseTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerLeft = tag.getBoolean("OwnerLeft");
        armTimer = tag.getInt("ArmTimer");
        // 只改同步数据，不播音效
        setArmed(tag.getBoolean("Armed"));
        victim = tag.hasUUID("Victim") ? tag.getUUID("Victim") : null;
        if (tag.contains("VictimStepX")) {
            victimStepPos = new BlockPos(tag.getInt("VictimStepX"), tag.getInt("VictimStepY"),
                    tag.getInt("VictimStepZ"));
        }
        defusingPlayer = tag.hasUUID("DefusingPlayer") ? tag.getUUID("DefusingPlayer") : null;
        defuseTicks = tag.getInt("DefuseTicks");
    }

    // === 场上查询 / 清场 ===

    /** 某玩家场上还活着的反人员地雷数量（HUD 计数 / 上限判定用）。 */
    public static int countOwned(@Nullable Player player) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        UUID target = player.getUUID();
        int count = 0;
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof AntiPersonnelMineEntity mine && !mine.isRemoved() && target.equals(mine.owner)) {
                count++;
            }
        }
        return count;
    }

    /** 当前维度里所有反人员地雷实体（快照，可安全遍历 / 修改）。 */
    public static List<AntiPersonnelMineEntity> allMines(ServerLevel level) {
        List<AntiPersonnelMineEntity> mines = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AntiPersonnelMineEntity mine) {
                mines.add(mine);
            }
        }
        return mines;
    }

    /**
     * 全清该维度里所有反人员地雷（<b>不引爆</b>）。
     *
     * <p>这是「本模组的补充」而非 RKD 原版行为：我们已经有每局清场的钩子
     * （{@code ModGameEvents}），顺手把反人员地雷也清掉，免得上一局的雷留到下一局炸人。</p>
     *
     * @return 清掉的数量
     */
    public static int clearAll(ServerLevel level) {
        List<AntiPersonnelMineEntity> mines = allMines(level);
        int removed = 0;
        for (AntiPersonnelMineEntity mine : mines) {
            if (mine.isRemoved()) {
                continue;
            }
            mine.abortDefusing(null);
            mine.discard();
            BetterGangsterRoleData.refreshOwner(level, mine.owner);
            removed++;
        }
        return removed;
    }

    /** 对局日志广播：「地雷被引爆」。 */
    private void broadcastExploded(ServerLevel level) {
        try {
            Player ownerPlayer = owner != null ? level.getPlayerByUUID(owner) : null;
            Component name = ownerPlayer != null
                    ? io.wifi.starrailexpress.api.replay.GameReplayUtils
                            .getReplayPlayerDisplayText(ownerPlayer, true)
                    : Component.literal("?");
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.cheeserolepack.anti_personnel_mine.exploded", name));
        } catch (Throwable ignored) {
        }
    }

    /** 对局日志广播：「地雷被拆除」（拆除者的名字）。 */
    private static void broadcastDefused(Player defuser) {
        if (defuser == null) {
            return;
        }
        try {
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.cheeserolepack.anti_personnel_mine.defused",
                            io.wifi.starrailexpress.api.replay.GameReplayUtils
                                    .getReplayPlayerDisplayText(defuser, true)));
        } catch (Throwable ignored) {
        }
    }
}
