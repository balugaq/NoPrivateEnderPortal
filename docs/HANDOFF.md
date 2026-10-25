# 交接文档（HANDOFF）

> NoPrivateEnderPortal —— 阻止非豁免玩家私建末地传送门
> 本文档供接手维护者快速建立完整心智模型，含事件链分析、拦截层取舍、测试与发版流程。

---

## 0. 一句话背景

玩家用末影之眼右键末地传送门框、在最后一块上激活传送门，会被 Paper 在同一右键包内同步触发
一串事件。在 `BlockPlaceEvent` / `BlockMultiPlaceEvent` 上取消来拦截，正是 PaperMC#13586 /
#13536 的**物品复制**触发路径。本插件把拦截点前移，从根本上避免进入那条路径。

- PaperMC#13586：https://github.com/PaperMC/Paper/issues/13586
- PaperMC#13536：https://github.com/PaperMC/Paper/issues/13536

---

## 1. 事件链分析（基于 Paper 1.21.11 字节码核验）

调用链：

```
ServerboundUseItemOnPacket
└─ ServerPlayerGameMode.useItemOn
   ├─ ① PlayerInteractEvent（RIGHT_CLICK_BLOCK）   // 最开头，早于任何块/物品逻辑
   ├─ 块侧 blockState.useItemOn：门框无重写 → 默认 TRY_WITH_EMPTY_HAND → PASS，无事件
   └─ stack.useOn → ItemStack.useOn
      └─ 先开 captureBlockStates，再 item.useOn
         └─ EnderEyeItem.useOn
            ├─ ② EntityChangeBlockEvent   // setBlock 之前；entity=玩家，门框 HAS_EYE false→true
            ├─ shrink(1) 消耗眼睛
            ├─ 找到完整门阵 → 3×3 destroyBlock + setBlock(END_PORTAL) → return SUCCESS
            └─ 关闭捕获，收集 capturedBlockStates（LinkedHashMap，按 setBlock 顺序）
         └─ ③ wasItemInteraction()==true 且 size>1 → BlockMultiPlaceEvent（==1 则 BlockPlaceEvent）
            └─ 不取消 → 逐块 notifyAndUpdatePhysics → ④ BlockPhysicsEvent（仅当有插件监听）
```

关键事实（均用 `javap` 反汇编本机构建 jar `build/libs/...jar` 逐条验证，非推测）：

1. **最后一块门框**触发的是 `BlockMultiPlaceEvent`（**不是** `BlockPlaceEvent`），一次带 **10 个方块**
   （1 门框 + 9 个 3×3 传送门方块）。
2. **非最后一块**只插眼不激活：③ 退化为 `BlockPlaceEvent`（只捕获门框 1 个方块），④ 只 1 次。
3. 全部同步发生在**同一个右键数据包**内，**没有 tick 延迟**。
4. 插眼睛之所以触发放置类事件：`ItemStack.useOn` 在 `item.useOn` 期间打开
   `captureBlockStates`，且 `InteractionResult.SUCCESS` 的 `ItemContext.DEFAULT` 满足
   `wasItemInteraction()==true`，绕过该门禁。
5. `EndPortalFrameBlock` 没有任何 `useItemOn` / `useWithoutItem` 重写——全部逻辑在物品侧
   `EnderEyeItem`（1.21.9 交互重构后的结构）。

---

## 2. 为什么选 `PlayerInteractEvent` 作为主闸

取消点效果对照（写给要改的人）：

| 取消点 | 效果 |
| --- | --- |
| `PlayerInteractEvent` `useInteractedBlock=DENY` + `useItemInHand=DENY` + `setCancelled(true)` | **全止**：后续一切（消耗眼睛 / 门框 HAS_EYE / 3×3 传送门）都不发生 |
| `EntityChangeBlockEvent` 取消 | 眼睛不消耗、门不激活（兜底层；事件在 `setBlock` 之前，方块从未被改） |
| `BlockMultiPlaceEvent` 取消 | 10 个方块整体回滚，但**眼睛已消耗**——会踩 #13586/#13536 的坑 |
| `BlockPhysicsEvent` 取消 | 仅跳过该位置邻接形状更新，不回滚 |

选择理由：

- `PlayerInteractEvent` 在 `ServerPlayerGameMode.useItemOn` **最开头**触发，早于 `captureBlockStates`
  的打开。此时取消，Paper 根本不会进入「捕获 → 回滚」那套逻辑，**完全避开复制路径**。
- `EntityChangeBlockEvent` 作为兜底：拦非右键路径（其他插件 / 命令触发的插眼）。它在 `setBlock`
  之前触发，方块从未被修改，同样没有复制风险。
- 二者都**不取消** `BlockMultiPlaceEvent` / `BlockPlaceEvent`，所以永远不会触发那两个 issue。

> 结论：**想拦末地激活，`PlayerInteractEvent` 是最干净的一道闸。**

---

## 3. 项目结构

见仓库根 `AGENTS.md` 的「包地图」。核心类：

- `NoPrivateEnderPortal`：装配 `EnderPortalListener`、命令、Lang、各 manager。
- `EnderPortalListener`：拦截逻辑本体（主闸 + 兜底 + 退出清理）。
- `core/ExemptionManager`：赦免判定（OP / 名单 / bypass 权限），判定核心是纯函数。
- `core/MessageThrottler`：提示消息冷却。
- `lang/Lang` + `lang/LangRegistry`：多语言（用户文件优先、不写回磁盘）。

---

## 4. 配置项（config.yml）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `language` | `zh_CN` | `lang/<language>.yml`，内置 `zh_CN` / `en_US` |
| `exempt-ops` | `true` | OP 是否豁免 |
| `exempt-players` | `[]` | 游戏名（大小写不敏感）或 UUID |
| `message-cooldown-ms` | `2000` | 提示冷却；≤0 表示不冷却 |

命令：`/npe reload`（`noprivateenderportal.admin`，默认 OP）。
权限：`noprivateenderportal.bypass`（默认 false，持有即放行）。

被拦截提示：`[NoPrivateEnderPortal] 你没有被允许创建末地传送门！`（受冷却控制）。

---

## 5. 测试

```bash
./gradlew test
```

- **纯 JUnit**（无服务器）：`LangRegistryTest`、`ExemptionManagerTest`、`MessageThrottlerTest`、
  `ColorsTest`。`ExemptionManager.isExempt(...)` 是纯函数，单测覆盖四条赦免路径。
- **MockBukkit 集成**（`NoPrivateEnderPortalTest`）：加载插件后构造事件驱动监听器。
  - MockBukkit v1.21 用 `PlayerMock.nextComponentMessage()` 读消息队列。
  - `PlayerMock` 没有 `addAttachment`，bypass 权限由纯单测覆盖，集成测试只用 `setOp` 区分。
  - 插件类**不能 `final`**（MockBukkit 用 ByteBuddy 子类代理加载）。
  - **MockBukkit 不实现服务端逻辑**，无法真正「右键激活传送门」；测试验证的是「事件来了监听器怎么做」。

> 跑完务必看 `build/reports/tests/test/index.html`，确认是 `passed` 而非被静默 `skipped`
> （MockBukkit 对未实现 API 会抛 `AssumptionException` 静默跳过，会伪装成绿色）。

---

## 6. 构建与发版

- 构建：`JDK 21+`，产物 `release 17`，Gradle 9.8.1（wrapper 已锁定）。
  ```bash
  ./gradlew clean build      # build/libs/NoPrivateEnderPortal-<version>.jar
  ```
- CI：`build.yml` 在 push/PR 到 `master` 时编译 + 测试 + 上传产物。
- 发版：**只改 `build.gradle.kts` 顶部的 `version`**，推到 `master` → `release.yml` 自动打
  tag `v<x.y.z>` + GitHub Release（附带 jar）。版本未变则跳过。
- 依赖：`libs.versions.toml` 集中管理。`paper-api`（1.17.1，compileOnly）/ `paper-api` 测试版
  （1.21.11）/ `mockbukkit`（4.116.3）/ `junit`（6.0.3）/ `shadow`（9.6.1）/ `run-paper`（3.1.0）。
  `dependabot.yml` **屏蔽**了 `paper-api` / `mockbukkit` / `run-paper`——它们绑定 Minecraft 版本，
  必须人工对齐。

---

## 7. 已知边界 / 注意事项

1. 创造模式下服务端保存/恢复物品计数，眼睛不真消耗但激活照常（OP / bypass 时）；非豁免创造玩家同样被拦。
2. 若 3×3 区域原本有方块，`destroyBlock` 会触发 Paper 私有 `BlockDestroyEvent`（非标准 Bukkit API），本插件不处理。
3. 与其他在 `PlayerInteractEvent` 上做文章的插件可能有优先级交互；本插件用 `EventPriority.LOWEST` 尽早拦截。
4. 复制类 bug 与本项目无关——本插件从不取消放置类事件。若玩家报告物品复制/丢失，优先排查其他插件。

---

## 8. 接手 checklist

- [ ] 读 `README.md`（用户视角）与 `AGENTS.md`（开发约束）。
- [ ] `./gradlew clean build` 能跑通、33 个测试全绿。
- [ ] 理解「红线 1」：不要去取消放置类事件。
- [ ] 改版本号前确认要发版。
- [ ] 升 `paper-api` / `mockbukkit` 前确认目标 Minecraft 版本。
