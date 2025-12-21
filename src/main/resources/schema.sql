-- SmartFridge Database Schema
-- Using name as primary key for simplicity and performance

-- All food items (both basic ingredients and recipes)
CREATE TABLE IF NOT EXISTS food_items (
    name TEXT PRIMARY KEY
);

-- Recipe dependencies (lightweight for graph algorithm)
-- Only recipes have entries here
CREATE TABLE IF NOT EXISTS recipe_dependencies (
    recipe_name TEXT NOT NULL,
    ingredient_name TEXT NOT NULL,
    is_seasoning INTEGER DEFAULT 0,  -- 0 = ingredient, 1 = seasoning
    FOREIGN KEY (recipe_name) REFERENCES food_items(name) ON DELETE CASCADE,
    FOREIGN KEY (ingredient_name) REFERENCES food_items(name) ON DELETE CASCADE,
    PRIMARY KEY (recipe_name, ingredient_name)
);

-- Recipe details (heavyweight data loaded on-demand)
-- Only recipes have entries here
CREATE TABLE IF NOT EXISTS recipe_details (
    recipe_name TEXT PRIMARY KEY,
    cuisine_type TEXT,
    instructions TEXT,
    image_url TEXT,
    FOREIGN KEY (recipe_name) REFERENCES food_items(name) ON DELETE CASCADE
);

-- Current supplies available in the fridge
CREATE TABLE IF NOT EXISTS supplies (
    name TEXT PRIMARY KEY,
    quantity INTEGER DEFAULT 1,
    sort_order INTEGER DEFAULT 0,
    FOREIGN KEY (name) REFERENCES food_items(name) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_recipe_deps_recipe ON recipe_dependencies(recipe_name);
CREATE INDEX IF NOT EXISTS idx_recipe_deps_ingredient ON recipe_dependencies(ingredient_name);

-- Canonical ingredient names with AI-generated aliases
-- Used for auto-mapping variants (e.g., "roma tomato" -> "tomato")
CREATE TABLE IF NOT EXISTS ingredient_aliases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    canonical_name TEXT NOT NULL,    -- e.g., "tomato"
    alias TEXT NOT NULL,             -- e.g., "roma tomato", "cherry tomato"
    confidence REAL DEFAULT 1.0,     -- AI confidence score (0.0-1.0)
    source TEXT DEFAULT 'manual',    -- 'manual', 'ai_generated'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(canonical_name, alias)
);

CREATE INDEX IF NOT EXISTS idx_aliases_canonical ON ingredient_aliases(canonical_name);
CREATE INDEX IF NOT EXISTS idx_aliases_alias ON ingredient_aliases(alias);

-- Recipe embeddings metadata (vectors stored in Qdrant)
-- Used for semantic search of recipes
CREATE TABLE IF NOT EXISTS recipe_embeddings (
    recipe_name TEXT PRIMARY KEY,
    embedding_id TEXT NOT NULL,      -- Qdrant point ID
    model_version TEXT,              -- e.g., "nomic-embed-text"
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipe_name) REFERENCES food_items(name) ON DELETE CASCADE
);
