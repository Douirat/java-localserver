
URL="http://localhost:8080/api/users/map"

echo "Testing GET $URL"

curl -i -X GET "$URL" \
  -H "Accept: application/json"