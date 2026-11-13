# runtime_mods/

这个目录**平时是空的**，`gradle build` 完全不需要它。

## 用途

只在你想要跑 `gradlew runClient`（开发环境里启动客户端）时才需要往这里放 jar。

`build.gradle` 里对应的那行是：

```groovy
modRuntimeOnly fileTree(dir: 'runtime_mods', include: '*.jar')
```

## 放什么

StarRailExpress 4.3.0 运行所需的整套前置，直接从你日常游戏的 `mods/` 目录拷过来即可：

- **wathe**（必须是**完整的**模组 jar，不是 `libs/wathe-1.3.2.jar` 那个只有接口的空壳）
- cloth-config
- modmenu
- simple-voice-chat
- architectury
- exposure（+ exposure_polaroid）
- ratatouille
- veil（+ molang-compiler、glsl-processor）
- midnightlib
- ForgeConfigAPIPort
- datasync
- fabric-api（Loom 已提供，通常不用放）

## 不需要放的

- `star_rail_express-4.3.0.jar`、`sre-lib-1.0.3.jar` —— 已在 `libs/` 里，由 `modImplementation` 提供
- **blindness（失明症）** —— 只服务于特定角色，且硬依赖 `kirin` + `owo-lib` + `player-animator`，本附加模组用不到
- presencefootsteps —— 硬依赖 `kirin`
- sodium / iris —— StarRailExpress 4.3.0 声明了 `breaks: sodium < 0.8.2`，放了会拒绝加载

## 注意

`libs/wathe-1.3.2.jar` 是**只保留部分接口的空壳**，仅用于编译。
SRE 发行版运行期的 `wathe_blocker` mixin 需要真实 Wathe 的类，所以 `runClient` 必须用真实 Wathe 覆盖它
（或把真实 wathe 放进本目录后，临时注释掉 `libs/wathe-1.3.2.jar` 那一行——否则两个同名模组会冲突）。
