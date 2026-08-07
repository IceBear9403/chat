#!/usr/bin/env bash
# =============================================================
# 阶段六：SSL 证书获取脚本
#
# 方案一（推荐，国内可达）：阿里云免费 DV 证书
#   → 阿里云控制台搜索「数字证书管理服务」→ 创建证书 → 申请免费证书(DV 单域名)
#   → 域名校验（DNS 解析 TXT 或 HTTP）→ 签发后下载「Nginx」格式证书
#   → 上传到 deploy/ssl/certs/，改名为 ${DOMAIN}.crt 和 ${DOMAIN}.key
#     fullchain.pem → ${DOMAIN}.crt ；privkey.pem → ${DOMAIN}.key
#
# 方案二（certbot 自动签发）：本脚本实现，需要服务器 80 端口可被外网访问
#   前置：apt install -y certbot
#   注意：certbot 到期前 30 天内可续期；本脚本 + cron 提示见文末
# =============================================================
set -euo pipefail

cd "$(dirname "$0")/.."

# 读取域名
if [ ! -f .env ]; then
  echo "✗ 未找到 .env，请先执行 cp .env.example .env 并配置 DOMAIN"
  exit 1
fi
DOMAIN=$(grep -E '^DOMAIN=' .env | cut -d= -f2)
[ -n "$DOMAIN" ] || { echo "✗ .env 中 DOMAIN 为空"; exit 1; }

mkdir -p ssl/certs ssl/webroot

echo "→ 为 ${DOMAIN} 申请 Let's Encrypt 证书..."

command -v certbot >/dev/null 2>&1 || {
  echo "✗ 未安装 certbot，执行: sudo apt update && sudo apt install -y certbot"
  exit 1
}

# 若已存在同名证书则先备份
if [ -f "ssl/certs/${DOMAIN}.crt" ]; then
  echo "→ 检测到已存在证书，备份为 .bak.$(date +%s)"
  cp "ssl/certs/${DOMAIN}.crt" "ssl/certs/${DOMAIN}.crt.bak"
  cp "ssl/certs/${DOMAIN}.key" "ssl/certs/${DOMAIN}.key.bak" 2>/dev/null || true
fi

# 用 webroot 模式（要求 80 端口已在 Nginx 上指向 ACME challenge 目录）
# Nginx 配置已内置 /.well-known/acme-challenge/ → /var/www/certbot
echo "→ 使用 webroot 模式（需 80 端口可访问）..."
sudo certbot certonly --webroot -w ssl/webroot -d "${DOMAIN}" --non-interactive \
  --agree-tos --email admin@"${DOMAIN}" --redirect --keep-until-expiring || {
    echo "  webroot 失败，尝试 standalone 模式（需临时停掉 80 端口占用）..."
    sudo certbot certonly --standalone -d "${DOMAIN}" --non-interactive \
      --agree-tos --email admin@"${DOMAIN}" --keep-until-expiring
  }

# 拷入容器挂载目录
LE_DIR="/etc/letsencrypt/live/${DOMAIN}"
sudo cp "${LE_DIR}/fullchain.pem" "ssl/certs/${DOMAIN}.crt"
sudo cp "${LE_DIR}/privkey.pem"  "ssl/certs/${DOMAIN}.key"
sudo chmod 644 "ssl/certs/${DOMAIN}.crt"
sudo chmod 600 "ssl/certs/${DOMAIN}.key"

echo "✓ 证书已写入 deploy/ssl/certs/${DOMAIN}.crt / .key"
echo "  重启 nginx 生效:  docker compose -f ../docker-compose.yml exec nginx nginx -s reload"
echo ""
echo "续期提示:  certbot renew --dry-run 测试；可加 cron:"
echo "  0 3 * * *  cd $(pwd) && ./ssl/issue-cert.sh"
