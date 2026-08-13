#!/bin/bash

rm -rf out

mkdir -p out

javac -d out \
    src/Main.java \
    src/config/*.java \
    src/http/server/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "Compilation successful."
echo "Starting server..."

java -cp out Main server.conf