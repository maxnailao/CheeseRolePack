package com.cheese.rolepack.client.render;

import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 反人员地雷实体的渲染器 —— 直接用方块模型（RKD 的 {@code landmine.json}）画出来，
 * 写法和 {@link ClaymoreMineEntityRenderer} 一致。
 *
 * <p>与阔剑地雷不同，反人员地雷的模型<b>没有</b>「布防后叠加激光」这一说
 * （RKD 是渲染器自己画轮廓，模型始终是同一块铁饼），所以这里永远用
 * {@code ModBlocks.ANTI_PERSONNEL_MINE.defaultBlockState()} 渲染。</p>
 */
public class AntiPersonnelMineEntityRenderer extends EntityRenderer<AntiPersonnelMineEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public AntiPersonnelMineEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(AntiPersonnelMineEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        BlockState state = ModBlocks.ANTI_PERSONNEL_MINE.defaultBlockState();

        poseStack.pushPose();
        // 实体在方块中心，模型原点在方块角 → 往回挪半格（与 vanilla FallingBlockRenderer 一致）
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        this.blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(AntiPersonnelMineEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
