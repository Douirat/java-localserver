#!/bin/bash

URL="http://localhost:8080/api/posts/123/users/456"

echo "Testing GET $URL"

curl -i -X GET "$URL" \
  -H "Accept: application/json"