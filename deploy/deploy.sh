#!/usr/bin/env bash
# =============================================================
# 阶段六：一键部署脚本（阿里云 Ubuntu 22.04）
#
#   cd deploy && ./deploy.sh
#
# 步骤：
#   1. 校验 docker / docker compose / python3
#   2. 若缺 .env 则从 .env.example 复制
#   3. 用 python3 从 .env 渲染 nginx 与 coturn 配置（替换 ${VAR} 占位）
#   4. 检查证书是否存在（无证书则提示先生成）
#   5. docker compose up -d --build
# =============================================================
set -euo pipefail

cd "$(dirname "$0")"

# ---------- 1. 前置检查 ----------
command -v docker >/dev/null 2>&1 || { echo "✗ 未安装 docker，请先安装"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "✗ docker compose 插件不可用"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "✗ 未安装 python3（用于渲染配置模板）"; exit 1; }

# ---------- 2. 生成 .env ----------
if [ ! -f .env ]; then
  echo "→ 未发现 .env，已从 .env.example 复制，请编辑 .env 填好配置后重新运行"
  cp .env.example .env
  echo "  请先编辑: $(pwd)/.env"
  exit 1
fi

# ---------- 3. 渲染配置模板 ----------
echo "→ 渲染 nginx / coturn 配置..."
mkdir -p nginx/generated coturn/generated ssl/certs
python3 - <<'PY'
import os, re, pathlib

def load_env(path):
    env = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env

env = load_env('.env')

def render(src, dst):
    text = pathlib.Path(src).read_text(encoding='utf-8')
    def sub(m):
        key = m.group(1)
        if key not in env:
            raise SystemExit(f"✗ .env 缺少变量 {key}（在 {src} 中引用）")
        return env[key]
    rendered = re.sub(r'\$\{([A-Z0-9_]+)\}', sub, text)
    pathlib.Path(dst).write_text(rendered, encoding='utf-8')
    print(f"  ✓ {dst}")

render('nginx/default.conf', 'nginx/generated/default.conf')
render('coturn/turnserver.conf', 'coturn/generated/turnserver.conf')
PY

DOMAIN=$(grep -E '^DOMAIN=' .env | head -1 | cut -d= -f2- | tr -d ' \t"\'"'")

# ---------- 4. 必填变量校验（防止带着占位值/空值直接部署） ----------
REQUIRED_VARS=(ECS_PUBLIC_IP DOMAIN MYSQL_ROOT_PASSWORD MYSQL_PASSWORD REDIS_PASSWORD JWT_ACCESS_SECRET JWT_REFRESH_SECRET CORS_ALLOWED_ORIGINS APP_PII_KEY)
MISSING=""
for v in "${REQUIRED_VARS[@]}"; do
  val=$(grep -E "^${v}=" .env | head -1 | cut -d= -f2- | tr -d ' \t"\'"'")
  if [ -z "$val" ]; then
    MISSING="$MISSING $v(空)"
  elif echo "$val" | grep -qE '请改|你的|change-me|example|XXX'; then
    MISSING="$MISSING $v(仍为示例占位)"
  fi
done
if [ -n "$MISSING" ]; then
  echo "✗ 以下必填变量未配置，请先编辑 .env:"
  echo "  $MISSING"
  exit 1
fi

# ---------- 5. 证书检查 ----------
if [ ! -f "ssl/certs/${DOMAIN}.crt" ] || [ ! -f "ssl/certs/${DOMAIN}.key" ]; then
  echo "⚠ 未发现 SSL 证书 ssl/certs/${DOMAIN}.crt / .key"
  echo "  可运行 ./ssl/issue-cert.sh 通过 certbot 获取，或在阿里云控制台下载 DV 证书后放入 ssl/certs/"
  echo "  未放证书前 nginx 将无法启动。"
fi

# ---------- 6. 启动 ----------
echo "→ 构建并启动服务（首次构建需数分钟）..."
docker compose up -d --build

echo ""
echo "✓ 部署完成。"
echo "  https://${DOMAIN}  前端"
echo "  https://${DOMAIN}/swagger-ui.html  接口文档(生产已关闭)"
echo ""
echo "  查看状态:  docker compose ps"
echo "  查看日志:  docker compose logs -f app"
echo "  TURN 自测:  turnutils_uclient -u ${TURN_USERNAME:-turnuser} -p <TURN_PASSWORD> ${ECS_PUBLIC_IP:-<ECS公网IP>} -y"
echo ""
