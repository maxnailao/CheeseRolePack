package com.cheese.rolepack.client;

import com.cheese.rolepack.client.hud.BetterGangsterHud;
import com.cheese.rolepack.client.hud.GreenHud;
import com.cheese.rolepack.client.render.AntiPersonnelMineEntityRenderer;
import com.cheese.rolepack.client.render.AntiPersonnelMineOutlineRenderer;
import com.cheese.rolepack.client.render.ClaymoreMineEntityRenderer;
import com.cheese.rolepack.client.render.ClaymoreMineOutlineRenderer;
import com.cheese.rolepack.content.entity.Rpg7RocketRenderer;
import com.cheese.rolepack.init.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/** 客户端注册钩子：两个职业 HUD、RPG-7 / 两颗地雷的实体渲染器、透视外框与悍匪主动技能键。 */
final class CheeseRolePackClientHooks {

    private CheeseRolePackClientHooks() {
    }

    static void register() {
        // 格林技能 HUD：全场返回房间(剩余次数:N)
        GreenHud.register();

        // 更好的悍匪 HUD：场上阔剑地雷 / 反人员地雷数量
        BetterGangsterHud.register();

        // RPG-7 火箭弹抛射物渲染器（复用火箭弹物品模型，弹头朝飞行方向）
        EntityRendererRegistry.register(ModEntities.RPG7_ROCKET, Rpg7RocketRenderer::new);

        // 阔剑地雷：实体渲染器用方块模型画出来（armed=true 时模型自带两条激光）
        EntityRendererRegistry.register(ModEntities.CLAYMORE_MINE, ClaymoreMineEntityRenderer::new);

        // 反人员地雷（移植自 RKD）：同样用方块模型渲染，但模型没有「布防后叠加」这一说
        EntityRendererRegistry.register(ModEntities.ANTI_PERSONNEL_MINE, AntiPersonnelMineEntityRenderer::new);

        // 透视外框（世界渲染阶段，穿墙）：
        // - 阔剑地雷：杀手阵营 / 杀手中立即可见（不需直觉）
        // - 反人员地雷：照 RKD，需要「杀手阵营 + 直觉开启」
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ClaymoreMineOutlineRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(AntiPersonnelMineOutlineRenderer::render);

        // 更好的悍匪主动技能「远程引爆」的客户端半边（准星选地雷 + C2S 包）
        BetterGangsterClientSkill.register();
    }
}
