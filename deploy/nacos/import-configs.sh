#!/usr/bin/env bash
# ============================================================
# 一键把 deploy/nacos/config/*.yaml 发布到 Nacos（Linux / macOS / Git Bash）
#
# 与 import-configs.ps1 等价：OpenAPI 发布 + 回读校验（忽略行尾差异）。
# 为什么要有 bash 版：P7 容器化后 CI / Linux 环境没有 PowerShell（对应台账 P2-7）。
#
# 用法：
#   bash deploy/nacos/import-configs.sh
#   NACOS_ADDR=http://192.168.1.10:8848 GROUP=DEFAULT_GROUP bash deploy/nacos/import-configs.sh
# ============================================================
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-http://localhost:8848}"
GROUP="${GROUP:-DEFAULT_GROUP}"
CONFIG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config"

if [ ! -d "$CONFIG_DIR" ]; then
  echo "[FAIL] config dir not found: $CONFIG_DIR"
  exit 1
fi

command -v curl >/dev/null 2>&1 || { echo "[FAIL] curl not found"; exit 1; }

tmp_remote="$(mktemp)"
trap 'rm -f "$tmp_remote"' EXIT

fail=0
for file in "$CONFIG_DIR"/*.yaml; do
  data_id="$(basename "$file")"

  # 发布：--data-urlencode 负责编码，避免中文注释被系统编码破坏
  http_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$NACOS_ADDR/nacos/v1/cs/configs" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "group=$GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@$file")"

  if [ "$http_code" != "200" ]; then
    echo "[FAIL] $data_id publish http=$http_code"
    fail=$((fail + 1))
    continue
  fi

  # 回读校验：忽略 CRLF/LF 差异（Nacos 可能把行尾统一）
  curl -s "$NACOS_ADDR/nacos/v1/cs/configs?dataId=$data_id&group=$GROUP" -o "$tmp_remote"
  if diff -q <(tr -d '\r' < "$file") <(tr -d '\r' < "$tmp_remote") >/dev/null; then
    echo "[OK]   published+verified: $data_id (group=$GROUP, $(wc -c < "$file") bytes)"
  else
    echo "[WARN] $data_id published but content differs (check line endings)"
    fail=$((fail + 1))
  fi
done

echo "RESULT failed=$fail"
[ "$fail" -eq 0 ]
