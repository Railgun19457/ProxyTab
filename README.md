# ProxyTab

![:ProxyTab](https://count.getloli.com/@railgun19457_ProxyTab?name=railgun19457_ProxyTab&theme=minecraft&padding=6&offset=0&align=top&scale=1&pixelated=1&darkmode=auto)

ProxyTab 是一个运行在 Velocity 上的全局 Tab 列表管理插件，用于在代理端统一接管玩家列表、Header/Footer 和公告展示。

## 功能特性

- 全局 Tab 列表渲染，统一展示全网在线玩家
- 按子服分组排序，支持子服显示名映射
- 支持 MiniMessage，Header、Footer、玩家条目和公告均可使用格式标签
- 支持玩家名正则过滤，可隐藏 Bot 或指定玩家
- 支持黑名单子服，避免与特定子服 Tab 插件冲突
- 支持聊天公告和 Tab 公告
- 支持“今日不再提醒”，公告更新后会自动刷新提醒状态
- 使用 JSON 本地持久化公告内容和玩家提醒状态
- 支持 `/proxytab reload` 热重载配置

## 运行环境

- Java 17+
- Velocity 3.3.0+

## 安装

1. 从 Release 下载插件 Jar（或本地构建）。
2. 放入 Velocity 的 `plugins` 目录。
3. 启动代理端，首次启动会自动生成配置文件：
   - `plugins/proxytab/config.yml`
   - `plugins/proxytab/data/announcements.json`
   - `plugins/proxytab/data/player-state.json`

## 命令

- `/proxytab` 显示插件状态和帮助信息
- `/ptab` `/proxytab` 的别名
- `/proxytab reload` 重载配置文件
- `/proxytab announcement set <chat|tab> <always|once_per_day> <content...>` 设置公告
- `/proxytab announcement delete <chat|tab>` 删除公告
- `/proxytab close-notice` 关闭今日聊天公告提醒（由点击按钮触发）

## 权限

- `proxytab.use`：基础命令权限，默认允许所有来源使用
- `proxytab.reload`：允许重载配置
- `proxytab.announcement`：允许设置或删除公告
- `proxytab.*`：允许所有 ProxyTab 管理操作

## 配置概览

`config.yml` 主要分区：

- `general`：网络名称、刷新间隔、玩家过滤规则、黑名单子服和默认子服显示名
- `servers`：子服分组顺序和子服显示名映射
- `tab`：Tab 开关、Header/Footer 独立开关、玩家条目格式和排序策略
- `announcements`：聊天公告/Tab 公告开关、关闭按钮和 Tab 公告外观
- `storage`：JSON 存储文件路径


## 占位符

Header/Footer 可用：

- `<network_id>`：配置中的群组服名称
- `<online>`：全服在线人数
- `<current_server>`：当前玩家所在子服显示名
- `<server_online>`：当前子服在线人数
- `<ping>`：当前玩家延迟

玩家条目可用：

- `<player_name>`：目标玩家名
- `<player_server>`：目标玩家所在子服显示名
- `<player_ping>`：目标玩家延迟
- `<ping>`：目标玩家延迟，兼容简写

Tab 公告格式可用：

- `<announcement>`：当前 Tab 公告正文

## 本地构建

```bash
gradle clean build
```

构建产物位于：

- `build/libs/ProxyTab-<version>.jar`
