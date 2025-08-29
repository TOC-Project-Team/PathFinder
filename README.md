# PathFinder Plugin Documentation ✨  

> Version: For Minecraft **1.21** and above   
> Function: Player-to-player (more function will be finished in the future)  
asynchronous A* pathfinding + real-time particle navigation   

---

## **Plugin Introduction**  
**PathFinder** uses **2000+ lines of A\* code** to help players find the optimal route over theoretical **unlimited distances**, and guides with **particle lines visible only to themselves** in real-time.  
- Fully **asynchronous** computation, won't lag the main thread ⚡  
- Supports **8 languages with automatic switching** (Simplified/Traditional Chinese, English, German, Russian, Spanish, Portuguese, French) 🌏  
- Default configuration for **medium to high-spec servers**, low-spec machines can also adapt by downgrading according to examples 

---

## **Plugin Demonstration** 
Navigation effect 🎥
![1756470220397.png](https://free.picui.cn/free/2025/08/29/68b19dcf4573e.png)
![1756470179981.png](https://free.picui.cn/free/2025/08/29/68b19dd0beeb5.png)
![1756470046658.png](https://free.picui.cn/free/2025/08/29/68b19dd0703bf.png)
![1756470278448.png](https://free.picui.cn/free/2025/08/29/68b19dd19ee3c.png)
![1756470353712.png](https://free.picui.cn/free/2025/08/29/68b19dd2d5f75.png)
![1756470434337.png](https://free.picui.cn/free/2025/08/29/68b19dd62a476.png)
![1756470500728.png](https://free.picui.cn/free/2025/08/29/68b19dd91589d.png)
![1756470561102.png](https://free.picui.cn/free/2025/08/29/68b19dda29a0c.png)
![1756470582360.png](https://free.picui.cn/free/2025/08/29/68b19ddb243f3.png)
![1756470656313.png](https://free.picui.cn/free/2025/08/29/68b19ddb872a3.png)

`/toc cd` Open player's navigation page 📋

![1756470687812.png](https://free.picui.cn/free/2025/08/29/68b19ddba00d2.png)

`/toc admin` display the actionbar ⚙️

![1756470753726.png](https://free.picui.cn/free/2025/08/29/68b19dde37604.png)


---

## **Commands and Permissions Quick Reference**  

| Command | Permission Node | Brief Description |
|---|---|---|
| `/toc admin` | `toc.admin` | Open admin menu (gui) |
| `/toc reconfig` | `toc.admin` | Reset configuration file to default |
| `/toc relang` | `toc.admin` | Reload language files |
| `/toc reload` | `toc.admin` | Reload the plugin |
| `/toc status` | `toc.admin` | View plugin version |
| `/toc cd` | `toc.cd` | Open player's navigation menu |
| `/toc nav view [--page=]` | `toc.view` | List all players currently navigating |
| `/toc lang <language\|reset>` | `toc.lang` | Switch interface language<br>Supports `zh-CN` `zh-TW` `en-US` `de-DE` `es-ES` `fr-FR` `pt-PT` `ru-RU`<br>Set to `reset` to restore default value |
| `/toc nav add <waypoint name>` | `toc.nav.add` | Create a new waypoint at your position |
| `/toc nav go <waypoint name>` | `toc.nav.go` | Navigate yourself to the waypoint |
| `/toc nav list [--page=] [--world=]` | `toc.nav.list` | View existing waypoints |
| `/toc nav remove <waypoint name>` | `toc.nav.remove` | Delete a waypoint |
| `/toc nav rename <old name> <new name>` | `toc.nav.rename` | Rename a waypoint |
| `/toc nav set <x/y/z/world> <value>` | `toc.nav.set` | Fine-tune waypoint coordinates or world |
| `/toc nav start <player> <waypoint name>` | `toc.nav.start` | **Admin** force a player to start navigation |
| `/toc nav stop` | `toc.nav.stop` | Stop **your own** navigation |
| `/toc nav stop [player]` | `toc.nav.stop.other` | **Admin** Stop **someone else's** navigation |

---

## **Configuration File Details**

###  `config.yml`
*Plugin main configuration* 📝
```yaml
# Plugin interface language, zh-CN Simplified Chinese; en-US English; see lang folder for others
language: zh-CN

pathfinder.yml
Pathfinding related configuration<br>Setting costs as negative will encourage the algorithm to choose certain behaviors, but if you really want to encourage certain behaviors<br>a better approach is to lower the positive costs of these behaviors rather than using negative costs ⚠️

# ========= Search Range & Precision =========
max_search_radius: 3000      # Maximum search distance (blocks) — Low-spec servers change to 100~500
max_iterations: 4000         # Maximum calculation steps — Low-spec servers change to 1000~2000

# ========= Particle Visual Effects =========
particle_spacing: 0.5        # Distance between particles (blocks), smaller = denser
max_particle_distance: 30    # Particle visibility distance, further = more bandwidth usage
particle_size: 1.0           # Particle size
path_refresh_ticks: 15       # Path refresh cycle (ticks), 20 ticks = 1 second

# ========= Movement "Costs": Higher numbers = less likely to choose =========
straight_cost: 1.0           # Walking straight one block 
diagonal_cost: 1.5           # Walking diagonally one block 
right_angle_turn_cost: 0.5   # Right angle turn 
diagonal_turn_cost: 1.0      # Diagonal turn 
break_block_cost: 100.0      # Breaking blocks (99999 almost prohibits it, 0 allows freely) 
door_cost: 0.0               # Going through doors (0 means prioritize using doors)
trapdoor_cost: 6.0           # Going through trapdoors
jump_cost: 0.0               # Jumping on flat ground
vertical_cost: 1.0           # Climbing ladders/scaffolds up and down
scaffolding_cost: 0.0        # Walking on scaffolding 
fall_cost: 2.0               # Direct free fall
block_jump_cost: 1.0         # Jumping across blocks
max_block_jump_distance: 4   # Maximum distance for jumping across blocks
max_safe_fall_height: 4      # Maximum "safe" fall height 

# ========= Performance Options =========
enable_path_caching: false    # true = reuse old paths to lower CPU usage, but may be slightly slower to respond
```

### Low-Spec Server Optimization Example
- max_search_radius: 200
- max_iterations: 1000
- path_refresh_ticks: 30

If you change the configuration, plugin will automatically take effect, no need to reload.

### Notes
This plugin is not recommended for high-precision parkour  
Currently only supports ladders and scaffolding for climbing paths  
Underwater pathfinding may have abnormalities

### Official Support
Discord Community: https://discord.gg/daSchNY7Sr
Language files: plugins/PathFinder/lang/ can be translated or added by yourself

Enjoy using it! 🎉
