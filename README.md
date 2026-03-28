# CrossRow

A multi-mode AI conversational system focused on humanities and social sciences, powered by Gemini 2.5 and Qwen.

**Live**: [c4rows.com](https://c4rows.com)

> This is a personal practice project exploring multi-agent architectures, RAG pipelines, and tool-calling patterns with Spring AI.

---

## Overview

CrossRow provides four distinct chat modes, each tailored to different user needs:

| Mode | Description |
|------|-------------|
| **Preferred** | Direct multimodal chat with user's preferred model (Gemini or Qwen) |
| **Auto** | AI evaluates query complexity and routes to the appropriate model — Qwen for simple tasks, Gemini for complex ones |
| **Agent** | ReAct agent with tool calling — can search the web, generate images, ask clarifying questions, and retrieve domain knowledge |
| **Expert** | Multi-agent mode — an orchestrator routes queries to a specialized expert (philosophy, psychology, or sociology), each with domain-specific prompts and knowledge |

### Knowledge Base

28 curated Markdown documents across three domains:

- **Philosophy** (12): Stoicism, Existentialism, Buddhism, Taoism, Confucianism, Kantianism, etc.
- **Psychology** (8): CBT, Psychoanalysis, Jungian Psychology, Adlerian Psychology, etc.
- **Sociology** (8): Marxism, Bourdieu, Foucault, Frankfurt School, Feminism, etc.

Documents are indexed into Elasticsearch at startup using **hybrid retrieval** — BM25 keyword search (with IK Chinese tokenizer) combined with KNN vector search (Vertex AI embeddings, 768-dim). Results are deduplicated and filtered by a dynamic score threshold.

### Tools

| Tool | Description |
|------|-------------|
| `searchWeb` | Brave Search API, returns top 5 results |
| `generateImage` | Vertex AI Imagen, supports multiple styles |
| `askHuman` | Pauses execution to ask the user for clarification |
| `terminate` | Ends the agent loop with a final answer |
| `updateUserMemory` | Persists user preferences/facts to Elasticsearch for long-term recall |
| `retrievePhilosophy/Psychology/Sociology` | Domain-specific hybrid RAG retrieval |
| `getCurrentTime` | Returns current timestamp |
| `calculator` | Basic arithmetic operations |

### Memory

- **Short-term**: Redis-backed `ChatMemory` per session, with async compression — when token count exceeds thresholds, older messages are summarized by Qwen and merged into a compact system message.
- **Long-term**: User-specific facts stored in Elasticsearch via `updateUserMemory` tool, accessible across sessions.

### Advisors (Middleware)

Spring AI advisor chain applied to every LLM call:

| Advisor | Purpose |
|---------|---------|
| `PromptInjectionGuard` | Regex + Base64 + typoglycemia detection for injection attempts |
| `SimpleAuth` | Validates user exists in database |
| `SimpleQuota` | Enforces daily chat/agent usage limits per role |
| `ChatMemory` | Injects conversation history into prompts |
| `MyLog` | Logs request/response and token usage |

---

## Evaluation

The project includes a benchmark suite (`evaluation/`) using three recognized methodologies:

### Methods

| Benchmark | Framework | What it tests |
|-----------|-----------|---------------|
| **RAGAS** | [RAGAS](https://docs.ragas.io/) — 500+ citations | RAG retrieval quality: faithfulness (are answers grounded in retrieved docs?) and answer relevancy (are answers on-topic?) |
| **LLM-as-Judge: Routing** | [Zheng et al. 2023](https://arxiv.org/abs/2306.05685) — 3000+ citations | Expert routing accuracy across 35 test cases including boundary questions |
| **LLM-as-Judge: Quality** | Same methodology | Response quality on 5 dimensions: relevance, professionalism, completeness, clarity, empathy |

### Results

| Dimension | Score | Grade |
|-----------|-------|-------|
| Routing Accuracy | **97.1%** | A |
| Response Quality | **93.9 / 100** | A |
| RAG Quality | **79.3%** | C |

- **Routing**: 35 test cases (10 philosophy, 10 psychology, 10 sociology, 5 boundary). Only 1 misrouted.
- **Response Quality**: Gemini judges each response on a 1-5 scale across 5 dimensions. Average 4.7/5.
- **RAG Quality**: Faithfulness 0.84 (low hallucination), answer relevancy 0.73 (philosophy domain retrieval is the weakest at 0.61 — documents are organized by school rather than by topic, causing mismatch on cross-school questions).

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2, Spring AI 1.1 |
| **Frontend** | React 19, Vite 7, Tailwind CSS 4 |
| **LLM** | Vertex AI Gemini 2.5, Qwen (via DashScope) |
| **Embeddings** | Vertex AI text-embedding (768-dim) |
| **Database** | PostgreSQL 17 (pgvector), Redis 7 |
| **Search** | Elasticsearch 8.17 (IK analyzer, hybrid BM25 + KNN) |
| **Storage** | Google Cloud Storage (images, user backgrounds) |
| **Auth** | JWT |
| **Deploy** | Docker Compose, GitHub Actions, Nginx |
| **Evaluation** | RAGAS, LLM-as-Judge (Gemini), Python |
