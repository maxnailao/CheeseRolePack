package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.item.Rpg7AmmoItem;
import com.cheese.rolepack.content.item.Rpg7Item;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 本模组的物品注册（移植自 Ricord / noellesroles 的 {@code ModItems}，只取 RPG-7 相关的两个）。
 *
 * <p>注册 id：{@code cheeserolepack:rpg7}、{@code cheeserolepack:rpg7_ammo}；
 * 对应的模型是 {@code assets/cheeserolepack/models/item/rpg7.json} 与 {@code rpg7_ammo.json}，
 * 装填状态通过 {@code custom_model_data=1} 切到 {@code rpg7_loaded.json}。</p>
 */
public final class ModItems {

    /**
     * RPG-7 火箭筒
     * - 右键发射火箭弹，左键装填（最高 1 发，自动消耗背包中的火箭弹）
     * - 初始无弹药，装填后模型显示炮弹部分
     */
    public static final Item RPG7 = register("rpg7",
            new Rpg7Item(new Item.Properties().stacksTo(1)));

    /**
     * RPG-7 火箭弹
     * - 用于装填 RPG-7
     */
    public static final Item RPG7_AMMO = register("rpg7_ammo",
            new Rpg7AmmoItem(new Item.Properties()));

    private ModItems() {
    }

    private static Item register(String path, Item item) {
        ResourceLocation id = CheeseRolePack.id(path);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    /** 空方法：仅用于确保本类被类加载，从而执行上面的静态注册。 */
    public static void register() {
        // 触发类加载
    }
}
