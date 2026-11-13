package com.cheese.rolepack.init;

import com.cheese.rolepack.content.item.Rpg7ShootPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 本模组的网络包注册（移植自 Ricord / noellesroles 的 {@code ModPackets} 中 RPG-7 那一小段）。
 *
 * <p>只注册 C2S 方向的 {@link Rpg7ShootPayload}：客户端右键/左键时发包，
 * 服务端收到后在 {@code Rpg7ShootPayload.handle} 里执行真正的射击 / 装填逻辑。</p>
 */
public final class ModPackets {

    private ModPackets() {
    }

    /** 在主入口调用：注册 playC2S 包类型与对应的服务端接收器。 */
    public static void register() {
        // 注册 RPG-7 射击/装填网络包（id = cheeserolepack:rpg7_shoot）
        PayloadTypeRegistry.playC2S().register(Rpg7ShootPayload.ID, Rpg7ShootPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Rpg7ShootPayload.ID, Rpg7ShootPayload::handle);
    }
}
