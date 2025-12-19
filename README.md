# SmartFridge
## SmartFridge v1.0
SmartFridge is a full-stack application that helps you discover what recipes you can cook with the ingredients currently in your fridge. It is an engineering implementation of Kahn's algorithm, inspired by LeetCode’s “Find All Possible Recipes from Given Supplies”.
- Frontend: Streamlit
- Backend: Spring Boot
- Database: SQLite

### Features
**Fridge Management**
- Add/remove ingredients with quantities
- Track what's in your fridge
- Real-time sync with database

**Recipe Book**
- Browse recipes by cuisine type
- Add custom recipes with ingredients and instructions
- Delete unwanted recipes

**Smart Recipe Generation**
- Find all cookable recipes based on current fridge contents
- Uses topological sorting (Kahn's algorithm) for dependency resolution
- View recipe details and instructions

**Technical**
- Local persistence with SQLite
- Fully offline: runs on your computer only
- Modular frontend architecture

### Tech Stack

#### Frontend
- Python 3.10+
- Streamlit (UI)
- Modular structure: `views/`, `api.py`, `styles.py`

#### Backend
- Java 17
- Spring Boot 3.2
- Spring Web (REST API)
- Direct SQL via JDBC

#### Database
- SQLite (embedded, file-based)
- File: `data/smartfridge.db`

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/fridge` | Get fridge contents with quantities |
| POST | `/api/fridge/{item}` | Add item to fridge |
| PUT | `/api/fridge/{item}` | Update item quantity |
| DELETE | `/api/fridge/{item}` | Remove item from fridge |
| GET | `/api/recipes` | Get all recipes by cuisine |
| GET | `/api/recipes/{name}` | Get recipe details |
| POST | `/api/recipes` | Add new recipe |
| DELETE | `/api/recipes/{name}` | Delete recipe |
| GET | `/api/cuisines` | Get available cuisine types |
| GET | `/api/generate` | Generate cookable recipes |

### Run Locally

**Prerequisites**
- Java 17+
- Maven
- Python 3.10+

**Backend**
```bash
cd SmartFridge
mvn spring-boot:run
```

**Frontend**
```bash
cd SmartFridge/frontend
pip install streamlit requests
streamlit run app.py
```

**Access**
- Frontend: http://localhost:8501
- Backend API: http://localhost:8080/api

### Project Structure
```
SmartFridge/
├── src/main/java/com/smartfridge/
│   ├── controller/    # REST endpoints
│   ├── service/       # Business logic
│   ├── dao/           # Database access
│   └── model/         # Data models
├── frontend/
│   ├── app.py         # Main entry point
│   ├── api.py         # API client
│   ├── styles.py      # CSS styles
│   ├── config.py      # Configuration
│   └── views/         # Page modules
└── data/
    └── smartfridge.db # SQLite database
```