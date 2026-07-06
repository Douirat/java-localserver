# Java LocalServer — Completion Plan

## Project Overview
This project is a custom HTTP server built in Java using NIO (Non-blocking I/O) with a `Selector` for I/O multiplexing. The server must handle multiple clients concurrently, serve static files, support CGI execution, and be configurable via a configuration file.

---

## Current Status

### ✅ Already Implemented (~15 features)
- Non-blocking I/O with `Selector` (NIO)
- Single `select()` loop for all clients
- HTTP request parsing (request line, headers, body)
- GET / POST / PUT / DELETE / PATCH routing
- Dynamic path variables (`/api/users/{id}`)
- Static file serving with MIME types
- Zero-copy file serving with `FileChannel.transferTo()`
- Cookie send/receive (`Set-Cookie`, `Cookie` header)
- JSON serializer (custom, no external libs)
- Query-string parsing
- Fluent `ServerBuilder` API
- Multiple ports registered in `Server`
- Path-traversal protection for static files

---

## 🔴 Critical — Must Complete (18 items)

### 1. **Fix Compile Errors in Server.java**
- `serverSocketChannel` is referenced but never declared as a field
- `this.port` referenced in `setPort()`/`getPort()` but field is `Set<Integer> ports`
- Unify single-port vs multi-port model

### 2. **Configuration File System**
Create `ConfigLoader.java` to parse a config file (JSON or NGINX-style) that controls:
- Host name(s) for virtual hosting
- Multiple ports per server
- Default server selection
- Custom error page paths
- Client body size limit
- Routes: accepted methods
- Routes: redirections (301/302)
- Routes: directory / file root
- Routes: default file for directory (e.g., `index.html`)
- Routes: CGI by file extension
- Routes: directory listing toggle

### 3. **Multiple Servers with Different Hostnames**
- Read `Host` header in `Request.java`
- Dispatch to correct virtual-server configuration based on hostname
- Support: `curl --resolve test.com:80:127.0.0.1 http://test.com/`

### 4. **DELETE Method — File Deletion**
- Implement DELETE handler that removes uploaded files
- Respect per-route method restrictions
- Test with and without permissions

### 5. **File Upload (POST multipart/form-data)**
- Parse `multipart/form-data` (boundary extraction, part headers, binary content)
- Save files to disk
- Return files via subsequent GET to verify no corruption

### 6. **Client Body Size Limit (413 Too Large)**
- Read `Content-Length` header
- If exceeds configured max, respond with `413 Request Entity Too Large` immediately

### 7. **Default Error Pages**
Create HTML error pages for:
- 400 Bad Request
- 403 Forbidden
- 404 Not Found
- 405 Method Not Allowed
- 413 Request Entity Too Large
- 500 Internal Server Error

### 8. **HTTP Redirections (301 / 302)**
- Add `Location` header + 3xx status support to `Response`
- Allow routes to be configured as redirects in config file

### 9. **Directory Listing**
- When requested path is a directory and listing is enabled, generate HTML directory index
- If listing is disabled, return 403 Forbidden

### 10. **Default File for Directory**
- If directory contains configured default file (e.g., `index.html`), serve it automatically

### 11. **Session Management**
Create `SessionManager.java`:
- Generate secure session ID
- Store session data in `ConcurrentHashMap`
- Read `Cookie` header on each request
- Inject session into `Request`

### 12. **Cookie Parsing from Incoming Requests**
- In `Connection.parseHeaders()`, detect `Cookie` header
- Split on `;` and call `request.addCookie()` for each pair

### 13. **Wrong / Malformed Request Handling**
- Catch all parsing exceptions per-connection
- Close only that client, keep server running
- Return appropriate error response (400 Bad Request)

### 14. **Request / Connection Timeout**
- Track `lastActivityTime` per `Connection`
- Periodically sweep idle connections
- Close with 408 Request Timeout

### 15. **CGI Execution**
Create `CGIHandler.java` using `ProcessBuilder`:
- Match requests by file extension (`.py`, `.pl`, `.cgi`)
- Set CGI environment variables (`REQUEST_METHOD`, `QUERY_STRING`, `CONTENT_LENGTH`, `PATH_INFO`, etc.)
- Read process stdout as HTTP response body
- Handle both chunked and non-chunked output

### 16. **Chunked Transfer Encoding**
- In `Connection.parseBody()`, detect `Transfer-Encoding: chunked` header
- Decode chunk sizes/data from buffer

### 17. **Port Conflict Detection**
- In `ConfigLoader` or `Server.start()`, detect duplicate ports before binding
- Log/exit with clear error message

### 18. **Per-Route Method Restrictions (405)**
- If path exists but method not allowed, return `405 Method Not Allowed`
- Include `Allow:` header listing valid methods

---

## 🟡 Quality & Robustness (4 items)

### 19. **Fix `selector.selectedKeys()` Iteration**
- Use `Iterator<SelectionKey>` pattern
- Call `it.remove()` inside loop to avoid `ConcurrentModificationException`

### 20. **Buffer Size for Large Bodies**
- Current 8KB buffer truncates large POST bodies
- Implement growable accumulation buffer for request bodies

### 21. **Reset `isStatic` Flag Between Requests**
- Reset `isStatic` and related fields when re-entering `READING` state
- Prevent incorrect static treatment of subsequent dynamic requests

### 22. **Correct `Content-Length` for Error Responses**
- Ensure error responses have correct `Content-Length` header

---

## 🟢 Bonus Features (2 items)

### 23. **Multiple CGI Systems**
- Implement at least 2 CGI handlers (e.g., Python + Perl, or Python + C++)

### 24. **Admin Dashboard / Server Metrics**
- Create endpoint for server statistics
- Display metrics like active connections, request count, etc.

---

## Stress Testing Requirements

### Siege Test
- Run: `siege -b [IP]:[PORT]`
- Must achieve ≥ 99.5% availability
- Verify no hanging connections

---

## Suggested Implementation Order

1. Fix compile errors in `Server.java`
2. Fix `Cookie` header parsing
3. Fix malformed request crash guard
4. Build `ConfigLoader.java` (unlocks config-dependent features)
5. Add error pages (400, 403, 404, 405, 413, 500)
6. Add body size limit (413)
7. Add `SessionManager.java`
8. Add file upload (multipart parser)
9. Add DELETE file handler
10. Add redirections (301/302)
11. Add directory listing + default file
12. Add virtual hostname routing (Host header)
13. Add connection timeouts
14. Add CGI handler (Python first, then second for bonus)
15. Add chunked transfer decoding
16. Fix `selectedKeys()` iterator pattern
17. Write stress test config and verify siege ≥ 99.5%
18. (Bonus) Admin dashboard endpoint

---

## Summary

| Category | Count |
|---|---|
| ✅ Already working | ~15 items |
| 🔴 Critical blockers | 18 issues |
| 🟡 Quality / robustness | 4 issues |
| 🟢 Bonus | 2 items |
| **Total remaining** | **24 items** |

The project has a solid foundation with core HTTP server functionality working. The remaining work focuses on configuration, error handling, file operations, CGI execution, and robustness improvements to meet the subject requirements.
