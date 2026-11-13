package com.cheese.rolepack;

import com.cheese.rolepack.init.ModCreativeTabs;
import com.cheese.rolepack.init.ModEntities;
import com.cheese.rolepack.init.ModItems;
import com.cheese.rolepack.init.ModPackets;
import com.cheese.rolepack.init.ModSounds;
import com.cheese.rolepack.role.GreenRole;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CheeseRolePack 主入口。
 *
 * <p>本模组是 StarRailExpress 的附加职业包，通过其公开 API 注册职业、技能与商店条目。</p>
 *
 * <p>目前内容：</p>
 * <ul>
 *   <li>格林（{@code cheeserolepack:green}，平民阵营）。注册点见 {@link GreenRole#register()}；</li>
 *   <li>RPG-7 武器系统（移植自 Ricord / noellesroles）：物品 {@link ModItems}、
 *       抛射物 {@link ModEntities}、音效 {@link ModSounds}、网络包 {@link ModPackets}，
 *       创造模式分页 {@code cheeserolepack:cheese_role} 见 {@link ModCreativeTabs}。</li>
 * </ul>
 */
public class CheeseRolePack implements ModInitializer {

    public static final String MOD_ID = "cheeserolepack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 生成本模组命名空间下的 {@link ResourceLocation}（{@code cheeserolepack:<path>}）。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // 冒烟检查：确认能正常引用 StarRailExpress 的类（依赖接通）
        LOGGER.info("CheeseRolePack 初始化中，StarRailExpress 主类 = {}", io.wifi.starrailexpress.SRE.class.getName());

        // 职业 / 技能 / 商店 / 空手左键事件
        GreenRole.register();

        // ———— RPG-7 武器系统 ————
        // 顺序要求：音效 → 物品 → 实体 → 网络包 → 创造分页（分页图标要取用已注册的物品）
        ModSounds.register();
        ModItems.register();
        ModEntities.register();
        ModPackets.register();
        ModCreativeTabs.register();

        // 可调配置（config/cheeserolepack.json）+ 游戏内调整命令 /cheeserolepack knockback <值>
        com.cheese.rolepack.config.GreenConfig.load();
        com.cheese.rolepack.config.GreenConfig.registerCommand();

        LOGGER.info("CheeseRolePack 初始化完成，职业 id = {}，创造分页 id = {}", GreenRole.ID, ModCreativeTabs.TAB_PATH);
    }
}
