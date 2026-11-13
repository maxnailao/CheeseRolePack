package com.cheese.rolepack.client;

import com.cheese.rolepack.client.hud.GreenHud;

/** 客户端注册钩子：注册格林的右下角技能 HUD。 */
final class CheeseRolePackClientHooks {

    private CheeseRolePackClientHooks() {
    }

    static void register() {
        // 格林技能 HUD：全场返回房间(剩余次数:N)
        GreenHud.register();
    }
}
