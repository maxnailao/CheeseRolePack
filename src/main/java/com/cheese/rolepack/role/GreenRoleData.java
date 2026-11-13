package com.cheese.rolepack.role;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 格林（Green）的职业数据。
 *
 * <p>只保存一个状态：「全场返回房间」技能在本局还剩几次。
 * 本局每局限一次，所以字段默认 {@link #USES_PER_ROUND}；</p>
 *
 * <p>换局重置：玩家每局被重新分配职业时，SRE 会新建一份 RoleData（见
 * {@code org.agmas.noellesroles.init.RoleInitialItems} 的发放链路与
 * {@code SRERoleDataPlayerComponent}），因此实例本身就是新的；这里再用
 * {@link #init()} 兜底重置一次，保证「每局限一次」。</p>
 *
 * <p>同步：只同步给数据拥有者本人（{@link #shouldSyncWith(ServerPlayer)}），
 * 供客户端右下角 HUD 显示剩余次数。</p>
 */
public class GreenRoleData extends SimpleRoleData {

    /** 每局可用次数（全局每局限一次）。 */
    public static final int USES_PER_ROUND = 1;

    /** NBT 同步键名。 */
    private static final String TAG_RETURNS_LEFT = "returnsLeft";

    /** 本局剩余次数。 */
    private int returnsLeft = USES_PER_ROUND;

    public GreenRoleData(RoleDataContext context) {
        super(context);
    }

    /** 剩余次数（0 表示本局已用完）。 */
    public int getReturnsLeft() {
        return returnsLeft;
    }

    /** 是否还有次数。 */
    public boolean hasUseLeft() {
        return returnsLeft > 0;
    }

    /**
     * 消耗一次次数并同步到客户端。
     *
     * @return 是否消耗成功（次数已用完时返回 {@code false}）
     */
    public boolean consumeUse() {
        if (returnsLeft <= 0) {
            return false;
        }
        returnsLeft--;
        sync();
        return true;
    }

    /** 职业赋予时重置（每局一次）。 */
    @Override
    public void init() {
        this.returnsLeft = USES_PER_ROUND;
    }

    /** 只同步给本人，避免无谓的网络开销。 */
    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt(TAG_RETURNS_LEFT, this.returnsLeft);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.returnsLeft = Math.max(0, tag.getInt(TAG_RETURNS_LEFT));
    }
}
