#!/usr/bin/env python3
import os
import sys

print("Content-Type: text/html")
print()

print("<!DOCTYPE html>")
print("<html>")
print("<head><title>Python CGI Test</title></head>")
print("<body>")
print("<h1>Python CGI Script Test</h1>")
print("<h2>Environment Variables:</h2>")
print("<ul>")
for key, value in sorted(os.environ.items()):
    if key.startswith('HTTP_') or key in ['REQUEST_METHOD', 'PATH_INFO', 'QUERY_STRING', 'CONTENT_TYPE', 'CONTENT_LENGTH', 'SERVER_SOFTWARE', 'SERVER_NAME', 'GATEWAY_INTERFACE', 'REMOTE_ADDR', 'SCRIPT_NAME', 'SCRIPT_FILENAME']:
        print(f"<li><strong>{key}</strong>: {value}</li>")
print("</ul>")
print("<h2>Request Method:</h2>")
print(f"<p>{os.environ.get('REQUEST_METHOD', 'UNKNOWN')}</p>")
print("<h2>PATH_INFO:</h2>")
print(f"<p>{os.environ.get('PATH_INFO', 'None')}</p>")
print("<h2>QUERY_STRING:</h2>")
print(f"<p>{os.environ.get('QUERY_STRING', 'None')}</p>")
print("</body>")
print("</html>")
