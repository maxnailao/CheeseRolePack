package com.cheese.rolepack.content.item;

import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.role.BetterGangsterRoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.List;

/**
 * 反人员地雷物品（移植自 RKD 的 {@code org.agmas.noellesroles.content.item.LandmineItem}）。
 *
 * <ul>
 *   <li>长按右键 {@value #PLACE_TICKS} tick（3 秒），在<b>脚下生成一颗地雷实体</b>；</li>
 *   <li>布设即消耗一颗（与 RKD 一致）；</li>
 *   <li>布设后不用右键 —— 布设者离开布设位置 3 秒后<b>自动布防</b>
 *       （见 {@link AntiPersonnelMineEntity}）；</li>
 *   <li><b>删除了 RKD 的「脚下那一格必须可替换」校验</b>：现在是实体，
 *       站在地毯 / 半砖 / 任何装饰方块上都能布设（这正是改成实体的目的）。</li>
 * </ul>
 *
 * <p>与 RKD 的唯一行为差异：RKD 用 {@code GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit} 判定，
 * 它把<b>创造模式也排除</b>了，导致从创造栏拿到地雷后右键毫无反应。这里和本模组的阔剑地雷保持一致，
 * 只排除旁观者（创造模式可以布设，方便测试与地图编辑）。</p>
 */
public class AntiPersonnelMineItem extends Item {

    /** 布设所需长按时长（3 秒，与 RKD 的 {@code PLACE_TICKS} 一致）。 */
    /** 布设所需长按时长：<b>2 秒</b>（40 tick）。 */
    /** 布设所需长按时长：<b>1 秒</b>（20 tick）。 */
    public static final int PLACE_TICKS = 20;

    public AntiPersonnelMineItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        // 只排除旁观者（创造模式允许，理由见类注释）
        if (player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        // 安全时间内不能布设：压入原版冷却（时长 = 安全时间剩余）
        if (isInSafeTime(player)) {
            applySafeTimeCooldown(player, stack);
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
     * 蓄力时播放「举起」的手臂动作。
     *
     * <p>默认 {@code UseAnim.NONE} 时，长按 3 秒期间玩家看不到任何反馈，很容易以为没生效而提前松手，
     * 那样 {@link #finishUsingItem} 根本不会被调用 —— 表现就是「右键没反应、地雷放不下来」。
     * 阔剑地雷当初就是这个坑，这里保持一致。</p>
     */
    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 蓄力中每 5 tick 刷一次 action bar 进度（复用阔剑地雷那条文案，格式一致）。 */
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
        if (!(level instanceof ServerLevel serverLevel) || !(user instanceof ServerPlayer player)) {
            return stack;
        }
        // 只排除旁观者 / 已死亡；创造模式允许（与 RKD 的差异，理由见类注释）
        if (player.isSpectator() || !player.isAlive()) {
            return stack;
        }
        // 蓄力过程中可能进入安全时间：这时候也不能真正布设
        if (isInSafeTime(player)) {
            applySafeTimeCooldown(player, stack);
            return stack;
        }

        // 每人最多 2 颗：场上已满就拒绝（不消耗物品）
        if (AntiPersonnelMineEntity.countOwned(player) >= AntiPersonnelMineEntity.MAX_PER_PLAYER) {
            player.displayClientMessage(Component.translatable(
                    "message.cheeserolepack.anti_personnel_mine.max",
                    AntiPersonnelMineEntity.MAX_PER_PLAYER), true);
            return stack;
        }

        // 在玩家脚下生成地雷实体（刚放下时未布防：布设者离开后 3 秒才自动布防）
        AntiPersonnelMineEntity mine = new AntiPersonnelMineEntity(serverLevel,
                player.getX(), player.getY(), player.getZ());
        mine.setOwner(player.getUUID());
        serverLevel.addFreshEntity(mine);

        // 对局日志广播：和纵火犯浇油一样，把「布设地雷」这个动作记进回放/日志
        try {
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.cheeserolepack.planted",
                            io.wifi.starrailexpress.api.replay.GameReplayUtils
                                    .getReplayPlayerDisplayText(player, true),
                            itemLabel("item.cheeserolepack.anti_personnel_mine")));
        } catch (Throwable ignored) {
            // 回放系统异常绝不影响玩法
        }
        // 刷新右下角 HUD 的「反人员地雷数量」
        BetterGangsterRoleData.refresh(player);

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
        stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cheeserolepack.anti_personnel_mine.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    /** 把道具名做成「[白色物品名]」——与 SRE 原生日志「使用了 [手榴弹]」的样式一致。 */
    private static Component itemLabel(String nameKey) {
        net.minecraft.ChatFormatting white = net.minecraft.ChatFormatting.WHITE;
        return Component.literal("[").withStyle(white)
                .append(Component.translatable(nameKey).withStyle(white))
                .append(Component.literal("]").withStyle(white));
    }

    /** 是否处于「安全时间」（SRE 的 SAFE_TIME 效果，商店在安全时间内禁止购买/使用）。 */
    private static boolean isInSafeTime(Player player) {
        return player != null && player.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME);
    }

    /**
     * 安全时间内把地雷压进<b>原版物品冷却</b>，冷却时长 = 安全时间剩余时长。
     *
     * <p>这样物品栏上会显示原版的冷却遮罩（和用过的物品一样），
     * 玩家一眼就能看出「现在不能用」，而不是右键后没反应。</p>
     */
    private static void applySafeTimeCooldown(Player player, ItemStack stack) {
        int ticks = 100; // 兜底：拿不到效果实例时压 5 秒
        try {
            var instance = player.getEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME);
            if (instance != null) {
                ticks = Math.max(20, instance.getDuration());
            }
        } catch (Throwable ignored) {
        }
        player.getCooldowns().addCooldown(stack.getItem(), ticks);
        player.displayClientMessage(
                Component.translatable("message.cheeserolepack.mine.safe_time"), true);
    }
}
