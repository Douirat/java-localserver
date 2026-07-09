# Java LocalServer — Full Project Overview

> Branch: `automated-QA`  
> Language: Java (pure `java.nio`, no external frameworks)  
> Pattern: Event-driven, single-threaded, non-blocking I/O

---

## Table of Contents

1. [What the Project Is](#1-what-the-project-is)
2. [Directory Structure](#2-directory-structure)
3. [Architecture Overview](#3-architecture-overview)
4. [Full Request Lifecycle](#4-full-request-lifecycle)
5. [Package-by-Package Breakdown](#5-package-by-package-breakdown)
6. [State Machines](#6-state-machines)
7. [Design Patterns Used](#7-design-patterns-used)
8. [Data Flow Diagram](#8-data-flow-diagram)
9. [What Is Complete vs. Missing](#9-what-is-complete-vs-missing)

---

## 1. What the Project Is

This is a **from-scratch HTTP/1.1 server written in Java** using only the standard library — no Netty, no Jetty, no Spring. The goal is to understand how the internet works at the server level by building every piece manually:

- Opening raw TCP sockets
- Multiplexing thousands of clients on **one thread** using `java.nio.channels.Selector`
- Parsing raw HTTP bytes into structured request objects
- Routing requests to handler functions (like a mini Express.js or Spring MVC)
- Serializing Java objects to JSON without any library
- Serving binary files with zero-copy I/O
- Managing cookies and sessions
- Executing CGI scripts as child processes

The server **must never crash**, handle malformed input gracefully, and sustain **≥ 99.5% availability** under siege stress tests.

---

## 2. Directory Structure

```
java-localserver/
│
├── src/
│   ├── Main.java                          ← Entry point / app definition
│   │
│   ├── http/
│   │   ├── server/
│   │   │   ├── Server.java                ← Core NIO event loop
│   │   │   ├── ServerBuilder.java         ← Fluent API to configure Server
│   │   │   ├── Serving.java               ← Interface for Server
│   │   │   └── ServingBuilder.java        ← Interface for ServerBuilder
│   │   │
│   │   ├── connecting/
│   │   │   ├── Connection.java            ← Per-client state machine + parser
│   │   │   ├── Connecting.java            ← Interface for Connection
│   │   │   └── state/
│   │   │       ├── ConnectionState.java   ← Enum: READING/PROCESSING/WRITING_*/CLOSED
│   │   │       └── RequestState.java      ← Enum: REQUEST_LINE/HEADERS/BODY/COMPLETE
│   │   │
│   │   ├── request/
│   │   │   ├── Request.java               ← Parsed HTTP request object
│   │   │   └── Requesting.java            ← Interface for Request
│   │   │
│   │   ├── router/
│   │   │   ├── Router.java                ← Route table + static file serving
│   │   │   └── Routing.java               ← Interface for Router
│   │   │
│   │   ├── handler/
│   │   │   └── Handler.java               ← @FunctionalInterface: Request → Response
│   │   │
│   │   ├── response/
│   │   │   ├── Response.java              ← HTTP response object
│   │   │   ├── ResponseBuilder.java       ← Fluent builder for Response
│   │   │   ├── Responding.java            ← Interface for Response
│   │   │   ├── RespondingBuilder.java     ← Interface for ResponseBuilder
│   │   │   ├── cookie/Cookie.java         ← Set-Cookie model (name, value, domain, path, expires...)
│   │   │   ├── responseBody/
│   │   │   │   ├── Body.java              ← Interface: BodyType type()
│   │   │   │   ├── BodyType.java          ← Enum: MEMORY | FILE
│   │   │   │   ├── MemoryBody.java        ← In-memory body (JSON, text)
│   │   │   │   └── FileBody.java          ← FileChannel-backed body (static files)
│   │   │   └── status/
│   │   │       └── HttpStatusMessages.java ← Status code → reason phrase map
│   │   │
│   │   ├── json/
│   │   │   └── Serializer.java            ← Custom reflection-based JSON serializer
│   │   │
│   │   ├── admin/AdminDashboard.java      ← Server metrics endpoint (bonus)
│   │   ├── cgi/CgiHandler.java            ← ProcessBuilder-based CGI executor
│   │   ├── config/ConfigLoader.java       ← JSON config file parser
│   │   ├── file/FileHandler.java          ← Upload/Delete handler
│   │   ├── session/
│   │   │   ├── Session.java               ← Session data object
│   │   │   └── SessionManager.java        ← Server-side session store
│   │   ├── transfer/ChunkedDecoder.java   ← Transfer-Encoding: chunked parser
│   │   └── upload/
│   │       ├── MultipartParser.java       ← multipart/form-data parser
│   │       └── MultipartPart.java         ← One part of a multipart body
│   │
│   └── model/User.java                    ← Example domain model (POJO)
│
├── error_pages/                           ← Custom HTML error pages (400,403,404,405,413,500)
├── static/                                ← Static file root (served at /static/*)
├── test/                                  ← Shell scripts for manual testing
│
├── config.json                            ← Server configuration file
├── run.sh                                 ← Build & run script
├── README.md
├── Subject.md                             ← Project requirements (evaluation rubric)
├── GAP_ANALYSIS.md                        ← What's missing vs requirements
├── COMPLETION_PLAN.md                     ← Ordered implementation plan
└── PROJECT_OVERVIEW.md                    ← This file
```

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         ONE THREAD                              │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   Selector (epoll/kqueue)                │  │
│  │                                                          │  │
│  │  ServerSocketChannel(port 8080)  ──► OP_ACCEPT           │  │
│  │  ServerSocketChannel(port 9090)  ──► OP_ACCEPT           │  │
│  │  SocketChannel(client A)         ──► OP_READ             │  │
│  │  SocketChannel(client B)         ──► OP_WRITE            │  │
│  │  SocketChannel(client C)         ──► OP_READ             │  │
│  └──────────────────────────────────────────────────────────┘  │
│         │                                                       │
│         ▼  selector.select() blocks until at least one ready    │
│  ┌──────────────┐                                               │
│  │ Event Loop   │ for each ready key:                           │
│  │              │   isAcceptable? → accept new client           │
│  │              │   isReadable?   → read bytes, parse request   │
│  │              │   isWritable?   → write response bytes        │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

**Key principle:** Nothing blocks. Every read/write is done _only_ when the OS signals it is ready. The `Selector` is the heart of the entire server.

---

## 4. Full Request Lifecycle

Step-by-step from browser sends a request to it receives a response:

```
Browser                   Selector Loop              Connection           Router
  │                           │                          │                  │
  │── TCP SYN ──────────────► │                          │                  │
  │                    isAcceptable()                    │                  │
  │                    channel.accept()                  │                  │
  │                    new Connection(socketChannel)     │                  │
  │                    register(OP_READ)                 │                  │
  │                           │                          │                  │
  │── GET /api/users ────────►│                          │                  │
  │   HTTP/1.1                │                          │                  │
  │                    isReadable()                      │                  │
  │                    channel.read(buffer) ────────────►│                  │
  │                                              ParseRequest()             │
  │                                              parseHeaders(lines)        │
  │                                              parseBody(offset)          │
  │                                              state = PROCESSING         │
  │                           │                          │                  │
  │                    router.serve(request) ────────────┼─────────────────►│
  │                                                      │         matchRoute()
  │                                                      │         handler.handle(req)
  │                                                      │◄─────────────────│
  │                    connection.prepareResponse()      │                  │
  │                    register(OP_WRITE)                │                  │
  │                           │                          │                  │
  │                    isWritable()                      │                  │
  │                    channel.write(buffer)             │                  │
  │◄── HTTP/1.1 200 OK ───────│                          │                  │
  │◄── {"name":"Alice"...} ───│                          │                  │
  │                    buffer empty → register(OP_READ)  │                  │
```

---

## 5. Package-by-Package Breakdown

---

### 5.1 Entry Point — `Main.java`

This is the application definition. It uses the fluent `ServerBuilder` API to wire up routes and start the server. Think of it as the `main()` of an Express.js app.

```java
Server server = new ServerBuilder()
    .port(8080)   // bind to port 8080

    // GET /api/cookies — reads incoming cookies, sets a new Set-Cookie header
    .get("/api/cookies", (Request request) -> {
        Map<String, String> cookies = request.getCookies();  // read all cookies from browser
        Response response = new Response();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setBody(cookies);                // serialize cookie map as JSON

        Cookie cookie = new Cookie("sessionId", "abc123");  // create a new cookie
        cookie.setDomain("localhost");
        cookie.setPath("/api");
        cookie.setExpires(new Date(now + 24h));
        cookie.setHttpOnly(true);
        response.addCookie(cookie);               // attach Set-Cookie to response
        return response;
    })

    // POST /api/users — accepts body, returns a User object as JSON
    .post("/api/users", (Request request) -> { ... })

    // GET /api/users — returns JSON array of User objects
    .get("/api/users", (Request request) -> { ... })

    // Dynamic path variables extracted from URL segments
    .get("/api/posts/{postId}/users/{userId}", (Request request) -> {
        String postId = request.getPathVariables().get("postId");
        String userId = request.getPathVariables().get("userId");
        ...
    })
    .build();

server.start();  // enters the NIO event loop — never returns
```

---

### 5.2 `http.server` — Server & Builder

#### `Server.java` — The NIO Event Loop

The core of the server. Owns the `Selector` and runs the infinite loop.

```java
// Fields
Set<Integer> ports   // all ports to listen on
Routing router       // the route dispatch table

public void start() {

    // 1. Open a Selector (OS-level I/O multiplexer)
    //    Linux  → epoll
    //    macOS  → kqueue
    //    Windows → select / IOCP wrapper
    var selector = Selector.open();

    // 2. For each port: open a server socket and register it
    for (int port : ports) {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);            // MUST be non-blocking for Selector
        server.bind(new InetSocketAddress(port));   // bind to OS port
        server.register(selector, OP_ACCEPT);       // tell selector: wake on new connections
    }

    // 3. Infinite event loop
    while (true) {
        selector.select();   // blocks until at least one channel is ready

        for (var key : selector.selectedKeys()) {

            // NEW CONNECTION: a browser connected
            if (key.isAcceptable()) {
                SocketChannel client = serverChannel.accept();
                client.configureBlocking(false);
                Connection connection = new Connection(client);  // per-client state
                client.register(selector, OP_READ, connection); // watch for data
            }

            // INCOMING DATA: client sent HTTP bytes
            if (key.isReadable()) {
                Connection connection = (Connection) key.attachment();
                int bytes = channel.read(connection.getBuffer());

                if (bytes == -1) { channel.close(); key.cancel(); }  // client disconnected

                connection.ParseRequest();  // try to parse what's in the buffer

                if (connection.getRequestState() == COMPLETE) {
                    Response response = router.serve(connection.getRequest());

                    if (response.isStatic()) {
                        // File path: write headers first, then use transferTo() for body
                        connection.setAsStaticResponse();
                        connection.loadBuffer(headers.getBytes());
                    } else {
                        // JSON path: serialize everything into one ByteBuffer
                        connection.prepareResponse();
                    }
                    key.interestOps(OP_WRITE);  // switch: now watch for write-readiness
                }
            }

            // WRITE READY: OS buffer has space, we can send bytes
            if (key.isWritable()) {
                if (connection.isStaticResponse()) {
                    if (state == WRITING_HEADERS) {
                        channel.write(buffer);           // send HTTP headers
                        if (!buffer.hasRemaining()) state = WRITING_BODY;
                    } else if (state == WRITING_BODY) {
                        // Zero-copy: OS moves bytes from disk to socket directly
                        long sent = fileChannel.transferTo(filePosition, remaining, channel);
                        filePosition += sent;
                        if (filePosition >= fileSize) { channel.close(); key.cancel(); }
                    }
                } else {
                    channel.write(buffer);               // send JSON/text response
                    if (!buffer.hasRemaining()) key.interestOps(OP_READ);  // back to reading
                }
            }
        }
        selector.selectedKeys().clear();  // MUST clear after each iteration
    }
}
```

#### `ServerBuilder.java` — Fluent Configuration API

Implements the Builder pattern. Every method returns `this` so calls chain:

```java
new ServerBuilder()           // creates blank Server
    .port(8080)               // calls server.setPort(8080)
    .get(path, handler)       // router.addRoute("GET", path, handler)
    .post(path, handler)      // router.addRoute("POST", path, handler)
    .delete(path, handler)    // router.addRoute("DELETE", path, handler)
    .serveStatic("static")    // sets the static root directory
    .build()                  // returns the fully configured Server
```

---

### 5.3 `http.connecting` — Connection & States

#### `Connection.java` — Per-Client State Machine + Parser

Each accepted TCP client gets exactly **one** `Connection` object. It holds the socket channel, read/write buffer, the partially-parsed request, the response, and the current state.

```java
// ParseRequest() — called every time bytes arrive from a client
// Implements incremental parsing — safe to call multiple times if data
// arrives in TCP fragments.

void ParseRequest() {
    buffer.flip();   // switch ByteBuffer from write-mode to read-mode

    // 1. Scan for \r\n\r\n (blank line separating headers from body)
    int headerEnd = findDoubleCRLF(buffer);

    if (headerEnd == -1) {
        buffer.compact();   // headers not fully arrived — keep reading
        return;
    }

    // 2. Extract header section as a String and split into lines
    String headersText = new String(headerBytes, UTF_8);
    String[] lines = headersText.split("\r\n");

    // 3. Parse request line + headers → returns Content-Length
    int contentLength = parseHeaders(lines);

    // 4. Check if body is fully received
    if (contentLength > 0) {
        parseBody(headerEnd + 4);    // +4 to skip the \r\n\r\n
    } else {
        requestState = COMPLETE;     // no body → done immediately
    }
}

// parseHeaders(lines)
// Line 0: "GET /api/users HTTP/1.1" → sets method, path, version
// Line 1+: "Host: localhost" → stored in request.headers map
// Returns Content-Length value (0 if not present)

// parseBody(bodyStart)
// Reads exactly Content-Length bytes from the buffer into request.body
// If not enough bytes yet → buffer.compact(), wait for more TCP data

// prepareResponse()
// 1. Serializer.toJson(response.getBody()) → JSON string
// 2. Build raw HTTP header string (status line + headers + blank line)
// 3. Concatenate: headers + JSON body
// 4. buffer.clear() → buffer.put(bytes) → buffer.flip() (ready to write)

// prepareHeaders(bodySize)
// Builds: "HTTP/1.1 200 OK\r\nContent-Type: ...\r\nContent-Length: 42\r\n\r\n"
// Also appends Set-Cookie lines for each cookie in the response
```

---

### 5.4 `http.request` — Request Parsing

#### `Request.java` — Parsed HTTP Request Object

A plain data holder for everything extracted from raw HTTP bytes:

```java
String method            // "GET", "POST", "DELETE", etc.
String path              // "/api/users" (query string stripped)
String version           // "HTTP/1.1"
Map<String,String> headers          // "Host" → "localhost:8080"
Map<String,String> queryParameters  // "?name=alice" → {"name":"alice"}
Map<String,String> pathVariables    // "/users/{id}" → {"id":"42"}
Map<String,String> cookies          // parsed from "Cookie:" header
byte[] body              // raw body bytes

// setRequestLine(["GET", "/api/users?name=alice", "HTTP/1.1"])
// → detects "?" → splits into path + queryParameters
// → stores method, path, version

// All getters return Collections.unmodifiableMap() — no external mutation
```

---

### 5.5 `http.router` — Routing Engine

#### `Router.java` — Route Table + Static File Serving

Two maps answer: **"Which handler runs for this request?"**

```java
Map<String, Map<String, Handler>> routes         // exact match (HashMap O(1) lookup)
Map<String, Map<String, Handler>> dynamicRoutes  // pattern match (regex loop)

// serve(Request)
// 1. GET + path starts with "/static/" → call serveFile()
// 2. matchRoute(request) → exact match first, then dynamic
// 3. No match → return 404

// matchRoute(Request)
// Step 1: routes["GET"]["/api/users"] — direct HashMap.get(), instant
// Step 2: loop dynamicRoutes["GET"]:
//   pattern "/api/posts/{postId}/users/{userId}"
//   → convert to regex "/api/posts/([^/]+)/users/([^/]+)"
//   → run Pattern.matcher(actualPath)
//   → if match: extract group(1)→"postId", group(2)→"userId"
//   → store in request.pathVariables
//   → return the handler

// serveFile(Path)
// Security: resolve path then check it's still inside static/ root
//           (prevents ../../etc/passwd path traversal attacks)
// → 404 if file doesn't exist
// → 403 if path escapes root or file not readable
// → 404 if path is a directory (not a regular file)
// → Open FileChannel, detect MIME type from extension
// → Return Response{FileBody} — signals Server to use zero-copy transferTo()

// 50+ MIME types defined in a static Map:
// "html"→"text/html; charset=UTF-8"
// "png" →"image/png"
// "mp4" →"video/mp4"
// "wasm"→"application/wasm"
// ... etc.
```

---

### 5.6 `http.handler` — Handler Contract

#### `Handler.java`

```java
@FunctionalInterface
public interface Handler {
    Response handle(Request request);
}
```

`@FunctionalInterface` means any lambda `(Request req) -> { return response; }` satisfies this interface automatically. This is the same concept as Express.js `(req, res) => {}` — a route handler is just a function.

---

### 5.7 `http.response` — Response & Builder

#### `Response.java` — HTTP Response Object

```java
String version          // "HTTP/1.1" (copied from request)
int status              // 200, 404, 500, etc.
String statusReason     // auto-derived: 404 → "Not Found"
Map<String,String> headers   // response headers
List<Cookie> cookies         // Set-Cookie values
boolean isStaticResponse     // true → Server uses FileChannel.transferTo()
Body body               // MemoryBody (JSON) or FileBody (binary)

// setStatus(int status) also calls HttpStatusMessages.getMessage(status)
// to auto-populate the reason phrase

// setBody(Object data) wraps in MemoryBody — Serializer converts to JSON later
// setBody(Body body) sets FileBody for binary files
```

#### `ResponseBuilder.java` — Fluent Builder

```java
new ResponseBuilder()
    .setStatus(404)
    .setHeader("Content-Type", "text/plain")
    .setBody("Not Found")
    .build();    // returns the configured Response
```

---

### 5.8 `http.response.cookie` — Cookie Model

#### `Cookie.java` — Full Set-Cookie Model

```java
String name        // cookie identifier
String value       // cookie data
String domain      // which domain can read it ("myapp.com")
String path        // which URL paths it applies to ("/api")
Date   expires     // when it expires (null = session cookie)
boolean secure     // only send over HTTPS
boolean httpOnly   // JS cannot read it (XSS protection)
String sameSite    // "Lax"/"Strict"/"None" (CSRF protection)
```

`Connection.prepareHeaders()` serialises this into:
```
Set-Cookie: sessionId=abc123; Domain=localhost; Path=/api; Expires=...; HttpOnly
```

---

### 5.9 `http.response.responseBody` — Body Types

The `Body` interface and its two implementations handle two fundamentally different response strategies:

```java
interface Body { BodyType type(); }  // MEMORY or FILE

// MemoryBody — for JSON, text, HTML strings
class MemoryBody implements Body {
    Object data;    // any Java object → Serializer converts to JSON string
}

// FileBody — for images, videos, downloads, static HTML files
class FileBody implements Body {
    FileChannel channel;  // open file handle
    // Server uses fileChannel.transferTo() → zero-copy, no heap memory used
}
```

The `BodyType` enum controls which write path `Server.java` takes:
- `MEMORY` → `prepareResponse()` → buffer → `channel.write(buffer)`
- `FILE` → `setAsStaticResponse()` → headers buffer → `fileChannel.transferTo()`

---

### 5.10 `http.json` — Custom JSON Serializer

#### `Serializer.java` — Reflection-based JSON

Zero-dependency JSON serializer. Handles all types recursively:

```java
toJson(null)                             → "null"
toJson("hello")                          → "\"hello\""
toJson(42)                               → "42"
toJson(true)                             → "true"
toJson(new int[]{1,2})                   → "[1,2]"
toJson(List.of("a","b"))                 → "[\"a\",\"b\"]"
toJson(Map.of("k","v"))                  → "{\"k\":\"v\"}"
toJson(MyEnum.VALUE)                     → "\"VALUE\""

// For any POJO:
// getDeclaredFields() → introspect all private fields
// field.setAccessible(true) → bypass private access
// Walks up superclass chain to include inherited fields
// Recursively serializes each field value

toJson(new User("Alice", 28, "alice@example.com"))
// → {"name":"Alice","age":28,"email":"alice@example.com"}
```

---

### 5.11 `model` — Domain Models

#### `User.java`

A plain Java object (POJO) used to demonstrate JSON serialization:
```java
String name;
int    age;
String email;
```
No annotations needed — `Serializer` converts it directly via reflection.

---

### 5.12 New Modules (Added in Feature Branch)

| Module | File | What It Does |
|---|---|---|
| Config | `http/config/ConfigLoader.java` | Parses `config.json` at startup: ports, routes, error pages, body limits, CGI extensions |
| CGI | `http/cgi/CgiHandler.java` | Spawns Python/Perl via `ProcessBuilder`, passes HTTP env vars, reads stdout as response |
| Session | `http/session/SessionManager.java` | Generates UUID session IDs, stores `Map<sessionId, Session>` in memory, reads `Cookie` header |
| Session | `http/session/Session.java` | Key-value store per session with creation/last-access timestamps |
| Upload | `http/upload/MultipartParser.java` | Parses `multipart/form-data` boundary, extracts file parts, saves to disk |
| Upload | `http/upload/MultipartPart.java` | Represents one part (filename, content-type, raw bytes) |
| Chunked | `http/transfer/ChunkedDecoder.java` | Decodes `Transfer-Encoding: chunked` per RFC 7230 |
| File | `http/file/FileHandler.java` | Handles POST uploads and DELETE file removal with permission checks |
| Admin | `http/admin/AdminDashboard.java` | Exposes `/admin/metrics`: active connections, total requests, uptime |
| Error pages | `error_pages/*.html` | Custom HTML for 400, 403, 404, 405, 413, 500 |

---

## 6. State Machines

### 6.1 `ConnectionState` — What the connection is doing

```
             ┌─────────┐
             │ READING │  ← initial state, bytes arriving from client
             └────┬────┘
                  │ request fully received (requestState == COMPLETE)
                  ▼
            ┌────────────┐
            │ PROCESSING │  ← router.serve() called here
            └─────┬──────┘
                  │ response prepared
                  ▼
         ┌─────────────────┐
         │ WRITING_HEADERS │  ← channel.write(headerBuffer)
         └────────┬────────┘
                  │ headers fully sent
         ┌────────▼────────┐
         │  WRITING_BODY   │  ← fileChannel.transferTo() [static files only]
         └────────┬────────┘
                  │ all bytes sent
                  ▼
             ┌────────┐
             │ CLOSED │  ← channel.close(), key.cancel()
             └────────┘
```

> For JSON/text responses: `WRITING_BODY` is skipped — headers and body are in one buffer.

### 6.2 `RequestState` — How far along parsing is

```
REQUEST_LINE → HEADERS → BODY → COMPLETE

REQUEST_LINE : "GET /path HTTP/1.1" not yet seen
HEADERS      : request line parsed, reading header key:value lines
BODY         : all headers parsed, accumulating body bytes
COMPLETE     : entire request in memory, ready to route
```

---

## 7. Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Builder** | `ServerBuilder`, `ResponseBuilder` | Readable fluent API for complex configuration |
| **Strategy** | `Handler` (@FunctionalInterface) | Route handlers are swappable lambdas |
| **State Machine** | `Connection` + `ConnectionState` + `RequestState` | Safe management of multi-step async I/O |
| **Template Method** | `Body` → `MemoryBody`/`FileBody` | Common interface, different write strategies |
| **Singleton** | `Router` inside `Server` | One shared route table per server instance |
| **Interface Segregation** | `Serving`, `Routing`, `Connecting`, `Requesting`, `Responding` | Each class exposes only its relevant contract |

---

## 8. Data Flow Diagram

```
                   ┌────────────────────────────────┐
                   │          Main.java              │
                   │  ServerBuilder → routes → Server│
                   └────────────────┬───────────────-┘
                                    │ server.start()
                                    ▼
                   ┌────────────────────────────────┐
                   │          Server.java            │
                   │        NIO Selector Loop        │
                   │  OP_ACCEPT → new Connection     │
                   │  OP_READ   → ParseRequest()     │
                   │  OP_WRITE  → write / transferTo │
                   └────┬───────────────────-┬───────┘
                        │                    │
               ParseRequest()         router.serve()
                        ▼                    ▼
         ┌──────────────────────┐  ┌───────────────────────┐
         │   Connection.java    │  │      Router.java       │
         │  Buffer management   │  │  routes HashMap O(1)  │
         │  Header parsing      │  │  dynamicRoutes regex  │
         │  Body parsing        │  │  serveFile + MIME     │
         │  Response assembly   │  └──────────┬────────────┘
         └──────────────────────┘             │ handler.handle(req)
                                              ▼
                                   ┌────────────────────┐
                                   │ Lambda in Main.java │
                                   │ (Request req) ->    │
                                   │   new Response(...) │
                                   └──────────┬──────────┘
                                              ▼
                                   ┌────────────────────┐
                                   │   Response.java     │
                                   │ status, headers,    │
                                   │ cookies, Body       │
                                   └────────┬────────────┘
                                            │
                             ┌──────────────┴──────────────┐
                             │                             │
                        MemoryBody                    FileBody
                             │                             │
                    Serializer.toJson()        FileChannel.transferTo()
                             │                             │
                        ByteBuffer                    zero-copy
                      channel.write()              socket write
```

---

## 9. What Is Complete vs. Missing

| Feature | Status | File |
|---|---|---|
| NIO Selector event loop | ✅ Done | `Server.java` |
| HTTP request parsing | ✅ Done | `Connection.java` |
| GET / POST / PUT / DELETE / PATCH routing | ✅ Done | `Router.java` |
| Dynamic path variables `{id}` | ✅ Done | `Router.java` |
| Static file serving + MIME types | ✅ Done | `Router.java` |
| Zero-copy `FileChannel.transferTo()` | ✅ Done | `Server.java` |
| Cookie send (`Set-Cookie`) | ✅ Done | `Cookie.java` |
| JSON serializer (no libs) | ✅ Done | `Serializer.java` |
| Query string parsing | ✅ Done | `Request.java` |
| Multi-port support | ✅ Done | `Server.java` |
| Path traversal protection | ✅ Done | `Router.java` |
| Config file (`config.json`) | ✅ Added | `ConfigLoader.java` |
| Custom error pages (400-500) | ✅ Added | `error_pages/` |
| Session management | ✅ Added | `SessionManager.java` |
| File upload (multipart) | ✅ Added | `MultipartParser.java` |
| Chunked transfer decoding | ✅ Added | `ChunkedDecoder.java` |
| CGI execution (Python + Perl) | ✅ Added | `CgiHandler.java` |
| Admin dashboard metrics | ✅ Added | `AdminDashboard.java` |
| Cookie receive (parse `Cookie:` header) | ⚠️ Partial | `Request.java` has map, `parseHeaders()` doesn't populate it |
| HTTP redirections (301/302) | ⚠️ Partial | `Response` supports status, no config-driven redirect |
| Directory listing | ⚠️ Missing | `Router.serveFile()` returns 404 for dirs |
| Default index file for directories | ⚠️ Missing | Not implemented |
| Body size limit (413) | ⚠️ Missing | No size check in `parseBody()` |
| Connection timeout (408) | ⚠️ Missing | No idle connection sweep |
| Virtual hostname routing | ⚠️ Missing | `Host` header not dispatched |
| 405 Method Not Allowed | ⚠️ Missing | All mismatches return 404 |
| Malformed request crash guard | ⚠️ Missing | `RuntimeException` propagates to server loop |
| `selectedKeys()` iterator fix | ⚠️ Missing | Direct for-each risks `ConcurrentModificationException` |
| Siege stress test ≥ 99.5% | ⏳ Untested | Run `siege -b [IP]:[PORT]` to verify |
