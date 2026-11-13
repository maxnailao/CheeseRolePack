package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.entity.Rpg7RocketEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 本模组的实体类型注册（移植自 Ricord / noellesroles 的 {@code ModEntities}）。
 *
 * <p>RPG-7 火箭弹抛射物：{@code cheeserolepack:rpg7_rocket}，
 * 碰撞箱 0.25×0.25、跟踪距离 128 格、每 2 tick 同步一次（与原始 mod 完全一致）。</p>
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

    private ModEntities() {
    }

    /** 空方法：仅用于确保本类被类加载，从而执行上面的静态注册。 */
    public static void register() {
        // 触发类加载
    }
}
