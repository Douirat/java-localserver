# LocalServer — TODO

Snapshot of what exists: `server.conf` (nginx-style config, written), `ServerConfig.java`
(host, port — single int, defaultServer, webRoot, defaultIndex, maxBodyBytes, directoryListing).
`ConfigLoader.java` and `Main.java` are empty stubs. No `Server.java`, `Router.java`,
`CGIHandler.java`, `Session.java`, or `Cookie.java` yet.

## BDouirat — server core, routing, CGI

- [ ] `Server.java` — single-threaded NIO event loop (one `Selector`, non-blocking channels)
- [ ] Multi-port support — bind and `select()` across all configured ports at once
- [ ] Read/write strictly through the selector — no reads/writes outside it
- [ ] Request timeout handling — drop slow/idle clients without blocking others
- [ ] HTTP parser — request line, headers, chunked and unchunked bodies
- [ ] `Router.java` — match method + path against `ServerConfig` locations
- [ ] Enforce `client_max_body_size` → 413
- [ ] Unmatched route → 404, disallowed method on a route → 405
- [ ] Default error pages for 400, 403, 404, 405, 413, 500 (with fallback if custom page missing)
- [ ] `CGIHandler.java` — `ProcessBuilder`, pass script path as arg 1, set `PATH_INFO`
- [ ] `utils/Session.java`, `utils/Cookie.java` — session id generation, cookie set/read
- [ ] `Main.java` — load config via `ConfigLoader`, start one `Server` per distinct port group

## Oqritel — config parsing & validation

- [ ] `ConfigLoader.java` — parse `server.conf` into one or more `ServerConfig`
- [ ] Extend `ServerConfig`: `List<Location>` (routes), `Map<Integer,String> errorPages`, `Map<String,String> cgiExtensions`
- [ ] `Location` model — root, allowed methods, redirect target, index file, cgi extension, directory listing toggle
- [ ] Multi-port per server block — decide `Set<Integer>` vs one `ServerConfig` per port, update parser accordingly
- [ ] Startup validation:
  - [ ] Same port configured twice **within one server** → reject with clear error
  - [ ] Invalid/missing `root` on a location → reject that block only
  - [ ] One broken server block must not stop the others from starting
- [ ] Default server selection when multiple blocks share a host:port
- [ ] Directory listing toggle wired through to `Router`

## Shared — integration & testing

- [ ] Wire `Main.java` → `ConfigLoader` → `Server` → `Router` end to end
- [ ] Manual test: single server, single port
- [ ] Manual test: multiple servers, different ports
- [ ] Manual test: multiple hostnames on same IP:port (`curl --resolve`)
- [ ] `siege -b [IP]:[PORT]` — confirm ≥99.5% availability, no hanging connections
- [ ] File upload round-trip test (upload then re-download, verify not corrupted)
- [ ] CGI test with chunked and unchunked request bodies

## Bonus (if time allows)

- [ ] Second CGI handler (e.g. add `.php` or `.pl` alongside `.py`)
- [ ] Admin dashboard / metrics endpoint
