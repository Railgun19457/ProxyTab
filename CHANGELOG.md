# 更新日志

## 0.0.4 - 2026-08-15

- 修复切换到黑名单子服时，由于提前在 `ServerPreConnectEvent` 释放 Tab，导致代理端再次接管又被清除的循环问题。
- 现改在 `ServerConnectedEvent` 中根据当前所在子服判断是否释放，避免对后端子服自带 Tab 插件产生干扰。

## 0.0.3 - 2026-05-18

- 新增通用虚拟玩家接口 `proxytab:virtual_players`，后端插件可注册假人或机器人 Tab 条目。
- 虚拟玩家会复用 ProxyTab 的玩家格式、服务器分组、排序和忽略规则。
- 支持虚拟玩家 `reset`、`update`、`hide`、`remove` 动作，同步 UUID、名称、皮肤、延迟和游戏模式。
- 新增接入文档 `docs/virtual-players.md`。

## 0.0.2 - 2026-05-15

- 更新版本号到 v0.0.2。
