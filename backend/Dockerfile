# 阶段1：构建
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 1. 复制项目描述文件
COPY pom.xml .

# 2. 增强型预下载依赖 (核心改进点)
# 除了 go-offline，增加 resolve-plugins 来确保打包用的 Maven 插件也被缓存
RUN mvn dependency:go-offline -B && \
    mvn dependency:resolve-plugins -B

# 3. 复制源代码
COPY src ./src

# 4. 构建打包 (核心改进点：去掉 -o)
# 只要上面的 pom.xml 层没变，Docker 就会直接跳过下载，使用缓存。
# 去掉 -o 是为了防止极个别漏掉的包导致构建失败。
RUN mvn clean package -DskipTests -B

# 阶段2：运行
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 复制构建产物
COPY --from=builder /build/target/*.jar app.jar

# 创建 config 目录
RUN mkdir -p /app/config

EXPOSE 8123

ENTRYPOINT ["java", "-jar", "app.jar"]