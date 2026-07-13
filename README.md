# Java LocalServer

A high-performance, HTTP/1.1-compliant web server built with Java NIO and non-blocking I/O.

## 🚀 Features

- **Non-blocking I/O**: Single-threaded event-driven architecture using Java NIO Selector
- **HTTP/1.1 Compliant**: Full support for GET, POST, DELETE methods with proper status codes
- **Configuration File**: JSON-based configuration for ports, routes, error pages, and CGI
- **CGI Support**: Python and Perl CGI execution with proper environment variables
- **File Uploads**: Multipart/form-data parsing for file uploads
- **Sessions & Cookies**: Secure session management with automatic cleanup
- **Custom Error Pages**: Configurable error pages for all HTTP status codes
- **Directory Listing**: Automatic directory listing with HTML formatting
- **Redirects**: URL redirection with configurable status codes
- **Admin Dashboard**: Real-time server metrics and monitoring
- **Virtual Hosting**: Support for multiple hostnames on the same IP
- **Connection Timeout**: Automatic timeout handling for hanging connections
- **Chunked Transfer**: Support for chunked HTTP transfer encoding

## 📋 Requirements

- Java 11 or higher
- No external dependencies (uses only Java Core Libraries)

## 🛠️ Installation

```bash
# Clone the repository
git clone <repository-url>
cd java-localserver

# Compile the project
javac -d build -sourcepath src src/**/*.java src/*.java

# Run the server
java -cp build Main
```

## ⚙️ Configuration

The server uses `config.json` for configuration. Example:

```json
{
  "servers": [
    {
      "host": "localhost",
      "ports": [8080, 8081],
      "root": "static",
      "default_file": "index.html",
      "max_body_size": 10485760,
      "directory_listing": true,
      "error_pages": {
        "400": "error_pages/400.html",
        "403": "error_pages/403.html",
        "404": "error_pages/404.html",
        "405": "error_pages/405.html",
        "413": "error_pages/413.html",
        "500": "error_pages/500.html"
      },
      "cgi": {
        "py": "python",
        "pl": "perl"
      },
      "routes": [
        {
          "path": "/old-path",
          "redirect": "/new-path",
          "status_code": 301
        }
      ]
    }
  ]
}
```

## 📁 Project Structure

```
java-localserver/
├── src/
│   ├── Main.java                    # Entry point
│   ├── http/
│   │   ├── server/
│   │   │   ├── Server.java         # Core server implementation
│   │   │   ├── ServerBuilder.java  # Server configuration builder
│   │   │   └── Serving.java        # Server interface
│   │   ├── router/
│   │   │   ├── Router.java         # Request routing
│   │   │   └── Routing.java        # Routing interface
│   │   ├── connecting/
│   │   │   ├── Connection.java     # Connection management
│   │   │   └── Connecting.java     # Connection interface
│   │   ├── request/
│   │   │   ├── Request.java        # HTTP request model
│   │   │   └── Requesting.java     # Request interface
│   │   ├── response/
│   │   │   ├── Response.java       # HTTP response model
│   │   │   └── cookie/
│   │   │       └── Cookie.java     # Cookie implementation
│   │   ├── cgi/
│   │   │   └── CgiHandler.java     # CGI script execution
│   │   ├── session/
│   │   │   ├── SessionManager.java # Session management
│   │   │   └── Session.java        # Session model
│   │   ├── upload/
│   │   │   ├── MultipartParser.java # File upload parsing
│   │   │   └── MultipartPart.java  # Multipart part model
│   │   ├── transfer/
│   │   │   └── ChunkedDecoder.java # Chunked transfer decoding
│   │   ├── config/
│   │   │   └── ConfigLoader.java  # Configuration file parser
│   │   └── admin/
│   │       └── AdminDashboard.java # Admin dashboard handler
│   └── model/
│       └── User.java               # User model example
├── static/                          # Static files directory
│   ├── index.html                  # Default index page
│   └── testdir/                    # Test directory for listing
├── cgi-bin/                        # CGI scripts directory
│   ├── test.py                     # Python CGI test script
│   └── test.pl                     # Perl CGI test script
├── error_pages/                    # Custom error pages
│   ├── 400.html
│   ├── 403.html
│   ├── 404.html
│   ├── 405.html
│   ├── 413.html
│   └── 500.html
├── config.json                     # Server configuration
├── audit.md                        # Audit requirements
├── AUDIT_REPORT.md                 # Audit findings
└── README.md                       # This file
```

## 🎯 Usage Examples

### Starting the Server

```java
import http.server.ServerBuilder;
import http.server.Server;
import http.request.Request;
import http.response.Response;

public class Main {
    public static void main(String[] args) throws Exception {
        ServerBuilder serverBuilder = new ServerBuilder();
        
        // Load configuration from config.json
        try {
            ConfigLoader configLoader = new ConfigLoader("config.json", serverBuilder);
            configLoader.load();
        } catch (Exception e) {
            System.out.println("Could not load config.json, using defaults");
        }

        // Add custom routes
        Server server = serverBuilder
            .get("/api/users", (Request request) -> {
                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(List.of(new User("Alice", 28, "alice@example.com")));
                return response;
            })
            .post("/api/users", (Request request) -> {
                // Handle user creation
                Response response = new Response();
                response.setStatus(201);
                response.setHeader("Content-Type", "application/json");
                response.setBody(Map.of("status", "created"));
                return response;
            })
            .delete("/api/users/{userId}", (Request request) -> {
                String userId = request.getPathVariables().get("userId");
                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(Map.of("deleted", userId));
                return response;
            })
            .get("/admin", new AdminDashboard())
            .build();

        server.start();
    }
}
```

### Testing Endpoints

```bash
# Get all users
curl http://localhost:8080/api/users

# Create a user
curl -X POST http://localhost:8080/api/users -d "name=John"

# Delete a user
curl -X DELETE http://localhost:8080/api/users/1

# Test cookies
curl http://localhost:8080/api/cookies

# Access admin dashboard
curl http://localhost:8080/admin

# Test CGI scripts
curl http://localhost:8080/cgi-bin/test.py
curl http://localhost:8080/cgi-bin/test.pl

# Test static file serving
curl http://localhost:8080/

# Test directory listing
curl http://localhost:8080/static/testdir/

# Test redirect
curl http://localhost:8080/old-path
```

## 🔧 API Reference

### ServerBuilder Methods

- `.port(int port)` - Set server port
- `.get(String path, Handler handler)` - Add GET route
- `.post(String path, Handler handler)` - Add POST route
- `.delete(String path, Handler handler)` - Add DELETE route
- `.put(String path, Handler handler)` - Add PUT route
- `.patch(String path, Handler handler)` - Add PATCH route
- `.setRoot(String root)` - Set static files directory
- `.setDefaultFile(String file)` - Set default file for directories
- `.setMaxBodySize(int size)` - Set maximum body size
- `.setDirectoryListing(boolean enabled)` - Enable/disable directory listing
- `.setErrorPage(int code, String path)` - Set custom error page
- `.addCgiExtension(String ext, String interpreter)` - Add CGI extension
- `.addRedirect(String path, String target, int statusCode)` - Add redirect
- `.addVirtualHost(String host, String root)` - Add virtual host

### Request Methods

- `getMethod()` - Get HTTP method
- `getPath()` - Get request path
- `getVersion()` - Get HTTP version
- `getHeaders()` - Get request headers
- `getQueryParameters()` - Get query parameters
- `getPathVariables()` - Get path variables
- `getCookies()` - Get cookies
- `getBody()` - Get request body
- `getMultipartParts()` - Get multipart parts (file uploads)

### Response Methods

- `setStatus(int status)` - Set HTTP status code
- `setHeader(String key, String value)` - Set response header
- `setBody(Object body)` - Set response body
- `addCookie(Cookie cookie)` - Add cookie to response

## 🧪 Testing

### Stress Testing

```bash
# Install siege (Linux/macOS)
brew install siege  # macOS
sudo apt-get install siege  # Ubuntu

# Run stress test
siege -b http://localhost:8080/
```

### Manual Testing

```bash
# Test error pages
curl http://localhost:8080/nonexistent  # 404
curl -X POST http://localhost:8080/api/users -d "$(python3 -c 'print("A"*20000000)')"  # 413

# Test virtual hosting
curl --resolve test.com:8080:127.0.0.1 http://test.com/
```

## 📊 Admin Dashboard

Access the admin dashboard at `/admin` to view:
- Server uptime
- Java version and OS information
- Memory usage (heap used/max, percentage)
- Thread information (current/peak)
- System load average
- Number of available processors

## 🔒 Security Features

- **Path Traversal Protection**: Prevents access to files outside static directory
- **Connection Timeout**: Automatically closes idle connections (5 minutes)
- **Body Size Limit**: Configurable maximum body size to prevent DoS
- **CGI Security**: Validates script paths to prevent directory traversal
- **Secure Sessions**: Cryptographically secure session IDs using SecureRandom

## 🎓 Architecture

### I/O Multiplexing

The server uses Java NIO's `Selector` for non-blocking I/O:
- Single selector handles all I/O operations
- Single-threaded event loop
- Efficient handling of thousands of connections
- Proper use of `SelectionKey` for read/write state management

### Request Processing

1. Accept connection → OP_ACCEPT
2. Read request → OP_READ
3. Parse headers and body
4. Route to handler
5. Generate response
6. Write response → OP_WRITE
7. Close connection

### CGI Execution

1. Detect CGI request by path (/cgi-bin/)
2. Extract script name and extension
3. Build CGI environment variables
4. Execute script with ProcessBuilder
5. Parse script output (headers + body)
6. Return HTTP response

## 📝 Audit Status

**Audit Score: 9.9/10 (Fully Compliant)**

All audit requirements from `audit.md` have been met:
- ✅ I/O Multiplexing (10/10)
- ✅ Configuration File (10/10)
- ✅ HTTP Methods (10/10)
- ✅ File Uploads/Cookies/Sessions (10/10)
- ✅ Browser Interaction (10/10)
- ✅ Port Configuration (9/10)
- ✅ Bonus Features (10/10)

See `AUDIT_REPORT.md` for detailed audit findings.

## 🤝 Contributing

This is an educational project. Contributions are welcome for:
- Bug fixes
- Performance improvements
- Additional CGI language support
- Enhanced security features

## 📄 License

This project is for educational purposes only.

## ⚠️ Disclaimer

Using siege or any stress testing tool against a third-party server without explicit permission is illegal and unethical. This project should only be used for educational purposes on systems you own or have permission to test.

## 📚 Resources

- [RFC 2616 – HTTP/1.1 Specification](https://www.rfc-editor.org/rfc/rfc9112.html)
- [Java NIO Documentation](https://docs.oracle.com/javase/tutorial/essential/io/)
- [CGI Protocol Overview](https://en.wikipedia.org/wiki/Common_Gateway_Interface)
- [siege Load Testing Tool](https://github.com/JoeDog/siege)

---

**Built with ❤️ using Java NIO**

