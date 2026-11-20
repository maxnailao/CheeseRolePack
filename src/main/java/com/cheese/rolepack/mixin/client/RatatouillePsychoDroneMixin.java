package com.cheese.rolepack.mixin.client;

import com.cheese.rolepack.client.GangsterPsychoMusic;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦掉「更好的悍匪疯狂模式」期间的原版背景 drone。
 *
 * <p>SRE 在 {@code SREClient} 里通过 ratatouille 的背景氛围系统注册了
 * {@code BackgroundAmbience(wathe:ambient.psycho_drone, ...)}。我们要给这个职业换成
 * 专属音乐，但 ratatouille 的类不在本模组的依赖里，没法直接调用它的 API，
 * 所以在 {@code BackgroundAmbience#tryStarting} 里把
 * {@code SoundManager#play(...)} 重定向：</p>
 * <ul>
 *   <li>如果是原版 psycho drone 且本地玩家正是更好的悍匪的疯狂模式 → <b>丢弃这次播放</b>
 *       （{@link GangsterPsychoMusic} 会同时播放自己的音乐）；</li>
 *   <li>其它情况（别的职业 / 别的背景音）→ 原样播放。</li>
 * </ul>
 *
 * <p>{@code require = 0}：ratatouille 版本不同、方法被改名时不会因为注入失败而崩溃，
 * 只是退回「原版音乐照旧」。</p>
 */
@Mixin(targets = "dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience")
public class RatatouillePsychoDroneMixin {

    @Redirect(method = "tryStarting", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"))
    private void cheeserolepack$replacePsychoDrone(SoundManager soundManager, @Nullable SoundInstance instance) {
        if (GangsterPsychoMusic.shouldSuppressVanillaDrone(instance)) {
            // 我们自己的音乐由 GangsterPsychoMusic 播放，这里直接吞掉原版的
            return;
        }
        soundManager.play(instance);
    }
}
