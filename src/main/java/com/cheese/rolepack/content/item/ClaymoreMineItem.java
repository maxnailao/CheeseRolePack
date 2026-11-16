package com.cheese.rolepack.content.item;

import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import com.cheese.rolepack.role.BetterGangsterRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 阔剑地雷物品（移植自 Ricord / noellesroles 的 {@code LandmineItem}）。
 *
 * <ul>
 *   <li>长按右键 3 秒，在<b>脚下生成一颗地雷实体</b>（不占用、也不要求任何方块）；</li>
 *   <li>布设成功即<b>消耗</b>物品，并进入 2 秒（40 tick）冷却（{@link #CONSUME_ON_PLACE}）；</li>
 *   <li>同一名玩家场上最多只有一颗地雷（{@link ClaymoreMineEntity#hasMine(Player)}）；</li>
 *   <li>布设时把<b>玩家面朝的水平方向</b>写进实体的 {@code facing}：
 *       模型激光与引爆扇形都只读这一个朝向，所以「激光朝哪边，就炸哪边」；</li>
 *   <li><b>布设位置几乎无限制</b>：只排除旁观者。地雷是实体，
 *       所以站在地毯、半砖、任何装饰方块上都能放（方块形态时期的最大痛点）。</li>
 * </ul>
 */
public class ClaymoreMineItem extends Item {

    /** 布设所需长按时长（3 秒，与原始反人员地雷一致）。 */
    /** 布设所需长按时长：<b>0.5 秒</b>（10 tick）。 */
    public static final int PLACE_TICKS = 10;

    /** 布设成功后的冷却（2 秒 = 40 tick）。 */
    public static final int PLACE_COOLDOWN_TICKS = 40;

    /**
     * 商店售价（金币）。
     *
     * <p>「更好的悍匪」商店按这个价上架（{@code BetterGangsterRole} 直接引用本常量），
     * 地雷被拆除时也按同一个数值返还给布设者（{@link ClaymoreMineEntity#defuse}）。</p>
     */
    public static final int PRICE = 50;

    /**
     * 布设后是否消耗物品。
     *
     * <p>地雷是花 {@value #PRICE} 金买的<b>消耗品</b>，布设即消耗；
     * 被拆除时按同一个价格返还金币（所以买了又拆不亏）。</p>
     */
    public static final boolean CONSUME_ON_PLACE = true;

    public ClaymoreMineItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        // 只排除旁观者：**允许创造模式**（否则从创造栏拿到物品后右键毫无反应）
        if (player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity user) {
        return PLACE_TICKS;
    }

    /**
     * 蓄力时播放「举起」的手臂动作（默认 {@code UseAnim.NONE} 时玩家看不到任何反馈，
     * 很容易以为没生效而提前松手）。
     */
    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 蓄力中每 5 tick 刷一次 action bar 进度，告诉玩家「正在布设，别松手」。 */
    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity livingEntity, @NotNull ItemStack stack,
            int remainingUseDuration) {
        if (level.isClientSide || !(livingEntity instanceof Player player)) {
            return;
        }
        int used = PLACE_TICKS - remainingUseDuration;
        if (used % 5 != 0) {
            return;
        }
        int percent = Math.min(100, used * 100 / PLACE_TICKS);
        player.displayClientMessage(
                Component.translatable("message.cheeserolepack.claymore_mine.planting", percent), true);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
            @NotNull LivingEntity user) {
        if (!(user instanceof Player player)) {
            return stack;
        }
        // 客户端什么都不做：冷却只在服务端「真正布设成功」后加
        if (!(level instanceof ServerLevel serverLevel)) {
            return stack;
        }
        if (player.isSpectator()) {
            return stack;
        }

        // 每人最多一颗：场上已有一颗就拒绝（不自动替换旧的）
        if (ClaymoreMineEntity.hasMine(player)) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.claymore_mine.already_one"), true);
            return stack;
        }

        // 在玩家脚下生成地雷实体（刚放下时未布防：不会引爆，也不显示激光）
        // 朝向取玩家 yaw 的**精确值**（不再量化成四向）：模型按同一个 yaw 旋转，激光与杀伤同步
        ClaymoreMineEntity mine = new ClaymoreMineEntity(serverLevel,
                player.getX(), player.getY(), player.getZ(), player.getYRot());
        mine.setOwner(player.getUUID());
        serverLevel.addFreshEntity(mine);

        // 对局日志广播：和纵火犯浇油一样，把「布设地雷」这个动作记进回放/日志
        try {
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.cheeserolepack.planted",
                            io.wifi.starrailexpress.api.replay.GameReplayUtils
                                    .getReplayPlayerDisplayText(player, true),
                            itemLabel("item.cheeserolepack.claymore_mine")));
        } catch (Throwable ignored) {
            // 回放系统异常绝不影响玩法
        }
        // 刷新右下角 HUD 的「阔剑地雷数量」
        BetterGangsterRoleData.refresh(player);

        // 布设成功：2 秒（40 tick）冷却
        player.getCooldowns().addCooldown(stack.getItem(), PLACE_COOLDOWN_TICKS);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
        if (CONSUME_ON_PLACE) {
            stack.shrink(1);
        }
        return stack;
    }

    /**
     * 统计玩家背包里有多少个阔剑地雷（主背包 + 副手）。
     *
     * <p>用途：商品条目据此判断「已经有一个了，别再买」，避免误买多个。</p>
     */
    public static int countInInventory(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(com.cheese.rolepack.init.ModItems.CLAYMORE_MINE)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cheeserolepack.claymore_mine.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    /** 把道具名做成「[白色物品名]」——与 SRE 原生日志「使用了 [手榴弹]」的样式一致。 */
    private static Component itemLabel(String nameKey) {
        net.minecraft.ChatFormatting white = net.minecraft.ChatFormatting.WHITE;
        return Component.literal("[").withStyle(white)
                .append(Component.translatable(nameKey).withStyle(white))
                .append(Component.literal("]").withStyle(white));
    }
}
