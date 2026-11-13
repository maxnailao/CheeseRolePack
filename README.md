# CheeseRolePack

StarRailExpress 的附加职业包（Fabric / Minecraft 1.21.1）。

## 依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| StarRailExpress | 4.3.0 | 本体 |
| sre_resource（`sre-lib`） | 1.0.3 | 资源库 |
| Fabric Loader | ≥ 0.19.3 | |
| Fabric API | 0.116.13+1.21.1 | |
| Cardinal Components API | 6.1.1 | SRE 的组件依赖 |

## 构建

```bash
./gradlew build
```

产物：`build/libs/CheeseRolePack-1.0.0.jar`

### `libs/` 里需要放的东西

本工程通过本地 jar 依赖 SRE，需要以下文件（未随仓库提供时请自行获取）：

```
libs/star_rail_express-4.3.0.jar       # SRE 发行版（编译用）
libs/star_rail_express-4.3.0-dev.jar   # SRE dev 构建（仅 dev 运行用，具名映射）
libs/sre-lib-1.0.3.jar                 # mod id = sre_resource
libs/wathe-1.3.2.jar                   # 空壳 jar，仅为编译 ShopEntry
```

> **为什么需要 dev jar**：SRE 发行版 jar 的 mixin 注解是 intermediary 名（如 `method_5652`）
> 且不带 refmap，在 dev 环境（具名映射）里会 `InvalidInjectionException`。
> `gradle.properties` 里发行版做 `modCompileOnly`、dev 版做 `runtimeOnly`，两者不会同时进运行时。

## 开发环境运行

```bash
./gradlew runClient
```

需要 `runtime_mods/` 里有 SRE 的整套运行期前置（cloth-config、modmenu、
simple-voice-chat、architectury、exposure、ratatouille、veil、midnightlib、
ForgeConfigAPIPort、datasync 等），详见 `runtime_mods/README.md`。

## 已实现职业

### 格林（`cheeserolepack:green`）— 平民阵营

> 爱丽丝....在哪里..

| 机制 | 说明 |
|---|---|
| 开局物品 | 书与笔，死亡后掉落 |
| 技能（SAN > 70，全局每局限一次） | 所有玩家回到各自房间；所有「SAN 可被减少」的职业 `-50` SAN。表现与 486 修饰符触发一致（COD 字幕 + 回放事件 + 2 秒时停），文案不同 |
| SAN < 30 空手左键 | 伤害恒为 0（整次取消原版攻击，因此不会产生任何死亡事件），只施加一次原版手感（0.4F）的击退 |
| 商店 | 癫狂 150g：购买后 SAN 直接归零（图标用拳套 `boxing_glove`） |

「SAN 可被减少」的判定为 `role.getMoodType() == SRERole.MoodType.REAL`
—— SRE 的 `SREPlayerMoodComponent` 只对 REAL 心情职业读写真实 SAN。
