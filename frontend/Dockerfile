# 阶段一：使用 Node.js 构建打包文件
FROM node:20-alpine as build
WORKDIR /app

# 先复制 package.json，利用 Docker 缓存加速依赖安装
COPY package*.json ./
RUN npm install

# 复制所有源代码并打包
COPY . .
RUN npm run build

# 阶段二：使用 Nginx 运行打包后的静态文件
FROM nginx:alpine

# 将第一阶段打包出的 dist 文件夹里的内容，复制到 Nginx 的网页目录下
COPY --from=build /app/dist /usr/share/nginx/html

# 用我们刚才写的 nginx.conf 替换掉默认的配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]