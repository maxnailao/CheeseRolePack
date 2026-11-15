package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.block.AntiPersonnelMineBlock;
import com.cheese.rolepack.content.block.ClaymoreMineBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 本模组的方块注册。
 *
 * <p>目前有两个方块，<b>都只是「模型载体」</b>，永远不会被放进世界（都没有 BlockItem、没有方块实体），
 * 只由各自的实体渲染器用 {@code BlockRenderDispatcher#renderSingleBlock} 读取来渲染实体：</p>
 * <ul>
 *   <li>{@code cheeserolepack:claymore_mine}（阔剑地雷）：<b>有</b> {@code armed} / {@code facing}
 *       两个属性，决定是否叠加激光模型与模型旋转；</li>
 *   <li>{@code cheeserolepack:anti_personnel_mine}（反人员地雷，移植自 RKD）：<b>无属性</b>，
 *       模型永远长一个样，透视轮廓由渲染器自己画。</li>
 * </ul>
 */
public final class ModBlocks {

    /**
     * 阔剑地雷方块（纯模型载体）
     * - 无碰撞体积、无需工具即可瞬间破坏
     * - 两个属性驱动模型：{@code armed} 决定是否叠加激光模型，{@code facing} 决定模型旋转
     */
    public static final Block CLAYMORE_MINE = Registry.register(
            BuiltInRegistries.BLOCK,
            CheeseRolePack.id("claymore_mine"),
            new ClaymoreMineBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.METAL)));

    /**
     * 反人员地雷方块（纯模型载体，移植自 RKD 的 {@code noellesroles:landmine}）
     * - 无属性、无方块实体、无 BlockItem
     * - 模型形状沿用 RKD：3.3~12.7 / 0~2.3（0.4×0.23 的扁铁饼）
     */
    public static final Block ANTI_PERSONNEL_MINE = Registry.register(
            BuiltInRegistries.BLOCK,
            CheeseRolePack.id("anti_personnel_mine"),
            new AntiPersonnelMineBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.METAL)));

    private ModBlocks() {
    }

    /** 空方法：仅用于确保本类被类加载，从而执行上面的静态注册。 */
    public static void register() {
        // 触发类加载
    }
}
