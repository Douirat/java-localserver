
URL="http://localhost:8000/api/users"

echo "Testing GET $URL"

curl -i -X GET "$URL" \
  -H "Accept: application/json"