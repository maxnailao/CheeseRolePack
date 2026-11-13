package com.cheese.rolepack;

import com.cheese.rolepack.role.GreenRole;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CheeseRolePack 主入口。
 *
 * <p>本模组是 StarRailExpress 的附加职业包，通过其公开 API 注册职业、技能与商店条目。</p>
 *
 * <p>目前内容：格林（{@code cheeserolepack:green}，平民阵营）。
 * 注册点见 {@link GreenRole#register()}。</p>
 */
public class CheeseRolePack implements ModInitializer {

    public static final String MOD_ID = "cheeserolepack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 冒烟检查：确认能正常引用 StarRailExpress 的类（依赖接通）
        LOGGER.info("CheeseRolePack 初始化中，StarRailExpress 主类 = {}", io.wifi.starrailexpress.SRE.class.getName());

        // 职业 / 技能 / 商店 / 空手左键事件
        GreenRole.register();

        LOGGER.info("CheeseRolePack 初始化完成，职业 id = {}", GreenRole.ID);
    }
}
