#!/usr/bin/env sh
set -eu

network_name="${AI_PLATFORM_NETWORK:-ai-platform}"

docker network inspect "${network_name}" >/dev/null 2>&1 || docker network create "${network_name}"
