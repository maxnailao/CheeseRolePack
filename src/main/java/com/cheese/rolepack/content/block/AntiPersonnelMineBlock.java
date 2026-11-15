package com.cheese.rolepack.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 反人员地雷方块 —— <b>只作为模型载体存在</b>
 * （形状沿用 RKD 的 {@code LandmineBlock}：3.3~12.7 / 0~2.3，即 0.4×0.23 的扁平方块）。
 *
 * <p>和阔剑地雷一样，地雷本体是实体 {@link com.cheese.rolepack.content.entity.AntiPersonnelMineEntity}：
 * 这个方块<b>永远不会被放进世界</b>（没有 BlockItem、没有方块实体、没有任何属性），
 * 只由 {@code AntiPersonnelMineEntityRenderer} 用
 * {@code BlockRenderDispatcher#renderSingleBlock} 读它的 blockstate / 模型 / 贴图来渲染。</p>
 *
 * <p>如果将来真的给这个方块加了 BlockItem，它会是「无碰撞、瞬破、金属音」的方块。</p>
 */
public class AntiPersonnelMineBlock extends Block {

    /** 与 RKD {@code LandmineBlock.SHAPE} 完全一致。 */
    private static final VoxelShape SHAPE = Block.box(3.3D, 0.0D, 3.3D, 12.7D, 2.3D, 12.7D);

    /** 方块 codec（RKD 原实现直接 return null，这里按 1.21.1 规范补全）。 */
    public static final MapCodec<AntiPersonnelMineBlock> CODEC = simpleCodec(AntiPersonnelMineBlock::new);

    public AntiPersonnelMineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty();
    }
}
