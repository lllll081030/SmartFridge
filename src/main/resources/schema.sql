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
