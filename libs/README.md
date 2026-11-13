# libs/ —— 本地依赖 jar（不入库）

本目录的 jar **不纳入版本控制**（它们都是第三方的 StarRailExpress 相关产物，体积也大）。
clone 下来后需要自行放入以下 4 个文件：

| 文件名 | 用途 | 从哪来 |
|---|---|---|
| `star_rail_express-4.3.0.jar` | SRE 发行版，**编译**用 | SRE 的 `build/libs/` 或发布包 |
| `star_rail_express-4.3.0-dev.jar` | SRE dev 构建，**仅 dev 运行**用 | SRE 的 `build/devlibs/`（由源码 `gradlew build` 产出） |
| `sre-lib-1.0.3.jar` | 资源库模组，mod id = `sre_resource` | SRE 的发布包 |
| `wathe-1.3.2.jar` | Wathe 空壳 jar，仅编译 `ShopEntry` 需要 | SRE 的 `libs/` |

## 为什么编译用发行版、运行用 dev 版

- **发行版**（`build/libs`）里的 mixin 注解是 intermediary 名（如 `method_5652`），且**不带 refmap**
  （现代 Loom 直接把注解字符串改写掉）。把它放进 dev 环境跑，SRE 自己的 mixin 会对不上具名方法，
  抛 `InvalidInjectionException`。
- **dev 版**（`build/devlibs`）里的注解是具名方法（如 `addAdditionalSaveData`），正好匹配 dev 环境。

所以 `build.gradle` 里：

```groovy
modCompileOnly files("libs/star_rail_express-4.3.0.jar")     // 编译：发行版
runtimeOnly   files("libs/star_rail_express-4.3.0-dev.jar")  // 运行：dev 版
```

两者不能同时进入运行时类路径（同一 mod id 会重复加载）。

> 两份 jar 必须**同源**（同一个 SRE 提交构建出来的）。版本不一致会出现
> `NoSuchFieldError` / `NoClassDefFoundError`（例如旧 dev jar 缺 `SimpleRoleData`）。
