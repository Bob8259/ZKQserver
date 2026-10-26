# ZKQserver

ZKQserver 是一个运行在 Android Root 环境下的自动化客户端程序。它通过 `app_process` 以 `su` 权限启动，作为 WebSocket 客户端主动连接上游控制服务，并执行服务端下发的触摸模拟与文件系统操作指令。

> 本进程**不监听任何本地端口**。所有指令均由上游服务端通过 WebSocket 下发。

---

## 目录

- [运行环境](#运行环境)
- [启动方式](#启动方式)
- [连接上游服务](#连接上游服务)
- [响应格式](#响应格式)
- [API 说明](#api-说明)
  - [1. 连接测试](#1-连接测试)
  - [2. 触摸模拟](#2-触摸模拟)
  - [3. 文件操作](#3-文件操作)
- [连接行为](#连接行为)

---

## 运行环境

| 项目 | 说明 |
| --- | --- |
| 平台 | Android（需要 Root 权限） |
| 运行方式 | `app_process`，无传统 Android Context |
| 语言 | Kotlin |
| 构建产物 | APK（通过 `app_process` 直接执行） |

---

## 启动方式

```sh
CLASSPATH=/data/local/tmp/zkqserver.apk app_process /data/local/tmp com.coc.zkqserver.AppProcessClient
```

`com.coc.zkqserver.AppProcessClient` 是唯一的启动入口，启动后即以客户端身份连接上游服务。

---

## 连接上游服务

进程启动后**不监听任何端口**，而是作为 WebSocket 客户端主动连接上游服务：

| 项目 | 值 |
| --- | --- |
| 地址 | `ws://localhost:16839/zkq` |
| 协议 | WebSocket，文本帧，JSON 负载 |
| 重连间隔 | 2 秒 |

---

## 响应格式

所有命令均返回统一的 JSON 格式响应。

### 成功

```json
{
  "status": "success",
  "data": "..."
}
```

### 失败

```json
{
  "status": "error",
  "message": "..."
}
```

> 注意：`touch_action` 类命令始终返回 `{"status":"success","data":"null"}`。

---

## API 说明

所有命令均以 JSON 字符串发送，必须包含 `actionType` 字段。

---

### 1. 连接测试

#### 请求

```json
{
  "actionType": "connection_test"
}
```

#### 响应

```json
{
  "status": "success",
  "data": "connected"
}
```

---

### 2. 触摸模拟

`actionType` 固定为 `"touch_action"`，通过 `subAction` 指定具体操作。

#### 公共参数

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `actionType` | String | 固定为 `"touch_action"` |
| `subAction` | String | 操作类型，见下表 |
| `x`, `y` | Float | 屏幕坐标 |
| `id` | Int | 多点触控的指针标识 |

#### 按下（touchdown）

```json
{ "actionType": "touch_action", "subAction": "touchdown", "x": 500.0, "y": 500.0, "id": 1 }
```

#### 移动（touchmove）

```json
{ "actionType": "touch_action", "subAction": "touchmove", "x": 600.0, "y": 600.0, "id": 1 }
```

#### 抬起（touchup）

```json
{ "actionType": "touch_action", "subAction": "touchup", "id": 1 }
```

---

### 3. 文件操作

`actionType` 固定为 `"file_action"`，通过 `subAction` 指定具体操作。

#### 读取文件

```json
{
  "actionType": "file_action",
  "subAction": "read",
  "path": "/data/user/0/com.coc.zkqserver/files/test.txt"
}
```

#### 写入文件

```json
{
  "actionType": "file_action",
  "subAction": "write",
  "path": "/data/user/0/com.coc.zkqserver/files/new_file.txt",
  "content": "写入的内容"
}
```

#### 创建文件

```json
{
  "actionType": "file_action",
  "subAction": "create",
  "path": "/data/user/0/com.coc.zkqserver/files/empty_file.txt"
}
```

#### 删除文件

```json
{
  "actionType": "file_action",
  "subAction": "delete",
  "path": "/data/user/0/com.coc.zkqserver/files/old_file.txt"
}
```

#### 检查文件是否存在

```json
{
  "actionType": "file_action",
  "subAction": "check_exists",
  "path": "/data/user/0/com.coc.zkqserver/files/my_document.txt"
}
```

#### 复制文件

```json
{
  "actionType": "file_action",
  "subAction": "copy",
  "path": "/data/user/0/com.coc.zkqserver/files/source.txt",
  "destPath": "/data/user/0/com.coc.zkqserver/files/destination.txt"
}
```

#### 重命名文件

```json
{
  "actionType": "file_action",
  "subAction": "rename",
  "path": "/data/user/0/com.coc.zkqserver/files/old_name.txt",
  "newPath": "/data/user/0/com.coc.zkqserver/files/new_name.txt"
}
```

---

## 连接行为

启动后即以 WebSocket 客户端身份主动连接 `ws://localhost:16839/zkq`，不监听任何本地端口。

- 启动时自动尝试连接 `ws://localhost:16839/zkq`。
- 若连接失败，等待 **2 秒** 后重试，直到连接成功。
- 连接断开后按相同策略自动重连。

### 首次连接公告

首次成功连接后，客户端会向服务器发送以下消息（后续重连不再发送）：

```json
{
  "actionType": "client_connected",
  "message": "ZKQserver connected"
}
```

### 接收并处理命令

服务器通过 WebSocket 向客户端发送 JSON 命令帧，客户端根据 `actionType` 分发处理，并将结果响应回服务器。

| `actionType` | 是否返回响应 |
| --- | --- |
| `connection_test` | 是 |
| `file_action` | 是 |
| `touch_action`（及其他值） | 是 |

响应格式与[响应格式](#响应格式)章节一致。