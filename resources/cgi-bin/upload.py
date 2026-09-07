#!/usr/bin/env python3
import sys
import os

content_length_str = os.environ.get("CONTENT_LENGTH", "")
body = ""
try:
    if content_length_str and int(content_length_str) > 0:
        body = sys.stdin.read(int(content_length_str))
    else:
        body = sys.stdin.read()
except Exception:
    pass

print("Content-Type: text/html")
print()

print("<!DOCTYPE html>")
print("<html>")
print("<head><title>Upload CGI</title></head>")
print("<body>")
print("<h1>Upload CGI</h1>")
print(f"<p>Received body: {body}</p>")
print("</body>")
print("</html>")