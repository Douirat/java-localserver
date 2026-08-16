# LocalServer — Build Order (follow in sequence)

Rule: don't start a step until the previous step's checkpoint passes. Each phase is independently testable.

---

## Phase 0 — Skeleton

1. Create the package structure exactly:
   ```
   src/Main.java
   src/Server.java
   src/Router.java
   src/CGIHandler.java
   src/ConfigLoader.java
   src/error.java
   src/utils/Session.java
   src/utils/Cookie.java
   config.json
   error_pages/
   ```
2. `Main.java` does nothing but: read config path from args, call `ConfigLoader.load(path)`, pass result to `new Server(configs).start()`.

**Checkpoint:** compiles, runs, exits cleanly (no logic yet).

---

## Phase 1 — Config Model (unblocks everything else)

1. Write `LocationConfig` (path, allowedMethods, root, defaultFile, directoryListing, redirectTo, cgiHandlers) — as specified in the previous breakdown.
2. Write `ServerConfig` (host, ports, serverNames, isDefault, clientMaxBodySize, errorPages, `List<LocationConfig> locations`, `resolveLocation(path)` method).
3. Write `config.json` with **one** server, **one** port, **one** location (`/`, root = static test dir, GET only).
4. `ConfigLoader.load()`: parse JSON → `List<ServerConfig>`. No validation logic yet, just structural parsing.

**Checkpoint:** write a throwaway `main()` that loads the config and prints the parsed object tree. Confirm every field matches the JSON.

---

## Phase 2 — Config Validation

1. In `ConfigLoader`, after parsing: group `ServerConfig`s by port.
2. Reject startup if two `ServerConfig`s on the same port have overlapping `serverNames` (or both `isDefault=true`).
3. Allow same port with distinct `serverNames` (virtual hosting) — don't reject this.

**Checkpoint:** add a second server block with a duplicate port+name to `config.json`, confirm `ConfigLoader` throws a clear startup error. Remove it before continuing.

---

## Phase 3 — Selector Event Loop (bind only, no HTTP yet)

1. In `Server.java`: for every unique port across all `ServerConfig`s, open one `ServerSocketChannel`, set non-blocking, bind, register `OP_ACCEPT` on one shared `Selector`.
2. Main loop: `selector.select()` → iterate keys → on `isAcceptable`, accept the connection, set non-blocking, register `OP_READ`, **do nothing with the data yet**.
3. Wrap every key-processing step in try/catch that closes the channel and continues the loop on any `IOException`.

**Checkpoint:** `nc <host> <port>` or `telnet` connects and stays connected without the server crashing or hanging.

---

## Phase 4 — Raw Bytes In, Hardcoded Bytes Out

1. On `isReadable`: read **once** into a `ByteBuffer`, don't parse it yet, just print byte count.
2. On the same key, immediately register `OP_WRITE` and on `isWritable`, write back a hardcoded `HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK` **once**, then close or keep-alive per your design.

**Checkpoint:** `curl -v http://host:port/` returns `200 OK` with body `OK`. This proves the loop, not the protocol.

---

## Phase 5 — HTTP Request Parsing (GET only)

1. Buffer incoming bytes per-connection (a small state object attached to the `SelectionKey` via `attach()`) until you see `\r\n\r\n`.
2. Parse request line (method, path, version) and headers into a `Request` object.
3. Handle only `GET` for now. Ignore body.

**Checkpoint:** `curl -v http://host:port/anything` — confirm your server logs the correct method/path/headers parsed from a real curl request.

---

## Phase 6 — Router + Static File Serving

1. `Router.route(Request req, ServerConfig config)`: call `config.resolveLocation(req.path)`.
2. If method not in `location.allowedMethods` → 405.
3. If path is a directory: serve `defaultFile` if present, else directory listing if enabled, else 404.
4. Otherwise serve the file bytes from `location.root + req.path` with correct `Content-Type` and `Content-Length`.

**Checkpoint:**
- `curl http://host:port/` → serves your default file
- `curl http://host:port/nonexistent` → 404
- `curl -X DELETE http://host:port/` → 405 (DELETE not yet allowed)

---

## Phase 7 — Error Pages

1. `error.java`: given a status code + `ServerConfig`, look up `errorPages` map, serve custom page if present, else a minimal built-in page.
2. Wire this into every failure branch already written in Phase 6 (404, 405) plus 400 (malformed request), 403 (permission), 413 (Phase 9), 500 (uncaught exception → don't crash, return 500 instead).

**Checkpoint:** trigger each status code (bad request line via raw `nc`, missing file, disallowed method) and confirm the correct custom error page renders.

---

## Phase 8 — POST + Body Handling (unchunked first)

1. Parse `Content-Length`, read exactly that many body bytes across possibly multiple `OP_READ` events (don't assume it arrives in one read).
2. Enforce `clientMaxBodySize` → 413 if exceeded, **stop reading further** for that connection.
3. Handle `POST` as file upload: write body to configured upload location, return 201/200.

**Checkpoint:**
- `curl -X POST --data "short body" http://host:port/upload` → 200/201, file appears on disk with correct content
- `curl -X POST --data "$(python3 -c 'print("A"*2000000)')" ...` (bigger than your limit) → 413

---

## Phase 9 — Chunked Transfer Encoding

1. Detect `Transfer-Encoding: chunked` header.
2. Implement chunk-size-prefixed body decoding (hex size line, `\r\n`, chunk data, `\r\n`, repeat until `0\r\n\r\n`).
3. Feed decoded bytes into the same body-handling path as Phase 8 (respecting body size limit on decoded size).

**Checkpoint:** `curl -X POST -H "Transfer-Encoding: chunked" --data-binary @somefile http://host:port/upload` → identical file on disk.

---

## Phase 10 — DELETE + Full Method Matrix

1. Implement `DELETE` on the router: remove file at resolved path if method allowed, else 405; if file missing, 404.

**Checkpoint:** upload a file, DELETE it, confirm 200 + file gone; DELETE again → 404.

---

## Phase 11 — Redirections

1. If `location.redirectTo != null`, short-circuit the router before file resolution: return 301/302 with `Location` header.

**Checkpoint:** `curl -v http://host:port/old-path` → 3xx with correct `Location` header, browser follows to new path.

---

## Phase 12 — Cookies + Sessions

1. `utils/Cookie.java`: parse `Cookie:` request header into key/value map; helper to build `Set-Cookie:` response header.
2. `utils/Session.java`: in-memory `Map<String sessionId, SessionData>`. On first request without a valid session cookie, generate an ID, store it, `Set-Cookie` it back.
3. Wire into `Router` so any route can read/write session state.

**Checkpoint:** `curl -v -c cookies.txt http://host:port/` then `curl -v -b cookies.txt http://host:port/` — confirm same session ID reused on the second request.

---

## Phase 13 — Virtual Hosts

1. In `Router` (or a pre-router step), read the `Host` header from the request.
2. Among all `ServerConfig`s bound to the connection's port, pick the one whose `serverNames` contains the Host header value; fall back to the `isDefault` one if no match.

**Checkpoint:** `curl --resolve test.com:80:127.0.0.1 http://test.com/` vs `curl http://127.0.0.1/` — confirm each hits the correct `ServerConfig`.

---

## Phase 14 — CGI

1. `CGIHandler`: before static file serving in `Router`, check `location.cgiHandlers` for the requested file's extension.
2. If matched, build `ProcessBuilder` with the interpreter + script path, set `PATH_INFO`, `REQUEST_METHOD`, `CONTENT_LENGTH`, `QUERY_STRING` env vars.
3. Feed request body to the process's stdin (respect chunked/unchunked from Phases 8–9), capture stdout as the response body — **do this without blocking the selector thread** (non-blocking pipe reads or a small bounded polling step folded into the event loop).

**Checkpoint:**
- `curl http://host:port/script.py` → correct script output
- Same with `-H "Transfer-Encoding: chunked"` and with a normal `Content-Length` body → both work
- Server keeps handling other clients while a CGI script is running (don't let one slow script freeze everything)

---

## Phase 15 — Timeouts + Robustness Pass

1. Track a "last activity" timestamp per connection; on each loop iteration (or periodically), close connections idle past your timeout.
2. Re-verify: every I/O call's return value is checked (`read()` returning -1 or 0, `write()` partial writes retried via `OP_WRITE` re-registration).
3. Re-verify: any exception in the per-client handling closes *only that client*, never propagates to kill the loop.

**Checkpoint:** open a raw `nc` connection, send nothing, confirm it's dropped after your configured timeout while other clients are unaffected.

---

## Phase 16 — Shared-Port Fault Isolation

1. Configure two `ServerConfig`s on the same port with different `serverNames`, deliberately break one (e.g. invalid root path).
2. Confirm at startup or at request-time the broken config doesn't take down the working one.

**Checkpoint:** requests to the broken host return a clean 500/404 (not a crash); requests to the working host succeed normally.

---

## Phase 17 — Load + Leak Testing

1. `siege -b [IP]:[PORT]` on a static empty page → confirm ≥99.5% availability.
2. Watch for hanging connections (`netstat`/`ss` during the siege run — connection count should stay bounded, not grow unbounded).
3. Run a long siege session while monitoring memory (`jconsole`/`jcmd` or simple heap logging) to catch leaks from unclosed channels/buffers.

**Checkpoint:** siege completes with the availability target, connection count returns to baseline afterward, memory doesn't climb monotonically across runs.

---

## Phase 18 — Bonus (only after everything above is solid)

1. Second CGI interpreter (e.g. add `.php` alongside `.py`) — should be a pure config + `cgiHandlers` map addition, no new code path if Phase 14 was built generically.
2. Admin/metrics route: expose a `/status` location returning in-memory counters (active connections, total requests, uptime) you were already tracking for Phase 15.

---

## Self-Defense Prep (from the audit doc, do this last)

Before your evaluation, be ready to point at the exact line where:
- `select()` is called (only one place in the codebase)
- a read/write happens exactly once per client per loop iteration
- a client is removed after an I/O error
- duplicate port detection happens in `ConfigLoader`