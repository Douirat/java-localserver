#!/bin/bash

curl -X POST \
  -H "Host: localhost" \
  -F "file=@videos/post_test.mp4" \
  http://127.0.0.1:8080/uploads