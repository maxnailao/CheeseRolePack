package com.cheese.rolepack.client;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.init.ModSounds;
import com.cheese.rolepack.role.BetterGangsterRole;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * 更好的悍匪「疯狂模式」专属背景音乐。
 *
 * <p><b>为什么不用资源包覆盖</b>：SRE 的疯狂模式背景音来自 ratatouille 的背景氛围系统
 * （{@code SREClient} 里注册的 {@code BackgroundAmbience(TMMSounds.AMBIENT_PSYCHO_DRONE, ...)}），
 * 音效 id 固定为 {@code wathe:ambient.psycho_drone}。Fabric 对<b>多个 mod 的同名资源</b>
 * 取加载顺序靠前的那份（wathe 先加载），所以在自己 jar 里放同路径 ogg 覆盖不生效。</p>
 *
 * <p><b>做法</b>：每客户端 tick 检查「本地玩家是更好的悍匪 + 处于疯狂模式」，
 * 满足时播放 {@link ModSounds#GANGSTER_PSYCHO}，并通过 {@code RatatouillePsychoDroneMixin}
 * 把原版 drone 的 {@code SoundManager#play(...)} 拦掉，避免两首叠加。</p>
 *
 * <p><b>判定容错</b>：疯狂状态同时看 {@code SREPlayerPsychoComponent#psychoTicks} 和
 * {@code SREClient#localPlayerPsychoActive}（SRE 自己缓存的标志）—— 任一说"在疯狂"就算。</p>
 */
public final class GangsterPsychoMusic {

    /**
     * 原版疯狂模式 drone 的<b>音效路径</b>（不含命名空间）。
     *
     * <p>注意：音效 id 是 {@code starrailexpress:ambient.psycho_drone}
     * （{@code TMMSounds} 用 {@code SRE.MOD_ID} 命名空间注册），
     * 而它指向的<b>文件</b>才是 {@code assets/wathe/sounds/ambient/psycho_drone.ogg}。
     * 所以这里按 path 匹配，命名空间不写死。</p>
     */
    public static final String VANILLA_PSYCHO_DRONE_PATH = "ambient.psycho_drone";

    /** 可能出现的 drone 音效 id（两个命名空间都试一遍，防止版本差异）。 */
    private static final ResourceLocation[] DRONE_IDS = {
            ResourceLocation.fromNamespaceAndPath("starrailexpress", VANILLA_PSYCHO_DRONE_PATH),
            ResourceLocation.fromNamespaceAndPath("wathe", VANILLA_PSYCHO_DRONE_PATH),
    };

    /** 主动静音 drone 的节流计数。 */
    private static int silenceTicks;

    /** 正在播放的实例（null = 没在播）。 */
    private static SoundInstance playing;

    /** 诊断用：上一 tick 的 active 值，只在变化时打日志。 */
    private static Boolean lastActive;

    /** 诊断用：启动后检查声音是否真的加载成功。 */
    private static int verifyTicks;

    private GangsterPsychoMusic() {
    }

    /** 客户端每 tick 调用。 */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            stop(client);
            return;
        }

        boolean active = isActive(client);
        if (lastActive == null || lastActive != active) {
            lastActive = active;
            CheeseRolePack.LOGGER.info(
                    "[疯狂音乐] 状态变化 active={} | 职业={} | psychoTicks={} | localPsychoActive={}",
                    active, roleIdOf(client), psychoTicksOf(client), safeLocalPsycho());
        }

        if (active) {
            // 双保险：即使 mixin 没拦住（版本差异），也主动把原版 drone 静音。
            // 每 10 tick 扫一遍所有音源，防止氛围系统把它重新拉起来。
            if (silenceTicks-- <= 0) {
                silenceTicks = 10;
                silenceVanillaDrone(client);
            }
            if (playing == null) {
                start(client);
            } else if (verifyTicks > 0) {
                // 播放后等 2 tick，确认音频引擎真的接住了这个声音（音效缺失时会失败）
                if (--verifyTicks == 0) {
                    boolean loaded;
                    try {
                        loaded = client.getSoundManager().isActive(playing);
                    } catch (Throwable t) {
                        loaded = false;
                    }
                    CheeseRolePack.LOGGER.info("[疯狂音乐] 播放校验 isActive={}（false 说明音效没加载成功）", loaded);
                }
            }
        } else {
            stop(client);
        }
    }

    /** 本地玩家此刻是否应该播放专属音乐。 */
    public static boolean isActive(Minecraft client) {
        if (client == null || client.player == null || SREClient.gameComponent == null) {
            return false;
        }
        try {
            if (!isBetterGangster(client)) {
                return false;
            }
            if (psychoTicksOf(client) > 0) {
                return true;
            }
            return safeLocalPsycho();
        } catch (Throwable t) {
            CheeseRolePack.LOGGER.warn("[疯狂音乐] 判定异常", t);
            return false;
        }
    }

    /** 本地玩家是不是更好的悍匪（角色 id 或路径任一匹配都算）。 */
    private static boolean isBetterGangster(Minecraft client) {
        try {
            var role = SREClient.gameComponent.getRole(client.player);
            if (role == null || role.identifier() == null) {
                return false;
            }
            if (BetterGangsterRole.ID.equals(role.identifier())) {
                return true;
            }
            return "better_gangster".equals(role.identifier().getPath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int psychoTicksOf(Minecraft client) {
        try {
            return SREPlayerPsychoComponent.KEY.get(client.player).psychoTicks;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean safeLocalPsycho() {
        try {
            return SREClient.localPlayerPsychoActive;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String roleIdOf(Minecraft client) {
        try {
            var role = SREClient.gameComponent.getRole(client.player);
            return role == null ? "null" : String.valueOf(role.identifier());
        } catch (Throwable t) {
            return "err";
        }
    }

    /**
     * 由 mixin 调用：判断这条背景音是否该被我们顶掉。
     *
     * <p>只拦 {@code wathe:ambient.psycho_drone}，且仅在我们自己进入疯狂模式时拦截，
     * 所以别的职业、别的背景音完全不受影响。</p>
     */
    public static boolean shouldSuppressVanillaDrone(SoundInstance instance) {
        if (instance == null || instance.getLocation() == null) {
            return false;
        }
        ResourceLocation loc = instance.getLocation();
        if (loc == null || !VANILLA_PSYCHO_DRONE_PATH.equals(loc.getPath())) {
            return false;
        }
        return isActive(Minecraft.getInstance());
    }

    private static void start(Minecraft client) {
        try {
            // 用 MASTER 音源 + 非定位 + 循环：
            //  - MASTER 保证「只要有任何声音能听见，这首就能听见」（MUSIC 滑块为 0 时 MUSIC 音源会静音）；
            //  - relative=true + Attenuation.NONE = 与摄像机位置无关的 2D 音乐；
            //  - looping=true：疯狂模式持续期间一直循环。
            playing = new SimpleSoundInstance(
                    ModSounds.GANGSTER_PSYCHO.getLocation(),
                    net.minecraft.sounds.SoundSource.MASTER,
                    1.0F, 1.0F,
                    SoundInstance.createUnseededRandom(),
                    true, 0,
                    SoundInstance.Attenuation.NONE,
                    0.0D, 0.0D, 0.0D,
                    true);
            client.getSoundManager().play(playing);
            verifyTicks = 3;
            float masterVol = 1.0F;
            float musicVol = 1.0F;
            try {
                masterVol = client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
                musicVol = client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MUSIC);
            } catch (Throwable ignored) {
            }
            CheeseRolePack.LOGGER.info("[疯狂音乐] 已开始播放 {} | 主音量={} 音乐音量={}",
                    ModSounds.GANGSTER_PSYCHO.getLocation(), masterVol, musicVol);
        } catch (Throwable t) {
            playing = null;
            verifyTicks = 0;
            CheeseRolePack.LOGGER.warn("[疯狂音乐] 播放失败", t);
        }
    }

    private static void stop(Minecraft client) {
        if (playing == null) {
            return;
        }
        try {
            if (client != null) {
                client.getSoundManager().stop(playing);
                CheeseRolePack.LOGGER.info("[疯狂音乐] 已停止");
            }
        } catch (Throwable ignored) {
            // 声音已被回收，忽略
        }
        playing = null;
        verifyTicks = 0;
    }

    /** 把原版疯狂模式 drone 在所有音源上强行停掉。 */
    private static void silenceVanillaDrone(Minecraft client) {
        try {
            var soundManager = client.getSoundManager();
            for (ResourceLocation id : DRONE_IDS) {
                for (var source : net.minecraft.sounds.SoundSource.values()) {
                    try {
                        soundManager.stop(id, source);
                    } catch (Throwable ignored) {
                        // 单个音源失败不影响其它
                    }
                }
            }
        } catch (Throwable ignored) {
            // 声音管理器不可用，忽略
        }
    }
}
