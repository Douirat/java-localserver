# LocalServer — TODO (updated)

Snapshot: `ConfigLoader.tokenize()` works. `ConfigLoader.parse()` only reads `host`/`port` from
ONE `server{}` block. `Server.java` binds and listens on all ports but `acceptClient()` closes
every connection immediately — nothing is ever read. `Serving.java` is an empty interface.
No `Router.java`, `CGIHandler.java`, `Session.java`, `Cookie.java` yet.

## Decide first (blocks both of you)

- [ ] Virtual hosting shape: one `Server` per bound port, holding `List<ServerConfig>` for that
      port, resolved per-request by `Host` header (fallback to `defaultServer`) — NOT one
      `ServerConfig` baked into the `Server` constructor like it is now.

## Oqritel — config parsing & model

- [ ] `parse()` → `parseAll()`: loop over every `server{}` block in the file, return `List<ServerConfig>` (current code stops after the first block)
- [ ] `Location` class: `path`, `root`, `methods`, `index`, `directoryListing`, `redirect`, `cgi` (extension → interpreter map)
- [ ] Parse nested `location{}` blocks (recursive descent on the existing token stream)
- [ ] Parse `error_page`, `client_max_body_size` (support `10M` / `10K` suffixes), `methods`, `index`, `directory_listing`, `cgi`
- [ ] `ServerConfig.matchLocation(String path)` — longest-prefix match over its `List<Location>`
- [ ] Per-block validation, not fatal for the whole file:
  - [ ] duplicate `host:port` across blocks → reject that block, keep parsing the rest
  - [ ] missing `root` on a location → reject that block only
  - [ ] unknown directive → log + skip, don't throw and abort the whole parse
- [ ] `defaultServer` flag actually parsed and honored (currently unused)

## BDouirat — server core

- [ ] `acceptClient()`: register `OP_READ` instead of `client.close()` — this is the #1 blocker, nothing works until this changes
- [ ] Per-connection state attached to the `SelectionKey` (read buffer, parse state, last-activity timestamp)
- [ ] Handle `key.isReadable()`: incremental HTTP parsing (request line, headers, chunked + unchunked bodies)
- [ ] Handle `key.isWritable()`: write response, then keep-alive or close
- [ ] Iterate `selectedKeys()` with an `Iterator`, `remove()` each key as processed
- [ ] Idle-timeout sweep — close stale connections; `select()` can block with no event on a hung client
- [ ] Rework `Server` constructor for the virtual-host shape above (`List<ServerConfig>` per port)
- [ ] Define `Serving.java` contract, e.g. `void serve(HttpRequest request, SocketChannel client)`
- [ ] `Router.java` — use `ServerConfig.matchLocation()` (once it exists) to pick the `Serving` impl, enforce method allow-list → 405, unmatched path → 404, body over `maxBodyBytes` → 413
- [ ] `Main.java` — call `ConfigLoader.parseAll()`, group configs by port, start one `Server` per group
- [ ] Default error pages for 400, 403, 404, 405, 413, 500 with a hardcoded fallback if a custom page is missing/misconfigured

## Blocked until the above lands

- [ ] `CGIHandler.java` — needs `Location.cgi` from Oqritel + the `Serving` contract from BDouirat
- [ ] `Session.java` / `Cookie.java` — needs a working response-write path first
- [ ] Any browser or `siege` testing — needs requests to actually be read and answered

## Bonus (later)

- [ ] Second CGI handler (e.g. `.php` alongside `.py`)
- [ ] Admin dashboard / metrics endpoint