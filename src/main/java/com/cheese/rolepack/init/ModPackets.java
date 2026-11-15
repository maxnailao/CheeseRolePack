package com.cheese.rolepack.init;

import com.cheese.rolepack.content.item.RemoteDetonatePayload;
import com.cheese.rolepack.content.item.Rpg7ShootPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 本模组的网络包注册（移植自 Ricord / noellesroles 的 {@code ModPackets} 中 RPG-7 那一小段）。
 *
 * <p>目前注册两个 C2S 包：</p>
 * <ul>
 *   <li>{@link Rpg7ShootPayload}（{@code cheeserolepack:rpg7_shoot}）：RPG-7 射击 / 装填；</li>
 *   <li>{@link RemoteDetonatePayload}（{@code cheeserolepack:remote_detonate}）：
 *       「更好的悍匪」远程引爆技能，客户端把准星选中的地雷 entity id 发上来。</li>
 * </ul>
 */
public final class ModPackets {

    private ModPackets() {
    }

    /** 在主入口调用：注册 playC2S 包类型与对应的服务端接收器。 */
    public static void register() {
        // 注册 RPG-7 射击/装填网络包（id = cheeserolepack:rpg7_shoot）
        PayloadTypeRegistry.playC2S().register(Rpg7ShootPayload.ID, Rpg7ShootPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Rpg7ShootPayload.ID, Rpg7ShootPayload::handle);

        // 注册远程引爆网络包（id = cheeserolepack:remote_detonate）
        PayloadTypeRegistry.playC2S().register(RemoteDetonatePayload.ID, RemoteDetonatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RemoteDetonatePayload.ID, RemoteDetonatePayload::handle);
    }
}
