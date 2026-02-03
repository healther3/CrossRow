# CrossRow

A Spring Boot AI application that combines LLM capabilities with RAG (Retrieval Augmented Generation) to provide philosophical guidance and life strategy advice.

> **Status**:  Under Active Development

## Overview

CrossRow is an AI-powered "Rational Life Strategist" that helps users navigate real-world challenges by combining:
- Large Language Model conversations with memory
- Philosophy-based knowledge retrieval (RAG)
- Hybrid search (semantic + keyword) powered by Elasticsearch

The system is designed to accept the user's described reality as absolute truth and provide logical, actionable solutions rather than emotional comfort.

## Features

### Completed

- **LLM Integration**: Chat with DashScope (Alibaba Cloud AI) Qwen models
- **Conversation Memory**: Persistent chat history via Redis
- **RAG Pipeline**: Retrieve relevant philosophical concepts to augment responses
- **Dual Vector Store Support**:
  - PgVector (PostgreSQL with vector extension)
  - Elasticsearch with hybrid search (BM25 + KNN)
- **Document Processing**: Automatic loading and chunking of Markdown knowledge base
- **Keyword Extraction**: AI-powered keyword enrichment for documents
- **Custom Advisors**:
  - Authentication advisor
  - Quota management advisor
  - Logging advisor
  - Re-reading advisor (query enhancement)

### Planned

- [ ] Agent framework with tool calling
- [ ] MCP (Model Context Protocol) integration
- [ ] Full-stack web interface
- [ ] Multi-turn reasoning chains

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CrossRow Application                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐ │
│  │   User      │───▶│  ChatClient │───▶│  DashScope LLM (Qwen)   │ │
│  │   Request   │    │  + Advisors │    │                         │ │
│  └─────────────┘    └──────┬──────┘    └─────────────────────────┘ │
│                            │                                        │
│                            ▼                                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Advisor Chain                             │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │   │
│  │  │   Auth   │ │  Quota   │ │  Memory  │ │   RAG Advisor  │  │   │
│  │  │ Advisor  │ │ Advisor  │ │ Advisor  │ │ (Hybrid Search)│  │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └───────┬────────┘  │   │
│  └─────────────────────────────────────────────────│───────────┘   │
│                                                    │                │
│                    ┌───────────────────────────────┘                │
│                    ▼                                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              HybridDocumentRetriever                         │   │
│  │                                                             │   │
│  │   Query ──▶ Embedding ──▶ Elasticsearch ──▶ Documents       │   │
│  │                          (BM25 + KNN)                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      Infrastructure (Docker)                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────────┐ │
│  │ PostgreSQL  │    │    Redis    │    │     Elasticsearch       │ │
│  │ + PgVector  │    │  (Memory)   │    │    8.17 (Vectors)       │ │
│  └─────────────┘    └─────────────┘    └─────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.2.4 |
| AI Framework | Spring AI + Spring AI Alibaba |
| LLM Provider | DashScope (Qwen models) |
| Embedding Model | DashScope text-embedding-v3 (1024 dims) |
| Vector Store | PostgreSQL + PgVector / Elasticsearch 8.17 |
| Chat Memory | Redis |
| Containerization | Docker Compose |

## Knowledge Base

The RAG system is powered by a curated collection of philosophical frameworks:

- Existentialism
- Stoicism
- Buddhism
- Confucianism
- Taoism
- Epicureanism
- Kantianism
- Pragmatism
- Pessimism
- Post-modernism
- Transhumanism
- Will to Power (Nietzsche)

## Project Structure

```
src/main/java/com/dyx/crossrow/
├── app/                    # Main application logic
├── advisor/                # Custom Spring AI advisors
│   ├── MyLogAdvisor.java
│   ├── ReReadingAdvisor.java
│   ├── SimpleAuthAdvisor.java
│   └── SimpleQuotaAdvisor.java
├── chatmemory/             # Chat memory implementations
│   ├── FileBasedChatMemory.java
│   └── RedisChatMemory.java
├── config/                 # Configuration classes
│   ├── ElasticSearchConfiguration.java
│   ├── HybridRagConfiguration.java
│   ├── PgVectorConfiguration.java
│   └── ...
├── elasticsearch/          # Elasticsearch integration
│   ├── CrossRowDocument.java
│   ├── ElasticsearchDocumentStore.java
│   └── ElasticsearchIndexManager.java
├── rag/                    # RAG components
│   ├── CrossRowDocumentLoader.java
│   ├── DocumentIndexer.java
│   └── SimpleKeyWordEnricher.java
└── retriever/              # Document retrievers
    └── HybridDocumentRetriever.java
```

## Getting Started

### Prerequisites

- Java 21
- Docker & Docker Compose
- DashScope API Key

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/CrossRow.git
   cd CrossRow
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d
   ```

3. **Configure API keys**
   
   Create `application-local.yml` or set environment variables:
   ```yaml
   spring:
     ai:
       dashscope:
         api-key: your-dashscope-api-key
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

### Docker Services

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL + PgVector | 5432 | Vector storage (alternative) |
| Redis | 6379 | Chat memory persistence |
| Elasticsearch | 9200 | Hybrid vector + text search |

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/chat` | Basic chat with memory |
| POST | `/chat/rag` | Chat with RAG augmentation |
| POST | `/chat/report` | Generate structured report |

## Configuration

Key configuration properties in `application.yml`:

```yaml
spring:
  elasticsearch:
    host: localhost
    port: 9200
    index-name: philosophy_docs
    
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```

## Development Notes

### Hybrid Search Implementation

The system uses Elasticsearch's bool query with should clauses to combine:
- **BM25**: Traditional keyword matching on `content` field
- **KNN**: Dense vector similarity on `embedding` field (1024 dimensions)

Note: RRF (Reciprocal Rank Fusion) requires an Elasticsearch paid license, so the current implementation uses score combination via bool should.

### Document Processing Pipeline

```
Markdown Files ──▶ DocumentLoader ──▶ KeywordEnricher ──▶ EmbeddingModel ──▶ Elasticsearch
                   (chunk by Q&A)    (AI extraction)     (vectorize)        (index)
```

## License

[MIT License](LICENSE)

## Acknowledgments

- [Spring AI](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)
- [DashScope](https://dashscope.aliyun.com/)
