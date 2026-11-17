package com.cheese.rolepack.content.entity;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.item.ClaymoreMineItem;
import com.cheese.rolepack.init.ModEntities;
import com.cheese.rolepack.role.BetterGangsterRoleData;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 阔剑地雷<b>实体</b>（由原方块实体 {@code ClaymoreMineBlockEntity} 整体迁移而来）。
 *
 * <p>为什么要从方块改成实体：方块形态要求「玩家脚下那一格可替换」，
 * 站在地毯 / 半砖 / 各种装饰方块上就完全放不下来；改成实体后布设<b>不再受任何方块阻挡</b>。</p>
 *
 * <p>模型仍然复用 {@link com.cheese.rolepack.content.block.ClaymoreMineBlock} 的 blockstate / 模型 / 贴图 ——
 * 那些资源只是给渲染器当「模型容器」用（{@code client/render/ClaymoreMineEntityRenderer} 里
 * 用 {@code BlockRenderDispatcher#renderSingleBlock} 画出来），方块本身永远不会被放进世界。</p>
 *
 * <p>行为（与原方块实体一致）：</p>
 * <ul>
 *   <li>布设时是<b>未布防</b>：模型不显示激光，也不参与引爆判定；</li>
 *   <li>空手右键布防；蹲下 + 空手右键开始拆除（5 秒 / 100 tick，蹲着且在 3 格内）；</li>
 *   <li>已布防时：玩家进入正面 0.8 格、横向 0.5 格内即引爆，
 *       杀伤是以正面为轴、半径 2.0 格、总张角 70° 的扇形；</li>
 *   <li>拆除完成返还 {@link ClaymoreMineItem#PRICE} 金并通知布设者。</li>
 * </ul>
 */
public class ClaymoreMineEntity extends Entity {

    /** 引爆判定：玩家必须落在正面方向 0 ~ 0.8 格的这一层里。 */
    /** 布设后 2 秒才允许手动布防（40 tick）。 */
    public static final int ARM_DELAY_TICKS = 20;

    public static final double TRIGGER_DEPTH = 1.0D;

    /** 引爆判定：正面那一层板的横向半宽。 */
    public static final double TRIGGER_HALF_WIDTH = 0.5D;

    /** 扇形杀伤的最大距离（格），对齐短管霰弹枪 {@code MIN_KILL_RANGE = 2.0}。 */
    public static final double KILL_RANGE = 3.0D;

    /** 扇形杀伤的半张角（度），总张角 70°，对齐霰弹枪 {@code FAN_HALF_ANGLE_DEGREES = 35.0}。 */
    public static final double KILL_HALF_ANGLE_DEGREES = 37.5D;

    /**
     * 雷心核心杀伤半径（格）：除了正前方扇形，紧贴地雷的这一个球也算杀伤范围。
     *
     * <p>解决「站在雷正上方/侧面却因为扇形判定不到而不死」的问题 —— 现实里贴这么近肯定会被炸。</p>
     */
    public static final double CORE_KILL_RADIUS = 0.3D;

    /**
     * 杀伤体积在 Y 轴上的高度（格）：雷心向上/向下各 {@value #KILL_HEIGHT} 格的容差。
     *
     * <p>之前扇形判定只算水平（X/Z）距离，<b>完全没有 Y 轴</b>，
     * 于是「站在雷正上方/正下方」「跳起来」「站在半砖上」这些情况会因为 depth≈0 而漏判，
     * 表现就是「明明贴着雷却不死」。加上这个高度窗口后，垂直方向 1 格内都算杀伤范围。</p>
     */
    public static final double KILL_HEIGHT = 1.0D;

    /** 半张角的正切值，用于 {@code |lateral| <= depth * tan(35°)} 的快速判定。 */
    private static final double KILL_TAN_HALF_ANGLE = Math.tan(Math.toRadians(KILL_HALF_ANGLE_DEGREES));

    /** 拆除所需时长（5 秒 = 100 tick）。 */
    public static final int DEFUSE_TICKS = 100;

    /** 拆除过程中每多少 tick 刷新一次 action bar 进度。 */
    public static final int DEFUSE_PROGRESS_INTERVAL = 10;

    /** 拆除者离地雷的最远距离（格），超过即中断。 */
    public static final double DEFUSE_MAX_DISTANCE = 1.0D;

    /** 同步数据索引 16：是否已布防（客户端渲染激光模型要用）。 */
    private static final EntityDataAccessor<Boolean> DATA_ARMED = SynchedEntityData.defineId(
            ClaymoreMineEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 同步数据索引 17：正面朝向的 <b>yaw（度，任意角度）</b>。
     *
     * <p>早期版本存的是四向 {@code Direction}，只能朝四个方向；现在改成浮点 yaw，
     * 布设时取 {@code player.getYRot()}，所以「朝哪看就朝哪炸」是连续的。
     * 默认 0（= 朝 +Z / 南）。</p>
     */
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(
            ClaymoreMineEntity.class, EntityDataSerializers.FLOAT);

    /** 布设者 UUID（爆炸的击杀者与拆除返还金币的对象）。 */
    private @Nullable UUID owner;

    /** 正在拆除这颗地雷的玩家 UUID（没人拆时为 {@code null}）。 */
    private @Nullable UUID defusingPlayer;

    /** 当前拆除进度（tick）。 */
    private int defuseTicks;

    /**
     * 远程引爆前摇剩余 tick（服务端字段，不同步）。
     *
     * <p>由「更好的悍匪」的远程引爆技能设置（{@link #igniteRemoteFuse(int)}），
     * 每 tick 递减，归零时执行正常的 {@link #explode(ServerLevel)}。
     * 前摇最长 3 秒，短到不值得存档 / 同步，所以既不写 NBT 也不同步。</p>
     */
    private int remoteFuseTicks;

    /** 这次爆炸是不是「远程引爆」触发的（只有远程引爆没炸到人才返还金币）。 */
    private boolean remoteDetonated;

    public ClaymoreMineEntity(EntityType<? extends ClaymoreMineEntity> entityType, Level level) {
        super(entityType, level);
        // 地雷不动、也不该被方块碰撞推来推去
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** 布设用构造：直接落在给定坐标（玩家脚下）并记住朝向 yaw。 */
    public ClaymoreMineEntity(Level level, double x, double y, double z, float yaw) {
        this(ModEntities.CLAYMORE_MINE, level);
        this.setPos(x, y, z);
        this.setYaw(yaw);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ARMED, false);
        builder.define(DATA_YAW, 0.0F);
    }

    // === 同步状态 ===

    public boolean isArmed() {
        return this.entityData.get(DATA_ARMED);
    }

    /** 只改数据（读档时用，不播音效）。 */
    public void setArmed(boolean armed) {
        this.entityData.set(DATA_ARMED, armed);
    }

    /** 布防（带一声按钮点击音效），已布防时是空操作。 */
    public void arm() {
        if (isArmed()) {
            return;
        }
        // 刚放下的 2 秒内不能手动布防（防止放下就立刻炸到自己）
        if (this.tickCount < ARM_DELAY_TICKS) {
            if (!this.level().isClientSide) {
                int remainTicks = ARM_DELAY_TICKS - this.tickCount;
                Component msg = Component.translatable(
                        "message.cheeserolepack.claymore_mine.arm_too_early",
                        String.format("%.1f", remainTicks / 20.0F));
                if (this.getOwner() != null) {
                    java.util.UUID ownerId = this.getOwner();
                    if (this.level() instanceof ServerLevel serverLevel) {
                        ServerPlayer ownerPlayer = serverLevel.getServer() == null
                                ? null
                                : serverLevel.getServer().getPlayerList().getPlayer(ownerId);
                        if (ownerPlayer != null) {
                            ownerPlayer.displayClientMessage(msg, true);
                        }
                    }
                }
            }
            return;
        }
        setArmed(true);
        if (!this.level().isClientSide) {
            this.playSound(SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F, 0.8F);
        }
    }

    /** 正面朝向的 yaw（度）：Minecraft 约定，0 = +Z（南），90 = -X（西）。 */
    public float getYaw() {
        return this.entityData.get(DATA_YAW);
    }

    /** 设置正面朝向 yaw（度）；存进同步数据前会归一化到 [0, 360)。 */
    public void setYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized < 0.0F) {
            normalized += 360.0F;
        }
        this.entityData.set(DATA_YAW, normalized);
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

    /** 远程前摇是否正在倒计时。 */
    public boolean isRemoteFuseLit() {
        return remoteFuseTicks > 0;
    }

    /**
     * 正面单位向量（激光模型与扇形判定共用这一个朝向）。
     *
     * <p>按 Minecraft 的 yaw 约定换算：yaw 0 → +Z（南）、90 → -X（西）。
     * 与 {@code Entity#getLookAngle()} 的水平分量完全一致。</p>
     */
    public Vec3 frontVector() {
        double radians = Math.toRadians(getYaw());
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
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

    // === 拆除状态 ===

    public @Nullable UUID getDefusingPlayer() {
        return defusingPlayer;
    }

    public int getDefuseTicks() {
        return defuseTicks;
    }

    /** 拆除进度百分比（0~100）。 */
    public int getDefusePercent() {
        return Math.min(100, defuseTicks * 100 / DEFUSE_TICKS);
    }

    /**
     * 开始拆除（「蹲下 + 空手右键」时调用）。
     *
     * @return {@code true} 表示这次点击有效（新开始，或就是同一个人在继续）；{@code false} 表示已有别人在拆
     */
    public boolean startDefusing(ServerPlayer player) {
        // 先判距离：不够就别进入拆除流程（否则下一 tick 的距离校验会失败，
        // 玩家看到的是「拆除中断」，很容易以为是 bug）
        if (player.distanceToSqr(this) > DEFUSE_MAX_DISTANCE * DEFUSE_MAX_DISTANCE) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.claymore_mine.defuse_too_far"), true);
            return false;
        }
        if (defusingPlayer != null) {
            return defusingPlayer.equals(player.getUUID());
        }
        this.defusingPlayer = player.getUUID();
        this.defuseTicks = 0;
        player.displayClientMessage(
                Component.translatable("message.cheeserolepack.claymore_mine.defuse_start"), true);
        // 与 C4 拆除一致的起手音效
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.TRIPWIRE_CLICK_ON, net.minecraft.sounds.SoundSource.PLAYERS, 0.65F, 1.45F);
        }
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
                    Component.translatable("message.cheeserolepack.claymore_mine.defuse_aborted"), true);
        }
    }

    /**
     * 拆除成功结算：移除实体 + 一声轻响 + 通知布设者并返还金币。
     *
     * <p>想「静默移除」（例如开局清场）请用 {@link #discard()}，它不返还任何东西。</p>
     *
     * @param byPlayer 拆除者，可为 {@code null}
     */
    public void defuse(@Nullable ServerPlayer byPlayer) {
        Level level = this.level();
        // 先抓住布设者（discard 之后实体就下线了）
        Player ownerPlayer = (owner != null) ? level.getPlayerByUUID(owner) : null;
        String defuserName = byPlayer != null ? byPlayer.getName().getString() : "";
        broadcastDefused(byPlayer);

        this.discard();
        // HUD 计数：地雷下线后重数一遍
        if (level instanceof ServerLevel serverLevel) {
            BetterGangsterRoleData.refreshOwner(serverLevel, owner);
        }

        if (level instanceof ServerLevel serverLevel) {
            // 与 C4 拆除成功一致：剪线 + 剪切两个音效
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.TRIPWIRE_CLICK_OFF, net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.2F);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        if (byPlayer != null) {
            byPlayer.displayClientMessage(
                    Component.translatable("message.cheeserolepack.claymore_mine.defuse_done"), false);
        }
        if (ownerPlayer != null) {
            // 通知布设者：他的雷被拆了（自己拆自己的也给一条，便于确认）
            ownerPlayer.displayClientMessage(Component.translatable(
                    "message.cheeserolepack.claymore_mine.defused_notify", defuserName, ClaymoreMineItem.PRICE), false);
            // 返还购买价（拿不到商店组件时静默跳过，绝不影响拆除本身）
            try {
                SREPlayerShopComponent.KEY.get(ownerPlayer).addToBalance(ClaymoreMineItem.PRICE);
            } catch (Throwable t) {
                CheeseRolePack.LOGGER.warn("拆除阔剑地雷后返还 {} 金失败，玩家 = {}",
                        ClaymoreMineItem.PRICE, ownerPlayer.getName().getString(), t);
            }
        }
    }

    // === tick ===

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 远程引爆前摇：到点就直接引爆（与正常触雷走同一个 explode）
        if (remoteFuseTicks > 0) {
            if (--remoteFuseTicks <= 0) {
                explode(serverLevel);
                return;
            }
        }
        tickDefusing(serverLevel);
        if (isArmed()) {
            tickTrigger(serverLevel);
        }
    }

    /**
     * 拆除进度：每 tick 校验「拆除者仍在本维度、活着、仍蹲下、距雷 ≤ 3 格」，任一不满足立即中断；
     * 满 {@link #DEFUSE_TICKS} tick 走 {@link #defuse(ServerPlayer)}；每 10 tick 刷一次 action bar。
     */
    private void tickDefusing(ServerLevel level) {
        if (defusingPlayer == null) {
            return;
        }
        // PlayerList#getPlayer 找不到（离线）时返回 null；维度另行用 level() 比对
        ServerPlayer defuser = level.getServer().getPlayerList().getPlayer(defusingPlayer);
        boolean valid = defuser != null
                && defuser.level() == level                          // 仍在同一维度
                && !defuser.isRemoved()
                && defuser.isAlive()
                // 用拆弹钳拆除不再要求蹲下（直接拿着钳子右键即可，参考 C4 的拆除手感）
                && defuser.distanceToSqr(this) <= DEFUSE_MAX_DISTANCE * DEFUSE_MAX_DISTANCE;
        if (!valid) {
            abortDefusing(defuser);
            return;
        }
        defuseTicks++;
        // 与 C4 拆除一致的进度粒子：每 tick 一小簇 CRIT
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                this.getX(), this.getY() + 0.15D, this.getZ(), 4, 0.12D, 0.12D, 0.12D, 0.02D);
        if (defuseTicks % 10 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.LEVER_CLICK, net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.8F);
        }
        if (defuseTicks % DEFUSE_PROGRESS_INTERVAL == 0) {
            defuser.displayClientMessage(Component.translatable(
                    "message.cheeserolepack.claymore_mine.defusing", getDefusePercent()), true);
        }
        if (defuseTicks >= DEFUSE_TICKS) {
            defuse(defuser);
        }
    }

    /**
     * 引爆判定：把玩家脚下中心相对雷心的水平位移投影到正面方向 {@code f} 与其垂线 {@code r} 上，
     * 落在 {@code 0 < 正面投影 ≤ 0.8}、{@code |横向投影| ≤ 0.5} 的这层板里即引爆。
     *
     * <p>站在雷心（正面投影为 0）不触发 —— 这样布设完就地空手布防不会立刻炸到自己。</p>
     */
    private void tickTrigger(ServerLevel level) {
        Vec3 front = frontVector();
        double centerX = this.getX();
        double centerZ = this.getZ();
        double mineMinY = this.getY();
        double mineMaxY = this.getY() + 1.0D;

        for (Player player : level.players()) {
            if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                continue;
            }
            // 只判定身体与地雷同一层的人，避免楼上/地下的人隔着地板触雷
            if (player.getBoundingBox().maxY <= mineMinY || player.getBoundingBox().minY >= mineMaxY) {
                continue;
            }
            double offsetX = player.getX() - centerX;
            double offsetZ = player.getZ() - centerZ;
            double depth = offsetX * front.x + offsetZ * front.z;
            if (depth <= 0.0D || depth > TRIGGER_DEPTH) {
                continue;
            }
            double lateral = Math.abs(offsetX * -front.z + offsetZ * front.x);
            if (lateral > TRIGGER_HALF_WIDTH) {
                continue;
            }
            explode(level);
            return;
        }
    }

    // === 引爆 ===

    /**
     * 引爆：播放爆炸音效 + 用 {@link ParticleTypes#SOUL_FIRE_FLAME} 画出杀伤扇形，
     * 然后按扇形（{@link #KILL_RANGE} 格 / 总张角 70°）击杀范围内的玩家，最后 {@link #discard()}。
     */
    public void explode(ServerLevel level) {
        broadcastExploded(level);
        Vec3 center = this.position();

        // 先下线自己（避免同一 tick 重复引爆），再刷新布设者的 HUD 计数
        this.discard();
        BetterGangsterRoleData.refreshOwner(level, owner);

        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);

        Vec3 front = frontVector();
        // 把真实杀伤区画出来：外弧 + 两条直边
        drawKillFan(level, center.add(0.0D, 0.15D, 0.0D), front, KILL_RANGE, KILL_HALF_ANGLE_DEGREES);

        // 击杀者记为布设该地雷的玩家
        Player killer = owner != null ? level.getPlayerByUUID(owner) : null;
        // 本次爆炸击杀人数：远程引爆「没炸到人」时才返还金币
        int kills = 0;
        // 命中爆炸体积的玩家数（含带盾没死的）—— 空炸返还用它判断
        int hits = 0;
        // 正面在水平面上的垂线（右手侧）
        double rightX = -front.z;
        double rightZ = front.x;

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || !GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            double offsetX = player.getX() - center.x;
            double offsetZ = player.getZ() - center.z;
            double depth = offsetX * front.x + offsetZ * front.z;
            double lateral = Math.abs(offsetX * rightX + offsetZ * rightZ);

            // Y 轴窗口：雷心上下各 KILL_HEIGHT 格（解决「站在雷上/跳起来/踩半砖」漏判）
            double deltaY = player.getY() - center.y;
            if (Math.abs(deltaY) > KILL_HEIGHT) {
                continue;
            }

            // ① 正前方扇形：0 < 深度 <= KILL_RANGE 且横向不超出该深度的张角
            boolean inFan = depth > 0.0D && depth <= KILL_RANGE
                    && lateral <= depth * KILL_TAN_HALF_ANGLE;
            // ② 雷心核心圆柱：水平距离 <= CORE_KILL_RADIUS（Y 已在上面限定），
            //    这样正上方 / 正下方 / 侧面贴身都算，不再依赖 3D 球心距离
            boolean inCore = (offsetX * offsetX + offsetZ * offsetZ)
                    <= CORE_KILL_RADIUS * CORE_KILL_RADIUS;
            // ③ 触发区本身：只要踩进触发矩形，就一定被炸。
            //    必须加上这一条 —— 触发区是「深度 0~1.4 × 横向 ±0.5」的矩形，
            //    而扇形在靠近雷心处非常窄（depth×0.767），侧面贴近会落在
            //    「触发区内、扇形外」，表现就是「触发了却没死」。
            boolean inTriggerBox = depth > 0.0D && depth <= TRIGGER_DEPTH
                    && lateral <= TRIGGER_HALF_WIDTH;

            if (!inFan && !inCore && !inTriggerBox) {
                continue;
            }
            // 先记「命中」：哪怕对方带护盾没死，也算炸到人（不算空炸）
            hits++;
            GameUtils.killPlayer(player, true, killer, CheeseRolePack.id("claymore_mine_explosion"));
            kills++;
        }

        // 远程引爆且一个人都没炸到 → 返还售价的 80%（踩雷触发不返还）
        // 空炸（命中 0 人）→ 返还售价的 90%（无论是否远程引爆）
        com.cheese.rolepack.util.MineRefund.refundIfEmptyBlast(
                level, owner, com.cheese.rolepack.content.item.ClaymoreMineItem.PRICE, hits);
    }

    /**
     * 把「杀伤扇形」用粒子画出来（外弧 + 两条直边），
     * 形态对齐短管霰弹枪 {@code ShortShotgunItem#spawnFanBoundaryParticles}（同样 SOUL_FIRE_FLAME 描边）。
     */
    private static void drawKillFan(ServerLevel level, Vec3 origin, Vec3 front, double range, double halfAngleDeg) {
        Vec3 aim = new Vec3(front.x, 0.0D, front.z);
        if (aim.lengthSqr() < 1.0E-6D) {
            return;
        }
        aim = aim.normalize();
        Vec3 right = new Vec3(-aim.z, 0.0D, aim.x);

        // 外弧
        for (double angle = -halfAngleDeg; angle <= halfAngleDeg + 0.001D; angle += 3.5D) {
            double rad = Math.toRadians(angle);
            Vec3 dir = aim.scale(Math.cos(rad)).add(right.scale(Math.sin(rad)));
            Vec3 at = origin.add(dir.scale(range));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        // 两条直边
        for (double edge : new double[] { -halfAngleDeg, halfAngleDeg }) {
            double rad = Math.toRadians(edge);
            Vec3 dir = aim.scale(Math.cos(rad)).add(right.scale(Math.sin(rad)));
            for (double distance = 0.0D; distance <= range + 0.001D; distance += 0.35D) {
                Vec3 at = origin.add(dir.scale(distance));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    // === 右键交互 ===

    /**
     * 空手右键地雷：
     * <ul>
     *   <li><b>蹲下</b> + 空手 → 开始拆除；</li>
     *   <li>未蹲下 + 空手 + 未布防 → 布防；</li>
     *   <li>已布防 → PASS（不再切换状态）。</li>
     * </ul>
     *
     * <p>主手非空（包括手里拿着阔剑地雷物品）一律 PASS，把这次右键让回给手里的物品。</p>
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // ① 拆弹钳：任何人都能拆（主手或副手拿着钳子）
        boolean pliers = player.getItemInHand(hand).is(org.agmas.noellesroles.init.ModItems.PLIERS)
                || player.getOffhandItem().is(org.agmas.noellesroles.init.ModItems.PLIERS);
        if (pliers) {
            if (this.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return startDefusing(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        // ② 所有者蹲下 + 空手右键：把地雷收回背包（未布防/已布防都能收）
        if (player.isShiftKeyDown() && isOwner(player)) {
            if (this.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return retrieve(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        // ③ 空手（不蹲下）：布防
        if (isArmed()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            arm();
        }
        return InteractionResult.SUCCESS;
    }

    // === 存档 ===

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putBoolean("Armed", isArmed());
        tag.putFloat("Yaw", getYaw());
        if (defusingPlayer != null) {
            tag.putUUID("DefusingPlayer", defusingPlayer);
        }
        tag.putInt("DefuseTicks", defuseTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // 只改数据，不播音效（读档时不该在地图上「咔」一声）
        setArmed(tag.getBoolean("Armed"));
        // 旧存档里存的是四向 "Facing"（字符串），这里兼容一下：按方向换算成 yaw
        if (tag.contains("Yaw")) {
            setYaw(tag.getFloat("Yaw"));
        } else if (tag.contains("Facing")) {
            Direction legacy = Direction.byName(tag.getString("Facing"));
            setYaw(legacy == null ? 0.0F : legacy.toYRot());
        } else {
            setYaw(0.0F);
        }
        defusingPlayer = tag.hasUUID("DefusingPlayer") ? tag.getUUID("DefusingPlayer") : null;
        defuseTicks = tag.getInt("DefuseTicks");
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
        return true;   // 否则准星选不中、右键不到
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;  // 玩家可以直接走过去踩雷
    }

    // === 场上查询 / 清场（替代原方块实体的静态索引）===

    /** 某玩家场上是否还有一颗他布设的地雷。 */
    public static boolean hasMine(@Nullable Player player) {
        return countOwned(player) >= 2;
    }

    /** 某玩家场上还活着的地雷数量（HUD 计数 / 上限判定都用它）。 */
    public static int countOwned(@Nullable Player player) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        UUID target = player.getUUID();
        int count = 0;
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ClaymoreMineEntity mine && !mine.isRemoved() && target.equals(mine.owner)) {
                count++;
            }
        }
        return count;
    }

    /** 当前维度里所有地雷实体（快照，可安全遍历 / 修改）。 */
    public static List<ClaymoreMineEntity> allMines(ServerLevel level) {
        List<ClaymoreMineEntity> mines = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ClaymoreMineEntity mine) {
                mines.add(mine);
            }
        }
        return mines;
    }

    /**
     * 全清该维度里所有地雷（<b>不引爆、不返还</b>）：供「每局开始 / 每局结束」调用。
     *
     * @return 清掉的数量
     */
    public static int clearAll(ServerLevel level) {
        List<ClaymoreMineEntity> mines = allMines(level);
        int removed = 0;
        for (ClaymoreMineEntity mine : mines) {
            if (mine.isRemoved()) {
                continue;
            }
            mine.abortDefusing(null);
            mine.discard();
            // HUD 计数归零（布设者在线时立即刷新）
            BetterGangsterRoleData.refreshOwner(level, mine.owner);
            removed++;
        }
        return removed;
    }

    /**
     * 对局日志广播：「地雷被引爆」。
     */
    private void broadcastExploded(ServerLevel level) {
        try {
            Player ownerPlayer = owner != null ? level.getPlayerByUUID(owner) : null;
            Component name = ownerPlayer != null
                    ? io.wifi.starrailexpress.api.replay.GameReplayUtils
                            .getReplayPlayerDisplayText(ownerPlayer, true)
                    : Component.literal("?");
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.cheeserolepack.exploded", name,
                            itemLabel("item.cheeserolepack.claymore_mine")));
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
                    Component.translatable("replay.cheeserolepack.defused",
                            io.wifi.starrailexpress.api.replay.GameReplayUtils
                                    .getReplayPlayerDisplayText(defuser, true),
                            itemLabel("item.cheeserolepack.claymore_mine")));
        } catch (Throwable ignored) {
        }
    }

    /** 把道具名做成「[白色物品名]」——与 SRE 原生日志「使用了 [手榴弹]」的样式一致。 */
    private static Component itemLabel(String nameKey) {
        net.minecraft.ChatFormatting white = net.minecraft.ChatFormatting.WHITE;
        return Component.literal("[").withStyle(white)
                .append(Component.translatable(nameKey).withStyle(white))
                .append(Component.literal("]").withStyle(white));
    }

    /** 是不是这颗地雷的所有者。 */
    public boolean isOwner(@org.jetbrains.annotations.Nullable Player player) {
        return owner != null && player != null && owner.equals(player.getUUID());
    }

    /**
     * 所有者把地雷<b>收回背包</b>（等价于撤销布设，不消耗物品）。
     *
     * <p>无论未布防还是已布防都能收；背包满时直接掉在脚下，不会凭空消失。</p>
     */
    public boolean retrieve(ServerPlayer player) {
        if (!isOwner(player)) {
            return false;
        }
        Level level = this.level();
        ItemStack returned = new ItemStack(com.cheese.rolepack.init.ModItems.CLAYMORE_MINE);
        if (!player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
        this.discard();
        if (level instanceof ServerLevel serverLevel) {
            BetterGangsterRoleData.refreshOwner(serverLevel, owner);
        }
        player.displayClientMessage(
                Component.translatable("message.cheeserolepack.claymore_mine.retrieved"), true);
        try {
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.cheeserolepack.retrieved",
                            io.wifi.starrailexpress.api.replay.GameReplayUtils
                                    .getReplayPlayerDisplayText(player, true),
                            itemLabel("item.cheeserolepack.claymore_mine")));
        } catch (Throwable ignored) {
        }
        return true;
    }
}
