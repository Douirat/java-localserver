# Local Servers — Apache & Nginx

> A logical, conceptual reference for understanding web servers, how they work, and when to use each.

---

## What is a Web Server?

A web server is a program that **listens** on a network port (usually 80 for HTTP, 443 for HTTPS) and **responds** to requests with files or data. It is the middleman between a browser and your files or apps.

On your local machine, `localhost` (127.0.0.1) is a loopback address — requests never leave your computer. Apache and Nginx work identically locally and in production; the only difference is that production servers are exposed to the internet.

---

## Core Concepts

| Concept | What it means |
|---|---|
| **Port binding** | The server claims a port on your machine. All traffic hitting that port goes to the server first. |
| **Document root** | A folder on disk mapped to a URL. Requesting `/index.html` fetches that file from the root folder. |
| **Virtual hosts** | One server can serve many sites by reading the `Host:` header and routing accordingly. |
| **Reverse proxy** | Instead of serving files, the server forwards requests to another app (Node, PHP, Python) on a different port and returns that app's response to the browser. |

---

## Request Lifecycle

What happens step by step when a browser requests a page:

1. **DNS / hosts file resolution** — Browser looks up the domain. Locally, `/etc/hosts` can map custom names like `myapp.local → 127.0.0.1`.
2. **TCP connection to port** — A three-way handshake opens a socket to port 80 or 443. The server is already listening and accepts the connection.
3. **HTTP request arrives** — The browser sends a text-based request: `GET /about HTTP/1.1` with headers (Host, cookies, etc.).
4. **Virtual host matching** — The server reads the `Host:` header and finds the matching config block (`<VirtualHost>` in Apache, `server {}` in Nginx).
5. **Routing: static or dynamic?** — If the path maps to a file → serve it directly. If it matches a proxy rule → forward to the Node/PHP/Python app on another port.
6. **Response is sent** — Server writes HTTP response headers (200 OK, Content-Type, etc.) then the body. Browser receives and renders.

```
Browser → TCP socket → Server process → VirtualHost / server{} match → static file OR proxy → Response
```

---

## Apache vs Nginx

### Side-by-Side Comparison

| | Apache (httpd) | Nginx |
|---|---|---|
| **Born** | 1995 | 2004 |
| **Concurrency model** | Thread or process per request | Async event loop (like Node.js) |
| **PHP support** | mod_php — baked in | Via PHP-FPM (separate process) |
| **Config style** | .htaccess per-directory allowed | Centralized only — no per-dir overrides |
| **Static file serving** | Good, but heavier | Excellent — very fast |
| **Dynamic content** | Very strong (rich module system) | Good via `proxy_pass` |
| **Memory usage** | Higher per connection | Lower — handles 10k+ connections |
| **Best for** | PHP apps, legacy apps, per-dir config | Static files, reverse proxying, high concurrency |

### The Key Conceptual Difference

Apache spawns a **new thread or process** for each request — simple and well-understood, but memory-heavy at scale.

Nginx uses a single **event loop** that handles thousands of concurrent connections without spawning new threads. This makes Nginx dramatically more efficient for serving static files and proxying to backend apps.

**Mental model:**
- Apache = one waiter assigned to each table for the entire meal
- Nginx = one waiter handling all tables asynchronously, moving between them as needed

---

## Configuration Reference

### Apache VirtualHost

```apache
# /etc/apache2/sites-available/myapp.conf

<VirtualHost *:80>
    ServerName    myapp.local          # match this Host: header
    DocumentRoot  /var/www/myapp       # serve files from here

    <Directory /var/www/myapp>
        Options Indexes FollowSymLinks
        AllowOverride All              # allow .htaccess overrides
        Require all granted
    </Directory>

    ErrorLog  ${APACHE_LOG_DIR}/myapp-error.log
    CustomLog ${APACHE_LOG_DIR}/myapp-access.log combined
</VirtualHost>
```

**Key Apache directives:**
- `ServerName` — which domain/hostname this block handles
- `DocumentRoot` — the folder that maps to the root URL `/`
- `AllowOverride All` — enables `.htaccess` files in subdirectories
- `<Directory>` / `<Location>` — scoped rules for folders or URL paths

Enable a site: `sudo a2ensite myapp.conf && sudo systemctl reload apache2`

---

### Nginx Server Block

```nginx
# /etc/nginx/sites-available/myapp

server {
    listen      80;
    server_name myapp.local;           # match Host: header
    root        /var/www/myapp;        # document root
    index       index.html index.php;

    location / {
        try_files $uri $uri/ =404;     # try file, then dir, then 404
    }

    location /api/ {
        proxy_pass http://localhost:3000;  # forward to Node/Python app
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    error_log  /var/log/nginx/myapp-error.log;
    access_log /var/log/nginx/myapp-access.log;
}
```

**Key Nginx directives:**
- `server_name` — which domain this block handles
- `root` — document root folder
- `try_files $uri $uri/ =404` — check for a matching file, then directory, then 404
- `proxy_pass` — forward the request to another service on a different port
- `location /path/` — scoped rules for specific URL paths

Enable a site: `sudo ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/ && sudo nginx -s reload`

---

## When to Use What

| Scenario | Recommended |
|---|---|
| PHP / WordPress site | Apache (simpler) or Nginx + PHP-FPM |
| Reverse proxy for Node / Python / Ruby | Nginx (`proxy_pass`) |
| Serving static HTML/CSS/JS | Nginx (fastest) |
| Legacy apps needing per-directory config | Apache (`.htaccess`) |
| High concurrency / many simultaneous connections | Nginx (event loop) |
| Local dev with XAMPP / WAMP / MAMP | Apache (bundled) |
| Load balancing multiple backend servers | Nginx (`upstream {}`) |
| SSL termination at the edge | Nginx (common pattern) |

### Quick Heuristic

- Use **Apache** if you're running PHP apps and want easy per-folder config or are working with a legacy codebase.
- Use **Nginx** if you want to proxy a modern app (Node, FastAPI, Rails), serve static sites, or need high performance.
- In production, many stacks use **both**: Nginx at the front as a load balancer and SSL terminator, Apache behind it handling PHP.

---

## Common Patterns

### Pattern 1 — Nginx as reverse proxy for a Node.js app

```nginx
server {
    listen 80;
    server_name myapp.local;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

### Pattern 2 — Local HTTPS with a self-signed cert (mkcert)

```bash
# Install mkcert, then:
mkcert myapp.local
# Generates myapp.local.pem and myapp.local-key.pem

# In your Nginx config:
listen 443 ssl;
ssl_certificate     /path/to/myapp.local.pem;
ssl_certificate_key /path/to/myapp.local-key.pem;
```

### Pattern 3 — Nginx load balancing two backends

```nginx
upstream myapp {
    server localhost:3000;
    server localhost:3001;
}

server {
    listen 80;
    location / {
        proxy_pass http://myapp;
    }
}
```

---

## Useful Commands

### Apache

```bash
sudo systemctl start apache2        # start
sudo systemctl stop apache2         # stop
sudo systemctl reload apache2       # reload config (no downtime)
sudo apachectl configtest           # test config for errors
sudo a2ensite myapp.conf            # enable a site
sudo a2dissite myapp.conf           # disable a site
sudo a2enmod rewrite                # enable a module (e.g. mod_rewrite)
```

### Nginx

```bash
sudo systemctl start nginx          # start
sudo systemctl stop nginx           # stop
sudo nginx -s reload                # reload config (no downtime)
sudo nginx -t                       # test config for errors
# enable a site (symlink pattern):
sudo ln -s /etc/nginx/sites-available/myapp /etc/nginx/sites-enabled/
```

---

## Key Takeaways

- A web server is fundamentally a **listener + responder**. Everything else — virtual hosts, proxying, SSL — is built on top of that.
- **Apache** is older, more flexible per-directory, and integrates PHP natively. Great for WordPress and traditional PHP stacks.
- **Nginx** is faster, uses less memory at scale, and excels at proxying and static file serving. Preferred for modern app architectures.
- **Locally**, both work the same as in production — you're just talking to 127.0.0.1 instead of a public IP.
- The most common production pattern: **Nginx in front → Apache or app server behind**.

---

*Tags: #servers #apache #nginx #http #devops #backend #localdev*
