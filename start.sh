# 重启容器
docker-compose up --build -d
# 查看后端容器实时日志（持续跟踪）
docker logs -f crossrow-backend
# 查看最近 200 行日志
docker logs --tail 200 crossrow-backend
# 只看 RAG 诊断相关的日志
docker logs -f crossrow-backend 2>&1 | findstr "RAG"
# 查看容器状态
docker ps
# 重启后端容器（改完代