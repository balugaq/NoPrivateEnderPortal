# AGENTS.md — NoPrivateEnderPortal

给在此仓库工作的 AI 智能体 / 协作者看的契约。人类维护者也建议一读。

## TL;DR（红线）

1. **绝不在 `BlockPlaceEvent` / `BlockMultiPlaceEvent` 上取消事件来拦末地激活。**
   那正是 PaperMC#13586 / #13536 的复制触发路径。拦截只能在
   `PlayerInteractEvent`（最前置）或 `EntityChangeBlockEvent`（兜底）。
2. **改了拦截逻辑，必须跑测试**（`npe` 的核心价值就是避开 dupe，回归不能丢）。
3. **改 `version` 即发版**：`build.gradle.kts` 顶部的 `version = "x.y.z"` 一变，
   推到 `master` 会自动打 tag + GitHub Release。不要随便 bump。
4. **`paper-api` / `mockbukkit` / `run-paper` 不要靠 Dependabot 自动升**——
   `dependabot.yml` 已屏蔽，二者绑定 Minecraft 服务端版本，需人工对齐。
5. **语言文件 `lang/*.yml` 用户优先、不写回磁盘**；新增/改键要同步 `zh_CN` 与 `en_US`。

## 项目定位

Paper 服务端插件：阻止非豁免玩家用末影之眼在末地传送门框上私建末地传送门。
- 运行目标：**Paper 1.17+**，Java **17+**（产物 `release 17`）。
- 构建需要 **JDK 21+**（MockBukkit v1.21 的要求）。
- 用 Gradle **9.8.1**（wrapper 已锁定，不要手改 `gradle-wrapper.properties` 的发行版）。

## 包地图

```
src/main/java/com/balugaq/noprivateenderportal/
├── NoPrivateEnderPortal.java       主类：onEnable/onDisable、reload、isExempt、notifyDenied、forgetThrottle
├── config/PluginConfig.java        config.yml 的类型化视图（集中定义所有键与默认值）
├── core/
│   ├── ExemptionManager.java       赦免判定（OP / 名单 / bypass 权限），判定核心是可单测的纯函数
│   └── MessageThrottler.java      每个玩家的提示消息冷却
├── listener/
│   └── EnderPortalListener.java   拦截逻辑：PlayerInteractEvent 主闸 + EntityChangeBlockEvent 兜底 + 退出清理
├── lang/
│   ├── Lang.java                  多语言静态门面（加载、sendMessage、t）
│   └── LangRegistry.java          注册表：键解析、占位符 {n}、用户文件优先、缺键告警一次
├── util/Colors.java               & 色码 → § 色码
└── command/ReloadCommand.java     /npe reload

src/main/resources/
├── plugin.yml                     name/version 由 processResources 的 ${name}/${version} 展开
├── config.yml                     默认配置（首次启动写入数据目录）
└── lang/zh_CN.yml, lang/en_US.yml 内置语言文件
```

## 构建命令

```bash
./gradlew clean build      # 编译（主+测试）+ 跑测试 + 打 shadowJar，产物 build/libs/*.jar
./gradlew test             # 只跑测试
./gradlew runServer        # 拉起 1.21.11 测试服务端（需网络下载服务端，带 JPDA 调试端口 5005）
```

- 测试报告：`build/reports/tests/test/index.html`
- 测试明细（含 skipped）：`build/test-results/test/*.xml`

## 测试约定

- **纯 JUnit**（不依赖服务器）：`LangRegistry`、`ExemptionManager`、`MessageThrottler`、`Colors`。
  `ExemptionManager.isExempt(boolean, boolean, String, UUID, boolean)` 是纯函数，单测直接覆盖
  四条赦免路径 + 大小写/UUID 解析。
- **MockBukkit 集成**（`NoPrivateEnderPortalTest`）：加载插件后手动构造事件驱动监听器，覆盖
  放行/拦截各分支。**注意**：MockBukkit 不实现 `EnderEyeItem` 等服务端逻辑，无法真正「右键激活
  传送门」，所以测试是「事件来了以后监听器怎么做」，不是端到端。
- MockBukkit v1.21 用 `PlayerMock.nextComponentMessage()` 读消息队列；`addAttachment` 不在
  `PlayerMock` 上，bypass 权限走纯单测覆盖（不要在集成测试里依赖 MockBukkit 的权限系统）。
- 插件类**不能是 `final`**（MockBukkit 用 ByteBuddy 生成子类代理来加载插件）。

## 发版流程

1. 改 `build.gradle.kts` 的 `version = "x.y.z"`。
2. 提交并推到 `master`。
3. `release.yml` 检测到 tag `v<x.y.z>` 不存在 → `./gradlew clean build` → 用 `softprops/action-gh-release`
   打 Release 并附带 `build/libs/*.jar`。
4. 若只是改了 `build.gradle.kts` 但 `version` 没变 → tag 已存在 → 自动跳过，不会重复发版。

> 不要用 `gradle.properties` 存版本——本仓库的版本只写在 `build.gradle.kts`，`release.yml` 的提取
> 逻辑也只认那里。

## 依赖管理（libs.versions.toml）

- `paper` = 1.17.1（**compileOnly**，主代码只对此 API 编译，保证 1.17 兼容）。
- `paper-test` = 1.21.11（**testOnly**，与 MockBukkit v1.21 对齐，不进产物）。
- `mockbukkit` = 4.116.3（MockBukkit v1.21）。
- `junit` = 6.0.3，`shadow` = 9.6.1，`run-paper` = 3.1.0。
- 升级 `paper` / `mockbukkit` / `run-paper` 必须人工确认与对应 Minecraft 版本匹配后再改。

## 不要做

- 不要为了「彻底拦截」去取消 `BlockMultiPlaceEvent` / `BlockPlaceEvent`（见红线 1）。
- 不要把插件类标 `final`。
- 不要手改 `gradle/wrapper/gradle-wrapper.properties` 里的发行版 URL（保持 9.8.1）。
- 不要依赖 `System.out` 给玩家发消息——走 `Lang.sendMessage`。
- 不要在 `lang/` 里加未在中英两份都同步的键。
