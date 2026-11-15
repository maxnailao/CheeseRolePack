package com.cheese.rolepack.client.render;

import com.cheese.rolepack.content.block.ClaymoreMineBlock;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import com.cheese.rolepack.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 阔剑地雷实体的渲染器。
 *
 * <p>模型完全复用方块资源（{@code models/block/claymore_mine.json} +
 * {@code claymore_mine_lasers.json}），用
 * {@link BlockRenderDispatcher#renderSingleBlock} 画出来：</p>
 * <ul>
 *   <li>{@code ARMED} 取实体的同步状态 → 已布防时 blockstate 的 multipart 叠加激光模型；</li>
 *   <li><b>{@code FACING} 固定传 {@link Direction#NORTH}</b>（= 模型 {@code y} 旋转 0），
 *       实体的任意角度朝向完全交给这里的 {@code poseStack} 旋转 ——
 *       否则 blockstate 的 {@code y} 旋转会和这里的旋转叠加两次；</li>
 *   <li>旋转角：模型的激光朝模型空间 <b>-Z（北）</b>，而 yaw 0 的正面是 +Z，所以按 vanilla
 *       {@code LivingEntityRenderer} 同一套约定转 {@code 180 - yaw} 度
 *       （推导与验证见 {@link #modelRotationDegrees(float)}）。</li>
 * </ul>
 *
 * <p>坐标：实体位置是地雷的<b>中心（脚底）</b>，而方块模型的坐标原点是方块角，
 * 因此渲染前统一 {@code translate(-0.5, 0, -0.5)}（与 vanilla {@code FallingBlockRenderer} 一致）。</p>
 */
public class ClaymoreMineEntityRenderer extends EntityRenderer<ClaymoreMineEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public ClaymoreMineEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(ClaymoreMineEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        // FACING 固定 NORTH：模型自身的 y 旋转恒为 0，真正的朝向由下面的 poseStack 旋转提供
        BlockState state = ModBlocks.CLAYMORE_MINE.defaultBlockState()
                .setValue(ClaymoreMineBlock.ARMED, entity.isArmed())
                .setValue(ClaymoreMineBlock.FACING, Direction.NORTH);

        poseStack.pushPose();
        // ⚠ 顺序不能反：PoseStack 是右乘（M = M · X），先 translate 再 rotate 会得到 T·R，
        // 顶点变成 R·v + t —— 方块中心会随朝向绕着支点公转，表现为「模型位置和实体位置对不上」。
        // 正确顺序是 rotate → translate，得到 R·T，顶点变成 R·(v − c)：
        // 先绕实体原点旋转，再整体平移半格，方块中心始终落在实体位置上。
        poseStack.mulPose(Axis.YP.rotationDegrees(modelRotationDegrees(entity.getYaw())));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        this.blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 把「模型 -Z 朝北」的模型转到实体正面所需的 {@code Axis.YP} 旋转角度（度）。
     *
     * <p><b>推导</b>：{@code Axis.YP.rotationDegrees(θ)} 是 JOML 关于 +Y 的右手旋转，
     * 作用在水平向量 (x, z) 上是 {@code x' = x·cosθ + z·sinθ, z' = -x·sinθ + z·cosθ}。
     * 模型正面是 (0, -1)（-Z），旋转后得到 {@code (-sinθ, -cosθ)}；
     * 而实体正面 {@code frontVector()} 是 {@code (-sin yaw, cos yaw)}。
     * 令两者相等：{@code sinθ = sin yaw} 且 {@code cosθ = -cos yaw} ⇒ {@code θ = 180° - yaw}。</p>
     *
     * <p><b>交叉验证</b>：这正是 vanilla {@code LivingEntityRenderer} 里
     * {@code poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot))} 的同一表达式 ——
     * MC 的实体模型也是朝 -Z 建的，所以同一套约定。</p>
     *
     * <p>数值抽查：yaw 0（朝 +Z 南）→ θ=180 → (0,-1) 旋转成 (0,+1)=+Z ✓；
     * yaw 90（朝 -X 西）→ θ=90 → (0,-1) → (-1,0)=-X ✓；
     * yaw 180（朝 -Z 北）→ θ=0 → 模型本来就朝北 ✓。</p>
     */
    public static float modelRotationDegrees(float yaw) {
        return 180.0F - yaw;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(ClaymoreMineEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
