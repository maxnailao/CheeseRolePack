package com.cheese.rolepack.role;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.entity.AntiPersonnelMineEntity;
import com.cheese.rolepack.content.entity.ClaymoreMineEntity;
import com.cheese.rolepack.content.item.ClaymoreMineItem;
import com.cheese.rolepack.content.item.Rpg7Item;
import com.cheese.rolepack.init.ModItems;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 「更好的悍匪」—— 以 StarRailExpress 的<b>悍匪</b>（{@code noellesroles:gangsters}，
 * 即 {@code ModRoles.GANGSTERS}）为模板，参数、初始物品、商店全部沿用，
 * 再额外加两样自研内容：<b>阔剑地雷</b>（{@value ClaymoreMineItem#PRICE} 金）与
 * <b>疯狂模式</b>（{@value #PSYCHO_MODE_PRICE} 金）。
 *
 * <p><b>注意别和「强盗」搞混</b>：SRE 里
 * {@code noellesroles:bandit} 是<b>强盗</b>（棕色、飞斧+撬棍），
 * {@code noellesroles:gangsters} 才是<b>悍匪</b>（灰黑色、C4+引爆器）。本职业对齐的是后者。</p>
 *
 * <p><b>与悍匪一致的部分</b>（参数取自 {@code ModRoles.GANGSTERS}）：</p>
 * <ul>
 *   <li>阵营：杀手侧（{@code isInnocent = false}）</li>
 *   <li>可使用杀手特性（{@code canUseKiller = true}）</li>
 *   <li>心情类型：{@code FAKE}（假心情，没有真实 SAN）</li>
 *   <li>冲刺时长：{@code Integer.MAX_VALUE}（无限）</li>
 *   <li>隐藏计分板：{@code true}，并且 {@code setCanSeeCoin(true)}（能看见金币）</li>
 *   <li>刷新：单局上限 1、需要 12 人开局、权重 7500</li>
 *   <li>颜色：{@code (60, 60, 60)} 灰黑</li>
 *   <li>初始物品：C4 炸药 + C4 引爆器</li>
 *   <li>商店：复用 SRE 注册在 {@code ShopContent.customEntries} 里的悍匪商店</li>
 * </ul>
 *
 * <p><b>本模组追加</b>：商店末尾追加阔剑地雷与「疯狂模式」。<b>RPG-7 与火箭弹已从商店下架</b> ——
 * 现在只能在疯狂模式里拿到：进入疯狂模式时发一把<b>已装填</b>的 RPG-7（外加一把刀），
 * 疯狂模式中用<b>刀</b>击杀其他玩家额外奖励 1 发火箭弹，疯狂模式结束时把 RPG-7 与火箭弹全部收回。</p>
 */
public class BetterGangsterRole extends NormalRole {

    /** 职业 id：{@code cheeserolepack:better_gangster}。 */
    public static final ResourceLocation ID = CheeseRolePack.id("better_gangster");

    /** 与悍匪同款灰黑色。 */
    private static final int COLOR = new Color(60, 60, 60).getRGB();

    /** 商店里「疯狂模式」的价格（金币）。 */
    public static final int PSYCHO_MODE_PRICE = 400;

    /** 商店里「反人员地雷」（移植自 RKD）的价格（金币）。 */
    public static final int AP_MINE_PRICE = 80;

    /** 商店里疯狂模式条目显示的名字（本模组自己的翻译键，避免和 SRE 原生疯狂模式混淆）。 */
    public static final String PSYCHO_MODE_NAME_KEY = "item.cheeserolepack.psycho_mode";

    private static BetterGangsterRole instance = null;

    private BetterGangsterRole() {
        // 参数与悍匪一一对应：非平民 / 可用杀手特性 / 假心情 / 无限冲刺 / 隐藏计分板
        super(ID, COLOR, false, true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true);
    }

    /** 注册职业（在 {@code CheeseRolePack#onInitialize()} 里调用一次）。 */
    public static SRERole register() {
        if (instance != null) {
            return instance;
        }
        instance = new BetterGangsterRole();
        // setRoleData 要在 registerRole 之前挂上（与 GreenRole 的写法一致）：
        // 职业数据 = 场上地雷计数，供右下角 HUD 显示
        TMMRoles.registerRole(instance.setRoleData(BetterGangsterRoleData::new))
                .setCanSeeCoin(true)                      // 与悍匪一致：能看见金币
                .setDefaultMax(1)                         // 单局最多 1 人
                .setDefaultEnableNeededPlayerCount(12)    // 需要 12 人开局
                .setDefaultEnableChance(7500);            // 与悍匪同权重
        // 疯狂模式下的刀杀奖励（只注册一次，随 register() 的 instance 判空一起保证）
        registerPsychoKillReward();
        // 远程引爆技能（统一技能系统里的登记；真正的触发走客户端 GKeyRoleSkill + C2S 包）
        registerRemoteDetonateSkill();
        // 生成规则：与悍匪互斥 + 与钳工绑定（职业实例已就绪，这里才注册）
        BetterGangsterSpawnRule.register();
        CheeseRolePack.LOGGER.info(
                "已注册更好的悍匪（{}），商店 = 悍匪商店 + 阔剑地雷 + 反人员地雷 + 疯狂模式", ID);
        return instance;
    }

    /** 注册后的职业实例。 */
    public static SRERole role() {
        return instance;
    }

    /**
     * 初始物品：与悍匪完全一致（C4 炸药 + C4 引爆器）。
     *
     * <p>SRE 用 {@code RoleInitialItems.INITIAL_ITEMS_MAP.put(ModRoles.GANGSTERS, ...)} 发这两件，
     * 那是按职业实例登记的表；附加模组无法往里写自己的角色，所以在职业里覆写本方法达到同样效果。</p>
     */
    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(org.agmas.noellesroles.init.ModItems.C4.getDefaultInstance());
        items.add(org.agmas.noellesroles.init.ModItems.C4_DETONATOR.getDefaultInstance());
        // 初始多给一颗阔剑地雷（与悍匪的 C4 组合区分开：本职业有地雷玩法）
        items.add(ModItems.CLAYMORE_MINE.getDefaultInstance());
        return items;
    }

    // === 疯狂模式（类似魔术师的疯狂模式，但走 SRE 官方入口，冷却与回放都规范）===

    /**
     * 疯狂模式开始时：发一把<b>已装填</b>的 RPG-7，并确保玩家有一把可以切换的刀。
     *
     * <p>调用时机由 SRE 保证：{@code SREPlayerPsychoComponent#startPsycho_time(int,int)} 在
     * 设完 {@code psychoTicks}/{@code armour} 之后调用本钩子（已用 javap 核对字节码）。</p>
     */
    @Override
    public void onPsychoStart(Player player, SREPlayerPsychoComponent component) {
        // 已装填的 RPG-7：弹药数拉满（MAX_AMMO = 1），Rpg7Item#setAmmoCount 会顺带切到「装填」模型
        ItemStack rocketLauncher = new ItemStack(ModItems.RPG7);
        Rpg7Item.setAmmoCount(rocketLauncher, Rpg7Item.MAX_AMMO);
        giveOrDrop(player, rocketLauncher);

        // 每次进入疯狂模式都重置「刀杀奖励已发」标记 → 每次疯狂模式只能拿一次
        resetPsychoKnifeReward(player);

        // 疯狂模式一开启就立刻刷新小刀冷却（玩家能马上挥第一刀）
        player.getCooldowns().removeCooldown(io.wifi.starrailexpress.index.TMMItems.KNIFE);

        player.displayClientMessage(
                Component.translatable("message.cheeserolepack.psycho_mode.start"), true);
        CheeseRolePack.LOGGER.info("[疯狂模式] {} 进入疯狂模式，已发放装填好的 RPG-7",
                player.getName().getString());
    }

    /**
     * 声明「本角色的疯狂模式物品由自己发放」，让 SRE 不要发默认的球棒。
     *
     * <p>SRE 的 {@code startPsycho_time} 会先调用本方法，<b>返回 false 就直接放弃进入疯狂模式</b>，
     * 所以这里必须返回 {@code true}；真正的发物品在 {@link #onPsychoStart} 里做。</p>
     */
    @Override
    public boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent component) {
        return true;
    }

    /**
     * 疯狂模式结束时：把背包里的 RPG-7 与全部火箭弹收回。
     *
     * <p>{@code SREItemUtils.clearItem(player, item)} 等价于「遍历背包（含合成格）删除所有匹配的堆」，
     * 返回删除的数量。</p>
     */
    @Override
    public void onPsychoOver(Player player, SREPlayerPsychoComponent component) {
        resetPsychoKnifeReward(player);
        int guns = SREItemUtils.clearItem(player, ModItems.RPG7);
        int ammo = SREItemUtils.clearItem(player, ModItems.RPG7_AMMO);

        CheeseRolePack.LOGGER.info("[疯狂模式] {} 疯狂模式结束，收回 RPG-7 × {}、火箭弹 × {}",
                player.getName().getString(), guns, ammo);
    }

    /**
     * 疯狂模式中<b>用刀杀人</b>额外奖励 1 发火箭弹。
     *
     * <p>判定条件：击杀者是本职业 + 击杀者正处于疯狂模式（{@code psychoTicks > 0}）
     * + 刀杀（SRE 给的 {@code DeathReason.KNIFE}，或击杀瞬间主手是 {@link TMMItems#KNIFE}）。</p>
     */
    private static void registerPsychoKillReward() {
        OnPlayerKilledPlayer.EVENT.register(BetterGangsterRole::onPlayerKilled);
    }

    private static void onPlayerKilled(ServerPlayer victim, ServerPlayer killer,
            OnPlayerKilledPlayer.DeathReason reason) {
        if (victim == null || killer == null || victim == killer) {
            return;
        }
        // 只对本职业生效（按 role id 比对，不依赖实例同一性）
        if (!isBetterGangster(killer)) {
            return;
        }
        // 必须在疯狂模式中
        if (SREPlayerPsychoComponent.KEY.get(killer).psychoTicks <= 0) {
            return;
        }
        // 必须是刀杀：SRE 的 GameMode 在击杀时会按凶器算出 DeathReason.KNIFE，
        // 但主手判定也很直接（而且两者都成立时最稳），任一满足即算刀杀
        boolean knifeKill = reason == OnPlayerKilledPlayer.DeathReason.KNIFE
                || killer.getMainHandItem().is(TMMItems.KNIFE);
        if (!knifeKill) {
            return;
        }
        // 每次疯狂模式只触发一次：已经拿过就不再给
        BetterGangsterRoleData data = io.wifi.starrailexpress.api.data.RoleData.getNullable(BetterGangsterRoleData.class, killer);
        if (data != null) {
            if (data.isPsychoKnifeRewardGranted()) {
                return;
            }
            data.setPsychoKnifeRewardGranted(true);
        }
        giveOrDrop(killer, new ItemStack(ModItems.RPG7_AMMO));
        killer.displayClientMessage(
                Component.translatable("message.cheeserolepack.psycho_mode.ammo_gain"), true);
    }

    /** 击杀者的职业是不是「更好的悍匪」。 */
    private static boolean isBetterGangster(Player player) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameComponent == null) {
            return false;
        }
        SRERole role = gameComponent.getRole(player);
        return role != null && ID.equals(role.identifier());
    }

    /** 背包有空位就放背包，没有就掉在脚下（绝不让奖励凭空消失）。 */
    private static void giveOrDrop(Player player, ItemStack stack) {
        if (SREItemUtils.insertStackInFreeSlot(player, stack)) {
            return;
        }
        player.drop(stack, false);
        CheeseRolePack.LOGGER.warn("[疯狂模式] {} 背包已满，{} 已掉落在脚下",
                player.getName().getString(), stack.getHoverName().getString());
    }

    // === 商店 ===

    /**
     * 商店：复用悍匪那一份，再追加阔剑地雷与「疯狂模式」。
     *
     * <p><b>RPG-7 与火箭弹已下架</b>（{@value #PSYCHO_MODE_PRICE} 金的疯狂模式取而代之）。</p>
     *
     * <p>复用方式：SRE 的 {@code RoleShopHandler} 在启动时执行
     * {@code ShopContent.customEntries.put(ModRoles.GANGSTERS_ID, GANGSTERS_SHOP)}，
     * 而 {@code ShopContent.customEntries} 与 {@code getShopEntries(ResourceLocation)} 都是 public，
     * 所以这里能直接取到同一份列表。</p>
     *
     * <p><b>注意</b>：必须拷贝一份再加条目 —— 直接往返回的列表里 add 会污染悍匪自己的商店。</p>
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> entries = new ArrayList<>();
        try {
            entries.addAll(ShopContent.getShopEntries(org.agmas.noellesroles.role.ModRoles.GANGSTERS_ID));
        } catch (Throwable t) {
            // 拿不到悍匪商店也不要崩：至少保证自研内容能买
            CheeseRolePack.LOGGER.warn("读取悍匪商店失败，更好的悍匪将只有自研条目", t);
        }

        // 阔剑地雷：消耗品，布设即消耗；被拆除时按同一个价格返还金币
        // 阔剑地雷：背包里已经有一枚就禁止购买（避免误买多个）
        entries.add(new ShopEntry(ModItems.CLAYMORE_MINE.getDefaultInstance(), ClaymoreMineItem.PRICE,
                ShopEntry.Type.TOOL));

        // 反人员地雷（移植自 RKD）：布设即消耗，自动布防；100 金，不限购
        entries.add(new ShopEntry(ModItems.ANTI_PERSONNEL_MINE.getDefaultInstance(), AP_MINE_PRICE,
                ShopEntry.Type.TOOL));

        // 疯狂模式：图标借用 SRE 的疯狂模式物品，名字换成本模组自己的翻译键
        // 图标改用 RPG-7（本模组的物品），不再用 SRE 的疯狂模式物品
        ItemStack psychoIcon = TMMItems.PSYCHO_MODE.getDefaultInstance();
        // ↑ 必须用 SRE 官方的疯狂模式物品：冷却（SREConfig.psychoModeCooldown）
        //   和「给别人上全局冷却」都记在这个物品上，用别的物品做图标会让
        //   商店按那个物品判断冷却 → 显示成没有 CD。
        psychoIcon.set(DataComponents.CUSTOM_NAME, Component.translatable(PSYCHO_MODE_NAME_KEY));
        entries.add(new ShopEntry(psychoIcon, PSYCHO_MODE_PRICE, ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                // 走 SRE 官方入口：加冷却 + startPsycho_time + 记回放。
                // 返回 false 时商店不会扣钱（已用 javap 核对 SREPlayerShopComponent 的购买流程）。
                return SREPlayerShopComponent.usePsychoMode_time(player,
                        GameConstants.getPsychoTimer(), GameConstants.getPsychoModeArmour());
            }
        });

        return reorderShop(entries);
    }

    /**
     * 把商店条目重排成需求指定的顺序：
     *
     * <pre>
     * 刀 → 霰弹枪 → C4 → 阔剑地雷 → 反人员地雷 → 撬棍 → 开锁器 → 关灯 → 疯狂模式
     * </pre>
     *
     * <p>做法是「按物品身份从原列表里摘出来再按顺序放回」，这样 SRE 悍匪商店里各条目的
     * <b>价格、限购、冷却逻辑全部原样保留</b>，只有顺序变了。摘不到的条目（例如 SRE 以后新增的）
     * 会按原相对顺序留在中间，疯狂模式恒定排在最后。</p>
     */
    private static List<ShopEntry> reorderShop(List<ShopEntry> base) {
        List<ShopEntry> rest = new ArrayList<>(base);
        List<ShopEntry> out = new ArrayList<>();

        take(out, rest, io.wifi.starrailexpress.index.TMMItems.KNIFE);                // 刀
        take(out, rest, org.agmas.noellesroles.init.ModItems.SHORT_SHOTGUN);          // 霰弹枪
        take(out, rest, org.agmas.noellesroles.init.ModItems.C4);                     // C4
        takeItem(out, rest, ModItems.CLAYMORE_MINE);                                  // 阔剑地雷
        takeItem(out, rest, ModItems.ANTI_PERSONNEL_MINE);                            // 反人员地雷
        take(out, rest, io.wifi.starrailexpress.index.TMMItems.CROWBAR);              // 撬棍
        take(out, rest, io.wifi.starrailexpress.index.TMMItems.LOCKPICK);             // 开锁器

        // 疯狂模式单独摘出来，恒定排最后
        ShopEntry psycho = null;
        for (java.util.Iterator<ShopEntry> it = rest.iterator(); it.hasNext();) {
            ShopEntry entry = it.next();
            if (entry.stack().is(TMMItems.PSYCHO_MODE)) {
                psycho = entry;
                it.remove();
                break;
            }
        }

        out.addAll(rest);   // 其余（关灯等）保持原相对顺序
        if (psycho != null) {
            out.add(psycho);
        }
        return out;
    }

    /** 按物品把条目从 {@code rest} 摘出来并追加到 {@code out}。 */
    private static void take(List<ShopEntry> out, List<ShopEntry> rest, net.minecraft.world.item.Item item) {
        takeItem(out, rest, item);
    }

    /** 同 {@link #take}，但返回被摘出的条目。 */
    private static ShopEntry takeItem(List<ShopEntry> out, List<ShopEntry> rest,
            net.minecraft.world.item.Item item) {
        for (java.util.Iterator<ShopEntry> it = rest.iterator(); it.hasNext();) {
            ShopEntry entry = it.next();
            if (entry.stack().is(item)) {
                out.add(entry);
                it.remove();
                return entry;
            }
        }
        return null;
    }

    // === 远程引爆技能 ===

    /** 技能 id。 */
    public static final ResourceLocation SKILL_ID = CheeseRolePack.id("remote_detonate");

    /** 技能名翻译键。 */
    public static final String SKILL_NAME_KEY = "skill.cheeserolepack.better_gangster.remote_detonate";

    /** 技能冷却（tick）：与 {@code cooldownSeconds(1)} 对齐。 */
    public static final int REMOTE_DETONATE_COOLDOWN_TICKS = 20;

    /** 触发者与地雷的最大作用距离（格）：超过这个距离就没法远程点燃。 */
    public static final double REMOTE_DETONATE_MAX_RANGE = 200.0D;

    /** 判定「附近有玩家带钳子」的半径（格）。 */
    public static final double PLIERS_BLOCK_RADIUS = 3.0D;

    /**
     * 注册统一技能（SRE 的技能系统会据此知道本职业有一个技能）。
     *
     * <p><b>注意</b>：真正的触发链路是「客户端 {@code GKeyRoleSkill} 用准星选中地雷 → 发
     * {@code RemoteDetonatePayload} → 服务端 {@link #tryRemoteDetonate}」，
     * 因为服务端拿不到玩家的准星目标。这里的 handler 只是让技能在 SRE 侧有登记
     * （以及万一某条输入路径直接把目标 UUID 送进来时也能工作），平时返回 false 不消耗冷却。</p>
     */
    private static void registerRemoteDetonateSkill() {
        try {
            RoleSkill.register(instance,
                    RoleSkill.skill(SKILL_ID, SKILL_NAME_KEY, BetterGangsterRole::onRemoteDetonateSkill)
                            .cooldownSeconds(1)
                            .showOnHud(false)
                            // 不要客户端上报目标：SRE 4.3.0 的 Builder 没有 targetType(...)，
                            // 其 findTarget 只认玩家，瞄地雷必然拿不到目标。
                            // 这里明确声明「本技能不需要客户端目标」，由服务端自己做射线选雷。
                            .withTarget(false)
                            .build());
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("注册远程引爆技能失败（不影响 GKey 触发链路）", t);
        }
    }

    /** 统一技能 handler：只有 SRE 直接给出目标 UUID 时才有意义。 */
    private static boolean onRemoteDetonateSkill(RoleSkill.RoleSkillContext context) {
        if (context == null || context.player() == null) {
            return false;
        }
        Player player = context.player();
        // 服务端自己做准星射线选雷：客户端与服务端用同一套算法
        // （com.cheese.rolepack.client.MineTargeting），因此与 HUD 提示完全一致。
        net.minecraft.world.entity.Entity target =
                com.cheese.rolepack.util.RemoteDetonateTargeting.pickForServer(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.no_target"), true);
            return false;
        }
        return tryRemoteDetonate(player, target);
    }

    /**
     * 服务端远程引爆（客户端 C2S 包与统一技能共用的唯一入口）。
     *
     * <p>校验顺序：目标必须是活着的地雷 → 玩家没在技能冷却 → 地雷 10 格内没有玩家带钳子 →
     * 按距离选前摇 tick 点燃。成功才进冷却。</p>
     *
     * @return 是否成功点燃
     */
    public static boolean tryRemoteDetonate(@NotNull Player player, @Nullable net.minecraft.world.entity.Entity target) {
        if (player == null || target == null || target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        // 1) 必须是地雷
        boolean claymore = target instanceof ClaymoreMineEntity;
        boolean apMine = target instanceof AntiPersonnelMineEntity;
        if (!claymore && !apMine) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.no_target"), true);
            return false;
        }
        // 未布防的地雷不能远程引爆（阔剑要手动布防，反人员地雷放下 3 秒后自动布防）
        boolean armed = (claymore && ((ClaymoreMineEntity) target).isArmed())
                || (apMine && ((AntiPersonnelMineEntity) target).isArmed());
        if (!armed) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.not_armed"), true);
            return false;
        }
        // 距离上限（防止客户端发一个超远目标）
        if (player.distanceToSqr(target) > REMOTE_DETONATE_MAX_RANGE * REMOTE_DETONATE_MAX_RANGE) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.too_far"), true);
            return false;
        }
        // 2) 冷却
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability != null && ability.getCooldown() > 0) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.cooldown"), true);
            return false;
        }
        // 3) 半径 PLIERS_BLOCK_RADIUS（3 格）内不能有玩家带钳子
        if (hasPliersNearby(level, target.position())) {
            CheeseRolePack.LOGGER.info("[远程引爆] 被钳子拦截：地雷 {} 的 {} 格内有玩家携带钳子",
                    target.blockPosition(), PLIERS_BLOCK_RADIUS);
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.pliers"), true);
            return false;
        }
        // 4) 按距离选前摇
        double distance = Math.sqrt(player.distanceToSqr(target));
        int fuse = fuseTicksFor(distance);
        if (claymore) {
            ((ClaymoreMineEntity) target).igniteRemoteFuse(fuse);
        } else {
            ((AntiPersonnelMineEntity) target).igniteRemoteFuse(fuse);
        }
        // 5) 进冷却
        if (ability != null) {
            ability.setCooldown(REMOTE_DETONATE_COOLDOWN_TICKS);
        }
        if (fuse <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.cheeserolepack.remote_detonate.instant"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.cheeserolepack.remote_detonate.ignited",
                    String.format("%.1f", fuse / 20.0D)), true);
        }
        CheeseRolePack.LOGGER.info("[远程引爆] {} 点燃了一颗地雷（{} 格 → {} tick 前摇）",
                player.getName().getString(), String.format("%.1f", distance), fuse);
        return true;
    }

    /**
     * 前摇 tick：≤10 格 → 0.5 秒（10 tick）；10~50 格 → 2 秒（40 tick）；&gt;50 格 → 3 秒（60 tick）。
     */
    public static int fuseTicksFor(double distance) {
        // 50 格内：瞬间引爆（0 前摇；实际会被 igniteRemoteFuse 抬到 1 tick，即下一 tick 起爆）
        if (distance <= 50.0D) {
            return 0;
        }
        // 50 格以外：1 秒前摇（20 tick）
        return 20;
    }

    /**
     * 地雷周围 {@value #PLIERS_BLOCK_RADIUS} 格内是否有任何玩家带着钳子。
     *
     * <p>检查范围：<b>主手 + 副手 + 整个背包</b>（{@code Inventory} 含主背包 / 护甲 / 副手）。</p>
     */
    public static boolean hasPliersNearby(ServerLevel level, net.minecraft.world.phys.Vec3 center) {
        double radiusSqr = PLIERS_BLOCK_RADIUS * PLIERS_BLOCK_RADIUS;
        for (Player other : level.players()) {
            if (other.isSpectator() || !other.isAlive()) {
                continue;
            }
            if (other.distanceToSqr(center) > radiusSqr) {
                continue;
            }
            if (carriesPliers(other)) {
                // 便于实测核对：把实际距离和判定半径一起打出来
                // 注意：SLF4J 只认 {}，不支持 {:.2f} 这类格式，所以小数用 String.format 预先格式化
                CheeseRolePack.LOGGER.info("[远程引爆] 持钳者 {} 距地雷 {} 格（判定半径 {} 格）",
                        other.getName().getString(),
                        String.format("%.2f", Math.sqrt(other.distanceToSqr(center))),
                        String.format("%.1f", PLIERS_BLOCK_RADIUS));
                return true;
            }
        }
        return false;
    }

    /** 玩家身上（主手 / 副手 / 背包）是否带着钳子。 */
    public static boolean carriesPliers(Player player) {
        if (player.getMainHandItem().is(org.agmas.noellesroles.init.ModItems.PLIERS)
                || player.getOffhandItem().is(org.agmas.noellesroles.init.ModItems.PLIERS)) {
            return true;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(org.agmas.noellesroles.init.ModItems.PLIERS)) {
                return true;
            }
        }
        return false;
    }

    /** 重置「本次疯狂模式的刀杀奖励已发」标记（进入/退出疯狂模式时各调一次）。 */
    private static void resetPsychoKnifeReward(Player player) {
        if (player == null) {
            return;
        }
        BetterGangsterRoleData data = io.wifi.starrailexpress.api.data.RoleData.getNullable(BetterGangsterRoleData.class, player);
        if (data != null) {
            data.setPsychoKnifeRewardGranted(false);
        }
    }

    /**
     * 疯狂模式下的皮肤：用 {@code temp/hanfei.png}（悍匪皮肤，64×64）。
     *
     * <p>SRE 会在玩家进入疯狂模式时调用本方法替换皮肤
     * （{@code PlayerEntityRendererMixin} 里 {@code role.getPsychoSkin(...)}），
     * 返回 {@code null} 则回退到 SRE 的默认 {@code textures/entity/psycho.png}。</p>
     *
     * <p>注意路径是<b>完整纹理路径</b>（含 {@code textures/} 与 {@code .png}），
     * 与 SRE 其它职业的写法一致。</p>
     */
    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return CheeseRolePack.id("textures/entity/custom_psycho/hanfei.png");
    }
}
