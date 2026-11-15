package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import com.cheese.rolepack.content.entity.Rpg7RocketEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 本模组的实体类型注册。
 *
 * <ul>
 *   <li>RPG-7 火箭弹抛射物：{@code cheeserolepack:rpg7_rocket}，
 *       碰撞箱 0.25×0.25、跟踪距离 128 格、每 2 tick 同步一次（与原始 mod 完全一致）；</li>
 *   <li>阔剑地雷：{@code cheeserolepack:claymore_mine}，碰撞箱 0.8×0.3、跟踪距离 64 格、
 *       每 2 tick 同步一次（{@code armed}/{@code facing} 靠实体同步数据下发，updateRate 要小）；</li>
 *   <li>反人员地雷：{@code cheeserolepack:anti_personnel_mine}，碰撞箱 0.8×0.3、跟踪距离 64 格、
 *       每 2 tick 同步一次（只需要同步 {@code armed}）。</li>
 * </ul>
 */
public final class ModEntities {

    /**
     * RPG-7 火箭弹抛射物 - 高速飞行，命中方块/实体引爆
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<Rpg7RocketEntity> RPG7_ROCKET = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CheeseRolePack.id("rpg7_rocket"),
            FabricEntityTypeBuilder.<Rpg7RocketEntity>create(MobCategory.MISC, Rpg7RocketEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 阔剑地雷实体 - 由地雷物品在玩家脚下生成，不受方块阻挡
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<ClaymoreMineEntity> CLAYMORE_MINE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CheeseRolePack.id("claymore_mine"),
            FabricEntityTypeBuilder.<ClaymoreMineEntity>create(MobCategory.MISC, ClaymoreMineEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.3F))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 反人员地雷实体（移植自 RKD） - 布设者离开后自动布防，玩家踩上再离开即引爆
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<AntiPersonnelMineEntity> ANTI_PERSONNEL_MINE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CheeseRolePack.id("anti_personnel_mine"),
            FabricEntityTypeBuilder.<AntiPersonnelMineEntity>create(MobCategory.MISC, AntiPersonnelMineEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.3F))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    private ModEntities() {
    }

    /** 空方法：仅用于确保本类被类加载，从而执行上面的静态注册。 */
    public static void register() {
        // 触发类加载
    }
}
