curl -i \
  -X POST \
  -H "Host: localhost" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=Bennacer&message=HelloCGI" \
  http://127.0.0.1:8080/cgi-bin/