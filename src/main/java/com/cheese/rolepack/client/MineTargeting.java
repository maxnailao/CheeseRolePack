package com.cheese.rolepack.client;

import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 准星选雷：用**真正的实体射线**挑出玩家正对着的地雷。
 *
 * <p><b>为什么不能直接用 {@code Entity#pick(...)}</b>：它的实现是
 * {@code level().clip(new ClipContext(...))}，只做<b>方块</b>射线检测，
 * 返回的必然是 {@code BlockHitResult}，永远拿不到 {@code EntityHitResult}。</p>
 *
 * <p><b>为什么不依赖 {@code client.hitResult}</b>：SRE 4.3.0 的统一技能系统用
 * {@code ClientAbilityHandler#findTarget} 取目标，而它的谓词默认写死
 * {@code entity instanceof Player}（4.3.0 的 {@code RoleSkill.Builder} 没有
 * {@code targetType(...)}，只有 {@code withTarget(boolean)}），所以瞄地雷永远拿不到目标。</p>
 *
 * <p><b>因此</b>：客户端与服务端都用本类的同一套射线算法自行选雷 ——
 * 两端参数完全一致（{@link #MAX_RANGE}、{@link #AIM_INFLATE}），
 * 所以「HUD 提示出现」与「技能真的引爆」不会不一致。</p>
 */
public final class MineTargeting {

    /** 射线最远距离（格）。 */
    public static final double MAX_RANGE = 64.0D;

    /** 求交时把地雷包围盒放大的量（格）：地雷只有 0.8×0.3，不放宽极难对准。 */
    private static final double AIM_INFLATE = 0.35D;

    private MineTargeting() {
    }

    /** 该实体是不是我们的地雷。 */
    public static boolean isMine(@Nullable Entity entity) {
        return entity instanceof ClaymoreMineEntity || entity instanceof AntiPersonnelMineEntity;
    }

    /** 客户端便捷重载（HUD 用）。 */
    @Nullable
    public static Entity pickMine(@Nullable LocalPlayer player) {
        return player == null ? null : pickMine(player.level(), player, MAX_RANGE);
    }

    /**
     * 从 {@code source} 的眼睛沿视线方向做实体射线，返回最近命中的地雷。
     *
     * <p><b>客户端与服务端都用它</b>：服务端也能算（实体位置、眼睛位置、视线朝向都是同步好的），
     * 所以技能完全不依赖客户端上报的目标。</p>
     *
     * @param level  所在世界
     * @param source 发射射线的实体（玩家）
     * @param range  最远距离（格）
     * @return 命中的地雷实体；没对准时返回 {@code null}
     */
    @Nullable
    public static Entity pickMine(@Nullable Level level, @Nullable Entity source, double range) {
        if (level == null || source == null) {
            return null;
        }
        Vec3 eye = source.getEyePosition(1.0F);
        Vec3 look = source.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));

        // 搜索范围：包围盒朝视线方向拉伸，再放宽一点
        AABB search = source.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0D);

        Entity best = null;
        double bestSqr = range * range;
        for (Entity entity : level.getEntities(source, search, MineTargeting::isMine)) {
            AABB box = entity.getBoundingBox().inflate(AIM_INFLATE);
            Vec3 hitPos;
            Optional<Vec3> clip = box.clip(eye, end);
            if (clip.isPresent()) {
                hitPos = clip.get();
            } else if (box.contains(eye)) {
                // 贴脸：眼睛已经在包围盒内，直接算命中
                hitPos = eye;
            } else {
                continue;
            }
            double distSqr = eye.distanceToSqr(hitPos);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = entity;
            }
        }
        return best;
    }
}
