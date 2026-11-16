package com.cheese.rolepack.util;

import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 服务端版本的「准星选雷」。
 *
 * <p>统一技能系统（SRE 4.3.0）的 {@code findTarget} 谓词写死是玩家，
 * 没法把地雷当目标，所以技能目标由服务端自己算：玩家眼睛 -> 视线方向 ->
 * 与地雷包围盒求交。参数与客户端 HUD 用的完全一致
 * （{@code MineTargeting}：64 格、包围盒外扩 0.35），因此
 * 「HUD 提示出现」与「按键真的引爆」不会出现不一致。</p>
 */
public final class RemoteDetonateTargeting {

    /** 射线最远距离（格）。 */
    public static final double MAX_RANGE = 200.0D;

    /** 包围盒放大（格）。 */
    private static final double AIM_INFLATE = 0.35D;

    private RemoteDetonateTargeting() {
    }

    /** 服务端：返回玩家视线命中的最近一颗地雷。 */
    @Nullable
    public static Entity pickForServer(@Nullable Player player) {
        if (player == null || player.level() == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(MAX_RANGE));
        AABB search = player.getBoundingBox().expandTowards(look.scale(MAX_RANGE)).inflate(2.0D);

        Entity best = null;
        double bestSqr = MAX_RANGE * MAX_RANGE;
        for (Entity entity : player.level().getEntities(player, search,
                e -> e instanceof ClaymoreMineEntity || e instanceof AntiPersonnelMineEntity)) {
            AABB box = entity.getBoundingBox().inflate(AIM_INFLATE);
            Vec3 hitPos;
            Optional<Vec3> clip = box.clip(eye, end);
            if (clip.isPresent()) {
                hitPos = clip.get();
            } else if (box.contains(eye)) {
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
