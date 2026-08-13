# LocalServer — full task sequence (implementation order)

This is the technical build order the project needs, regardless of who does what —
use it to see what blocks what.

```mermaid
sequenceDiagram
    participant M as Main.java
    participant CFG as ConfigLoader
    participant SC as ServerConfig
    participant SRV as Server (NIO)
    participant RT as Router
    participant CGI as CGIHandler
    participant SESS as Session/Cookie
    participant ERR as Error pages
    participant TEST as Testing (browser + siege)

    M->>CFG: 1. Parse server.conf
    CFG->>SC: 2. Populate host, ports, locations, cgi, error_page
    Note over SC: Extend model — List<Location>, error_page map, multi-port
    CFG->>CFG: 3. Validate config (duplicate ports, missing root, bad values)
    Note over CFG: One bad server block must not kill the others
    M->>SRV: 4. Init single selector, bind all configured ports
    SRV->>RT: 5. Register routes from ServerConfig locations
    RT->>ERR: 6. Load default (400/403/404/405/413/500) + custom error pages
    SRV->>SRV: 7. Event loop — accept/read/write via one select(), one thread
    SRV->>RT: 8. Dispatch parsed request (GET/POST/DELETE, chunked or not)
    RT->>RT: 9. Match method + path, enforce client_max_body_size
    RT->>CGI: 10. Execute CGI via ProcessBuilder if extension matches
    RT->>SESS: 11. Attach or create session cookie
    RT-->>SRV: 12. Build HTTP/1.1 response, correct status code
    SRV-->>TEST: 13. Serve to browser (static site, uploads, redirects, dir listing)
    TEST->>SRV: 14. siege -b [IP]:[PORT], target 99.5% availability
    TEST-->>M: 15. Report bugs / memory leaks — loop back to relevant module
```

**Blocking dependencies to note:**
- Router, CGIHandler, and error pages can't be tested end-to-end until `ConfigLoader` and `Main.java` exist.
- Multi-port binding in `Server.java` depends on `ServerConfig` supporting more than one port per config block.
- Sessions/cookies and directory listing are independent of the above and can be built in parallel.
