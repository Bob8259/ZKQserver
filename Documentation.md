This documentation covers the ZKQserver client. The process does **not** listen on any socket; it connects out to an upstream server and executes the commands that server sends.

Transport: Unix domain socket, default name `zkqRootServer`, newline-delimited JSON.


---

# Client Command Reference

This document outlines the JSON-based command structure for simulating touch interactions and performing file system operations. These are the commands the upstream server sends to the client.

## 0. Response Format

Most commands return a JSON response to indicate the result of the operation.

### Success Response
```json
{
  "status": "success",
  "data": "..." 
}
```

### Error Response
```json
{
  "status": "error",
  "message": "..."
}
```

*Note: `touch_action` commands return `{"status":"success","data":"null"}` on success.*

---

## 1. Touch Simulation Commands

All touch simulation commands are **JSON strings**. Each command must include an `actionType` of `"touch_action"` and a `subAction` field.

### Common Parameters

| Parameter | Type | Description |
| --- | --- | --- |
| **actionType** | String | Always `"touch_action"`. |
| **subAction** | String | Specifies the action (e.g., `"touchdown"`, `"touchmove"`, `"touchup"`). |
| **x, y** | Float | Screen coordinates. |
| **id** | Int | Pointer identifier for multi-touch. |

---

### Basic Touch Commands

#### **Touch Down**

Simulates a finger pressing the screen.

* **Syntax:**
```json
{ "actionType": "touch_action", "subAction": "touchdown", "x": Float, "y": Float, "id": Int }

```


* **Example:**
```json
{ "actionType": "touch_action", "subAction": "touchdown", "x": 500.0, "y": 500.0, "id": 1 }

```



#### **Touch Move**

Moves an active pointer to a new location.

* **Syntax:**
```json
{ "actionType": "touch_action", "subAction": "touchmove", "x": Float, "y": Float, "id": Int }
```


* **Example:**
```json
{ "actionType": "touch_action", "subAction": "touchmove", "x": 600.0, "y": 600.0, "id": 1 }
```



#### **Touch Up**

Lifts an active pointer from the screen.

* **Syntax:**
```json
{ "actionType": "touch_action", "subAction": "touchup", "id": Int }

```


* **Example:**
```json
{ "actionType": "touch_action", "subAction": "touchup", "id": 1 }

```



---

---

## 2. File Actions

Commands used for interacting with the device file system. These use `actionType: "file_action"`.

### Read File

```json
{
  "actionType": "file_action",
  "subAction": "read",
  "path": "/data/user/0/com.coc.zkqserver/files/test.txt"
}

```

### Write File

```json
{
  "actionType": "file_action",
  "subAction": "write",
  "path": "/data/user/0/com.coc.zkqserver/files/new_file.txt",
  "content": "Hello, this is some content to write."
}

```

### Create File

```json
{
  "actionType": "file_action",
  "subAction": "create",
  "path": "/data/user/0/com.coc.zkqserver/files/empty_file.txt"
}

```

### Delete File

```json
{
  "actionType": "file_action",
  "subAction": "delete",
  "path": "/data/user/0/com.coc.zkqserver/files/old_file.txt"
}

```

### Check File Existence

```json
{
  "actionType": "file_action",
  "subAction": "check_exists",
  "path": "/data/user/0/com.coc.zkqserver/files/my_document.txt"
}

```

### Copy File

```json
{
  "actionType": "file_action",
  "subAction": "copy",
  "path": "/data/user/0/com.coc.zkqserver/files/source.txt",
  "destPath": "/data/user/0/com.coc.zkqserver/files/destination.txt"
}

```

### Rename File

```json
{
  "actionType": "file_action",
  "subAction": "rename",
  "path": "/data/user/0/com.coc.zkqserver/files/old_name.txt",
  "newPath": "/data/user/0/com.coc.zkqserver/files/new_name.txt"
}
```
---

## 3. Connection Test

Used to verify the link to the upstream server.

### Connection Test

* **Request:**
```json
{
  "actionType": "connection_test"
}
```

* **Response:**
```json
{
  "status": "success",
  "data": "connected" 
}
```

---

## 4. Client Mode (app_process)

When launched via `app_process`, the app runs purely as a client. It dials the upstream server over a **Unix domain socket** and opens no listening socket of its own. The entry point is `com.coc.zkqserver.AppProcessClient`.

### Launch Command

```sh
# Default socket name (zkqRootServer)
CLASSPATH=/data/local/tmp/zkqserver.apk app_process /data/local/tmp com.coc.zkqserver.AppProcessClient

# Custom socket name
CLASSPATH=/data/local/tmp/zkqserver.apk app_process /data/local/tmp com.coc.zkqserver.AppProcessClient mySocketName
```

The first positional argument is optional and selects the socket name. It defaults to `zkqRootServer`.

| Argument | Namespace | Notes |
| --- | --- | --- |
| `zkqRootServer` (default) | abstract | Bare name in the Linux abstract namespace. Needs no filesystem permission, and the name disappears automatically when the process exits. |
| `/data/local/tmp/zkq.sock` | filesystem | Absolute path. Requires write access to the parent directory, and the server must unlink a stale socket file when restarting. |

### Wire Format

Frames are newline-delimited JSON (NDJSON): one JSON object followed by a single `\n` byte, in both directions. JSON string escaping guarantees a payload never contains a raw newline, so the delimiter stays unambiguous. The client buffers at most 1 MiB per frame and drops the remainder of anything larger.

This replaces the WebSocket text frames used by earlier versions. WebSocket is an HTTP upgrade layered on TCP and cannot be carried over a Unix domain socket, so the transport changed rather than being wrapped.

### Connection Behavior

- On startup, the client attempts to connect to the configured socket.
- If the connection fails for any reason, it waits **2 seconds** and retries. This loop runs indefinitely until a connection is established.
- Reconnection follows the same retry policy if the server drops the connection.

### Announcement Message

Immediately after the **first** successful connection, the client sends the following frame to the server:

```json
{
  "actionType": "client_connected",
  "message": "ZKQserver connected"
}
```

No announcement is sent on subsequent reconnections.

### Receiving Actions from the Server

The server writes NDJSON frames to the socket. The client reads each frame, dispatches on its `actionType` field, and writes a response frame back over the same connection.

| `actionType` | Returns response? |
| --- | --- |
| `connection_test` | Yes |
| `file_action` | Yes |
| `touch_action` (or any other value) | Yes |

#### connection_test

```json
{ "actionType": "connection_test" }
```

Response:

```json
{ "status": "success", "data": "connected" }
```

#### file_action

The server sends any of the file sub-actions defined in section 2. Examples:

```json
{ "actionType": "file_action", "subAction": "read", "path": "/data/local/tmp/example.txt" }
```

```json
{ "actionType": "file_action", "subAction": "write", "path": "/data/local/tmp/example.txt", "content": "hello" }
```

```json
{ "actionType": "file_action", "subAction": "create", "path": "/data/local/tmp/new.txt" }
```

```json
{ "actionType": "file_action", "subAction": "delete", "path": "/data/local/tmp/old.txt" }
```

```json
{ "actionType": "file_action", "subAction": "check_exists", "path": "/data/local/tmp/file.txt" }
```

```json
{ "actionType": "file_action", "subAction": "copy", "path": "/data/local/tmp/src.txt", "destPath": "/data/local/tmp/dst.txt" }
```

```json
{ "actionType": "file_action", "subAction": "rename", "path": "/data/local/tmp/old.txt", "newPath": "/data/local/tmp/new.txt" }
```

See section 2 for the full field reference and expected responses.

#### touch_action

The server sends any of the touch sub-actions defined in section 1. Examples:

```json
{ "actionType": "touch_action", "subAction": "touchdown", "x": 500.0, "y": 800.0, "id": 1 }
```

```json
{ "actionType": "touch_action", "subAction": "touchmove", "x": 520.0, "y": 820.0, "id": 1 }
```

```json
{ "actionType": "touch_action", "subAction": "touchup", "id": 1 }
```

Response for all touch sub-actions:

```json
{ "status": "success", "data": "null" }
```

See section 1 for the full field reference.

### Response Format

Responses follow the same format as the server API (see section 0):

```json
{ "status": "success", "data": "..." }
```

or

```json
{ "status": "error", "message": "..." }
```
