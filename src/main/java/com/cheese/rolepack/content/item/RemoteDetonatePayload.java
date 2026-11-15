package com.cheese.rolepack.content.item;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.role.BetterGangsterRole;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 「远程引爆地雷」的 C2S 网络包（{@code cheeserolepack:remote_detonate}）。
 *
 * <p>客户端用准星射线选中一颗地雷后，把它的 <b>entity id</b> 发上来；
 * 服务端在 {@link #handle} 里重新取出实体、做完整校验（是不是地雷、附近有没有钳子、冷却），
 * 通过后点燃前摇。客户端发来的 id 只当「候选」，一切以服务端校验为准。</p>
 */
public record RemoteDetonatePayload(int entityId) implements CustomPacketPayload {

    public static final Type<RemoteDetonatePayload> ID = new Type<>(CheeseRolePack.id("remote_detonate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteDetonatePayload> CODEC = StreamCodec.ofMember(
            (RemoteDetonatePayload payload, RegistryFriendlyByteBuf buf) -> buf.writeVarInt(payload.entityId()),
            (RegistryFriendlyByteBuf buf) -> new RemoteDetonatePayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RemoteDetonatePayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (player == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                return;
            }
            Entity target = level.getEntity(payload.entityId());
            if (target == null) {
                return;
            }
            BetterGangsterRole.tryRemoteDetonate(player, target);
        });
    }
}
