# SmartFridge

A full-stack application that helps you discover what recipes you can cook with the ingredients in your fridge. Uses Kahn's algorithm (topological sorting) for dependency resolution, inspired by LeetCode's "Find All Possible Recipes from Given Supplies".

---

![alt text](image.png)

![alt text](image.png)

## 🚀 What's New

### v2.0 - AI-Powered Recipe Parser

![AI Recipe Parser](pics/v2_ai_recipe_parser.png)
![AI Food Recognition](pics/v2_ai_recipe_food.png)

**New Features:**
- 🤖 **AI Recipe Parser** - Paste any recipe text, AI extracts ingredients, seasonings, and instructions
- 🧂 **Ingredient vs Seasoning Separation** - Seasonings don't count towards recipe requirements
- 📊 **Improved UI** - Better recipe display with separate ingredient/seasoning sections

**Technical Improvements:**
- Ollama integration for local LLM (llama3.2:1b)
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
| Frontend | Python 3.10+, Streamlit |
| Backend | Java 17, Spring Boot 3.2 |
| Database | SQLite (embedded) |
| AI | Ollama (local LLM) |

---

## Quick Start

### Prerequisites
- Java 17+
- Maven
- Python 3.10+
- Ollama (for AI features)

### Run Locally

**Backend**
```bash
cd SmartFridge
mvn spring-boot:run
```

**Frontend**
```bash
cd SmartFridge/frontend
pip install -r requirements.txt
streamlit run app.py
```

**Access**
- Frontend: http://localhost:8501
- Backend API: http://localhost:8080/api

### Run with Docker
```bash
docker-compose build
docker-compose up -d
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/fridge` | Get fridge contents |
| POST | `/api/fridge/{item}` | Add item to fridge |
| DELETE | `/api/fridge/{item}` | Remove item |
| GET | `/api/recipes` | Get all recipes by cuisine |
| POST | `/api/recipes` | Add new recipe (with seasonings) |
| DELETE | `/api/recipes/{name}` | Delete recipe |
| GET | `/api/generate` | Generate cookable recipes |

---

## Project Structure
```
SmartFridge/
├── src/main/java/com/smartfridge/
│   ├── controller/         # REST endpoints
│   ├── service/            # Business logic
│   ├── dao/                # Database access
│   │   ├── DatabaseInitializer.java
│   │   ├── RecipeDao.java
│   │   └── SupplyDao.java
│   └── model/              # Data models
├── frontend/
│   ├── app.py              # Main entry
│   ├── api.py              # API client
│   ├── ollama_client.py    # AI integration
│   └── views/              # Page modules
└── pics/                   # Screenshots
```

---

## License
MIT