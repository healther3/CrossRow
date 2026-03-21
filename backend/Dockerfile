# 阶段1：构建
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

# 阶段2：运行
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 复制构建产物
COPY --from=builder /build/target/*.jar app.jar

# 创建 config 目录（用于挂载 GCP 凭据）
RUN mkdir -p /app/config

EXPOSE 8123

ENTRYPOINT ["java", "-jar", "app.jar"]
