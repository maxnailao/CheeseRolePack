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

    /** RPG-7 发射音效的可听半径（格）—— 必须声明在 {@link #RPG7_SHOOT} 之前。 */
    public static final float RPG7_SHOOT_RANGE = 512.0F;

    /**
     * RPG-7 发射音效（由服务端在射击时播放）。
     *
     * <p><b>全场可闻</b>：用 {@link SoundEvent#createFixedRangeEvent} 把可听半径固定为
     * {@value #RPG7_SHOOT_RANGE} 格（狙击枪那种「整个地图都听到」的效果），
     * 而不是按音量推算出来的十几格。</p>
     */
    public static final SoundEvent RPG7_SHOOT = registerFixed("rpg7_shoot", RPG7_SHOOT_RANGE);

    /**
     * 更好的悍匪「疯狂模式」专属背景音乐（客户端本地播放，流式加载）。
     *
     * <p>对应 {@code assets/cheeserolepack/sounds/music/gangster_psycho.ogg}
     * 与 {@code sounds.json} 里的 {@code "music_gangster_psycho"}。</p>
     */
    public static final SoundEvent GANGSTER_PSYCHO = register("music_gangster_psycho");

    private ModSounds() {
    }

    /** 注册一个「可听半径固定」的音效（用于狙击枪式的全场音效）。 */
    private static SoundEvent registerFixed(String path, float range) {
        ResourceLocation id = CheeseRolePack.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createFixedRangeEvent(id, range));
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
