#!/usr/bin/env python3
import time
print("Content-Type: text/html")
print()

print("<!DOCTYPE html>")
print("<html>")
print("<head><title>CGI Test</title></head>")
print("<body>")
print("<h1>Hello from Python CGI</h1>")
print("<p>The Java HTTP server executed this script.</p>")
print("</body>")
print("</html>")

time.sleep(60)  # Simulate some processing time