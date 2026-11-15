package com.cheese.rolepack.client.render;

import com.cheese.rolepack.content.block.ClaymoreMineBlock;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阔剑地雷的<b>红色透视线框</b>渲染器
 * （轮廓部分移植自 Ricord / noellesroles 的 {@code LandmineOutlineRenderer}）。
 *
 * <p>地雷是实体，所以每帧用
 * {@code level.getEntitiesOfClass(ClaymoreMineEntity.class, 玩家包围盒外扩 64 格)} 找雷，
 * 不维护任何静态实例表。</p>
 *
 * <p><b>可见性</b>：只有「杀手阵营 / 杀手中立」（{@code isKillerTeam}）且地雷<b>已布防</b>时才画；
 * 刻意不检查直觉（这是会踩到就炸死人的陷阱，杀手方应当永远能穿墙定位它）。</p>
 *
 * <p><b>线框形状</b>：不再是 {@code renderLineBox(shape.bounds())} 那种方盒子，而是
 * <b>按方块模型的真实几何</b>画 —— 取已烘焙模型的全部 quads（主体 + 激光），
 * 把每个 quad 的 4 条边拆出来、按 0.01 精度去重后<b>缓存</b>，然后用 {@link #THICK_LINES} 画这些棱。
 * 于是透视框就是模型本身的轮廓（含激光部分的形状）。</p>
 *
 * <p><b>朝向</b>：实体渲染器会把模型绕 Y 轴转 {@code 180 - yaw} 度，
 * 这里用同一个角度旋转线框顶点，保证红框与看到的模型严丝合缝。</p>
 */
public final class ClaymoreMineOutlineRenderer {

    /** 带厚度、永远不被遮挡的线框（NO_DEPTH_TEST 穿墙；不用 ITEM_ENTITY_TARGET，否则画不出来）。 */
    private static final RenderType THICK_LINES = RenderType.create("cheeserolepack_claymore_mine_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            // 单颗地雷就有 ~911 条棱（1822 个顶点），起始 buffer 给大一点，少几次扩容
            VertexFormat.Mode.LINES, 8192, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** 只在 64 格内画，避免超远轮廓干扰视野。 */
    private static final double RENDER_DISTANCE_SQR = 64.0D * 64.0D;

    /** 顶点坐标量化精度（×100 = 保留两位小数）：同一条棱会被多个面/元素重复产出，靠它去重。 */
    private static final float QUANTIZE = 100.0F;

    /**
     * 线框用的方块状态：<b>已布防 + 固定 NORTH</b>。
     *
     * <p>固定 NORTH 时 blockstate 给模型的 {@code y} 旋转是 0，模型几何里的「-Z」就是「正前方」，
     * 与实体渲染器「先固定 NORTH，再自己绕 Y 旋转」的做法一致，不会叠加两次。</p>
     */
    private static final BlockState OUTLINE_STATE = ModBlocks.CLAYMORE_MINE.defaultBlockState()
            .setValue(ClaymoreMineBlock.ARMED, true)
            .setValue(ClaymoreMineBlock.FACING, Direction.NORTH);

    /**
     * 去重后的模型棱缓存（按 BlockState 缓存；第一次用到时烘焙模型 + 拆边，之后每帧只读）。
     *
     * <p>模型共 102 个长方体（主体 38 + 激光 64），102×12 = 1224 条原始棱，
     * 按 0.01 量化去重后约 <b>911</b> 条。（这个数字是离线按同一算法解析两个模型 JSON 得到的，
     * 与运行时烘焙四边形的顶点集合一致。）</p>
     */
    private static final Map<BlockState, List<Line>> EDGE_CACHE = new ConcurrentHashMap<>();

    private ClaymoreMineOutlineRenderer() {
    }

    /** 一条线段（模型空间：单位格，0~1）。 */
    private record Line(float x1, float y1, float z1, float x2, float y2, float z2) {
    }

    /** 量化后的端点键（无向边：两端点排序后作为 key）。 */
    private record EdgeKey(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    /** 注册到 {@code WorldRenderEvents.AFTER_TRANSLUCENT}。 */
    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrixStack();
        Level level = context.world();
        if (consumers == null || matrices == null || level == null) {
            return;
        }
        // 红框透视的门槛：杀手阵营 / 杀手中立（不需要开直觉）
        if (!canSeeOutline(client)) {
            return;
        }

        Vec3 cameraPos = context.camera().getPosition();
        VertexConsumer builder = null;
        List<Line> edges = null;

        for (ClaymoreMineEntity mine : level.getEntitiesOfClass(ClaymoreMineEntity.class,
                client.player.getBoundingBox().inflate(64.0D))) {
            // 只画已布防的地雷
            if (!mine.isArmed()) {
                continue;
            }
            if (mine.distanceToSqr(client.player) > RENDER_DISTANCE_SQR) {
                continue;
            }
            if (edges == null) {
                edges = edgesFor(OUTLINE_STATE);
            }
            if (edges.isEmpty()) {
                continue;
            }
            if (builder == null) {
                builder = consumers.getBuffer(THICK_LINES);
            }
            renderEdges(matrices, builder, mine, edges, cameraPos);
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

    /** 画一颗地雷的整套棱：模型 0~1 空间 → 减 0.5 居中 → 按 {@code 180-yaw} 旋转 → 平移到实体位置。 */
    private static void renderEdges(PoseStack matrices, VertexConsumer builder, ClaymoreMineEntity mine,
            List<Line> edges, Vec3 cameraPos) {
        float entityX = (float) (mine.getX() - cameraPos.x);
        float entityY = (float) (mine.getY() - cameraPos.y);
        float entityZ = (float) (mine.getZ() - cameraPos.z);

        // 与 ClaymoreMineEntityRenderer 完全同一个旋转：模型 -Z（北）→ 实体正面
        double theta = Math.toRadians(ClaymoreMineEntityRenderer.modelRotationDegrees(mine.getYaw()));
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);

        PoseStack.Pose pose = matrices.last();
        for (Line line : edges) {
            // 模型空间原点在方块角，实体中心在 0.5 → 先减 0.5 居中
            float ax = line.x1() - 0.5F;
            float az = line.z1() - 0.5F;
            float bx = line.x2() - 0.5F;
            float bz = line.z2() - 0.5F;

            // JOML 关于 +Y 的右手旋转：x' = x·cos + z·sin, z' = -x·sin + z·cos
            float axr = ax * cos + az * sin;
            float azr = -ax * sin + az * cos;
            float bxr = bx * cos + bz * sin;
            float bzr = -bx * sin + bz * cos;

            float x1 = entityX + axr;
            float y1 = entityY + line.y1();
            float z1 = entityZ + azr;
            float x2 = entityX + bxr;
            float y2 = entityY + line.y2();
            float z2 = entityZ + bzr;

            float nx = x2 - x1;
            float ny = y2 - y1;
            float nz = z2 - z1;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0E-5F) {
                nx /= len;
                ny /= len;
                nz /= len;
            }

            builder.addVertex(pose, x1, y1, z1).setColor(1.0F, 0.15F, 0.15F, 1.0F).setNormal(pose, nx, ny, nz);
            builder.addVertex(pose, x2, y2, z2).setColor(1.0F, 0.15F, 0.15F, 1.0F).setNormal(pose, nx, ny, nz);
        }
    }

    /**
     * 取某个 BlockState 的「模型棱集合」（首次调用时烘焙模型并拆边，之后走缓存）。
     *
     * <p>{@code getBlockModel(state).getQuads(state, null, random)} 拿无朝向 quads，
     * 再遍历六个 {@link Direction} 拿有朝向的 quads；每个 quad 的 4 个顶点按顺序连 4 条边，
     * 端点量化到 0.01 后排序作为无向边的 key 放进 {@link Set} 去重。</p>
     */
    private static List<Line> edgesFor(BlockState state) {
        return EDGE_CACHE.computeIfAbsent(state, s -> {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(s);
            RandomSource random = RandomSource.create();
            Set<EdgeKey> seen = new HashSet<>();
            List<Line> lines = new ArrayList<>();

            collectEdges(model.getQuads(s, null, random), seen, lines);
            for (Direction direction : Direction.values()) {
                collectEdges(model.getQuads(s, direction, random), seen, lines);
            }
            return List.copyOf(lines);
        });
    }

    /** 把一批 quad 的 4 条边拆出来（去重后）加进 {@code out}。 */
    private static void collectEdges(List<BakedQuad> quads, Set<EdgeKey> seen, List<Line> out) {
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            // vanilla 的 VertexConsumer#putBulkData 就是这么读的：BLOCK 格式固定 8 个 int 一个顶点
            int stride = 8;
            int vertexCount = data.length / stride;
            if (vertexCount < 4) {
                continue;
            }
            // 每个顶点前 3 个 int 是位置 (x,y,z) 的 float 位模式
            float[] xs = new float[4];
            float[] ys = new float[4];
            float[] zs = new float[4];
            for (int i = 0; i < 4; i++) {
                int base = i * stride;
                xs[i] = Float.intBitsToFloat(data[base]);
                ys[i] = Float.intBitsToFloat(data[base + 1]);
                zs[i] = Float.intBitsToFloat(data[base + 2]);
            }
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                addEdge(seen, out, xs[i], ys[i], zs[i], xs[j], ys[j], zs[j]);
            }
        }
    }

    /** 加一条无向边（量化 + 端点排序后去重）。 */
    private static void addEdge(Set<EdgeKey> seen, List<Line> out,
            float x1, float y1, float z1, float x2, float y2, float z2) {
        int ax = Math.round(x1 * QUANTIZE);
        int ay = Math.round(y1 * QUANTIZE);
        int az = Math.round(z1 * QUANTIZE);
        int bx = Math.round(x2 * QUANTIZE);
        int by = Math.round(y2 * QUANTIZE);
        int bz = Math.round(z2 * QUANTIZE);

        // 端点排序，保证 (A,B) 与 (B,A) 是同一个 key
        boolean swap = ax > bx
                || (ax == bx && (ay > by || (ay == by && az > bz)));
        EdgeKey key = swap
                ? new EdgeKey(bx, by, bz, ax, ay, az)
                : new EdgeKey(ax, ay, az, bx, by, bz);
        if (!seen.add(key)) {
            return;
        }
        // 用原始（未量化）坐标绘制，避免量化带来的抖动
        out.add(swap
                ? new Line(x2, y2, z2, x1, y1, z1)
                : new Line(x1, y1, z1, x2, y2, z2));
    }

    /**
     * 当前观看者能否看到地雷的红色透视线框。
     *
     * <p>判据：本地玩家属于杀手阵营或杀手中立（{@code SREGameWorldComponent#isKillerTeam} 的实现是
     * {@code role.canUseKiller() || role.isNeutralForKiller()}），且对局正在进行。
     * 「杀手中立」已被该方法覆盖，不需要额外的 {@code InstinctType} 判断；
     * 也<b>刻意不检查</b>直觉是否开启。</p>
     */
    private static boolean canSeeOutline(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        SREGameWorldComponent gameComponent = SREClient.gameComponent;
        if (gameComponent == null || !gameComponent.isRunning()) {
            return false;
        }
        return gameComponent.isKillerTeam(client.player);
    }
}
