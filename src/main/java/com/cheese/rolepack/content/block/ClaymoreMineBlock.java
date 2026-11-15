package com.cheese.rolepack.content.block;

import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 阔剑地雷方块 —— <b>现在只作为模型载体存在</b>。
 *
 * <p>地雷本体已经从方块改成了实体（{@link ClaymoreMineEntity}）：
 * 方块形态要求「玩家脚下那一格可替换」，站在地毯 / 半砖 / 装饰方块上就放不下来；
 * 改成实体后布设不再受任何方块阻挡。</p>
 *
 * <p>本方块<b>永远不会被放进世界</b>（没有 BlockItem、没有方块实体），
 * 只由 {@code ClaymoreMineEntityRenderer} 用
 * {@code BlockRenderDispatcher#renderSingleBlock} 读取 blockstate / 模型 / 贴图来渲染。</p>
 *
 * <p>两个属性决定模型长什么样：</p>
 * <ul>
 *   <li>{@link #ARMED}：{@code blockstates/claymore_mine.json} 是 multipart，
 *       命中 {@code when: {armed: "true"}} 时叠加 {@code block/claymore_mine_lasers}（两条激光）；</li>
 *   <li>{@link #FACING}：blockstate 按 {@code facing} 给模型叠加
 *       {@code y} 旋转（north=0 / east=90 / south=180 / west=270），所以激光朝哪边由它决定。</li>
 * </ul>
 */
public class ClaymoreMineBlock extends Block {

    /** 地雷模型的碰撞/选中形状（与模型范围一致）。 */
    private static final VoxelShape SHAPE = Block.box(3.3D, 0.0D, 3.3D, 12.7D, 2.3D, 12.7D);

    /** 是否已布防（决定是否叠加激光模型）。 */
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    /**
     * 正面朝向（水平四向，blockstate 用）。
     *
     * <p>模型里的激光朝模型空间 <b>-Z（北）</b>，所以 {@code facing=north} 对应 {@code y=0}。</p>
     *
     * <p><b>实体渲染器固定传 NORTH</b>（模型自身不做 blockstate 旋转），
     * 真正的朝向由 {@code ClaymoreMineEntityRenderer} 用 poseStack 按实体的 <b>yaw（任意角度）</b>
     * 绕 Y 旋转 {@code 180 - yaw} 度实现 —— 这样不会和 blockstate 的 {@code y} 旋转叠加两次，
     * 引爆扇形（{@link ClaymoreMineEntity#frontVector()}）也仍然与激光同向。</p>
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 方块 codec。 */
    public static final MapCodec<ClaymoreMineBlock> CODEC = simpleCodec(ClaymoreMineBlock::new);

    public ClaymoreMineBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(ARMED, false)
                .setValue(FACING, Direction.NORTH));
    }

    /** 注册方块状态：布防标记 + 正面朝向。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED, FACING);
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * 若将来真的给这个方块加了 BlockItem，放置时正面朝向取<b>放置者面朝的方向</b>
     * （{@code UseOnContext#getHorizontalDirection()} 的实现就是 {@code player.getDirection()}）。
     */
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
