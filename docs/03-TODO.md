# LocalServer — What Is Actually Required

Two documents, two purposes:
- **README (spec)** → what you must build
- **README (audit)** → exact questions the corrector asks, i.e. what you must be able to defend live

Everything below maps spec → architecture → audit question, so nothing is abstract.

---

## 1. Hard Constraints (non-negotiable, checked first)

| Constraint | Why it matters |
|---|---|
| Java only, `java.nio` + `java.net` | No Netty/Jetty/Grizzly — corrector will grep your `pom.xml`/deps |
| **One process, one thread** | No `Thread`, no `ExecutorService`, no `CompletableFuture.supplyAsync` |
| **One `Selector`** for all read/write | Audit explicitly asks: *"is there only one select?"* |
| Never crash | Any uncaught exception in the event loop = automatic fail |
| Non-blocking, event-driven I/O | `Selector.select()` + `SelectionKey` interest ops, not blocking `read()` |

The audit README's core functional test is literally: **trace the code path from `select()` to a client's read/write and count how many reads/writes happen per client per select() call.** The correct answer is **one** read or write per client per loop iteration. If your code does a `while (channel has data) read()` loop inside a single select cycle, that's a fail — it defeats the purpose of multiplexing and can starve other clients.

---

## 2. Event Loop Architecture (this is 80% of your grade)

```
                    ┌─────────────────────────────┐
                    │        Selector.select()     │◄────┐
                    └──────────────┬───────────────┘     │
                                   │ returns ready keys    │
                    ┌──────────────▼───────────────┐     │
                    │  for each SelectionKey:       │     │
                    │    isAcceptable → accept()    │     │
                    │    isReadable   → ONE read()  │     │
                    │    isWritable   → ONE write() │     │
                    └──────────────┬───────────────┘     │
                                   │                       │
                    ┌──────────────▼───────────────┐     │
                    │ update interest ops per client │     │
                    │ (OP_READ ↔ OP_WRITE)           │     │
                    └──────────────┬───────────────┘     │
                                   └───────────────────────┘
```

Skeleton that satisfies "checked return values" + "client removed on error" (both explicit audit questions):

```java
while (running) {
    selector.select(timeoutMs); // enforces your "timeout long requests" requirement
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();
        try {
            if (!key.isValid()) continue;
            if (key.isAcceptable()) handleAccept(key);
            else if (key.isReadable()) handleRead(key);   // exactly one read
            else if (key.isWritable()) handleWrite(key);  // exactly one write
        } catch (IOException e) {
            closeClient(key); // socket errors -> drop the client, never propagate up
        }
    }
    checkTimeouts(); // walk connections, close ones past their deadline
}
```

**Multi-port / multi-server**: register one `ServerSocketChannel` per configured port on the *same* selector, all in non-blocking mode, all with `OP_ACCEPT`. Store a `port → List<ServerConfig>` map so you can resolve virtual hosts (see §4).

---

## 3. Requirement Table (spec → class → audit question)

| Spec requirement | Where it lives | Audit will ask |
|---|---|---|
| GET/POST/DELETE | `Router` / handler | Status codes correct for each? |
| File upload | request body parsing + `client_max_body_size` | `curl -X POST --data` shorter/longer than limit |
| Cookies/sessions | `utils/Session.java`, `utils/Cookie.java` | "Working session system present?" |
| Error pages 400/403/404/405/413/500 | `error.java` + config `error_pages` | Custom error pages configurable? |
| Chunked + unchunked | request parser | CGI test explicitly redoes this |
| CGI by extension | `CGIHandler` via `ProcessBuilder` | Chunked/unchunked CGI both work? |
| Directory listing toggle | `Router` | "Try to list a directory" |
| Redirections | `Router` | "Try a redirected URL" |
| Same port configured twice | `ConfigLoader` validation | Must be caught as a startup error |
| Shared port, one config broken | `ConfigLoader` / `Server` | Other valid configs must still run |
| Virtual hosts (same IP:port, different Host header) | `Router` host matching | `curl --resolve test.com:80:127.0.0.1` |

---

## 4. Config Model — Your Actual Blocker

Your `ServerConfig.java` needs to model **Location blocks** before `ConfigLoader`, `Router`, and `CGIHandler` can be wired end-to-end. Here's the minimal shape that satisfies every config requirement in the spec:

```
ServerConfig (one per "server" block)
 ├─ host, List<Integer> ports
 ├─ List<String> serverNames        // for virtual host matching
 ├─ boolean isDefault                // default server for a port
 ├─ long clientMaxBodySize
 ├─ Map<Integer, String> errorPages  // status -> path
 └─ List<LocationConfig> locations
                 │
                 ▼
        LocationConfig (one per "route")
         ├─ String path                  // e.g. "/upload"
         ├─ Set<String> allowedMethods   // GET, POST, DELETE
         ├─ String root                  // filesystem root for this path
         ├─ String defaultFile           // e.g. "index.html"
         ├─ boolean directoryListing
         ├─ String redirectTo            // nullable
         └─ Map<String, String> cgiExtensions // ".py" -> "/usr/bin/python3"
```

```java
public class LocationConfig {
    private String path;
    private Set<String> allowedMethods = new HashSet<>();
    private String root;
    private String defaultFile = "index.html";
    private boolean directoryListing = false;
    private String redirectTo;              // null = no redirect
    private Map<String, String> cgiHandlers = new HashMap<>(); // ext -> interpreter

    // matches longest-prefix route, e.g. "/api/v1" beats "/api"
    public boolean matches(String requestPath) {
        return requestPath.startsWith(path);
    }
}

public class ServerConfig {
    private String host;
    private List<Integer> ports = new ArrayList<>();
    private List<String> serverNames = new ArrayList<>();
    private boolean isDefault;
    private long clientMaxBodySize = 1_000_000;
    private Map<Integer, String> errorPages = new HashMap<>();
    private List<LocationConfig> locations = new ArrayList<>();

    public LocationConfig resolveLocation(String requestPath) {
        return locations.stream()
            .filter(l -> l.matches(requestPath))
            .max(Comparator.comparingInt(l -> l.getPath().length())) // longest match wins
            .orElse(null);
    }
}
```

Once `ServerConfig` exposes `resolveLocation()`, the dependency chain unblocks cleanly:
- **`Router`** calls `serverConfig.resolveLocation(path)` to get method restrictions, root dir, redirect, directory-listing flag.
- **`CGIHandler`** checks `location.getCgiHandlers()` for the extension of the requested file before falling back to static serving.
- **`ConfigLoader`** just needs to parse the `locations` array into `LocationConfig` objects and attach them to the right `ServerConfig` — no regex needed per the spec.

For the **duplicate-port validation** the audit tests explicitly: in `ConfigLoader`, after building all `ServerConfig`s, group by port and reject if two `ServerConfig`s on the same port both have `isDefault=true`, or reduce this to: same port + same `serverNames` overlap → hard error at startup; same port + different `serverNames` → allowed (virtual hosting), matched later by `Host` header in `Router`.

---

## 5. CGI — Minimal Compliant Shape

```java
ProcessBuilder pb = new ProcessBuilder(interpreterPath, scriptFile.getAbsolutePath());
pb.environment().put("PATH_INFO", pathInfo);
pb.environment().put("REQUEST_METHOD", method);
pb.environment().put("CONTENT_LENGTH", String.valueOf(bodyLength));
pb.redirectErrorStream(false);
Process proc = pb.start();
// write request body to proc.getOutputStream(), read response from proc.getInputStream()
// NEVER block the selector thread waiting on this — run it off to the side and
// poll/drain the process's streams via non-blocking channels or a bounded read loop
// tied back into your event loop, not a blocking Thread.join()
```

The audit explicitly retests CGI with **both chunked and unchunked** bodies, so don't special-case one.

---

## 6. Testing Checklist (pulled directly from the audit doc)

- [ ] Single server, single port
- [ ] Multiple servers, different ports
- [ ] Multiple servers, different hostnames, same IP:port (`curl --resolve`)
- [ ] Custom error pages per status code
- [ ] Body size limit (`curl -X POST --data` under/over limit → 413)
- [ ] Routes with method restrictions (DELETE with/without permission)
- [ ] Default file for directory paths
- [ ] Malformed request → server stays alive
- [ ] File upload → download → byte-identical
- [ ] Sessions/cookies persist across requests
- [ ] Wrong URL → correct error page
- [ ] Directory listing on/off
- [ ] Redirect route
- [ ] Duplicate port in config → startup error
- [ ] Shared port, one bad config → other configs still serve
- [ ] `siege -b [IP]:[PORT]` → ≥99.5% availability, no hanging connections

---

## 7. Bonus (optional, worth mentioning if time allows)

- Second CGI interpreter (e.g. `.php` alongside `.py`)
- Admin/metrics endpoint (connection count, uptime, requests served) — easy to add since you already have the selector loop; just expose a route that reads in-memory counters