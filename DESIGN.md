# RookieFortuneTree（元宝树浇水）插件实现方案（基于 BukkitSpring + Starters）

> 目标：按 `C:\Users\Administrator\Desktop\game-docs\元宝树浇水\策划玩法.md` 的“定金 + 随机气泡 + 暴击 + 可重刷 + 每日次数限制 + 新手规则”玩法，结合当前工程使用的 **BukkitSpring** 框架、**OdalitaMenus** 菜单框架与 **starter-redis / starter-mybatis / starter-loki / starter-prometheus**（可选），设计 `examples/RookieFortuneTree` 插件的可落地实现方案。

---

## 1. 玩法要点（与策划文档对齐）

### 1.1 玩家流程
1) 通过菜单或指令打开「元宝树」GUI  
2) 点击“浇水/摇树”
   - 扣除定金（或消耗免费次数 / 首次消耗 100）
   - 播放音效/粒子（可选）
   - 生成若干“元宝气泡”（普通/暴击）
3) 点击气泡逐个收取（或“一键收取”）→ 发放元宝奖励  
4) 今日次数耗尽 → 按钮置灰，显示次日重置倒计时  
5) （可选）未收取前退出再进入 → 刷新随机结果（重刷机制）

### 1.2 关键规则
- **每日限制**：默认每日仅可浇水/摘取 `1` 次（支持 `resetAt` 自定义重置时刻）
- **新手规则**：首次摘取消耗 `100` 元宝；之后获得 `9` 次免费摘取机会（仍计入每日次数）
- **定金/收益**：每级有 `deposit` 与 `rewardMax`；保证 **单次收获总额 > 定金** 且 **≤ rewardMax**
- **暴击**：小概率生成高收益气泡；可配置暴击概率/区间/占比
- **重刷**（可选）：未收取前退出重进可刷新结果；需配套限制（次数/放弃成本/锁定结果）

---

## 2. 技术选型与依赖

### 2.1 框架与库
- **BukkitSpring**：IoC/DI、生命周期、事件/命令注册的装配方式（参考 `examples/MonsterSignInPlugin`）
- **OdalitaMenus**：GUI 菜单（Chest Inventory）渲染与点击回调（参考 `examples/MonsterSignInPlugin` / `examples/RookieBlackSmith`）

### 2.2 Starters（按需启用）
- `bukkitspring-starter-redis`（推荐）：玩法状态/今日次数/收取进度的**缓存与原子更新**（多服更稳）
- `bukkitspring-starter-mybatis`（推荐）：玩家长期数据（等级/经验/统计）与当日结果（可选锁定）持久化
- `bukkitspring-starter-loki`（可选）：关键行为日志（浇水/收取/暴击/重刷）打点
- `bukkitspring-starter-prometheus`（可选）：指标统计（浇水次数、总定金、总产出、暴击次数等）

> 说明：Redis/MyBatis 是否可用由 BukkitSpring 主插件配置决定（starter 会将 `RedisService` / `MybatisService` 等注册为 Global Bean）。业务插件侧应使用 `@Autowired(required=false)` + `isEnabled()` 进行兼容处理，或在文档中声明为硬依赖。

### 2.3 工程接入（建议骨架）
参考现有示例工程（`examples/MonsterSignInPlugin`）的做法，RookieFortuneTree 建议按“独立 Maven 插件工程”组织：

- `pom.xml`：用 `systemPath` 引用本仓库已构建产物（BukkitSpring + starters + OdalitaMenus）
- `src/main/resources/plugin.yml`：声明 `depend: [BukkitSpring, OdalitaMenus]` 与命令/权限

`pom.xml` 依赖片段示例（仅示意路径，按实际 jar 名称调整）：
```xml
<dependency>
  <groupId>org.spigotmc</groupId>
  <artifactId>spigot-api</artifactId>
  <version>1.20.4-R0.1-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>

<dependency>
  <groupId>com.cuzz</groupId>
  <artifactId>BukkitSpring</artifactId>
  <version>1.0.0</version>
  <scope>system</scope>
  <systemPath>${project.basedir}/../../platform/bukkitspring-platform-bukkit/target/BukkitSpring-bukkit.jar</systemPath>
</dependency>

<dependency>
  <groupId>nl.odalitadevelopments</groupId>
  <artifactId>OdalitaMenusPlugin</artifactId>
  <version>1.0.0</version>
  <scope>system</scope>
  <systemPath>${project.basedir}/../MonsterSignInPlugin/lib/OdalitaMenusPlugin-1.0.0.jar</systemPath>
</dependency>

<!-- 可选：redis/mybatis/loki/prometheus starters（按需添加） -->
```

---

## 3. 总体架构（分层 + 事件驱动）

推荐沿用现有示例插件的组织方式（命令/菜单点击 → 发事件 → Controller/Service 处理），便于做权限校验、取消原因与后续扩展。

### 3.1 包结构建议
```
com.cuzz.rookiefortunetree
  RookieFortuneTreePlugin            # JavaPlugin + BukkitSpring.registerPlugin
  bootstrap/FortuneTreeBootstrap      # @PostConstruct：加载配置、注册命令、注册事件、保存资源
  command/FortuneTreeCommandWrapper   # CommandExecutor + TabCompleter
  controller/FortuneTreeController    # Listener：处理 FortuneTreeRequestEvent
  event/FortuneTreeRequestEvent       # Cancellable：OPEN_GUI/WATER/COLLECT/RELOAD/DEBUG...
  menu/FortuneTreeMenu                # @Menu + PlayerMenuProvider：主界面
  menu/FortuneTreeRulesMenu           # 可选：玩法说明面板
  menu/icon/MenuIconConfig            # 可选：图标与布局从 yml 驱动
  service/FortuneTreeService          # 玩法编排：水->生成->收取->刷新GUI
  service/RewardGenerator             # 随机结果生成（保证约束）
  service/ResetClock                  # 日切/重置时刻计算
  service/economy/EconomyGateway      # Vault/Command 两种实现
  storage/FortuneTreeStore            # 状态存取抽象（Redis/MyBatis/Memory）
  storage/redis/RedisFortuneTreeStore # 主实现：Hash + Bitmap + Seed
  repository/*Mapper + xml            # MyBatis：玩家表/当日表（可选）
  model/*                             # PlayerState/Attempt/Bubble/LevelConfig...
  util/Placeholders Keys Json...
```

### 3.2 事件与入口
- **命令入口**：`/wt gui`、`/wt water`、`/wt collect`、`/wt reload`、`/wt debug`
- **GUI入口**：点击菜单按钮触发同一套请求事件
- **事件模型**：`FortuneTreeRequestEvent` 携带 `Action` 与必要参数（如 bubbleIndex、指定 level 等）

好处：
- 所有入口统一走 `Service`，避免重复逻辑
- 任何操作可 `event.setCancelled(true)` 并带 `cancelReason` 给玩家提示

---

## 4. 状态机设计（防刷与幂等）

### 4.1 单人单日状态（核心）
每位玩家在一个“日周期”（由 `resetAt` 决定）内有且只有一次“浇水 attempt”：

**IDLE**（未浇水）  
→ `WATER` 成功后进入  
**PENDING**（已浇水，未收取完）  
→ `COLLECT` 若全部收取完成进入  
**COLLECTED**（今日完成）

### 4.2 允许“退出重刷”时的约束
策划定义是 **未收取前** 退出再进可刷新结果，因此：
- 仅当 `PENDING` 且 `collectedCount == 0` 才允许重刷
- 重刷次数受 `reroll.maxTimesPerWater` 限制（`0` 可表示无限，但不建议）
- 一旦发生第一次收取（或 `lockAfterFirstCollect=true`），本次结果必须锁定，不再变化

> 实现要点：**结果用 seed 表示**。重刷本质是更换 seed 并增加 `rerollCount`。

---

## 5. 数值/随机生成（保证“收益 > 定金”与“≤ 上限”）

建议“先定总额，再拆气泡”，避免反复抽样难以满足约束。

### 5.1 生成输入
由 `levelConfig` 与全局配置提供：
- `deposit`（定金）
- `rewardMax`（单次收获上限）
- `bubbleCount`（min/max）
- `crit.chance`（暴击出现概率）
- `crit.share`（暴击占总收益的比例范围，如 0.55~0.80）
- `minProfit`（最小净收益，默认 1）

### 5.2 生成流程（推荐）
1) `bubbleCount = randInt(minBubbles, maxBubbles)`
2) `total = randInt(deposit + minProfit, rewardMax)`（确保净收益为正）
3) `critCount`：
   - 可用“每次最多 1 个暴击”简化（更接近“爆一个大泡泡”手感）
   - 或 `binomial(bubbleCount, critChance)` 并设上限
4) 分配：
   - `critTotal = total * rand(critShareMin..critShareMax)`（有暴击时）
   - `normalTotal = total - critTotal`
5) 拆分为气泡金额：
   - 使用“随机切分”保证每个气泡金额 ≥ 1 且总和精确等于分配值
6) 标记气泡类型（NORMAL/CRIT）与展示素材（不同材质/自定义模型）

### 5.3 锁定结果
`attempt.seed` 固化后，气泡列表可由 `seed + levelId + attemptId` 生成，保证可复现；收取进度用 bitmap 记录，避免重复发奖。

---

## 6. GUI 设计（OdalitaMenus）

### 6.1 菜单类型与布局
建议 `MenuType.CHEST_6_ROW`（54 格）：
- 顶栏：余额/说明/关闭
- 中间：树主图标（展示等级、经验、今日状态）
- 主按钮：浇水/摇树（展示定金或“免费次数”）
- 气泡区：固定一组槽位（围绕树），按生成的 bubble 列表渲染
- 辅助按钮：一键收取、重刷提示（可选）、debug（管理员）

### 6.2 状态渲染
- **可浇水**：按钮高亮 + 二次确认（可选），气泡区为背景
- **待收取**：气泡区出现 ClickableItem；点击播放音效/提示并刷新界面
- **已完成**：按钮置灰，展示重置倒计时

### 6.3 图标配置化（推荐）
参考 `examples/MonsterSignInPlugin` 的 `menu_icons.yml`：
- 用 `menu_icons.yml` 定义各类 icon（TREE/WATER_BUTTON/BUBBLE_NORMAL/BUBBLE_CRIT/COLLECT_ALL/RULES/CLOSE/BORDER...）
- 支持 `Slot` 字段配置布局
- Name/Lore 支持占位符：`{level}` `{deposit}` `{rewardMax}` `{free}` `{reset}` `{reroll}` `{today}` `{amount}` …

---

## 7. 经济系统对接（Vault/Command 双模式）

### 7.1 抽象接口
`EconomyGateway`：
- `boolean take(Player, int amount, String reason)`
- `void give(Player, int amount, String reason)`
- （可选）`long balance(Player)`

### 7.2 实现策略
- **Vault 模式**（推荐）：有明确成功/失败与余额校验
- **Command 模式**：执行配置的命令模板
  - `takeDepositCommand: "eco take {player} {deposit}"`
  - `giveRewardCommand: "eco give {player} {amount}"`
  - 注意：命令模式难以得到“是否扣款成功”的可靠回执，建议在设计中给出风险说明或要求经济插件命令本身具备失败提示且不扣不发。

---

## 8. 存储设计（Redis + MyBatis）

### 8.1 Redis（推荐作为实时玩法状态）
**Key 建议：**
- `ft:player:{uuid}`（Hash）：
  - `level`, `exp`, `free`, `firstDone`, `totalDeposit`, `totalReward`, `critCount`, `lastCycle`
- `ft:attempt:{uuid}:{cycle}`（Hash）：
  - `status`(IDLE/PENDING/COLLECTED), `level`, `deposit`, `rewardMax`, `seed`, `bubbleCount`, `rerollCount`, `createdAt`
- `ft:collect:{uuid}:{cycle}`（Bitmap）：
  - bit i 表示第 i 个气泡是否已收取

**TTL：**
- attempt/collect 建议设置 7~30 天 TTL，避免键无限增长

**原子性：**
- 单服可用“玩家级锁 + 顺序写入”
- 多服建议用 Lua 或 `WATCH/MULTI`（若需要）来保证 `WATER` 与 `COLLECT` 的幂等

### 8.2 MyBatis（持久化与统计）
建议至少持久化：
- 玩家长期数据：等级/经验/免费次数/累计统计
- 当日 attempt：用于“锁定结果防刷”或服务器重启后恢复待收取

**表设计建议：**
1) `fortune_tree_player`
   - `uuid`(PK), `level`, `exp`, `free_picks`, `first_done`
   - `total_deposit`, `total_reward`, `crit_count`
   - `updated_at`
2) `fortune_tree_attempt`
   - `uuid`, `cycle_id`(联合唯一)
   - `status`, `level`, `deposit`, `reward_total`, `seed`, `bubble_count`, `reroll_count`
   - `collect_bits`(varchar/text) 或另表存 bitmap
   - `created_at`, `collected_at`

> MyBatis starter会自动扫描 `@Mapper` 接口并从 `resources/mappers/*.xml` 加载映射；可参考 `examples/MonsterSignInPlugin` 的 mapper 同步方式。

---

## 9. 命令与权限（与策划文档一致）

### 9.1 命令
- `/wt gui`：打开 GUI（`dailywatertree.gui`）
- `/water` 或 `/wt water [level]`：执行浇水/摇树（`dailywatertree.use`）
- `/wt collect`：一键收取（可选，`dailywatertree.use`）
- `/wt reload`：重载配置（`dailywatertree.admin`）
- `/wt debug`：输出玩家状态（`dailywatertree.admin`）

### 9.2 `plugin.yml` 权限建议
- `dailywatertree.gui` 默认 true
- `dailywatertree.use` 默认 true
- `dailywatertree.admin` 默认 op

---

## 10. 观测与运营（可选）

- **Loki**：记录关键事件（player/uuid、cycle、deposit、reward、crit、rerollCount、耗时）
- **Prometheus**：
  - counter：`ft_water_total`、`ft_collect_total`、`ft_crit_total`
  - gauge：`ft_deposit_sum`、`ft_reward_sum`（或用 counter）

---

## 11. 落地步骤（推荐实现顺序）

1) 建立插件骨架：JavaPlugin + BukkitSpring 容器接入 + plugin.yml + /wt 命令
2) 实现 `ResetClock` 与“每日 cycleId”逻辑（支持 resetAt）
3) 实现 `FortuneTreeStore`（先 Memory，再接 Redis）
4) 实现 `RewardGenerator`（总额→拆分→气泡列表），并写单元测试验证约束（总和、上限、>deposit）
5) 实现 `FortuneTreeMenu`：三态渲染 + 点击事件派发
6) 实现经济网关：先 Command 模式，再接 Vault
7) 引入 MyBatis 持久化（可选）：玩家表/attempt 表 + mapper
8) 加入重刷控制（reroll）与防刷策略（锁定结果/次数上限/放弃成本）

---

## 12. 风险点与处理建议

- **命令经济扣款不可靠**：建议默认使用 Vault；或要求经济命令在扣款失败时能返回明确反馈并不改变余额
- **重刷导致期望收益上升**：务必限制 `maxTimesPerWater` 或采用“浇水即落库锁定结果”
- **重复点击/双发奖**：收取必须以 store 中的 bitmap/状态为准，先标记后发奖（或使用原子操作）
- **跨服一致性**：若多服共享经济与数据，推荐 Redis 作为状态源并使用原子更新
