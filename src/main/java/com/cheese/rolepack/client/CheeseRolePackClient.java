package com.cheese.rolepack.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * CheeseRolePack 客户端入口。
 *
 * <p>目前仅作为占位；后续格林的技能 HUD（右下角「全场返回房间(剩余次数:1)」）会注册在这里。</p>
 */
public class CheeseRolePackClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CheeseRolePackClientHooks.register();
        // 更好的悍匪「疯狂模式」专属音乐：每 tick 检查进入/退出并切换播放
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(client -> GangsterPsychoMusic.tick());
    }
}
