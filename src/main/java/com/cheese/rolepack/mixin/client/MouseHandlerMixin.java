package com.cheese.rolepack.mixin.client;

import com.cheese.rolepack.content.item.Rpg7Item;
import com.cheese.rolepack.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端鼠标按下钩子：手持 RPG-7 时，左键改为「装填」而不是普通攻击。
 *
 * <p>原实现（Ricord / noellesroles）把这段逻辑写在 StarRailExpress 自己的
 * {@code io.wifi.starrailexpress.mixin.input.MouseHandlerMixin#onPress} 里；
 * 本附加模组无法修改 StarRailExpress，所以改成自己的客户端 mixin，
 * 注入目标与触发条件完全一致：</p>
 * <ul>
 *   <li>只处理左键按下（{@code GLFW_MOUSE_BUTTON_LEFT} + {@code GLFW_PRESS}）；</li>
 *   <li>没有打开任何界面（{@code client.screen == null}）；</li>
 *   <li>主手是 {@code cheeserolepack:rpg7} 时调用 {@link Rpg7Item#tryReloadFromClient}，
 *       并取消这次左键（不触发近战攻击）。</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void cheeserolepack$rpg7Reload(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        // 如果当前有打开的 GUI（如背包 / 商店），不要拦截鼠标事件
        if (client.screen != null) {
            return;
        }

        LocalPlayer player = client.player;
        ItemStack mainHandStack = player.getMainHandItem();

        // 处理 RPG-7 左键装填
        if (mainHandStack.is(ModItems.RPG7)) {
            Rpg7Item.tryReloadFromClient(player);
            ci.cancel();
        }
    }
}
