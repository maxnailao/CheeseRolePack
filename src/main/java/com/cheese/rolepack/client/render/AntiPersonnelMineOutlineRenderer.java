package com.cheese.rolepack.client.render;

import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.init.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.OptionalDouble;

/**
 * 反人员地雷的<b>红色透视轮廓</b>渲染器
 * （移植自 RKD 的 {@code org.agmas.noellesroles.client.LandmineOutlineRenderer}）。
 *
 * <p><b>可见性</b>（与 RKD 完全一致）：只有「杀手阵营 / 杀手中立」<b>且</b>开着杀手直觉时可见；
 * 另外加了「未布防不画」（RKD 的渲染器没这一步，但未布防的哑雷不该被透视看到）。</p>
 *
 * <p><b>画什么</b>：RKD 用 {@code TaskBlockOverlayRenderer.renderBlockOverlay} 画方块轮廓；
 * 这里改成给实体画一个「和模型对齐」的红框 ——
 * 取方块模型自带的 {@code SHAPE}（3.3~12.7 / 0~2.3）平移到实体位置上，
 * 视觉上就是 RKD 那个贴地的红色扁框。</p>
 *
 * <p><b>线框实现</b>：RKD 的写法带 {@code ITEM_ENTITY_TARGET} 输出状态，在部分光影/渲染环境下
 * 画不出线（我们阔剑地雷当时就是被这个坑到）。这里沿用修好的那套：
 * {@code NO_DEPTH_TEST} + 不用 {@code ITEM_ENTITY_TARGET} + 显式 {@code endBatch}。</p>
 *
 * <p><b>注意</b>：RKD 的反人员地雷渲染器<b>没有</b>「两条激光线」——那是我们阔剑地雷
 * （模型自带激光）的特性，RKD 的地雷模型就是一整块铁饼，激光线无从照搬。</p>
 */
public final class AntiPersonnelMineOutlineRenderer {

    /** 带厚度、永远不被遮挡的线框（配置与 {@link ClaymoreMineOutlineRenderer} 保持一致）。 */
    private static final RenderType THICK_LINES = RenderType.create("cheeserolepack_anti_personnel_mine_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** RKD 的渲染距离上限：64 格。 */
    private static final double SEARCH_RADIUS = 64.0D;

    private AntiPersonnelMineOutlineRenderer() {
    }

    /** 注册到 {@code WorldRenderEvents.AFTER_TRANSLUCENT}。 */
    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
            return;
        }
        // 仅杀手阵营（含杀手中立）且开启了杀手透视时可见 —— 与 RKD 一致
        if (!SREClient.gameComponent.isKillerTeam(client.player)) {
            return;
        }

        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrixStack();
        Level level = context.world();
        if (consumers == null || matrices == null || level == null) {
            return;
        }

        Vec3 cameraPos = context.camera().getPosition();
        VertexConsumer builder = null;

        AABB search = client.player.getBoundingBox().inflate(SEARCH_RADIUS);
        for (AntiPersonnelMineEntity mine : level.getEntitiesOfClass(AntiPersonnelMineEntity.class, search)) {
            // 未布防是个哑雷，不该被透视看到
            if (!mine.isArmed()) {
                continue;
            }
            if (builder == null) {
                builder = consumers.getBuffer(THICK_LINES);
            }
            AABB box = modelBox(mine, level);
            LevelRenderer.renderLineBox(matrices, builder,
                    box.minX - cameraPos.x, box.minY - cameraPos.y, box.minZ - cameraPos.z,
                    box.maxX - cameraPos.x, box.maxY - cameraPos.y, box.maxZ - cameraPos.z,
                    1.0F, 0.15F, 0.15F, 1.0F);
        }

        // 自定义 RenderType 不在 vanilla 的 batch 列表里，必须显式 flush 一次
        if (builder != null && consumers instanceof MultiBufferSource.BufferSource bufferSource) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            bufferSource.endBatch(THICK_LINES);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /**
     * 地雷模型在世界里的包围盒。
     *
     * <p>实体渲染器把模型从方块角挪到了「实体位置 - 0.5」，所以这里同样把方块形状平移过去，
     * 红框就会和看到的模型严丝合缝。</p>
     */
    private static AABB modelBox(AntiPersonnelMineEntity mine, Level level) {
        BlockPos pos = mine.blockPosition();
        VoxelShape shape = ModBlocks.ANTI_PERSONNEL_MINE.defaultBlockState().getShape(level, pos);
        AABB local = shape.isEmpty() ? new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D) : shape.bounds();
        return local.move(mine.getX() - 0.5D, mine.getY(), mine.getZ() - 0.5D);
    }
}
