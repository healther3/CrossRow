# 阶段1：构建
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 先只复制 pom.xml，下载依赖（利用 Docker 层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源代码并构建（只有代码变化时才重新构建）
COPY src ./src
RUN mvn clean package -DskipTests -o

# 阶段2：运行
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 复制构建产物
COPY --from=builder /build/target/*.jar app.jar

# 创建 config 目录（用于挂载 GCP 凭据）
RUN mkdir -p /app/config

EXPOSE 8123

ENTRYPOINT ["java", "-jar", "app.jar"]
