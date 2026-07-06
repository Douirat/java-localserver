# Java LocalServer — Gap Analysis
> Based on [Subject.md](file:///c:/Users/exe/Desktop/java-localserver/Subject.md) requirements vs the current codebase.

---

## ✅ What Is Already Done

| Feature | Where |
|---|---|
| Non-blocking I/O with `Selector` (NIO) | [Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java) |
| Single `select()` loop for all clients | [Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java#L82-L215) |
| Accept, read, write via the selector | [Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java#L91-L210) |
| HTTP request parsing (request line, headers, body) | [Connection.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/connecting/Connection.java#L172-L285) |
| GET / POST / PUT / DELETE / PATCH routing | [Router.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/router/Router.java) |
| Dynamic path variables (`/api/users/{id}`) | [Router.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/router/Router.java#L327-L350) |
| Static file serving with MIME types | [Router.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/router/Router.java#L208-L284) |
| `FileChannel.transferTo()` for zero-copy file serving | [Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java#L173-L193) |
| Cookie send/receive (`Set-Cookie`, `Cookie` header) | [Cookie.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/response/cookie/Cookie.java), [Connection.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/connecting/Connection.java#L343-L362) |
| JSON serializer (custom, no external libs) | [Serializer.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/json/Serializer.java) |
| Query-string parsing | [Request.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/request/Request.java#L39-L56) |
| Fluent `ServerBuilder` API | [ServerBuilder.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/ServerBuilder.java) |
| Multiple ports registered in `Server` | [Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java#L50-L59) |
| Path-traversal protection for static files | [Router.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/router/Router.java#L222-L229) |

---

## 🔴 Critical — Must-Have (Mandatory Requirements)

### 1. Compile Errors / Broken Code
[Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java) **does not compile** in its current state:
- `serverSocketChannel` is referenced on line 73 but was never declared as a field — it conflicts with the per-port loop (lines 50-59).
- `this.port` is referenced in `setPort()`/`getPort()` (lines 228-239) but the field is declared as `Set<Integer> ports`.
- The `setPort()` / `getPort()` API is incompatible with multi-port design.

**Fix needed:** Unify the single-port vs multi-port model, remove the stale `serverSocketChannel` reference.

---

### 2. Configuration File (`config.json` or similar)

**Currently: not present at all.**

The subject requires a config file that controls:

| Config item | Status |
|---|---|
| Host name(s) | ❌ Missing |
| Multiple ports per server | ❌ Missing |
| Default server selection | ❌ Missing |
| Custom error page paths | ❌ Missing |
| Client body size limit | ❌ Missing |
| Routes: accepted methods | ❌ Missing |
| Routes: redirections (301/302) | ❌ Missing |
| Routes: directory / file root | ❌ Missing |
| Routes: default file for a directory | ❌ Missing |
| Routes: CGI by file extension | ❌ Missing |
| Routes: directory listing toggle | ❌ Missing |

**What to build:** A `ConfigLoader.java` that parses a config file (JSON or NGINX-style) and feeds the `ServerBuilder` programmatically.

---

### 3. Multiple Servers with Different Hostnames

The subject tests:
```
curl --resolve test.com:80:127.0.0.1 http://test.com/
```

**Currently:** The server distinguishes ports but ignores the `Host` header entirely — it cannot route by virtual hostname.

**Fix needed:** Read the `Host` header in [Request.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/request/Request.java) and dispatch to the correct virtual-server configuration.

---

### 4. DELETE Method — File Deletion

The subject explicitly asks:
> *"Try to DELETE something with and without permission"*

**Currently:** `DELETE` is registered in the router but no actual file-deletion logic exists anywhere. The only `DELETE` demo route in [Main.java](file:///c:/Users/exe/Desktop/java-localserver/src/Main.java) is absent.

**Fix needed:** Implement a DELETE handler that removes an uploaded file from the server's upload directory, and respects per-route method restrictions.

---

### 5. File Upload (POST multipart/form-data)

**Currently:** The body is read as raw `byte[]`, but there is no multipart parser.

The subject requires:
> *"Upload some files to the server and get them back to test they were not corrupted."*

**Fix needed:** Parse `multipart/form-data` (boundary extraction, part headers, binary content) and save files to disk. Return the file via a subsequent GET.

---

### 6. Client Body Size Limit (413 Too Large)

**Currently:** `Connection.java` reads whatever is in the buffer with no size check.

**Fix needed:** Read the `Content-Length` header and, if it exceeds a configured max, respond with `413 Request Entity Too Large` immediately.

---

### 7. Default Error Pages (400, 403, 404, 405, 413, 500)

**Currently:** The router returns plain-text `"Not Found"` for 404 and a bare `ResponseBuilder` for 403/500. No custom HTML error pages exist anywhere.

**Fix needed:**
- Create an `error_pages/` directory with HTML files (400.html, 403.html, 404.html, 405.html, 413.html, 500.html).
- Create an `ErrorHandler` or extend the router to serve them.

---

### 8. HTTP Redirections (301 / 302)

**Currently:** No redirect support in the router or config.

**Fix needed:** Add a `Location` header + 3xx status support to `Response`, and allow routes to be configured as redirects.

---

### 9. Directory Listing

The subject asks:
> *"Try to list a directory, is it handled properly?"*

**Currently:** [Router.java L240-L243](file:///c:/Users/exe/Desktop/java-localserver/src/http/router/Router.java#L240-L243) returns a 404 for directories — it does not list them.

**Fix needed:** When the requested path is a directory and listing is enabled in config, generate an HTML directory index. If listing is disabled, return 403.

---

### 10. Default File for Directory (e.g., `index.html`)

**Currently:** Not implemented — directory hits return 404.

**Fix needed:** If a directory contains the configured default file (e.g., `index.html`), serve it automatically.

---

### 11. Session Management

The subject asks:
> *"A working session and cookies system is present on the server?"*

**Currently:** Cookies can be sent/received, but there is no server-side session store (no session ID generation, no `Map<sessionId, sessionData>`).

**Fix needed:** Create a `SessionManager.java` that:
- Generates a secure session ID.
- Stores session data in a `ConcurrentHashMap`.
- Reads the `Cookie` header on each request and injects the session into the `Request`.

---

### 12. Cookie Parsing from Incoming Requests

**Currently:** `Request.java` has a `cookies` map and `addCookie()` method, but [Connection.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/connecting/Connection.java#L246-L257)'s `parseHeaders()` never reads the `Cookie:` header and populates it.

**Fix needed:** In `parseHeaders()`, detect the `Cookie` header value, split on `;`, and call `request.addCookie()` for each pair.

---

### 13. Wrong / Malformed Request Handling

The subject asks:
> *"Test a WRONG request, is the server still working properly?"*

**Currently:** `parseHeaders()` throws `RuntimeException` on a bad request line — this will crash the server loop.

**Fix needed:** Catch all parsing exceptions per-connection, close only that client, and keep the server running.

---

### 14. Request / Connection Timeout

**Currently:** No timeout implemented. A client that connects and sends nothing will hold the selector key forever.

**Fix needed:** Track the `lastActivityTime` per `Connection`; periodically sweep idle connections and close them (408 Request Timeout).

---

### 15. CGI Execution

**Currently:** No CGI handler exists anywhere.

**Fix needed:** Create a `CGIHandler.java` using `ProcessBuilder` that:
- Matches requests by file extension (e.g., `.py`, `.pl`, `*.cgi`).
- Sets CGI environment variables (`REQUEST_METHOD`, `QUERY_STRING`, `CONTENT_LENGTH`, `PATH_INFO`, etc.).
- Reads the process stdout as the HTTP response body.
- Handles both chunked and non-chunked output.

> Bonus: implement a second CGI handler (e.g., Perl or C++) for extra credit.

---

### 16. Chunked Transfer Encoding

**Currently:** The server does not parse `Transfer-Encoding: chunked` in incoming requests.

**Fix needed:** In `Connection.parseBody()`, detect the `Transfer-Encoding: chunked` header and decode chunk sizes/data from the buffer.

---

### 17. Port Conflict Detection

The subject asks:
> *"Configure the same port multiple times — the server should find the error."*

**Currently:** No startup validation. The OS will raise a `BindException` at runtime with no user-friendly message.

**Fix needed:** In `ConfigLoader` or `Server.start()`, detect duplicate ports before binding and log/exit with a clear error.

---

### 18. Per-Route Method Restrictions (405 Method Not Allowed)

**Currently:** The router silently returns `404` for any unmatched route, including method mismatches.

**Fix needed:** If a path exists but the method is not allowed, return `405 Method Not Allowed` with an `Allow:` header listing the valid methods.

---

## 🟡 Important — Quality / Robustness

### 19. `selector.selectedKeys()` Iteration — ConcurrentModificationException

**Currently:** [Server.java L89](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java#L89) iterates directly over `selector.selectedKeys()` while the loop may implicitly modify it. The standard NIO pattern uses an `Iterator` and calls `it.remove()` inside the loop.

**Fix needed:** Use `Iterator<SelectionKey> it = selector.selectedKeys().iterator()` and `it.remove()` per key.

---

### 20. Buffer Size — Large Bodies / Files

**Currently:** A single `ByteBuffer.allocate(8192)` is used for both reading and writing. A POST body larger than 8 KB will silently be truncated.

**Fix needed:** Increase the buffer or implement a growable accumulation buffer for request bodies.

---

### 21. `isStatic` Flag Reset Between Requests

**Currently:** `Connection.isStatic` is set to `true` on the first static response. If keep-alive connections reuse the same `Connection` object, subsequent dynamic requests will incorrectly be treated as static.

**Fix needed:** Reset `isStatic` and all related fields when re-entering `READING` state.

---

### 22. Correct `Content-Length` for Error Responses

**Currently:** `ResponseBuilder` for 404/403/500 responses sets a body string but the auto-injected `Content-Length` only works in `prepareResponse()`. If the error response goes through the static path, the length will be wrong.

---

## 🟢 Bonus (Optional but Mentioned in Subject)

| Bonus | Status |
|---|---|
| Second CGI handler (Python + Perl, or Python + C++) | ❌ Not started |
| Admin dashboard / server metrics endpoint | ❌ Not started |

---

## Summary Table

| Category | Count |
|---|---|
| ✅ Already working | ~15 items |
| 🔴 Critical blockers | 18 issues |
| 🟡 Quality / robustness | 4 issues |
| 🟢 Bonus | 2 items |

---

## Suggested Implementation Order

1. **Fix compile errors** in [Server.java](file:///c:/Users/exe/Desktop/java-localserver/src/http/server/Server.java) (field naming, port model).
2. **Fix `Cookie` header parsing** — easy, high impact.
3. **Fix malformed request crash guard** — keep server alive.
4. **Build `ConfigLoader.java`** — unlocks all config-dependent features.
5. **Add error pages** (400, 403, 404, 405, 413, 500).
6. **Add body size limit** (413).
7. **Add `SessionManager.java`**.
8. **Add file upload** (multipart parser).
9. **Add DELETE file handler**.
10. **Add redirections** (301/302).
11. **Add directory listing + default file**.
12. **Add virtual hostname routing** (Host header).
13. **Add connection timeouts**.
14. **Add CGI handler** (Python first, then a second one for bonus).
15. **Add chunked transfer decoding**.
16. **Fix `selectedKeys()` iterator pattern**.
17. **Write stress test config** and verify `siege -b` ≥ 99.5% availability.
18. **(Bonus)** Admin dashboard endpoint.
