package com.cheese.rolepack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CheeseRolePack 的可调配置（JSON，位于 {@code config/cheeserolepack.json}）。
 *
 * <p>目前只有一项：空手左键的<b>额外击退强度</b>。</p>
 *
 * <p><b>为什么是「额外」而不是「总距离」</b>：格林打人走
 * {@code victim.hurt(...)}，其中 0.4F 的击退是原版在 {@code LivingEntity#hurt} 内部产生的，
 * 外部无法单独调小或替换。所以这里的值表示「在原版击退之上再加多少推力」：
 * <ul>
 *   <li>{@code 0.0}（默认）—— 与原版空手打人完全一致</li>
 *   <li>{@code 0.4} —— 大约相当于原版推力的两倍</li>
 * </ul>
 * </p>
 *
 * <p>两种改法：</p>
 * <ul>
 *   <li>游戏内命令：{@code /cheeserolepack knockback 0.6}（需要 OP / 权限等级 2），立即生效并存盘</li>
 *   <li>直接编辑 {@code config/cheeserolepack.json} 后重开游戏</li>
 * </ul>
 */
public final class GreenConfig {

    private GreenConfig() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String KEY_PUNCH_EXTRA_KNOCKBACK = "punchExtraKnockback";

    /** 额外击退强度上限，防止手滑填个 1000 把人推出地图 */
    private static final double MAX_EXTRA_KNOCKBACK = 10.0D;

    /** 在原版近战击退（0.4F）之上额外附加的推力，默认 0 = 纯原版。 */
    private static volatile double punchExtraKnockback = 0.0D;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cheeserolepack.json");
    }

    /** 当前配置的额外击退强度。 */
    public static double punchExtraKnockback() {
        return punchExtraKnockback;
    }

    /** 设置额外击退强度（自动夹到 [0, 10] 并存盘）。 */
    public static void setPunchExtraKnockback(double value) {
        punchExtraKnockback = clamp(value);
        save();
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(MAX_EXTRA_KNOCKBACK, value));
    }

    /** 读取配置文件；文件不存在时按默认值写一份出来，方便玩家直接改。 */
    public static void load() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                save();
                return;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null && obj.has(KEY_PUNCH_EXTRA_KNOCKBACK)) {
                punchExtraKnockback = clamp(obj.get(KEY_PUNCH_EXTRA_KNOCKBACK).getAsDouble());
            }
        } catch (Exception e) {
            // 配置损坏时不要影响启动：退回默认值
            punchExtraKnockback = 0.0D;
        }
    }

    /** 写回配置文件。 */
    public static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty(KEY_PUNCH_EXTRA_KNOCKBACK, punchExtraKnockback);
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 写盘失败不影响运行（值仍在内存里生效）
        }
    }

    /** 注册 {@code /cheeserolepack knockback <值>} 命令，便于游戏内即时调整。 */
    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher
                .register(Commands.literal("cheeserolepack")
                        .then(Commands.literal("knockback")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "[CheeseRolePack] 当前额外击退强度 = " + punchExtraKnockback), false);
                                    return 1;
                                })
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, MAX_EXTRA_KNOCKBACK))
                                        .executes(context -> {
                                            double value = DoubleArgumentType.getDouble(context, "value");
                                            setPunchExtraKnockback(value);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "[CheeseRolePack] 额外击退强度已设为 " + punchExtraKnockback
                                                            + "（原版击退 0.4 + 该值，已存盘）"),
                                                    true);
                                            return 1;
                                        })))));
    }
}
