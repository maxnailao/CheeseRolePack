package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 本模组的创造模式物品栏分页。
 *
 * <p>注册 id：{@code cheeserolepack:cheese_role}，标题翻译键
 * {@code itemGroup.cheeserolepack.cheese_role}（zh_cn / en_us 都是「CheeseRole」），
 * 图标使用 RPG-7 火箭筒。目前分页里只有 RPG-7 与它的火箭弹。</p>
 */
public final class ModCreativeTabs {

    /** 分页 id 路径（完整 id = {@code cheeserolepack:cheese_role}）。 */
    public static final String TAB_PATH = "cheese_role";

    /** 分页标题翻译键。 */
    public static final String TITLE_KEY = "itemGroup.cheeserolepack.cheese_role";

    /** 创造模式分页：图标 = RPG-7。 */
    public static final CreativeModeTab CHEESE_ROLE = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            CheeseRolePack.id(TAB_PATH),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable(TITLE_KEY))
                    .icon(() -> new ItemStack(ModItems.RPG7))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.RPG7);
                        output.accept(ModItems.RPG7_AMMO);
                    })
                    .build());

    private ModCreativeTabs() {
    }

    /** 空方法：仅用于确保本类被类加载，从而执行上面的静态注册。 */
    public static void register() {
        // 触发类加载
    }
}
