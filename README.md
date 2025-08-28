# PathFinder 插件文档 ✨  

> 来源：https://www.spigotmc.org/resources/pathfinder-advanced-a-pathfinding-navigation-plugin.128119/ 📌  
> 版本：面向 Minecraft **1.21** 及以上 🚀  
> 功能：玩家-到-玩家（未来扩展更多目标）异步 A* 寻路 + 实时粒子导航 🧭  

---

## **插件简介**  
**PathFinder** 用 **2000+ 行 A\* 代码** 帮玩家在理论 **无限距离** 找到最优路线，并以 **仅自己可见的粒子线** 实时指引。  
- 全程 **异步** 运算，不卡主线程 ⚡  
- 支持 **8 种语言自动切换** (简/繁中文、英、德、俄、西、葡、法) 🌏  
- 默认配置面向 **中高配置服务器**，低配机也可按示例降配适应 🛠️  

---

## **插件演示** 
导航效果 🎥

![]()

`/toc cd` 打开玩家导航页面 📋

![]()

`/toc admin` 打开管理员菜单 ⚙️

![]()


---

## **指令与权限速查表**  

| 指令 | 权限节点 | 简短描述 |
|---|---|---|
| `/toc admin` | `toc.admin` | 打开图形化管理员菜单 🖥️ |
| `/toc reconfig` | `toc.admin` | 把配置文件恢复到默认 🔧 |
| `/toc relang` | `toc.admin` | 重载语言文件，无需重启 🔄 |
| `/toc reload` | `toc.admin` | 重载整个插件 🔄 |
| `/toc status` | `toc.admin` | 查看插件、服务器版本 📊 |
| `/toc cd` | `toc.cd` | 打开玩家用导航主菜单 🧭 |
| `/toc nav view [--page=]` | `toc.view` | 列出当前正在导航的所有玩家 👥 |
| `/toc lang <语言\|reset>` | `toc.lang` | 切换界面语言<br>支持 `zh-CN` `zh-TW` `en-US` `de-DE` `es-ES` `fr-FR` `pt-PT` `ru-RU`<br>设为`reset`可重置至默认值 🌐 |
| `/toc nav add <路径点名>` | `toc.nav.add` | 在脚下新建一个路径点 📍 |
| `/toc nav go <路径点名>` | `toc.nav.go` | 自己导航到该路径点 🚶 |
| `/toc nav list [--page=] [--world=]` | `toc.nav.list` | 查看已有路径点 📋 |
| `/toc nav remove <路径点名>` | `toc.nav.remove` | 删除路径点 🗑️ |
| `/toc nav rename <旧名> <新名>` | `toc.nav.rename` | 重命名路径点 ✏️ |
| `/toc nav set <x/y/z/world> <值>` | `toc.nav.set` | 微调路径点坐标或世界 🎯 |
| `/toc nav start <玩家> <路径点名>` | `toc.nav.start` | **管理员** 强制让某玩家开始导航 👮 |
| `/toc nav stop` | `toc.nav.stop` | 停止**自己**的导航 🛑 |
| `/toc nav stop [玩家]` | `toc.nav.stop.other` | 停止**他人**的导航 🛑 |

---

## **配置文件详解**

###  `config.yml`
*插件主配置文件* 📝
```yaml
# 插件界面语言，zh-CN 简体中文；en-US 英文；其余见 lang 文件夹
language: zh-CN
```

---

###  `pathfinder.yml`
*寻路相关配置<br>代价设置为负数将鼓励算法选择某行为，但是如果真的想鼓励某些行为<br>更好的方法是降低这些行为的正代价，而不是使用负代价* ⚠️
```yaml
# ========= 搜索范围 & 精度 =========
max_search_radius: 3000      # 最远找多远（格）——低配服改 100~500 📏
max_iterations: 4000         # 最多算多少步——低配服改 1000~2000 🔄

# ========= 粒子视觉效果 =========
particle_spacing: 0.5        # 两粒子间距（格），越小越密 ✨
max_particle_distance: 30    # 粒子可见距离，越远越吃带宽 👀
particle_size: 1.0           # 粒子大小 🔮
path_refresh_ticks: 15       # 路线刷新周期（tick），20 tick=1 秒；越小越灵敏但 CPU 越高 ⏱️

# ========= 移动「代价」：数字越大越不愿走 =========
straight_cost: 1.0           # 直走一格 ➡️
diagonal_cost: 1.5           # 斜走一格 ↗️
right_angle_turn_cost: 0.5   # 直角转弯 ↪️
diagonal_turn_cost: 1.0      # 斜角转弯 ↩️
break_block_cost: 100.0      # 破方块（99999 几乎禁止，0 就随便拆） ⛏️
door_cost: 0.0               # 过门（0 表示优先推门） 🚪
trapdoor_cost: 6.0           # 过活板门 🪤
jump_cost: 0.0               # 平地跳跃 🤸
vertical_cost: 1.0           # 爬梯子/脚手架上下 🧗
scaffolding_cost: 0.0        # 走脚手架 🏗️
fall_cost: 2.0               # 直接自由落体 🪂
block_jump_cost: 1.0         # 跨方块跳跃 🦘
max_block_jump_distance: 4   # 最远可跨的方块距离 📐
max_safe_fall_height: 4      # 最高"安全"下落高度 🛡️

# ========= 性能选项 =========
enable_path_caching: false    # true = 复用旧路线省 CPU，但可能反应稍慢 📦
```

---

## **低配服务器调优示例**

1. `max_search_radius: 200` 📏  
2. `max_iterations: 1000` 🔄  
3. `path_refresh_ticks: 30` ⏱️  

*改动后配置文件会自动生效，无需reload* ✅

---

### *注意事项*
- 该插件并 **不推荐** 用于高精度 **跑酷** 🏃‍♂️  
- 目前对于攀爬路径 **仅** 支持 **梯子** 和 **脚手架** 🪜
- **水下寻路** 可能会出现异常 🌊

---

## **官方支持**

- **Discord 社群**：https://discord.gg/daSchNY7Sr 💬  
- **语言文件**：plugins/PathFinder/lang/ 可自行翻译或新增 🌍  

祝使用愉快！ 🎉
