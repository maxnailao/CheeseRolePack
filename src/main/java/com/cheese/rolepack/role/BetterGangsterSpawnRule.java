package com.cheese.rolepack.role;

import com.cheese.rolepack.CheeseRolePack;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「更好的悍匪」的生成规则：<b>与悍匪互斥 + 与钳工绑定</b>。
 *
 * <h2>一、钳工是谁</h2>
 * <p>钳工 = {@code org.agmas.noellesroles.role.ModRoles.FITTER}，id = {@code noellesroles:fitter}
 * （{@code Noellesroles.id("fitter")}），平民阵营，初始物品是拆弹钳 {@code ModItems.PLIERS}。
 * SRE 的 lang 里写着悍匪「与钳工绑定生成」，对应的实现就是
 * {@code InitModRolesMax: Harpymodloader.addOccupationRole(ModRoles.GANGSTERS, ModRoles.FITTER)}。</p>
 *
 * <h2>二、两条规则怎么实现</h2>
 * <ol>
 *   <li><b>绑定（原生）</b>：照抄 SRE 对悍匪的做法，注册
 *       {@code Harpymodloader.addOccupationRole(更好的悍匪, 钳工)}。
 *       生成计划构建时 {@code RoleAssignmentManager#expandWithCompanionRoles} 会强制把钳工塞进计划，
 *       优先挤掉一个<b>与钳工同类型（平民）</b>的角色，并且<b>永远不会挤掉"有绑定角色的那个职业"本人</b>
 *       （见该方法里对 companionOwner 的跳过逻辑）。</li>
 *   <li><b>互斥（后处理）</b>：SRE 原生有 {@code SRERole#addTwoWayOpposingRole}，由
 *       {@code RoleAssignmentManager#removeOpposingJobs} 消费，但它<b>先移除谁是不确定的</b>，
 *       而我们要的是「必须保留更好的悍匪」。因此在职业分配<b>落盘前</b>的
 *       {@link OnGamePlayerRolesConfirm#beforeAssignRole} 里确定性地把<b>悍匪</b>改成平民。</li>
 *   <li><b>绑定的兜底</b>：同一个钩子里再检查一次「有更好的悍匪但没有钳工」，
 *       按「普通平民 → 其它非杀手阵营平民 → 放弃」的顺序把一个平民改派成钳工。
 *       这条兜底保证即使原生绑定被某局的自定义配置 / 非标准模式绕过，反制角色也一定在场。</li>
 * </ol>
 *
 * <p><b>插在哪个事件</b>：{@code OnGamePlayerRolesConfirm}（harpymodloader）在
 * {@code SREMurderGameMode#assignRole} 里、{@code SREGameWorldComponent#addRole} 提交之前触发，
 * 拿到的是可以随便改的 {@code Map<Player, SRERole>}，所以改派之后玩家拿到的初始物品、
 * 商店余额、职业数据都是按<b>新职业</b>走的 —— 不会出现「职业是平民、背包里还留着 C4」的残留。</p>
 *
 * <p><b>幂等</b>：每个分支都先判断条件再动手，改完之后条件不再成立；
 * 整个流程包在 try/catch 里，任何异常只 {@code warn}，绝不影响开局。</p>
 */
public final class BetterGangsterSpawnRule {

    /** 是否启用「与钳工绑定」。关掉只影响补钳工，不影响互斥。 */
    public static final boolean BIND_WITH_FITTER = true;

    /** 是否启用「与悍匪互斥」。 */
    public static final boolean EXCLUSIVE_WITH_GANGSTERS = true;

    /** 钳工的 id（{@code noellesroles:fitter}），仅用于日志/排查。 */
    public static final ResourceLocation FITTER_ID = ModRoles.FITTER_ID;

    /** 悍匪的 id（{@code noellesroles:gangsters}），仅用于日志/排查。 */
    public static final ResourceLocation GANGSTERS_ID = ModRoles.GANGSTERS_ID;

    /** 原生绑定只注册一次（{@link Harpymodloader#addOccupationRole} 是累加式注册，重复调用会加出多个钳工）。 */
    private static boolean nativeBindingDone;

    /** 介绍页的「关联内容」只登记一次。 */
    private static boolean relatedRolesDone;

    private BetterGangsterSpawnRule() {
    }

    /** 注册生成规则（在 {@link BetterGangsterRole#register()} 里调用一次，此时职业实例已存在）。 */
    public static void register() {
        ensureNativeBinding();
        ensureRelatedRoles();
        // 方案 B：分配落盘前的确定性后处理（互斥 + 绑定的最终保证）
        try {
            OnGamePlayerRolesConfirm.EVENT.register(BetterGangsterSpawnRule::beforeAssignRole);
            CheeseRolePack.LOGGER.info("[生成规则] 已挂接 OnGamePlayerRolesConfirm（互斥 + 补钳工兜底）");
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[生成规则] 挂接 OnGamePlayerRolesConfirm 失败", t);
        }
    }

    /**
     * 方案 A：注册原生「绑定生成」（与 SRE 给悍匪绑钳工完全一致）。
     *
     * <p>{@code ModRoles.FITTER} 是在 noellesroles 的初始化里才赋值的，理论上我们作为
     * SRE 的依赖方一定晚于它；万一取不到就只 warn，靠开局兜底补钳工，并且每次开局再尝试注册一次。</p>
     */
    /**
     * 在职业介绍页（U 键 GUI）的「关联内容」里登记<b>悍匪</b>与<b>钳工</b>，让玩家能直接跳转查看。
     *
     * <p>用的是 SRE 原生的 {@code SRERole#addBothRelatedRole(SRERole...)}：其注释写明「用于职业介绍」，
     * 只影响介绍页展示，<b>不参与生成逻辑</b>（生成只看 occupationRoles / opposingRoles）。</p>
     */
    private static void ensureRelatedRoles() {
        if (relatedRolesDone) {
            return;
        }
        SRERole self = BetterGangsterRole.role();
        if (self == null) {
            return;
        }
        SRERole gangsters = ModRoles.GANGSTERS;
        SRERole fitter = ModRoles.FITTER;
        if (gangsters == null || fitter == null) {
            CheeseRolePack.LOGGER.debug("[生成规则] 悍匪/钳工职业尚未注册，介绍页关联内容下次再登记");
            return;
        }
        try {
            // 对立职业：单向登记「悍匪」——介绍页会显示在「对立职业」区块，
            // 同时让 SRE 原生 removeOpposingJobs 在生成阶段确定性地移除悍匪
            // （它移除的是 opposingRoles 里被引用的那一方；悍匪身上没有引用我们，所以绝不会反向移除我们）。
            self.addOpposingRole(gangsters);
            // 双向关联：钳工（钳工的介绍页也能跳转回来；我们这边它已经在「绑定职业」区块里）
            self.addBothRelatedRole(fitter);
            relatedRolesDone = true;
            CheeseRolePack.LOGGER.info("[生成规则] 介绍页已登记：对立职业=悍匪（{}）、关联职业=钳工（{}）",
                    GANGSTERS_ID, FITTER_ID);
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[生成规则] 登记介绍页关联/对立职业失败", t);
        }
    }

    private static void ensureNativeBinding() {
        if (nativeBindingDone || !BIND_WITH_FITTER) {
            return;
        }
        SRERole self = BetterGangsterRole.role();
        SRERole fitter = ModRoles.FITTER;
        if (self == null) {
            CheeseRolePack.LOGGER.warn("[生成规则] 职业实例还没创建，跳过绑钳工");
            return;
        }
        if (fitter == null) {
            CheeseRolePack.LOGGER.warn("[生成规则] 钳工职业（{}）还没注册，本次跳过原生绑定（开局兜底仍会补钳工）",
                    FITTER_ID);
            return;
        }
        try {
            Harpymodloader.addOccupationRole(self, fitter);
            nativeBindingDone = true;
            CheeseRolePack.LOGGER.info("[生成规则] 已绑定：更好的悍匪（{}）→ 钳工（{}）",
                    self.identifier(), FITTER_ID);
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[生成规则] 注册原生绑定（钳工）失败，将只依赖开局兜底", t);
        }
    }

    /** 事件入口：任何异常都只 warn，绝不让开局崩。 */
    private static void beforeAssignRole(ServerLevel level, Map<Player, SRERole> plan) {
        try {
            // 上一局如果没绑定成功（例如钳工职业当时还没注册），这里补一次，下一局就走上原生通道
            ensureNativeBinding();
            ensureRelatedRoles();
            enforce(level, plan);
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[生成规则] 处理职业分配计划时出错（已忽略，不影响开局）", t);
        }
    }

    /**
     * 真正干活的地方（拆出来便于测试 / 复用）。
     *
     * <p>顺序很关键：<b>先做互斥</b>（把悍匪改成平民，于是多出一个平民），
     * <b>再做绑定</b>（正好可以把这个新平民改成钳工）。这样「只有一个杀手位 + 一个平民位」的极端局面
     * 也能一步到位：更好的悍匪 + 平民 → 更好的悍匪 + 钳工。</p>
     */
    public static void enforce(@Nullable ServerLevel level, @Nullable Map<Player, SRERole> plan) {
        SRERole self = BetterGangsterRole.role();
        if (plan == null || plan.isEmpty() || self == null) {
            return;
        }

        // 1) 互斥：两者同时出现 → 保留更好的悍匪，把悍匪改成平民
        if (EXCLUSIVE_WITH_GANGSTERS) {
            List<Player> gangsters = playersWithRole(plan, ModRoles.GANGSTERS);
            if (!gangsters.isEmpty() && !playersWithRole(plan, self).isEmpty()) {
                SRERole civilian = TMMRoles.CIVILIAN;
                for (Player player : gangsters) {
                    CheeseRolePack.LOGGER.warn(
                            "[生成规则] 更好的悍匪与悍匪同局出现，把 {} 由悍匪改派为平民（保留更好的悍匪）",
                            player.getName().getString());
                    plan.put(player, civilian);
                }
            }
        }

        // 2) 钳工去重：原生绑定是「每个有绑定关系的职业各加一个钳工」，
        //    如果这一局同时抽到了更好的悍匪和悍匪，就会冒出两个钳工 → 只留一个
        if (BIND_WITH_FITTER) {
            List<Player> fitters = playersWithRole(plan, ModRoles.FITTER);
            for (int i = 1; i < fitters.size(); i++) {
                Player extra = fitters.get(i);
                CheeseRolePack.LOGGER.warn("[生成规则] 计划里出现了 {} 个钳工，把 {} 改回平民（钳工保持唯一）",
                        fitters.size(), extra.getName().getString());
                plan.put(extra, TMMRoles.CIVILIAN);
            }
        }

        // 3) 绑定：有更好的悍匪就必须有钳工
        if (BIND_WITH_FITTER) {
            List<Player> betterGangsters = playersWithRole(plan, self);
            if (betterGangsters.isEmpty()) {
                return;   // 本局没有更好的悍匪，不用管钳工
            }
            if (!playersWithRole(plan, ModRoles.FITTER).isEmpty()) {
                return;   // 钳工已经在场（原生绑定生效），什么都不用做
            }
            Player chosen = pickCivilianToConvert(plan);
            if (chosen == null) {
                // 极端情况：一个平民都没有。绝不改杀手阵营、也绝不取消更好的悍匪
                CheeseRolePack.LOGGER.warn(
                        "[生成规则] 本局有更好的悍匪但没有任何可改派的平民，放弃补钳工（更好的悍匪保留，本局没有反制角色）");
                return;
            }
            CheeseRolePack.LOGGER.warn("[生成规则] 更好的悍匪在场但缺少钳工，把 {}（{}）改派为钳工",
                    chosen.getName().getString(), String.valueOf(plan.get(chosen).identifier()));
            plan.put(chosen, ModRoles.FITTER);
        }
    }

    /**
     * 挑一个玩家改派成钳工，优先级从高到低：
     * <ol>
     *   <li><b>普通平民</b>（{@link TMMRoles#CIVILIAN} / {@link TMMRoles#DISCOVERY_CIVILIAN}）—— 首选，
     *       不影响任何机制；</li>
     *   <li><b>其它平民阵营角色</b>（非杀手阵营、非中立、非义警）—— 次选，会打乱一点本局配置；</li>
     *   <li>都没有 → 返回 {@code null}（<b>绝不</b>挑杀手阵营 / 中立角色）。</li>
     * </ol>
     * 已死亡 / 旁观的玩家一律跳过。
     */
    public static @Nullable Player pickCivilianToConvert(@NotNull Map<Player, SRERole> plan) {
        // 第一优先：普通平民
        for (Map.Entry<Player, SRERole> entry : plan.entrySet()) {
            if (!isEligibleTarget(entry.getKey())) {
                continue;
            }
            SRERole role = entry.getValue();
            if (role == TMMRoles.CIVILIAN || role == TMMRoles.DISCOVERY_CIVILIAN) {
                return entry.getKey();
            }
        }
        // 第二优先：其它平民阵营角色（非杀手 / 非中立 / 非义警）
        for (Map.Entry<Player, SRERole> entry : plan.entrySet()) {
            if (!isEligibleTarget(entry.getKey())) {
                continue;
            }
            SRERole role = entry.getValue();
            if (role == null || role == ModRoles.FITTER || role == ModRoles.GANGSTERS) {
                continue;
            }
            if (isPlainInnocent(role)) {
                CheeseRolePack.LOGGER.warn("[生成规则] 没有普通平民可改，退而改派平民阵营角色 {}", role.identifier());
                return entry.getKey();
            }
        }
        return null;
    }

    /** 该玩家能否作为改派目标（活着、非旁观）。 */
    private static boolean isEligibleTarget(@Nullable Player player) {
        return player != null && !player.isSpectator() && player.isAlive();
    }

    /** 是不是「非杀手阵营、非中立、非义警」的普通平民阵营角色。 */
    private static boolean isPlainInnocent(@Nullable SRERole role) {
        if (role == null) {
            return false;
        }
        try {
            if (SRERoleWorldComponent.isKillerTeamRoleStatic(role)) {
                return false;
            }
            return !role.isNeutrals() && !role.isVigilanteTeam();
        } catch (Throwable ignored) {
            // 拿不到阵营信息时宁可不动
            return false;
        }
    }

    /** 计划里所有拥有指定职业的玩家（快照，可安全遍历 / 修改计划）。 */
    private static List<Player> playersWithRole(@NotNull Map<Player, SRERole> plan, @Nullable SRERole role) {
        List<Player> result = new ArrayList<>();
        if (role == null) {
            return result;
        }
        for (Map.Entry<Player, SRERole> entry : plan.entrySet()) {
            if (entry.getValue() == role) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
