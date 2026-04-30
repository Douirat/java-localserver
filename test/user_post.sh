#!/bin/bash

echo "Sending request to server..."

curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Ali","age":25,"email":"ali@test.com"}'
