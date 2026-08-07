# =============================================================
# 阶段六：社交网站后端镜像（多阶段构建）
#
# 构建上下文 = backend/（docker-compose 中以 ./backend 为 context）
#   docker build -t social-app:latest .
#
# 阶段一：Maven 编译
# 阶段二：JRE 运行（容器内 jar 统一命名 social-application.jar）
# =============================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先拷贝 Maven Wrapper 与全部 pom（利用层缓存：源码改动不触发依赖下载）
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY social-common/pom.xml social-common/pom.xml
COPY social-service/pom.xml social-service/pom.xml
COPY social-websocket/pom.xml social-websocket/pom.xml
COPY social-api/pom.xml social-api/pom.xml
COPY social-application/pom.xml social-application/pom.xml

# 预下载依赖（离线层缓存）
RUN ./mvnw -q -B dependency:go-offline -DskipTests || true

# 再拷贝源码编译打包
COPY social-common/ social-common/
COPY social-service/ social-service/
COPY social-websocket/ social-websocket/
COPY social-api/ social-api/
COPY social-application/ social-application/

RUN ./mvnw -q -B -pl social-application -am package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区与字体（验证码图片等可能用到；同时保证 wall 时间与 DB 一致）
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# 从构建阶段拷贝可执行 jar
COPY --from=build /build/social-application/target/social-application-*.jar /app/social-application.jar

# 本地文件存储目录（LocalStorageService 默认 ./uploads，以卷挂载持久化）
RUN mkdir -p /app/uploads
VOLUME /app/uploads

EXPOSE 18081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/social-application.jar --spring.profiles.active=prod"]
