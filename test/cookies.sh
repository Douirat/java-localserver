URL="http://localhost:8080/api/cookies"

echo "Testing GET $URL"

curl -i -X GET "$URL" \
  -H "Accept: application/json" \
  -H "Cookie: sessionId=abc123; userId=42; theme=dark"