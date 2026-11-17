package com.cheese.rolepack.init;

import com.cheese.rolepack.CheeseRolePack;
import com.cheese.rolepack.content.item.AntiPersonnelMineItem;
import com.cheese.rolepack.content.item.ClaymoreMineItem;
import com.cheese.rolepack.content.item.Rpg7AmmoItem;
import com.cheese.rolepack.content.item.Rpg7Item;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 本模组的物品注册（移植自 Ricord / noellesroles 的 {@code ModItems}：
 * RPG-7 三件套 + 阔剑地雷 + 反人员地雷）。
 *
 * <p>注册 id：{@code cheeserolepack:rpg7}、{@code cheeserolepack:rpg7_ammo}、
 * {@code cheeserolepack:claymore_mine}、{@code cheeserolepack:anti_personnel_mine}；
 * RPG-7 对应的模型是 {@code assets/cheeserolepack/models/item/rpg7.json} 与 {@code rpg7_ammo.json}，
 * 装填状态通过 {@code custom_model_data=1} 切到 {@code rpg7_loaded.json}；
 * 两颗地雷分别是 {@code models/item/claymore_mine.json} 与 {@code anti_personnel_mine.json}。</p>
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

    /**
     * 阔剑地雷
     * - 长按右键 3 秒在脚下布设一颗未布防的地雷，布设后 2 秒冷却
     * - 空手右键地雷布防：布防后画出两条激光线，玩家踩进正面 0.8 格即引爆
     */
    public static final Item CLAYMORE_MINE = register("claymore_mine",
            new ClaymoreMineItem(new Item.Properties().stacksTo(16)));

    /**
     * 反人员地雷（移植自 RKD 的 {@code noellesroles:landmine}）
     * - 长按右键 3 秒在脚下布设，布设即消耗
     * - 布设者离开布设位置 3 秒后<b>自动布防</b>（不需要右键）
     * - 待发后玩家踩上地雷、再离开踩踏位置即引爆（球形 2.5 格）
     */
    public static final Item ANTI_PERSONNEL_MINE = register("anti_personnel_mine",
            new AntiPersonnelMineItem(new Item.Properties().stacksTo(16)));

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
