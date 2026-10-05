#!/usr/bin/env bash
# Health probe for containers that have no curl or wget. Usage: container-health.sh <port> <path> <expected-status>
# Opens a TCP connection, sends a minimal GET and checks the status line.
exec 3<>"/dev/tcp/127.0.0.1/$1" || exit 1
printf 'GET %s HTTP/1.0\r\nHost: localhost\r\nAccept: */*\r\n\r\n' "$2" >&3
head -1 <&3 | grep -q " $3 "
