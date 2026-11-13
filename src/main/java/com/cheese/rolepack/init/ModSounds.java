package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 本模组的音效注册。
 *
 * <p>目前只有 RPG-7 的发射音效：{@code cheeserolepack:rpg7_shoot}，
 * 对应 {@code assets/cheeserolepack/sounds/rpg7_shoot.ogg} 与
 * {@code assets/cheeserolepack/sounds.json} 中的 {@code "rpg7_shoot"} 条目
 * （sounds.json 的键是相对所属命名空间的路径，命名空间由文件所在目录决定，
 * 所以这里写 {@code rpg7_shoot} 而不是 {@code noellesroles.rpg7_shoot} 那样的冗余前缀）。</p>
 */
public final class ModSounds {

    /** RPG-7 发射音效（由服务端在射击时播放）。 */
    public static final SoundEvent RPG7_SHOOT = register("rpg7_shoot");

    private ModSounds() {
    }

    private static SoundEvent register(String path) {
        ResourceLocation id = CheeseRolePack.id(path);
        // createVariableRangeEvent：声音传播距离交给服务端的 playSound 决定，
        // 与原始 mod（noellesroles.rpg7_shoot）里的注册方式一致。
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** 空方法：仅用于确保本类被类加载，从而执行上面的静态注册。 */
    public static void register() {
        // 触发类加载
    }
}
