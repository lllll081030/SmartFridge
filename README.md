# 🧊 SmartFridge

> **"What can I cook tonight?"** — Let AI figure it out.

A full-stack smart kitchen assistant that discovers recipes from your fridge ingredients. Powered by **semantic search**, **AI substitutions**, **hybrid retrieval**, and **Redis caching** — using **OpenAI API** for embeddings and text generation.

### ✨ Key Features
- 🔍 **Hybrid Search** — Find recipes by ingredients + natural language ("something quick with chicken")
- 🤖 **AI Substitutions** — Missing an ingredient? Get smart alternatives from your fridge
- 🧠 **Semantic Understanding** — Search by meaning, not just keywords
- ⚡ **Redis Caching** — Fast repeated searches with vector embedding cache
- 📖 **Recipe Parser** — Paste any recipe, AI extracts ingredients automatically
- 🥕 **Fridge Tracker** — Manage what you have, find what you can make

---
## 🚀 What's New

### v2.4 - Redis Vector Caching

**New Features:**
- ⚡ **Vector Cache Service** - Cache embedding vectors in Redis for faster repeated searches
- 🔄 **Search Result Caching** - Cache complete search results with configurable TTL
- 📊 **Cache-Aside Pattern** - Automatic cache miss/hit handling with graceful degradation

**Technical:**
- Spring Data Redis integration with custom templates
- SHA-256 hashed cache keys for efficient storage
- Configurable TTL (default: 1 hour)
- Graceful fallback when Redis unavailable

---

### v2.3 - Hybrid Search with RRF Fusion

![Hybrid Search](pics/v2_hybrid_search.png)

**New Features:**
- ⚡ **True Hybrid Search** - Combines semantic meaning + keyword matching using Reciprocal Rank Fusion (RRF)
- 🎯 **Score Threshold Filter** - Slider to filter out low-relevance results (0-100%)
- 🧠 **Multi-Vector Architecture** - Each recipe stored with dense (semantic) + sparse (keyword) vectors

**Technical:**
- Qdrant's `prefetch` API with dual queries
- BM25-style sparse vectors for ingredient matching
- Eliminates semantic drift from template patterns

---

### v2.2 - Ingredient Substitution Recommendations

![Almost Cookable](pics/v2_almost_cookable.png)

**New Features:**
- 🔄 **AI Substitution Suggestions** - When ingredients are missing, AI suggests alternatives from your fridge
- 📊 **Almost Cookable Recipes** - Find recipes you're close to making
- 🧪 **Missing Ingredient Analysis** - See exactly what you need

---

### v2.1 - Semantic Search & Ingredient Aliases

![Semantic Search](pics/v2_semantic_search.png)

**New Features:**
- 🔍 **Semantic Search** - Find recipes by meaning, not just keywords ("something healthy" → salads)
- 🏷️ **Canonical Names & Aliases** - Auto-mapping ingredient variants
  - `roma tomato` → canonical: `tomato`
  - AI-generated aliases for new ingredients
- 📊 **Vector Database** - Qdrant stores recipe embeddings for similarity search

**Technical:**
- OpenAI `text-embedding-3-small` for 1536-dim embeddings
- Qdrant vector database integration

---

### v2.0 - AI-Powered Recipe Parser

![AI Recipe Parser](pics/v2_ai_recipe_parser.png)
![AI Food Recognition](pics/v2_ai_recipe_food.png)

**New Features:**
- 🤖 **AI Recipe Parser** - Paste any recipe text, AI extracts ingredients, seasonings, and instructions
- 🧂 **Ingredient vs Seasoning Separation** - Seasonings don't count towards recipe requirements
- 📊 **Improved UI** - Better recipe display with separate ingredient/seasoning sections

**Technical Improvements:**
- OpenAI API integration for LLM-powered recipe parsing (gpt-4o-mini)
- Database schema: `is_seasoning` column for proper separation

---

### v1 - Full-Stack Foundation

![Fridge Management](pics/v1_my_fridge.png)
![Recipe Book](pics/v1_recipe_book.png)
![Generate Recipes](pics/v1_generate_recipe.png)

**Features:**
- 🥕 **Fridge Management** - Add/remove ingredients with quantities
- 📖 **Recipe Book** - Browse recipes by cuisine, add custom recipes
- 🍳 **Smart Recipe Generation** - Find cookable recipes using Kahn's algorithm
- 💾 **Local Persistence** - SQLite database, fully offline

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Python 3.11+, Streamlit |
| AI Service | Python 3.11+, Flask |
| Backend | Java 17, Spring Boot 3.2 |
| Database | SQLite (relational), Qdrant (vector) |
| Cache | Redis 7 (vector & search caching) |
| AI | OpenAI API (LLM + embeddings) |
| CI/CD | GitHub Actions, Docker |

### 🔄 CI/CD Pipeline

Automated builds on every push:
- ✅ **Backend**: Maven build, test, package
- ✅ **Frontend**: Python lint with flake8
- ✅ **Docker**: Multi-stage image builds
- ✅ **Integration**: Full docker-compose health checks
- 📦 **Release**: Auto-publish to Docker Hub on tag

---

## Quick Start

### Prerequisites
- Java 17+
- Maven
- Python 3.11+
- Docker Desktop
- OpenAI API key (set in `.env`)

### Option 1: Run with Docker Compose (Recommended)

This starts all services (backend, frontend, AI service, Qdrant, Redis) in containers:

```bash
# 1. Set your OpenAI API key
echo OPENAI_API_KEY=sk-your-key-here > .env

# 2. Start all containers
docker-compose up --build
```

**Architecture:**
```
┌───────────────────────────────────────────────────────────────────┐
│                         Docker Compose                             │
│  ┌──────────┐  ┌───────────┐  ┌──────────┐  ┌───────┐  ┌───────┐ │
│  │ Backend  │  │AI Service │  │ Frontend │  │Qdrant │  │ Redis │ │
│  │  :8080   │→→│  :5001    │  │  :8501   │  │ :6333 │  │ :6379 │ │
│  └────┬─────┘  └─────┬─────┘  └────┬─────┘  └───────┘  └───────┘ │
└───────┼──────────────┼─────────────┼────────────────────────────-─┘
        │              │             │
        └──────────────┼─────────────┘
                       ▼
               ┌─────────────┐
               │ OpenAI API  │
               │api.openai.com│
               └─────────────┘
```

### Option 2: Run Locally (Development)

**1. Start External Services**

```bash
# Qdrant (Vector Database)
docker run -d -p 6333:6333 -p 6334:6334 \
  -v qdrant_storage:/qdrant/storage \
  --name smartfridge-qdrant \
  qdrant/qdrant

# Redis (Cache)
docker run -d -p 6379:6379 \
  -v redis_data:/data \
  --name smartfridge-redis \
  redis:7-alpine

# Set OpenAI API key
echo OPENAI_API_KEY=sk-your-key-here > .env
```

**2. Start Application Services** (3 terminals)

```bash
# Terminal 1: Spring Boot Backend
cd SmartFridge
mvn spring-boot:run
# Runs on http://localhost:8080

# Terminal 2: Flask AI Service
cd SmartFridge/frontend
pip install -r requirements.txt
python ai_service.py
# Runs on http://localhost:5001

# Terminal 3: Streamlit Frontend
cd SmartFridge/frontend
streamlit run app.py
# Runs on http://localhost:8501
```

### Post-Setup (First Run)

1. Open **http://localhost:8501**
2. Go to **Search** → **Admin** tab
3. Click **"📊 Index All Recipes"** to populate Qdrant
4. Wait for indexing to complete (~30 seconds)

### Service URLs
| Service | Port | Purpose |
|---------|------|---------|
| Frontend | http://localhost:8501 | Streamlit UI |
| Backend API | http://localhost:8080/api | Spring Boot REST |
| AI Service | http://localhost:5001 | Flask (substitutions, parsing) |
| Qdrant | http://localhost:6333 | Vector database dashboard |
| Redis | localhost:6379 | Vector & search result cache |

---

## API Endpoints

### Core Recipe & Fridge
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/fridge` | Get fridge contents with quantities |
| POST | `/api/fridge/{item}` | Add item to fridge |
| PUT | `/api/fridge/{item}` | Update item count |
| DELETE | `/api/fridge/{item}` | Remove item |
| GET | `/api/recipes` | Get all recipes by cuisine |
| GET | `/api/recipes/{name}` | Get recipe details |
| POST | `/api/recipes` | Add new recipe |
| DELETE | `/api/recipes/{name}` | Delete recipe |
| GET | `/api/generate` | Generate cookable recipes |

### Search & Discovery
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/recipes/search` | Semantic search (query + limit) |
| POST | `/api/recipes/hybrid-search` | Hybrid search (ingredients + query + threshold) |
| GET | `/api/recipes/almost-cookable` | Find recipes with few missing ingredients |
| POST | `/api/search/index-all` | Re-index all recipes to Qdrant |
| GET | `/api/search/stats` | Vector search statistics |

### Ingredients & Substitutions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/recipes/{name}/missing` | Get missing ingredients |
| GET | `/api/recipes/{name}/substitutions` | AI substitution suggestions |
| GET | `/api/ingredients/{name}/aliases` | Get ingredient aliases |
| POST | `/api/ingredients/{name}/generate-aliases` | AI-generate aliases |

### AI Service (Flask - port 5001)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/ai/substitutions` | Get ingredient substitutions |
| POST | `/ai/parse-recipe` | Parse recipe text with AI |
| GET | `/health` | Health check |

---

## Project Structure
```
SmartFridge/
├── src/main/java/com/smartfridge/
│   ├── SmartFridgeApplication.java  # Spring Boot entry point
│   ├── config/
│   │   └── RedisConfig.java         # Redis template configuration
│   ├── controller/
│   │   └── RecipeController.java    # All REST endpoints
│   ├── service/
│   │   ├── RecipeService.java       # Core recipe logic
│   │   ├── VectorSearchService.java # Qdrant hybrid search
│   │   ├── VectorCacheService.java  # Redis caching (v2.4)
│   │   ├── EmbeddingService.java    # Dense embeddings (OpenAI)
│   │   ├── SparseEmbeddingService.java # BM25 sparse vectors
│   │   ├── IngredientResolver.java  # Alias resolution
│   │   └── IngredientSubstitutionService.java
│   ├── dao/
│   │   ├── RecipeDao.java           # Recipe CRUD
│   │   ├── SupplyDao.java           # Fridge management
│   │   ├── IngredientAliasDao.java  # Alias storage
│   │   └── DatabaseInitializer.java
│   └── model/                       # Data models (7 classes)
├── src/main/resources/
│   └── application.properties       # App configuration
├── frontend/
│   ├── app.py                       # Streamlit main entry
│   ├── api.py                       # Backend API client
│   ├── config.py                    # Frontend config
│   ├── styles.py                    # UI styling
│   ├── ai_service.py                # Flask AI service (port 5001)
│   ├── openai_client.py             # OpenAI API integration
│   ├── Dockerfile                   # Streamlit container
│   ├── Dockerfile.ai                # Flask AI service container
│   ├── requirements.txt             # Python dependencies
│   └── views/
│       ├── fridge.py                # Fridge management UI
│       ├── recipes.py               # Recipe book UI
│       ├── generate.py              # Recipe generation + AI substitutions
│       ├── search.py                # Semantic/hybrid search UI
│       └── recipe_parser.py         # AI recipe parser UI
├── data/                            # SQLite database (auto-created)
├── Dockerfile                       # Backend container (multi-stage)
├── docker-compose.yml               # All 5 services orchestration
├── pom.xml                          # Maven dependencies
└── pics/                            # Screenshots
```

---

## Configuration

### Environment Variables (Docker)

| Variable | Service | Default | Description |
|----------|---------|---------|-------------|
| `OPENAI_API_KEY` | backend, frontend, ai-service | (required) | OpenAI API key |
| `AI_SERVICE_URL` | backend, frontend | `http://localhost:5001` | Flask AI service URL |
| `SPRING_DATA_REDIS_HOST` | backend | `localhost` | Redis hostname |
| `QDRANT_HOST` | backend | `localhost` | Qdrant hostname |
| `SMARTFRIDGE_API_URL` | frontend | `http://localhost:8080/api` | Backend API URL |

### application.properties

```properties
# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms

# Vector Cache TTL (seconds)
vector.cache.ttl=3600

# OpenAI Configuration
openai.api-key=${OPENAI_API_KEY}
openai.base-url=${OPENAI_BASE_URL:https://api.openai.com/v1}
openai.embedding-model=text-embedding-3-small
openai.chat-model=gpt-4o-mini

# Qdrant Configuration
qdrant.host=localhost
qdrant.port=6333

# AI Service Configuration
ai.service.url=${AI_SERVICE_URL:http://localhost:5001}
```

---

## License
MIT
