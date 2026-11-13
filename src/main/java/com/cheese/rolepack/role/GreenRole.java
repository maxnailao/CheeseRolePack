package com.cheese.rolepack.role;

import com.cheese.rolepack.CheeseRolePack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 格林（Green）—— 平民阵营职业。
 *
 * <p>台词 / 描述：<i>爱丽丝....在哪里..</i></p>
 *
 * <p>机制一览：</p>
 * <ul>
 * <li>开局自带一本「书与笔」（{@link Items#WRITABLE_BOOK}），死亡时从背包取出并掉落在原地
 * （SRE 默认保留死亡玩家的背包，所以必须在 {@link #onDeath} 里显式掉落）；</li>
 * <li>技能「全场返回房间」：自身 SAN &gt; 70 时可用，把<b>所有</b>玩家传送回各自房间，
 * 并让所有「SAN 可被减少」的职业（{@link SRERole.MoodType#REAL}）减少 50 点 SAN。
 * 全局每局限一次（次数记在 {@link GreenRoleData} 里并同步给客户端 HUD）；</li>
 * <li>自身 SAN &lt; 30 时，空手左键会把目标击退，但<b>绝不造成伤害</b>（见
 * {@link #onAttackEntity}）；</li>
 * <li>商店一条：「癫狂」150 金币，购买后立刻把自身 SAN 归零，图标复用拳套
 * （{@code noellesroles:boxing_glove}）的贴图。</li>
 * </ul>
 *
 * <p>本类只负责「注册 + 业务逻辑」，客户端 HUD 见
 * {@code com.cheese.rolepack.client.hud.GreenHud}。</p>
 */
public class GreenRole extends NormalRole {

    /** 职业 id 路径（完整 id = {@code cheeserolepack:green}）。 */
    public static final String ROLE_PATH = "green";

    /** 职业 id：{@code cheeserolepack:green}（不会与 SRE 已有职业路径冲突）。 */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(CheeseRolePack.MOD_ID, ROLE_PATH);

    /** 技能 id：{@code cheeserolepack:green_return_rooms}。 */
    public static final ResourceLocation SKILL_ID =
            ResourceLocation.fromNamespaceAndPath(CheeseRolePack.MOD_ID, "green_return_rooms");

    /** 技能名翻译键。 */
    public static final String SKILL_NAME_KEY = "skill.cheeserolepack.green.return_rooms";

    /** 职业颜色（ARGB，偏青绿）。 */
    private static final int COLOR = 0xFF4CC38A;

    /**
     * 技能所需的 SAN 下限：SAN &gt; 70 才能使用。
     *
     * <p>SRE 的 mood（SAN）是 0~1 的浮点数（{@code SREPlayerMoodComponent#getMood()}），
     * 所以 70 点 SAN 对应 0.70。</p>
     */
    public static final float SKILL_MIN_MOOD = 0.70F;

    /** 低于该 SAN（SAN &lt; 30）时空手左键才会造成击退。 */
    public static final float PUNCH_MAX_MOOD = 0.30F;

    /** 技能消耗的 SAN（50 点 = 0.50）。 */
    public static final float SKILL_MOOD_COST = 0.50F;

    /**
     * 击退力度：直接采用原版近战击退的 0.4F
     * （{@code LivingEntity#hurt} 对普通近战伤害调用的就是 {@code knockback(0.4F, ...)}），
     * 所以手感与原版空手打人一致。注意不要调大，否则就不是「原版击退」了。
     */
    private static final double VANILLA_PUNCH_KNOCKBACK = 0.4D;

    /** 每拳固定伤害，取值与红美铃一致（1.0），实际结算时会夹到「最多打到 1 血」。 */
    private static final float PUNCH_DAMAGE = 1.0F;


    /** 两次空手左键之间的冷却（tick），沿用红美铃 / 骷髅用屏障物品当通用冷却的写法。 */
    private static final int PUNCH_COOLDOWN_TICKS = 10;

    /** 商店「癫狂」的价格（金币）。 */
    public static final int MANIA_PRICE = 150;

    /** 避免重复注册（职业 / 技能 / 商店 / 事件都只能注册一次）。 */
    private static boolean registered = false;

    /** 注册后的职业实例。 */
    private static SRERole roleInstance = null;

    private GreenRole() {
        // 平民阵营：isInnocent = true、canUseKiller = false
        // 心情类型必须是 REAL，格林才有真正的 SAN（技能门槛与击退门槛都依赖它）
        // 冲刺时长直接用 GameConstants.getInTicks(0, 10)（= TMMRoles 里那个常量自身的算法），
        // 不直接读 TMMRoles.CIVILIAN_MAX_SPRINT_TICKS：该字段不是编译期常量，会生成 getstatic
        // 引用，一旦运行期 SRE 构建版本里没有这个字段就会 NoSuchFieldError。
        super(ID, COLOR, true, false, SRERole.MoodType.REAL,
                io.wifi.starrailexpress.game.GameConstants.getInTicks(0, 10), true);
    }

    /**
     * 注册职业、技能、商店与事件（在 {@code CheeseRolePack#onInitialize()} 里调用一次）。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // ① 注册职业 + 绑定职业数据
        roleInstance = TMMRoles.registerRole(new GreenRole().setRoleData(GreenRoleData::new))
                .setDefaultMax(1)              // 单局最多 1 人 → 「全局每局限一次」等价于「每人每局限一次」
                .setDefaultEnableChance(200)   // 2% 刷新权重（与 SRE 彩蛋职业同档位）
                .setCanBeRandomedByOtherRoles(true);

        // ② 技能（统一技能系统：G 键）
        RoleSkill.register(roleInstance,
                RoleSkill.skill(SKILL_ID, SKILL_NAME_KEY, GreenRole::useReturnSkill)
                        // 真正的限制是「每局一次」，冷却只用来防止同一 tick 内重复触发
                        .cooldownSeconds(1)
                        .showOnHud(false)     // 冷却 HUD 不用通用 HUD：右下角自绘剩余次数
                        .build());

        // ③ 商店：一条「癫狂」
        // 真正生效的是 GreenRole#getShopEntries() 覆写：ShopContent.getShopEntries(...) 的第一优先级
        // 就是角色覆写（其次才是 ShopContent.customEntries，最后才是杀手默认刀具）。
        // 之所以不只用 customEntries：SRE 的 RoleShopHandler#shopRegister() 里有
        // `ShopContent.customEntries.clear()`，而它在服务端启动（SERVER_STARTED）与客户端每局开局
        // （OnGameStartedClient）都会被调用，附加模组写进去的条目会被静默清掉。
        // 这里仍往 customEntries 里补一份（有代码/索引直接读这个 map），并在服务端启动后登记：
        // 放在 SERVER_STARTED 而不是初始化期，是为了避免在模组初始化阶段提前触发 SRE 物品类的静态初始化。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> registerShopEntry());

        // ④ 空手左键：放行 + 命中结算（绝不致死）
        AllowPlayerPunching.EVENT.register(GreenRole::allowPunching);
        AttackEntityCallback.EVENT.register(GreenRole::onAttackEntity);

        CheeseRolePack.LOGGER.info("已注册格林（{}），技能 = {}", ID, SKILL_ID);
    }

    /** 注册后的职业实例（可能在 {@link #register()} 之前为 {@code null}）。 */
    public static SRERole role() {
        return roleInstance;
    }

    // ==================== 阵营 / 判定辅助 ====================

    /** 该玩家是否是格林。 */
    public static boolean isGreen(Player player) {
        if (player == null || SRE.isLobby || player.level() == null) {
            return false;
        }
        SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        return role != null && ID.equals(role.identifier());
    }

    /** 该玩家当前的 SAN（0~1；非 REAL 心情职业恒为 1）。 */
    public static float san(Player player) {
        return SREPlayerMoodComponent.KEY.get(player).getMood();
    }

    /** SAN 是否低到可以空手击退（SAN &lt; 30）。 */
    public static boolean isSanLowEnoughToPunch(Player player) {
        return san(player) < PUNCH_MAX_MOOD;
    }

    // ==================== ① 开局物品 / 死亡掉落 ====================

    /**
     * 开局自带一本「书与笔」。
     *
     * <p>自定义（附加模组）职业的初始物品走的就是这条回退路径：
     * {@code ModdedRoleAssigned.EVENT → RoleInitialItems.addInitialItemsForRole →
     * role.getDefaultItems()}。</p>
     */
    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>(super.getDefaultItems());
        items.add(Items.WRITABLE_BOOK.getDefaultInstance());
        return items;
    }

    /**
     * 死亡时把「书与笔」从背包里取出来掉在原地。
     *
     * <p>SRE 的死亡流程（{@code GameMode#killPlayer}）只掉落 {@code shouldDropOnDeath} 的物品，
     * 其余背包内容会保留到复活，所以这里必须自己把书取出来并 {@link Player#drop}，
     * 否则书会跟着玩家进旁观者状态。</p>
     *
     * <p>调用时机：{@code GameMode#killPlayer} 先调用 {@code role.onDeath(...)}，
     * 之后才遍历背包掉落，因此这里取出的物品不会被重复掉落。</p>
     */
    @Override
    public void onDeath(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);
        if (victim == null) {
            return;
        }
        dropQuillBooks(victim);
    }

    /** 把背包里所有「书与笔」取出并掉落在玩家当前位置。 */
    private static void dropQuillBooks(Player victim) {
        Inventory inventory = victim.getInventory();
        List<ItemStack> toDrop = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.WRITABLE_BOOK)) {
                toDrop.add(stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (ItemStack stack : toDrop) {
            victim.drop(stack, true, false);
        }
    }

    // ==================== ② 技能：全场返回房间 ====================

    /**
     * 技能「全场返回房间」。
     *
     * <p>返回 {@code true} 才会消耗冷却；失败（SAN 不够 / 次数用完）返回 {@code false}。</p>
     */
    public static boolean useReturnSkill(RoleSkill.RoleSkillContext context) {
        if (context == null) {
            return false;
        }
        ServerPlayer player = context.player();
        if (player == null || player.isSpectator()) {
            return false;
        }
        GreenRoleData data = RoleData.getNullable(GreenRoleData.class, player);
        if (data == null || !data.hasUseLeft()) {
            sendActionBar(player, Component.translatable("message.cheeserolepack.green.no_uses"));
            return false;
        }
        if (san(player) <= SKILL_MIN_MOOD) {
            sendActionBar(player, Component.translatable("message.cheeserolepack.green.san_too_low"));
            return false;
        }

        ServerLevel level = player.serverLevel();
        int moved = returnEveryoneToOwnRoom(level);
        int drained = drainEveryoneSan(level);

        // 全局每局限一次：把次数扣光并同步给客户端 HUD（1 → 0）
        data.consumeUse();

        // ———— 表现层：与 486 修饰符触发时一致（COD 字幕 + 回放事件），仅文案不同；不含时停 ————
        // 参考 org/agmas/noellesroles/modifier/AnimeModifiers.java 的 RE0_486 触发分支。
        io.wifi.starrailexpress.util.SRENetworkMessageUtils.sendCODSubtitleToAll(
                Component.translatable("message.cheeserolepack.green.trigger.title")
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable("message.cheeserolepack.green.trigger.subtitle"),
                100, COLOR, false, net.exmo.sre.subtitle.SubtitleS2CPayload.POS_CENTER);
        io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("message.cheeserolepack.green.trigger.text",
                        io.wifi.starrailexpress.api.replay.GameReplayUtils.getReplayPlayerDisplayText(player, true))
                        .withStyle(ChatFormatting.YELLOW),
                false);
        // 说明：这里**不再**触发 TimeStopEffect，即去掉「时停 2 秒」那段广播。

        Component message = Component.translatable("message.cheeserolepack.green.used", moved, drained);
        level.players().forEach(p -> sendActionBar(p, message));

        CheeseRolePack.LOGGER.debug("格林技能发动：{} 人被送回房间，{} 个可减 SAN 职业各 -50 SAN",
                moved, drained);
        return true;
    }

    /**
     * 把所有玩家传送回各自的房间。
     *
     * <p>房间映射：{@code GameUtils.roomToPlayer}（UUID → 房间号）+
     * {@code AreasWorldComponent#getRoomPosition(int)} 取坐标。
     * 两处都可能拿不到值（没分配房间 / 该房间没有记录），所以都要判空。</p>
     *
     * @return 实际被传送的人数
     */
    private static int returnEveryoneToOwnRoom(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        int moved = 0;
        for (ServerPlayer target : level.players()) {
            Integer room = GameUtils.roomToPlayer.get(target.getUUID());
            if (room == null) {
                continue; // 没有分配房间：跳过，绝不冒险传送
            }
            Vec3 pos = areas.getRoomPosition(room);
            if (pos == null) {
                continue; // 该房间没有坐标记录
            }
            target.teleportTo(level, pos.x, pos.y, pos.z, target.getYRot(), target.getXRot());
            moved++;
        }
        return moved;
    }

    /**
     * 让所有「SAN 可被减少」的职业减少 50 点 SAN。
     *
     * <p>判定条件：{@code role.getMoodType() == SRERole.MoodType.REAL}。
     * 原因见 {@code SREPlayerMoodComponent}：{@code getMood/setMood/addMood} 只对 REAL
     * 心情职业生效，NONE / FAKE 的 SAN 恒为 1，强行加减没有意义。
     * 这里用 {@code addMood}（内部会 clamp 到 0~1）而不是 {@code setMood(mood - 0.5)}，
     * 避免把 SAN 直接写成负数。</p>
     *
     * @return 实际被扣 SAN 的人数
     */
    private static int drainEveryoneSan(ServerLevel level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        int drained = 0;
        for (ServerPlayer target : level.players()) {
            SRERole role = game.getRole(target);
            if (role == null || role.getMoodType() != SRERole.MoodType.REAL) {
                continue; // SAN 不可被减少的角色：跳过
            }
            SREPlayerMoodComponent.KEY.get(target).addMood(-SKILL_MOOD_COST);
            drained++;
        }
        return drained;
    }

    // ==================== ③ 空手左键击退（绝不致死） ====================

    /**
     * 放行格林在 SAN &lt; 30 时的空手近战（{@link AllowPlayerPunching} 是「任一监听器返回 true 即允许」）。
     */
    public static boolean allowPunching(Player player) {
        if (player == null || SRE.isLobby) {
            return false;
        }
        if (player.hasEffect(ModEffects.SAFE_TIME)) {
            return false; // 安全时间内不允许出手
        }
        if (!player.getMainHandItem().isEmpty()) {
            return false; // 必须空手
        }
        return isGreen(player) && isSanLowEnoughToPunch(player);
    }

    /**
     * 空手左键命中玩家：<b>伤害恒为 0</b>，只施加一次<b>原版手感</b>的击退。
     *
     * <p>参考红美铃（{@code HoanMeirinFistPunchHandler}）与骷髅（{@code SkeletonRole}）的写法：
     * {@code AllowPlayerPunching} 放行空手攻击 + {@code AttackEntityCallback} 做命中结算。</p>
     *
     * <p><b>伤害为 0 的实现</b>：本方法返回 {@link InteractionResult#SUCCESS_NO_ITEM_USED}，
     * Fabric 的 {@code AttackEntityCallback} 在服务端收到非 PASS 结果后会直接
     * {@code CallbackInfo#cancel()} 掉原版 {@code Player#attack}，因此原版攻击（含其伤害）
     * 完全不执行 —— 既然一点伤害都不产生，就<b>绝不会触发任何死亡事件</b>
     * （包括红美铃那种「被拳头打死」的死亡归属，也就不会因死亡事件引发 bug）。</p>
     *
     * <p><b>击退</b>：力度取原版近战击退的 {@code 0.4F}（{@code LivingEntity#hurt} 对普通近战
     * 用的就是它），方向为「从目标指向攻击者」，所以目标被推离攻击者，手感与原版空手打人一致。</p>
     */
    public static InteractionResult onAttackEntity(Player attacker, Level level, InteractionHand hand,
            Entity target, EntityHitResult hitResult) {
        if (SRE.isLobby) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Player victim) || victim == attacker) {
            return InteractionResult.PASS;
        }
        if (!attacker.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // 必须空手
        }
        if (!isGreen(attacker) || !isSanLowEnoughToPunch(attacker)) {
            return InteractionResult.PASS;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            return InteractionResult.PASS;
        }
        if (victim.getCooldowns().isOnCooldown(Items.CLOCK)) {
            return InteractionResult.PASS; // 目标处于安全时间
        }
        if (level.isClientSide) {
            // 客户端也取消，避免本地先做出伤害/击退预测（真正结算在服务端）
            return InteractionResult.SUCCESS;
        }
        if (attacker.getCooldowns().isOnCooldown(Items.BARRIER)) {
            return InteractionResult.SUCCESS_NO_ITEM_USED; // 自己的出拳冷却中：静默取消
        }

        // ———— 仿照红美铃（HoanMeirinFistPunchHandler）的出拳 ————
        // 沿用红美铃的 10 tick 冷却与「每拳固定 1.0 伤害」的手感，
        // 但因为格林打不死人，红美铃那套「连击计数 [N/3] → 第 3 拳击杀」没有意义，已整体去掉。
        attacker.getCooldowns().addCooldown(Items.BARRIER, PUNCH_COOLDOWN_TICKS);

        // 每拳固定 1.0 伤害（红美铃的数值），但**夹到最多只打到 1 血**：
        // 这样既保留了红美铃那种「一拳一点血」的手感，又不可能打出致死一击，
        // 因而不会触发 die() / 死亡归属 / 任何死亡事件。
        float safeDamage = Math.min(PUNCH_DAMAGE, Math.max(0.0F, victim.getHealth() - 1.0F));
        if (safeDamage > 0.0F) {
            // hurt() 自己会处理伤害结算、命中音效与击退（原版近战击退 0.4F），无需另行施加
            victim.hurt(attacker.damageSources().playerAttack(attacker), safeDamage);
        } else {
            // 目标已经只剩 1 血：不再造成伤害，但仍给一次原版手感的击退
            victim.knockback(VANILLA_PUNCH_KNOCKBACK,
                    attacker.getX() - victim.getX(),
                    attacker.getZ() - victim.getZ());
        }

        // 取消原版攻击：伤害已由上面的 hurt(1.0, 夹到 1 血) 结算，不走原版属性伤害
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    // ==================== ④ 商店：「癫狂」 ====================

    /** 商店条目单例（双端复用；展示图标/价格固定，可以安全共享）。 */
    private static ShopEntry maniaEntry = null;

    /**
     * 格林的专属商店：只有一条「癫狂」。
     *
     * <p>SRE 的 {@code ShopContent.getShopEntries(role)} 解析顺序是
     * <b>角色覆写 → {@code ShopContent.customEntries} → 杀手默认刀具</b>，
     * 所以覆写这里是最稳的做法（4.3.0 与 4.4.0 都支持，且不会被
     * {@code RoleShopHandler#shopRegister()} 里的 {@code customEntries.clear()} 清掉）。</p>
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(maniaEntry());
        return entries;
    }

    /** 取（必要时创建）「癫狂」条目。 */
    private static ShopEntry maniaEntry() {
        if (maniaEntry == null) {
            maniaEntry = createManiaEntry();
        }
        return maniaEntry;
    }

    /** 往 {@link ShopContent#customEntries} 里补登记一份（该 map 会被 SRE 清空，故需要时可重复调用）。 */
    private static void registerShopEntry() {
        ShopContent.customEntries.put(ID, List.of(maniaEntry()));
    }

    /**
     * 商店条目「癫狂」：150 金币，购买后立刻把购买者的 SAN 归零。
     *
     * <p>展示图标用拳套（{@code noellesroles:boxing_glove}）的物品贴图，
     * 名字用 {@code CUSTOM_NAME} 覆盖成「癫狂」，这样图标是拳套、名字是癫狂。</p>
     */
    private static ShopEntry createManiaEntry() {
        ItemStack icon = ModItems.BOXING_GLOVE.getDefaultInstance();
        icon.set(DataComponents.CUSTOM_NAME, Component.translatable("shop.cheeserolepack.mania"));

        return new ShopEntry(icon, MANIA_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                if (player == null) {
                    return false;
                }
                // 立刻把 SAN 归零（setMood 内部对非 REAL 心情职业会重置为 1，属正常行为）
                SREPlayerMoodComponent.KEY.get(player).setMood(0.0F);
                sendActionBar(player,
                        Component.translatable("message.cheeserolepack.green.mania")
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                return true;
            }
        };
    }

    // ==================== 工具 ====================

    /** 发送动作栏提示（界面开着也看得到）。 */
    private static void sendActionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }
}
