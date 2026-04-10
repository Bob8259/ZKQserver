This documentation of the server. 
URL:localhost:6839
Connect to the server via websocket.


---

# Server API Documentation

This document outlines the JSON-based command structure for simulating touch interactions and performing file system operations.

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

*Note: Currently, `touch_action` commands do not return a response.*

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

## 3. Server Test

Used to test the connection to the server.

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

