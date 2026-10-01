# PathFinder Plugin Documentation

**English** | [简体中文](README.zh-CN.md)

> Latest supported server version: **26.1**  
> Function: player navigation, waypoint management, and particle-based route guidance  
> Build: Java 21 + Gradle + Shadow fat jar packaging

---

## Plugin Introduction

**PathFinder** is a Paper/Spigot plugin focused on navigation and waypoint workflows for Minecraft servers. It uses Java-based path guidance, asynchronous runtime tasks, and player-visible particle routes to guide users toward targets without blocking the main server thread.

Key points:

- Asynchronous path processing designed to avoid main-thread lag.
- Real-time particle path guidance visible to the navigating player.
- Waypoint creation, editing, listing, and navigation through `/toc nav`.
- GUI entry points for player navigation and admin controls.
- Multi-language support with bundled language files.
- Configurable pathfinding cost, particle, and search-radius settings.

---

## Plugin Demonstration

Navigation effect:

![Navigation Demo 1](https://free.picui.cn/free/2025/08/29/68b19dcf4573e.png)
![Navigation Demo 2](https://free.picui.cn/free/2025/08/29/68b19dd0beeb5.png)
![Navigation Demo 3](https://free.picui.cn/free/2025/08/29/68b19dd0703bf.png)
![Navigation Demo 4](https://free.picui.cn/free/2025/08/29/68b19dd19ee3c.png)
![Navigation Demo 5](https://free.picui.cn/free/2025/08/29/68b19dd2d5f75.png)
![Navigation Demo 6](https://free.picui.cn/free/2025/08/29/68b19dd62a476.png)
![Navigation Demo 7](https://free.picui.cn/free/2025/08/29/68b19dd91589d.png)
![Navigation Demo 8](https://free.picui.cn/free/2025/08/29/68b19dda29a0c.png)
![Navigation Demo 9](https://free.picui.cn/free/2025/08/29/68b19ddb243f3.png)
![Navigation Demo 10](https://free.picui.cn/free/2025/08/29/68b19ddb872a3.png)

`/toc cd` opens the player navigation page:

![Player Navigation GUI](https://free.picui.cn/free/2025/08/29/68b19ddba00d2.png)

`/toc admin` opens the admin menu:

![Admin GUI](https://free.picui.cn/free/2025/08/29/68b19dde37604.png)

---

## Commands And Permissions Quick Reference

| Command | Permission Node | Description |
| --- | --- | --- |
| `/toc admin` | `toc.admin` | Open the admin menu |
| `/toc reload` | `toc.admin` | Reload plugin configuration |
| `/toc status` | `toc.admin` | View plugin status information |
| `/toc cd` | `toc.cd` | Open the player navigation menu |
| `/toc lang <language\|reset>` | `toc.lang` | Change or reset the player's language |
| `/toc nav add <name> <x> <y> <z> [world]` | `toc.nav.add` | Create a waypoint |
| `/toc nav remove <name>` | `toc.nav.remove` | Delete a waypoint |
| `/toc nav rename <old_name> <new_name>` | `toc.nav.rename` | Rename a waypoint |
| `/toc nav set <name> <x\|y\|z\|world> <value>` | `toc.nav.set` | Modify a waypoint field |
| `/toc nav start <player> <name>` | `toc.nav.start` | Force a player to start navigation |
| `/toc nav go <name>` | `toc.nav.go` | Navigate to a saved waypoint |
| `/toc nav stop` | `toc.nav.stop` | Stop your own navigation |
| `/toc nav stop <player>` | `toc.nav.stop.other` | Stop another player's navigation |
| `/toc nav list [--page=] [--world=]` | `toc.nav.list` | List saved waypoints |
| `/toc nav view [--page=]` | `toc.view` | View active navigation sessions |

---

## Configuration File Details

### `config.yml`

Main plugin configuration:

```yaml
language: "en-US"
allow_navigation_to_invisible: false
metrics: true
```

Configuration notes:

- `language` sets the default plugin language.
- `allow_navigation_to_invisible` controls whether invisible target players can still be navigated to.
- `metrics` toggles anonymous usage statistics sent to [bStats](https://bstats.org). Server owners can also opt out globally in `plugins/bStats/config.yml`.

### `pathfinder.yml`

Pathfinding configuration:

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

Tuning notes:

- Larger `max_search_radius` increases range but also increases processing cost.
- Larger `max_iterations` improves accuracy at a higher CPU and memory cost.
- Smaller `particle_spacing` creates denser particle lines.
- Larger `max_particle_distance` increases client-facing visibility and bandwidth usage.
- Every refresh recalculates the route from the current world state, so newly opened lower-cost routes can be detected without stale path reuse.

### Low-Spec Server Optimization Example

```yaml
max_search_radius: 200
max_iterations: 1000
path_refresh_ticks: 30
```

Configuration changes are intended to be lightweight to maintain, and lower values are more suitable for smaller servers.

### Notes

- Routes are planned from each block's real collision shape, so carpets, slabs, stairs, snow layers and similar low blocks are walked over, pressure plates, signs and banners are walked through, and fences and walls (1.5 blocks tall) are not jumped over. Blocks added in newer Minecraft versions work the same way without plugin changes.
- Ladders, vines (including weeping, twisting and cave vines) and scaffolding are climbed, also when they start one block above the ground (the player jumps up to grab them). From the top of a ladder or vine, or standing on top of scaffolding, the route can continue onto a block up to one block higher. The player can also stand on the top edge of a ladder and jump from there, reaching a block two higher than the ladder; vines have no collision, so this does not work with them.
- Simple parkour is supported: jumps between blocks at the same or different heights (up to one block higher), in any direction, when the arc is clear. `max_block_jump_distance` limits how far a jump reaches; gaps are further limited to what a sprint jump can clear (at most 4 blocks, i.e. a 3-block gap). From the top edge of a ladder the reach is shorter: 3 blocks to the same height or lower, 2 blocks to a block one higher. Jumps over lava are allowed as long as the player's body never touches it.
- Wooden and copper doors, trapdoors and fence gates are treated as openable; closed iron doors and trapdoors are not.
- Breaking blocks is only suggested when walking, jumping and climbing cannot reach the target. Blocks the route breaks stay broken for the rest of the route, so it never relies on a block it told the player to break.
- Underwater pathfinding can require additional testing depending on your map design.

---

## Build

### Full Fat Jar Build

```bash
./scripts/build-fatjar.sh
```

This script:

- attempts to auto-detect `JAVA_HOME`
- cleans stray `.class` files outside Gradle output directories
- runs `./gradlew clean shadowJar`
- verifies the generated fat jar and reports its size and class count

### Build Artifact

The main artifact produced by `./scripts/build-fatjar.sh` is written to:

```text
.gradle-build/libs/PathFinder-1.9.0-all.jar
```

The script resolves the final file from `.gradle-build/libs/*-all.jar`, so the exact filename follows the version declared in `build.gradle`.

If `RELEASE_COPY=1` is provided, the script also copies a timestamped build artifact into:

```text
release/
```

Example:

```bash
RELEASE_COPY=1 ./scripts/build-fatjar.sh
```

### Fast Build

```bash
./scripts/quick-build.sh
```

This is a faster build path that skips the full clean step and runs `shadowJar` directly.

### Legacy Entry Point

```bash
./build.sh
```

---

## Project Layout

```text
.
├── build.gradle
├── scripts/                  build helpers
├── src/main/java/org/momu/pathfinder/
│   ├── api/                  public developer API (stable, used by other plugins)
│   ├── bootstrap/            plugin entry point and config file watcher
│   ├── command/              /toc command; nav/ holds the /toc nav subcommands
│   ├── config/               pathfinder.yml settings and translated messages
│   ├── gui/                  /toc cd and /toc admin chest menus
│   ├── integration/          implementation of the developer API
│   ├── listener/             Bukkit event listeners
│   ├── navigation/
│   │   ├── NavigationService entry point for starting any navigation
│   │   ├── session/          who is navigating where, privacy, global on/off switch
│   │   ├── runtime/          per-player guidance task, scheduling, water landing
│   │   ├── pathfinding/      A* search, block classification, terrain rules
│   │   ├── display/          particle path renderer and action bar
│   │   └── locate/           beacon and stronghold search
│   └── waypoint/             saved waypoints and waypoints.yml storage
└── src/main/resources/       plugin.yml, config files, lang/*.yml
```

How a navigation runs:

1. A command, menu click, listener or API call goes through `NavigationService`.
2. `NavigationTracker` records the target and fires `PathFinderNavigationStartEvent`.
3. `GuidanceTask` runs every `path_refresh_ticks`: it validates the target on the main thread, runs
   `AStarPathfinder` asynchronously, then draws the path with `PathRenderer` and updates the action bar.
4. Arriving, stopping or losing the target ends the navigation through `NavigationTracker.stopNavigation`,
   which cancels the task and fires `PathFinderNavigationStopEvent`.

---

## Developer API

Other plugins can control PathFinder through a Java API: start and stop navigation, manage waypoints, and listen to navigation events.

### Setup

1. Add PathFinder to your plugin's `plugin.yml`:

   ```yaml
   depend: [PathFinder]      # or softdepend: [PathFinder] if PathFinder is optional
   ```

2. Add PathFinder as a `compileOnly` dependency. It is already on the server at runtime, so do **not** shade it. PathFinder is available through [JitPack](https://jitpack.io/#TOC-Project-Team/PathFinder-source):

   Gradle:

   ```groovy
   repositories {
       maven { url 'https://jitpack.io' }
   }

   dependencies {
       compileOnly 'com.github.TOC-Project-Team:PathFinder-source:1.9.0'
   }
   ```

   Maven:

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
       <version>1.9.0</version>
       <scope>provided</scope>
   </dependency>
   ```

   Alternatively, put the release jar in your project and use `compileOnly files('libs/PathFinder-1.9.0-all.jar')`.

3. Get the API instance:

   ```java
   import org.momu.pathfinder.api.PathFinderAPI;
   import org.momu.pathfinder.api.PathFinderProvider;

   PathFinderAPI api = PathFinderProvider.get();
   // or: Bukkit.getServicesManager().load(PathFinderAPI.class);
   ```

   With `softdepend`, check `PathFinderProvider.isAvailable()` first. Call API methods from the main server thread.

### Navigation

```java
// Guide a player to any location (must be in the player's world)
NavigationResult result = api.navigateToLocation(player, location, "Quest Target");
if (!result.isSuccess()) {
    player.sendMessage("Cannot navigate: " + result);
}

// Hide the distance/direction action bar, particles only
api.navigateToLocation(player, location, "Hidden Treasure", false);

// Saved waypoint (same as /toc nav go <name>)
api.navigateToWaypoint(player, "spawn");

// Follow another player (honours the target's location-privacy setting)
api.navigateToPlayer(player, targetPlayer);

// Stop / query
api.stopNavigation(player);
api.isNavigating(player.getUniqueId());
api.getSession(player.getUniqueId()).ifPresent(session ->
        getLogger().info(session.type() + " -> " + session.targetLocation()));
api.getActiveSessions();
```

`NavigationResult` values: `SUCCESS`, `PLAYER_OFFLINE`, `INVALID_TARGET`, `WAYPOINT_NOT_FOUND`, `WORLD_MISMATCH`, `TARGET_UNAVAILABLE`, `ALREADY_NAVIGATING`, `NAVIGATION_DISABLED`, `CANCELLED`.

### Waypoints

```java
api.createWaypoint("market", location);   // name: 1-32 characters, case-insensitive, unique
api.moveWaypoint("market", newLocation);
api.renameWaypoint("market", "bazaar");
api.removeWaypoint("bazaar");

Optional<WaypointSnapshot> wp = api.getWaypoint("spawn");
List<WaypointSnapshot> all = api.getWaypoints();
List<WaypointSnapshot> inWorld = api.getWaypoints(world);
```

Waypoints created through the API are saved to `waypoints.yml` and appear in `/toc nav list` like any other waypoint.

### Global settings

```java
api.setNavigationEnabled(false);                 // same as the admin GUI toggle
api.setLocationHidden(player.getUniqueId(), true); // same as the "hide my location" button
```

### Events

All events are in `org.momu.pathfinder.api.event` and carry the player and a `NavigationSession` snapshot (`type`, `targetLocation`, `targetPlayerId`, `displayName`).

| Event | When | Notes |
| --- | --- | --- |
| `PathFinderNavigationStartEvent` | Before any navigation starts (GUI, command or API) | Cancellable |
| `PathFinderNavigationArriveEvent` | The player reached the target | Followed by a stop event with reason `ARRIVED` |
| `PathFinderNavigationStopEvent` | A navigation ended | `getReason()`: `ARRIVED`, `CANCELLED`, `API`, `REPLACED`, `TARGET_UNAVAILABLE`, `PLAYER_DIED`, `PLAYER_QUIT`, `WORLD_CHANGED`, `GAME_MODE_CHANGED`, `NAVIGATION_DISABLED`, `PLUGIN_DISABLED`, `OTHER` |

```java
@EventHandler
public void onArrive(PathFinderNavigationArriveEvent event) {
    if ("Quest Target".equals(event.getSession().displayName())) {
        event.getPlayer().sendMessage("Quest objective reached!");
    }
}

@EventHandler
public void onStart(PathFinderNavigationStartEvent event) {
    if (event.getSession().type() == NavigationType.STRONGHOLD && !event.getPlayer().hasPermission("myplugin.stronghold")) {
        event.setCancelled(true);
    }
}
```

All events are fired on the main server thread, so listeners can safely use the Bukkit API.

---

## Language Support

Bundled language files currently include:

- `zh-CN`
- `zh-TW`
- `en-US`
- `de-DE`
- `ru-RU`
- `es-ES`
- `pt-PT`
- `fr-FR`

Language files live under `plugins/PathFinder/lang/` at runtime and can be extended as needed. Language filenames should follow RFC 1766 style identifiers such as `en-US` or `zh-CN`.

---

## Official Support

Discord community: [https://discord.gg/daSchNY7Sr](https://discord.gg/daSchNY7Sr)

---

## License

PathFinder is released under the [MIT License](LICENSE).

Enjoy using PathFinder.
