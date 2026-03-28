# CrossRow 评测套件

对 CrossRow 多智能体系统进行评测闭环，覆盖三个维度：

| # | 评测 | 框架/方法论 | 测什么 |
|---|------|-----------|--------|
| 1 | **RAGAS** | [RAGAS](https://docs.ragas.io/) (论文引用 500+) | RAG 检索质量：faithfulness、answer_relevancy |
| 2 | **GAIA** | [GAIA Benchmark](https://huggingface.co/datasets/gaia-benchmark/GAIA) (NeurIPS 2023) | Agent 工具调用能力 (web-search 子集) |
| 3 | **LLM-as-Judge** | [Zheng et al. 2023](https://arxiv.org/abs/2306.05685) (引用 3000+) | 路由准确性 + 响应质量 (5维度评分) |

## 快速开始

### 1. 安装依赖

```bash
cd evaluation
pip install -r requirements.txt
```

### 2. 配置

复制配置文件并填入实际值：

```bash
cp config.py config_local.py
```

编辑 `config_local.py`：

```python
API_BASE_URL = "http://localhost:8123/api"   # CrossRow 后端地址
GOOGLE_API_KEY = "your-gemini-api-key"       # 用于 RAGAS judge 和 GAIA answer extraction
JUDGE_MODEL = "gemini-2.5-flash"             # Judge 模型
```

### 3. 确保后端运行

评测需要 CrossRow 后端运行中（包含 Elasticsearch、Redis 等依赖）。

后端已新增 `/eval/**` 端点（无需 JWT 认证）：
- `GET /api/eval/rag?domain=philosophy&question=...` — 返回检索上下文 + 生成回答
- `GET /api/eval/routing?question=...` — 返回路由决策
- `GET /api/eval/agent/sync?message=...` — 同步 Agent 调用
- `POST /api/eval/rag/batch` — 批量 RAG 评测
- `POST /api/eval/routing/batch` — 批量路由评测

### 4. 运行评测

```bash
# 运行全部
python run_all.py

# 只跑 RAGAS（评估知识库质量，决定是否需要更新）
python run_all.py --only ragas

# 只跑 GAIA（Agent 工具调用能力）
python run_all.py --only gaia --gaia-max 10

# GAIA + 裸 Gemini 对比
python run_all.py --only gaia --gaia-compare

# 只跑路由准确性
python run_all.py --only judge --judge-routing-only

# 只跑响应质量
python run_all.py --only judge --judge-quality-only

# 跳过 GAIA，只跑 RAGAS + Judge
python run_all.py --skip gaia

# 单独测试某个领域的 RAG
python ragas_eval.py --domain philosophy
```

## 评测报告

运行后自动生成 Markdown 报告到 `reports/` 目录：

```
reports/
├── eval_report_20260328_143000.md   # 综合报告
├── ragas_raw.json                   # RAGAS 原始数据
├── gaia_raw.json                    # GAIA 原始数据
└── judge_raw.json                   # Judge 原始数据
```

## 评测指标说明

### RAGAS 指标
- **Faithfulness**: 回答是否忠于检索到的上下文（不幻觉）
- **Answer Relevancy**: 回答是否切题

### GAIA 指标
- **Accuracy**: 精确匹配正确率（CrossRow Agent vs 裸 Gemini）

### LLM-as-Judge 指标
- **路由准确率**: 35 个测试用例，含哲学/心理学/社会学 + 边界问题
- **响应质量**: 5 维度评分（相关性/专业性/完整性/清晰度/共情度）

## 目录结构

```
evaluation/
├── config.py               # 配置（复制为 config_local.py 使用）
├── requirements.txt         # Python 依赖
├── run_all.py              # 主入口
├── ragas_eval.py           # RAGAS 评测
├── gaia_eval.py            # GAIA 评测
├── judge_eval.py           # LLM-as-Judge 评测
├── datasets/
│   ├── routing_tests.json  # 路由测试集 (35 条)
│   ├── ragas_tests.json    # RAG 测试集 (28 条)
│   └── quality_tests.json  # 质量测试集 (10 条)
├── utils/
│   ├── api_client.py       # CrossRow API 客户端
│   └── report.py           # 报告生成器
└── reports/                # 输出目录（自动创建）
```
