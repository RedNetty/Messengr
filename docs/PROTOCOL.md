# Messengr Wire Protocol

This document describes the text-based, line-oriented protocol used between the
Messengr server and any compatible client.  Each message is a single UTF-8 line
terminated by `\n` (a `println` / `readLine` pair).

---

## Connection lifecycle

```
Client                            Server
  |                                 |
  |------ TCP connect ------------->|
  |<----- (no greeting) ------------|   ← server waits for AUTH
  |------ AUTH:<username> --------->|
  |<----- AUTH_SUCCESS:<u>,<id> ----|
  |<----- SERVER:<welcome text> ----|
  |                                 |
  |        … normal session …       |
  |                                 |
  |------ DISCONNECT -------------->|   ← clean client exit
  |<----- (connection closed) ------|
```

---

## Message formats

### `MSG` — Chat message

Sent by the **client** to broadcast a message to the current room.

```
MSG:<room>:<text>
```

| Field  | Description                                    |
|--------|------------------------------------------------|
| `room` | Destination room name (e.g. `general`)         |
| `text` | The message body (may contain spaces/unicode)  |

When the server **delivers** a chat message to other clients it reformats it:

```
MSG:[HH:mm:ss] <username>: <text>
```

---

### `CMD` — Command

Sent by the **client** to invoke a server command.

```
CMD:<action> [arguments]
```

| Action              | Arguments           | Description                          |
|---------------------|---------------------|--------------------------------------|
| `help`              | –                   | Print available commands             |
| `users`             | –                   | List users in the current room       |
| `rooms`             | –                   | List all rooms                       |
| `join <room>`       | room name           | Join or switch to a room             |
| `create <room> [desc]` | room name + optional description | Create a new room |
| `msg <user> <text>` | target username + message | Send a private message        |
| `pm <user> <text>`  | alias for `msg`     |                                      |
| `stats`             | –                   | Print server statistics              |
| `kick <user>`       | username            | Kick a user (admin only)             |

---

### `AUTH` / `AUTH_SUCCESS` — Authentication

**Client → Server**

```
AUTH:<username>
```

Admin login (requires `ADMIN_PASSWORD` env var set on the server):

```
AUTH:<username>:admin:<password>
```

**Server → Client** (on success)

```
AUTH_SUCCESS:<username>,<uuid>
```

| Field      | Description                                        |
|------------|----------------------------------------------------|
| `username` | Possibly suffixed with a counter if a collision exists (e.g. `alice1`) |
| `uuid`     | The server-assigned UUID for this session          |

On failure the server sends an `ERROR:` line and closes the connection.

---

### `PRIVATE` / `PRIVATE_SENT` — Private messages

**Delivered to recipient:**

```
PRIVATE:[HH:mm:ss] <sender> -> <recipient>: <text>
```

**Echo to sender:**

```
PRIVATE_SENT:[HH:mm:ss] To <recipient>: <text>
```

---

### `SERVER` — Server notification

Informational messages originating from the server (joins, leaves, etc.).

```
SERVER:<text>
```

---

### `ERROR` — Error response

```
ERROR:<human-readable description>
```

Sent when a client request cannot be fulfilled (unknown command, permission
denied, room not found, etc.).

---

### `DISCONNECT` — Disconnection signal

Sent by **either** side to initiate a clean shutdown of the session.

```
DISCONNECT
```

When sent by the **server** it is preceded by a `SERVER:` shutdown notice.

---

### `KICKED` — Forceful removal

```
KICKED:<reason>
```

Sent to a client immediately before the server forcefully closes the connection.

---

### `HISTORY` — Replay of recent messages on join

On successful authentication the server replays the last 10 global messages:

```
HISTORY:<original MSG line>
```

---

## Notes for implementors

* All messages use **`:`** as the first delimiter.  Fields after the prefix may
  themselves contain colons.  Use a `split(":", 3)` / `split(":", 2)` strategy
  depending on the message type.
* The server assigns a unique username if the requested one is already taken
  (suffix `1`, `2`, …).
* The `general` room always exists and cannot be deleted.
* Admin privileges are granted at authentication time only; there is no
  privilege-escalation command.
