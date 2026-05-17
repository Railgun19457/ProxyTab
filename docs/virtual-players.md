# 虚拟玩家接入协议

ProxyTab 支持后端插件通过 Velocity Plugin Message 注册虚拟玩家条目。注册后的虚拟玩家会和真实玩家一起参与 `player-format` 渲染、服务器分组、排序和忽略规则。

## 通道

- Plugin Message channel: `proxytab:virtual_players`
- 当前协议版本: `1`
- 后端插件向 Velocity 发送消息，ProxyTab 根据消息来源的后端服务器自动确定 `<player_server>`。
- Bukkit/Paper 插件需要至少一个真实玩家作为 carrier 调用 `Player#sendPluginMessage(...)`；没有真实玩家在线时无法通过 Bukkit 标准插件消息发送。

## 数据格式

所有字符串使用 Java `DataOutputStream#writeUTF` 格式。UUID 写入两个 `long`：`mostSignificantBits`、`leastSignificantBits`。

每条消息先写：

```text
byte protocolVersion = 1
UTF action
```

### `reset`

清空当前来源后端服务器注册的所有虚拟玩家和隐藏标记。建议后端插件启动、重载或有真实玩家作为 carrier 后先发送一次。

```text
byte 1
UTF "reset"
```

### `update`

新增或更新一个应显示在 ProxyTab 中的虚拟玩家。

```text
byte 1
UTF "update"
long uuidMost
long uuidLeast
UTF username
UTF textureValue
UTF textureSignature
int latency
int gameMode
```

- `uuid` 必须稳定，否则客户端会看到重复或闪烁的 Tab 条目。
- `username` 建议遵守 Minecraft 玩家名限制，最多 16 字符。
- `textureValue` 和 `textureSignature` 可为空字符串。
- `latency` 为显示延迟，单位 ms。
- `gameMode` 使用原版数值：`0` survival，`1` creative，`2` adventure，`3` spectator；非法值会按 `0` 处理。

### `hide`

隐藏一个仍由后端服务器发出的虚拟玩家。ProxyTab 会在每轮渲染中移除该 UUID 的原始 Tab entry，适合实体仍在线但不应显示的场景。

```text
byte 1
UTF "hide"
long uuidMost
long uuidLeast
```

### `remove`

删除虚拟玩家状态。适合虚拟玩家实体已销毁或插件关闭时调用。

```text
byte 1
UTF "remove"
long uuidMost
long uuidLeast
```

## Bukkit/Paper 示例

```java
private static final String CHANNEL = "proxytab:virtual_players";

public void onEnable() {
    getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
}

private void sendUpdate(Player carrier, UUID uuid, String name, String textureValue, String textureSignature) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeByte(1);
        out.writeUTF("update");
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeUTF(name);
        out.writeUTF(textureValue == null ? "" : textureValue);
        out.writeUTF(textureSignature == null ? "" : textureSignature);
        out.writeInt(0);
        out.writeInt(0);
    } catch (IOException exception) {
        throw new UncheckedIOException(exception);
    }
    carrier.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
}
```

## 接入建议

- 后端插件应在虚拟玩家创建、皮肤变化、显示状态变化、移除、插件关闭时同步状态。
- 如果虚拟玩家不应显示，发送 `hide` 而不是只依赖后端隐藏包，这样 ProxyTab 可以清掉代理端已存在的原始条目。
- 如果虚拟玩家永久销毁，发送 `remove`，避免保留隐藏标记。
- 后端插件重载或玩家进入后，如果存在 carrier，先发送 `reset` 再发送当前全部 `update`/`hide` 状态。
