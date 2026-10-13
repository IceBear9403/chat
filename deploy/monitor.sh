#!/usr/bin/env bash
# =============================================================
# 阶段七：轻量健康监控脚本（配合 crontab 定时巡检）
#
# 用法：
#   ./monitor.sh            # 单次巡检（异常即输出，可用于 cron 日志）
#   ./monitor.sh watch      # 持续监控（Ctrl+C 退出）
#   ./monitor.sh status     # 只打印当前状态，不告警
#
# 接入 cron（每分钟检查一次，异常写日志）：
#   * * * * * cd /root/social/deploy && ./monitor.sh >> /var/log/social-monitor.log 2>&1
#
# 可选告警：把 WEBHOOK_URL 换成钉钉/企业微信机器人的 POST 地址，命中异常时推送。
# =============================================================
set -u

cd "$(dirname "$0")" || exit 1

# ---------- 配置 ----------
ALERT_WEBHOOK="${ALERT_WEBHOOK:-}"   # 钉钉/企微机器人 webhook，留空不推送
MAX_RESTART_DIFF=${MAX_RESTART_DIFF:-300}   # 容器重启超过 N 秒前的说明有过重启，给个提示
: "${COMPOSE:=docker compose}"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
MODE="${1:-check}"
ISSUES=()

say()  { printf '%s\n' "$*"; }
ok()   { [ "$MODE" = "status" ] && printf "${GREEN}✔ %s${NC}\n" "$*" || true; }
warn() { printf "${YELLOW}⚠ %s${NC}\n" "$*"; ISSUES+=("$*"); }
fail() { printf "${RED}✘ %s${NC}\n" "$*"; ISSUES+=("$*"); }

notify() {
  [ -z "$ALERT_WEBHOOK" ] && return 0
  local text="[社交圈告警] $(date '+%F %T')\n$(printf '%s\n' "${ISSUES[@]}")"
  curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"$text\"}}" "$ALERT_WEBHOOK" >/dev/null || true
}

# ---------- 1. docker / compose 可用 ----------
if ! $COMPOSE version >/dev/null 2>&1; then
  fail "docker compose 不可用，请检查 Docker 是否启动"
  notify; exit 1
fi

# ---------- 2. 容器健康 ----------
CONTAINERS=$($COMPOSE ps --format '{{.Name}} {{.Status}}' 2>/dev/null)
if [ -z "$CONTAINERS" ]; then
  fail "没有运行中的容器（docker compose ps 为空）"
  notify; exit 1
fi
while IFS= read -r line; do
  name=$(echo "$line" | awk '{print $1}')
  status=$(echo "$line" | awk '{print $2}')
  if echo "$status" | grep -q "(healthy)"; then
    ok "$name healthy"
  elif echo "$status" | grep -q "(unhealthy)"; then
    fail "$name unhealthy：$status"
  elif echo "$status" | grep -q "Up"; then
    # 在启动 / 无 healthcheck 的容器（如 coturn host 网络）
    ok "$name up：$status"
  elif echo "$status" | grep -q "Restarting"; then
    fail "$name 反复重启：$status"
  else
    fail "$name 异常：$status"
  fi
done <<< "$CONTAINERS"

# ---------- 3. 容器重启次数（说明曾崩溃/被 OOM） ----------
for name in $(echo "$CONTAINERS" | awk '{print $1}'); do
  count=$(docker inspect -f '{{.RestartCount}}' "$name" 2>/dev/null || echo 0)
  [ "${count:-0}" -gt 0 ] && warn "$name 重启过 $count 次（容器内 RestartCount）"
done

# ---------- 4. 资源占用 ----------
MEM=$(free -m | awk '/Mem:/{printf "%d", $3*100/$2}')
SWAP=$(free -m | awk '/Swap:/{printf "%d", $3*100/$2}')
DISK=$(df -h / | awk 'NR==2{print $5}' | tr -d '%')
say "宿主：内存 ${MEM}%  磁盘 ${DISK}%"
[ "$MEM" -gt 90 ]  && fail "宿主机内存占用 ${MEM}%（>90%）"
[ "$SWAP" -gt 50 ] && warn "Swap 占用 ${SWAP}%（可能内存不足）"
[ "$DISK" -gt 85 ] && fail "磁盘占用 ${DISK}%（>85%，注意清理）"

# ---------- 5. 业务端口自测 ----------
NGINX_UP=$(docker inspect -f '{{.State.Health.Status}}' social-nginx 2>/dev/null || echo unknown)
case "$NGINX_UP" in
  healthy)  ok "nginx 健康检查通过" ;;
  unknown)  say "${YELLOW}? nginx 无健康检查或未运行${NC}" ;;
  *)        fail "nginx 健康状态：$NGINX_UP" ;;
esac

# 后端 health（经 nginx 转发，验证链路）
BACKEND_UP=$(docker inspect -f '{{.State.Health.Status}}' social-app 2>/dev/null || echo unknown)
case "$BACKEND_UP" in
  healthy) ok "app 健康检查通过" ;;
  *)       fail "app 健康状态：$BACKEND_UP" ;;
esac

# ---------- 6. MySQL 慢查询日志大小（排障提示） ----------
SLOW_LINES=$(docker exec social-mysql sh -c 'wc -l < /var/log/mysql/slow.log 2>/dev/null' 2>/dev/null || echo 0)
[ "${SLOW_LINES:-0}" -gt 0 ] 2>/dev/null && warn "MySQL 慢查询日志已有 ${SLOW_LINES} 条（>1s），关注：docker exec social-mysql tail -f /var/log/mysql/slow.log"

# ---------- 7. Redis 内存（maxmemory 余量） ----------
REDIS_MEM=$(docker exec social-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env 2>/dev/null | cut -d= -f2-)" info memory 2>/dev/null | awk -F: '/used_memory_human/{print $2}')
[ -n "$REDIS_MEM" ] && say "Redis 已用内存：$REDIS_MEM（上限 256mb，LRU 淘汰）"

# ---------- 汇总 ----------
if [ "${#ISSUES[@]}" -eq 0 ]; then
  [ "$MODE" != "status" ] && say "${GREEN}✓ 全部正常${NC}"
else
  say ""
  say "${RED}发现 ${#ISSUES[@]} 个异常：${NC}"
  notify
fi

if [ "$MODE" = "watch" ]; then
  say "持续监控中（每 60s 一次，Ctrl+C 退出）..."
  while true; do
    sleep 60
    ISSUES=()
    "$0" check >/dev/null 2>&1 || true
    [ "${#ISSUES[@]}" -gt 0 ] && notify
  done
fi
