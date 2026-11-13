package com.cheese.rolepack.client;

import com.cheese.rolepack.client.hud.GreenHud;
import com.cheese.rolepack.content.entity.Rpg7RocketRenderer;
import com.cheese.rolepack.init.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** 客户端注册钩子：注册格林的右下角技能 HUD 与 RPG-7 火箭弹渲染器。 */
final class CheeseRolePackClientHooks {

    private CheeseRolePackClientHooks() {
    }

    static void register() {
        // 格林技能 HUD：全场返回房间(剩余次数:N)
        GreenHud.register();

        // RPG-7 火箭弹抛射物渲染器（复用火箭弹物品模型，弹头朝飞行方向）
        EntityRendererRegistry.register(ModEntities.RPG7_ROCKET, Rpg7RocketRenderer::new);
    }
}
