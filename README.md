# NoPrivateEnderPortal

> 阻止非豁免玩家用末影之眼在末地传送门框上私建末地传送门。

本插件是为规避 / 修复以下两个 PaperMC 问题而编写的：

- [PaperMC/Paper#13586](https://github.com/PaperMC/Paper/issues/13586)
- [PaperMC/Paper#13536](https://github.com/PaperMC/Paper/issues/13536)

这两个 issue 的本质是：**在某些分支下，取消「放置类事件」会导致物品复制（dupe）**。
`#13536` 是「无限复制」，`#13586` 是「容器物品复制」——它们的触发路径都绕不开
`BlockPlaceEvent` / `BlockMultiPlaceEvent` 的取消。

因此本插件**刻意不碰**那两个放置类事件，而是把拦截点前移到
`PlayerInteractEvent`（交互最开头），从根本上让「插眼 → 激活传送门」这一整段逻辑
根本不发生，从而既拦住了私建传送门，又完全避开了复制 bug 的触发条件。

---

## 这是怎么回事（事件时序结论）

在 **Paper 1.21.11** 上，玩家右键把末影之眼放进**最后一块**门框、激活传送门时，
**同一个右键数据包内**会同步触发 4 类事件（没有 tick 延迟）：

| 顺序 | 事件 | 说明 |
| --- | --- | --- |
| ① | `PlayerInteractEvent`（RIGHT_CLICK_BLOCK） | `ServerPlayerGameMode.useItemOn` 最开头，早于任何块/物品逻辑 |
| ② | `EntityChangeBlockEvent` | `EnderEyeItem.useOn` 内、`setBlock` 之前；entity=玩家，门框 `HAS_EYE` false→true |
| ③ | `BlockMultiPlaceEvent` | **不是** `BlockPlaceEvent`：一次带 **10 个方块**（门框 + 3×3 传送门方块） |
| ④ | `BlockPhysicsEvent` ×10 | **仅当有插件监听时**才触发（Paper 的 `hasPhysicsEvent` 开关） |

非最后一块门框（只插眼、不激活）时，③ 会变成普通的 `BlockPlaceEvent`（只捕获到门框 1 个方块），④ 只触发 1 次。

**两个反直觉点：**

1. **插个眼睛为什么会触发放置类事件？**
   `ItemStack.useOn` 在 `item.useOn` 期间打开了 `serverLevel.captureBlockStates`，
   期间所有 `Level.setBlock` 都被捕获；而 `InteractionResult.SUCCESS` 的
   `ItemContext.DEFAULT` 满足 `wasItemInteraction()==true`，所以插眼睛照样走
   `wasItemInteraction()` 门禁。最后一块门框 → 捕获 10 个位置 → 走 MultiPlace 分支。

2. **`EndPortalFrameBlock` 本身不参与交互**
   这个类没有任何 `useItemOn` / `useWithoutItem` 重写，全部逻辑在物品侧 `EnderEyeItem`
   （1.21.9 交互重构后的结构）。

---

## 安装与使用

1. 下载最新版 jar（GitHub Releases 或自行构建）。
2. 丢进服务端的 `plugins/` 目录。
3. 启动 / 重启服务端。
4. 按需修改 `plugins/NoPrivateEnderPortal/config.yml`（见下），或执行 `/npe reload` 热重载。

### 构建

```bash
./gradlew clean build
# 产物：build/libs/NoPrivateEnderPortal-<version>.jar
```

> 构建需要 JDK 21+（测试基建 MockBukkit v1.21 的要求）；插件产物本身目标字节码为 Java 17，
> 即**最低可在 Java 17 运行**。支持 **Paper 1.17+**（api-version 1.17）。

---

## 配置（config.yml）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `language` | `zh_CN` | 语言文件选择，对应 `lang/<language>.yml`（内置 `zh_CN` / `en_US`） |
| `exempt-ops` | `true` | OP 是否豁免（即 OP 仍可正常插眼激活传送门） |
| `exempt-players` | `[]` | 赦免名单：填写游戏名（大小写不敏感）或玩家 UUID |
| `message-cooldown-ms` | `2000` | 被拦截提示消息的冷却毫秒数；写 `0` 或负数表示不冷却 |

示例：

```yaml
language: zh_CN
exempt-ops: true
exempt-players:
  - Steve
  - "11111111-1111-1111-1111-111111111111"
message-cooldown-ms: 2000
```

---

## 命令与权限

| 命令 | 权限 | 默认 | 说明 |
| --- | --- | --- | --- |
| `/npe reload` | `noprivateenderportal.admin` | OP | 重读 `config.yml` 与语言文件，无需重启 |
| — | `noprivateenderportal.bypass` | 否 | 持有此权限的玩家**直接放行**，可私建传送门 |

被拦截时玩家会收到（受冷却控制）：

```
[NoPrivateEnderPortal] 你没有被允许创建末地传送门！
```

---

## 多语言

语言文件位于 `src/main/resources/lang/<语言>.yml`，由 `config.yml` 的 `language` 选择。
插件首次启动会把内置语言文件复制到 `plugins/NoPrivateEnderPortal/lang/`，之后以磁盘文件为准，
内置版本仅作缺键兜底，**不会被改写回磁盘**（避免毁掉你加的注释与排版）。

- 占位符：`{0}` `{1}` …（按 `sendMessage` / `Lang.t` 的参数顺序替换，参数不足时原样保留）
- 颜色代码：`&`（如 `&a` 绿色，`&&` 转义为字面量 `&`）
- 文本里的单/双引号无需转义

---

## 兼容性

- **服务端**：Paper 1.17+（api-version 1.17）。Spigot 未测试。
- **Java**：运行环境 Java 17+；构建需要 JDK 21+。
- **混合服务端（Purpur / Folia 等）**：未测试，但拦截点选在 `PlayerInteractEvent`，
  对各分支兼容性较好。

---

## 玩家可能会反馈的「bug」

本插件选择在前置交互阶段拦截，**不会**触发 `#13586` / `#13536` 的复制路径，但仍有一些
「看起来像 bug」的正常现象，提前说明以减少误解：

1. **右键门框没反应 / 眼睛没消耗**：这是**预期行为**——非豁免玩家被拦下，眼睛不会少，
   门框也不会亮起。玩家只是被「静默」拒绝了（仅收到一条提示）。

2. **OP / 被赦免玩家一切正常**：插眼、激活、眼睛消耗都与原版一致。

3. **创造模式**：创造模式下服务端会保存/恢复物品计数，眼睛不会真的消耗，但事件与激活
   照常发生（OP 或持有 bypass 权限时）。非豁免的创造模式玩家同样会被拦截。

4. **与其他插件冲突**：若另一个插件也在 `PlayerInteractEvent` 上做处理（例如自定义门框、
   自定义末地逻辑），优先级可能影响结果。本插件用 `EventPriority.LOWEST` 尽早拦截。

5. **`EntityChangeBlockEvent` 兜底**：极少数通过非右键路径（其他插件 / 命令）触发的插眼，
   会在兜底层被取消。这种情况下方块从未被修改，不会复制。

> 如果你确实遇到**物品复制 / 丢失**，那几乎一定不是本插件造成的——本插件从不取消
> `BlockPlaceEvent` / `BlockMultiPlaceEvent`，而那两个 issue 的复制恰好都发生在取消这类事件时。

---

## 各取消点的效果（写给要改插件的人）

| 取消点 | 设置 | 效果 |
| --- | --- | --- |
| `PlayerInteractEvent` `useInteractedBlock=DENY` | 全止 | 后续一切（消耗眼睛 / 门框 HAS_EYE / 3×3 传送门）都不发生 |
| `useItemInHand=DENY` | 跳过物品交互 | 无 ②③④ |
| `EntityChangeBlockEvent` | 取消 | 眼睛不消耗、门不激活（兜底层） |
| `BlockMultiPlaceEvent` | 取消 | 10 个方块整体回滚，但眼睛已消耗（**会踩 #13586/#13536 的坑，本插件不用**） |

**想拦末地激活的话，`PlayerInteractEvent` 是最干净的一道闸**；它早于 `captureBlockStates`
的打开，根本不会进入复制路径。

---

## 开源协议

[MIT](./LICENSE)
