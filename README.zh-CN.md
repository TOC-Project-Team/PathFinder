# PathFinder 插件文档

[English](README.md) | **简体中文**

> 最新支持的服务端版本：**26.1**  
> 功能：玩家导航、路径点管理、粒子路线指引  
> 构建：Java 21 + Gradle + Shadow 打包完整 jar

---

## 插件介绍

**PathFinder** 是一款专注于导航和路径点的 Paper/Spigot 插件。它在后台线程计算路线，并用只有导航玩家本人能看到的粒子把路线画出来，引导玩家走向目标，不会卡服务器主线程。

主要特点：

- 路线在后台异步计算，避免主线程卡顿。
- 实时粒子路线，只有正在导航的玩家能看到。
- 通过 `/toc nav` 创建、编辑、查看路径点并导航过去。
- 提供玩家导航菜单和管理员菜单。
- 内置多种语言。
- 寻路代价、粒子效果、搜索半径等都可以配置。

---

## 效果展示

导航效果：

![导航演示 1](https://free.picui.cn/free/2025/08/29/68b19dcf4573e.png)
![导航演示 2](https://free.picui.cn/free/2025/08/29/68b19dd0beeb5.png)
![导航演示 3](https://free.picui.cn/free/2025/08/29/68b19dd0703bf.png)
![导航演示 4](https://free.picui.cn/free/2025/08/29/68b19dd19ee3c.png)
![导航演示 5](https://free.picui.cn/free/2025/08/29/68b19dd2d5f75.png)
![导航演示 6](https://free.picui.cn/free/2025/08/29/68b19dd62a476.png)
![导航演示 7](https://free.picui.cn/free/2025/08/29/68b19dd91589d.png)
![导航演示 8](https://free.picui.cn/free/2025/08/29/68b19dda29a0c.png)
![导航演示 9](https://free.picui.cn/free/2025/08/29/68b19ddb243f3.png)
![导航演示 10](https://free.picui.cn/free/2025/08/29/68b19ddb872a3.png)

`/toc cd` 打开玩家导航菜单：

![玩家导航菜单](https://free.picui.cn/free/2025/08/29/68b19ddba00d2.png)

`/toc admin` 打开管理员菜单：

![管理员菜单](https://free.picui.cn/free/2025/08/29/68b19dde37604.png)

---

## 命令与权限速查

| 命令 | 权限节点 | 说明 |
| --- | --- | --- |
| `/toc admin` | `toc.admin` | 打开管理员菜单 |
| `/toc reload` | `toc.admin` | 重载插件配置 |
| `/toc status` | `toc.admin` | 查看插件状态 |
| `/toc cd` | `toc.cd` | 打开玩家导航菜单 |
| `/toc lang <语言\|reset>` | `toc.lang` | 切换或重置个人语言 |
| `/toc nav add <名称> <x> <y> <z> [世界]` | `toc.nav.add` | 创建路径点 |
| `/toc nav remove <名称>` | `toc.nav.remove` | 删除路径点 |
| `/toc nav rename <旧名称> <新名称>` | `toc.nav.rename` | 重命名路径点 |
| `/toc nav set <名称> <x\|y\|z\|world> <值>` | `toc.nav.set` | 修改路径点的某一项 |
| `/toc nav start <玩家> <名称>` | `toc.nav.start` | 让某个玩家开始导航到路径点 |
| `/toc nav go <名称>` | `toc.nav.go` | 导航到已保存的路径点 |
| `/toc nav stop` | `toc.nav.stop` | 停止自己的导航 |
| `/toc nav stop <玩家>` | `toc.nav.stop.other` | 停止其他玩家的导航 |
| `/toc nav list [--page=] [--world=]` | `toc.nav.list` | 列出已保存的路径点 |
| `/toc nav view [--page=]` | `toc.view` | 查看当前所有正在进行的导航 |

---

## 配置文件说明

### `config.yml`

插件主配置：

```yaml
language: "en-US"
allow_navigation_to_invisible: false
metrics: true
```

说明：

- `language`：插件默认语言。
- `allow_navigation_to_invisible`：是否允许导航到处于隐身状态的玩家。
- `metrics`：是否向 [bStats](https://bstats.org) 发送匿名使用统计。服主也可以在 `plugins/bStats/config.yml` 中统一关闭。

### `pathfinder.yml`

寻路配置：

```yaml
max_search_radius: 3000
max_iterations: 4000
particle_spacing: 0.5
max_particle_distance: 30
particle_size: 1.0
path_refresh_ticks: 15
diagonal_cost: 1.5
straight_cost: 1.0
right_angle_turn_cost: 0.5
diagonal_turn_cost: 1.0
break_block_cost: 100.0
door_cost: 0.0
trapdoor_cost: 6.0
jump_cost: 0.0
vertical_cost: 1.0
scaffolding_cost: 0.0
fall_cost: 2.0
block_jump_cost: 1.0
max_block_jump_distance: 4
max_safe_fall_height: 4
```

调整建议：

- `max_search_radius` 越大，寻路范围越大，计算开销也越高。
- `max_iterations` 越大，路线越准确，但占用更多 CPU 和内存。
- `particle_spacing` 越小，粒子线越密。
- `max_particle_distance` 越大，玩家能看到的路线越长，带宽占用也越多。
- 每次刷新都会根据当前地形重新计算路线，所以新打通的更近的路能被及时发现，不会沿用过时的旧路线。

### 低配服务器优化示例

```yaml
max_search_radius: 200
max_iterations: 1000
path_refresh_ticks: 30
```

配置项尽量保持简单易维护，小型服务器适合用更低的数值。

### 注意事项

- 路线按照每个方块真实的碰撞箱规划：地毯、台阶、楼梯、雪层等矮方块可以直接走上去，压力板、告示牌、旗帜可以直接穿过，栅栏和墙（1.5 格高）不会被当作能跳上去的方块。新版本加入的方块同样适用，不需要更新插件。
- 支持攀爬梯子、藤蔓（包括垂泪藤、缠怨藤和洞穴藤蔓）和脚手架。爬到梯子或藤蔓顶端，或站在脚手架顶上时，可以继续走上最多高一格的方块。
- 支持简单的跑酷：在跳跃轨迹没有阻挡时，可以向任意方向跳到同高或不同高度（最多高一格）的方块上。`max_block_jump_distance` 限制最远跳跃距离，另外间隔不会超过疾跑跳能跨过的距离（平地 3 格，落点高一格时 2 格）。不会建议跨越岩浆或火的跳跃。
- 木门、铜门、活板门和栅栏门视为可以打开；关闭的铁门和铁活板门不能通过。
- 只有在行走、跳跃和攀爬都无法到达时，才会建议破坏方块。
- 水下寻路的效果与地图设计有关，建议在自己的地图上多测试。

---

## 构建

### 完整构建

```bash
./scripts/build-fatjar.sh
```

这个脚本会：

- 尝试自动识别 `JAVA_HOME`
- 清理 Gradle 输出目录之外的零散 `.class` 文件
- 执行 `./gradlew clean shadowJar`
- 检查生成的 jar，并报告文件大小和类数量

### 构建产物

`./scripts/build-fatjar.sh` 生成的主要文件位于：

```text
.gradle-build/libs/PathFinder-1.8.0-all.jar
```

脚本从 `.gradle-build/libs/*-all.jar` 中找到最终文件，所以文件名会跟随 `build.gradle` 里声明的版本号。

如果设置了 `RELEASE_COPY=1`，脚本还会把带时间戳的构建产物复制到：

```text
release/
```

示例：

```bash
RELEASE_COPY=1 ./scripts/build-fatjar.sh
```

### 快速构建

```bash
./scripts/quick-build.sh
```

跳过完整的清理步骤，直接执行 `shadowJar`，速度更快。

### 旧版入口

```bash
./build.sh
```

---

## 项目结构

```text
.
├── build.gradle
├── scripts/                  构建脚本
├── src/main/java/org/momu/pathfinder/
│   ├── api/                  对外开发者 API（保持稳定，供其他插件使用）
│   ├── bootstrap/            插件入口和配置文件监听
│   ├── command/              /toc 命令；nav/ 下是 /toc nav 子命令
│   ├── config/               pathfinder.yml 配置和多语言消息
│   ├── gui/                  /toc cd 和 /toc admin 箱子菜单
│   ├── integration/          开发者 API 的实现
│   ├── listener/             Bukkit 事件监听
│   ├── navigation/
│   │   ├── NavigationService 所有导航的统一入口
│   │   ├── session/          谁在导航去哪、位置隐私、全局开关
│   │   ├── runtime/          每个玩家的导航任务、任务调度、水面落点
│   │   ├── pathfinding/      A* 寻路、方块分类、地形规则
│   │   ├── display/          粒子路线绘制和动作栏
│   │   └── locate/           信标和要塞搜索
│   └── waypoint/             路径点及 waypoints.yml 存储
└── src/main/resources/       plugin.yml、配置文件、lang/*.yml
```

一次导航的执行流程：

1. 命令、菜单点击、事件监听或 API 调用都通过 `NavigationService` 发起导航。
2. `NavigationTracker` 记录目标，并触发 `PathFinderNavigationStartEvent`。
3. `GuidanceTask` 每隔 `path_refresh_ticks` 运行一次：先在主线程检查目标是否仍然有效，再异步运行 `AStarPathfinder` 计算路线，最后用 `PathRenderer` 画出路线并更新动作栏。
4. 到达目标、手动停止或目标失效时，通过 `NavigationTracker.stopNavigation` 结束导航，它会取消任务并触发 `PathFinderNavigationStopEvent`。

---

## 开发者 API

其他插件可以通过 Java API 控制 PathFinder：开始和停止导航、管理路径点、监听导航事件。

### 接入

1. 在你的插件 `plugin.yml` 中声明依赖：

   ```yaml
   depend: [PathFinder]      # 如果 PathFinder 是可选的，改用 softdepend: [PathFinder]
   ```

2. 以 `compileOnly` 方式添加依赖。服务器运行时已经有 PathFinder，所以**不要**把它打包进你的插件。可以通过 [JitPack](https://jitpack.io/#TOC-Project-Team/PathFinder-source) 获取：

   Gradle：

   ```groovy
   repositories {
       maven { url 'https://jitpack.io' }
   }

   dependencies {
       compileOnly 'com.github.TOC-Project-Team:PathFinder-source:1.8.0'
   }
   ```

   Maven：

   ```xml
   <repositories>
       <repository>
           <id>jitpack.io</id>
           <url>https://jitpack.io</url>
       </repository>
   </repositories>

   <dependency>
       <groupId>com.github.TOC-Project-Team</groupId>
       <artifactId>PathFinder-source</artifactId>
       <version>1.8.0</version>
       <scope>provided</scope>
   </dependency>
   ```

   也可以把发布的 jar 放进项目，使用 `compileOnly files('libs/PathFinder-1.8.0-all.jar')`。

3. 获取 API 实例：

   ```java
   import org.momu.pathfinder.api.PathFinderAPI;
   import org.momu.pathfinder.api.PathFinderProvider;

   PathFinderAPI api = PathFinderProvider.get();
   // 或者：Bukkit.getServicesManager().load(PathFinderAPI.class);
   ```

   使用 `softdepend` 时，请先调用 `PathFinderProvider.isAvailable()` 检查。API 方法请在服务器主线程中调用。

### 导航

```java
// 引导玩家前往任意位置（必须和玩家在同一个世界）
NavigationResult result = api.navigateToLocation(player, location, "任务目标");
if (!result.isSuccess()) {
    player.sendMessage("无法导航：" + result);
}

// 不显示距离/方向动作栏，只显示粒子
api.navigateToLocation(player, location, "隐藏宝藏", false);

// 已保存的路径点（等同于 /toc nav go <名称>）
api.navigateToWaypoint(player, "spawn");

// 跟随另一名玩家（会遵守对方的位置隐私设置）
api.navigateToPlayer(player, targetPlayer);

// 停止 / 查询
api.stopNavigation(player);
api.isNavigating(player.getUniqueId());
api.getSession(player.getUniqueId()).ifPresent(session ->
        getLogger().info(session.type() + " -> " + session.targetLocation()));
api.getActiveSessions();
```

`NavigationResult` 的取值：`SUCCESS`、`PLAYER_OFFLINE`、`INVALID_TARGET`、`WAYPOINT_NOT_FOUND`、`WORLD_MISMATCH`、`TARGET_UNAVAILABLE`、`ALREADY_NAVIGATING`、`NAVIGATION_DISABLED`、`CANCELLED`。

### 路径点

```java
api.createWaypoint("market", location);   // 名称：1-32 个字符，不区分大小写，不能重复
api.moveWaypoint("market", newLocation);
api.renameWaypoint("market", "bazaar");
api.removeWaypoint("bazaar");

Optional<WaypointSnapshot> wp = api.getWaypoint("spawn");
List<WaypointSnapshot> all = api.getWaypoints();
List<WaypointSnapshot> inWorld = api.getWaypoints(world);
```

通过 API 创建的路径点会保存到 `waypoints.yml`，并和其他路径点一样出现在 `/toc nav list` 中。

### 全局设置

```java
api.setNavigationEnabled(false);                   // 等同于管理员菜单里的开关
api.setLocationHidden(player.getUniqueId(), true); // 等同于"隐藏我的位置"按钮
```

### 事件

所有事件都在 `org.momu.pathfinder.api.event` 包中，携带玩家和一份 `NavigationSession` 快照（`type`、`targetLocation`、`targetPlayerId`、`displayName`）。

| 事件 | 触发时机 | 说明 |
| --- | --- | --- |
| `PathFinderNavigationStartEvent` | 任何导航开始之前（菜单、命令或 API） | 可取消 |
| `PathFinderNavigationArriveEvent` | 玩家到达目标 | 之后会紧跟一个原因为 `ARRIVED` 的停止事件 |
| `PathFinderNavigationStopEvent` | 导航结束 | `getReason()`：`ARRIVED`、`CANCELLED`、`API`、`REPLACED`、`TARGET_UNAVAILABLE`、`PLAYER_DIED`、`PLAYER_QUIT`、`WORLD_CHANGED`、`GAME_MODE_CHANGED`、`NAVIGATION_DISABLED`、`PLUGIN_DISABLED`、`OTHER` |

```java
@EventHandler
public void onArrive(PathFinderNavigationArriveEvent event) {
    if ("任务目标".equals(event.getSession().displayName())) {
        event.getPlayer().sendMessage("已到达任务目标！");
    }
}

@EventHandler
public void onStart(PathFinderNavigationStartEvent event) {
    if (event.getSession().type() == NavigationType.STRONGHOLD && !event.getPlayer().hasPermission("myplugin.stronghold")) {
        event.setCancelled(true);
    }
}
```

所有事件都在服务器主线程触发，监听器里可以放心使用 Bukkit API。

---

## 语言支持

目前内置的语言文件：

- `zh-CN`
- `zh-TW`
- `en-US`
- `de-DE`
- `ru-RU`
- `es-ES`
- `pt-PT`
- `fr-FR`

运行时语言文件位于 `plugins/PathFinder/lang/`，可以按需添加新语言。文件名请使用 RFC 1766 风格的标识，例如 `en-US`、`zh-CN`。

---

## 官方支持

Discord 社区：[https://discord.gg/daSchNY7Sr](https://discord.gg/daSchNY7Sr)

---

## 许可证

PathFinder 基于 [MIT 许可证](LICENSE) 发布。

祝你使用愉快。
