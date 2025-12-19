package com.smartfridge.dao;

import com.smartfridge.model.CuisineType;
import com.smartfridge.model.RecipeDetails;
import com.smartfridge.model.RecipeSimple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class RecipeDao {

    @Autowired
    private DataSource dataSource; // Use pooled connections via HikariCP

    /**
     * Initialize database schema on startup
     */
    @PostConstruct
    public void initializeDatabase() {
        try (Connection conn = getConnection();
                InputStream schemaStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {

            if (schemaStream == null) {
                throw new RuntimeException("schema.sql not found in resources");
            }

            // Read schema and strip comment lines
            String schema = new BufferedReader(new InputStreamReader(schemaStream))
                    .lines()
                    .filter(line -> !line.trim().startsWith("--")) // Remove comment lines
                    .collect(Collectors.joining("\n"));

            // Execute each statement separately
            String[] statements = schema.split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }

            // Migration: Add sort_order column if it doesn't exist
            migrateAddSortOrderColumn(conn);

            System.out.println("Database initialized successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Migration: Add sort_order column to supplies table if it doesn't exist
     */
    private void migrateAddSortOrderColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check if column exists by querying PRAGMA
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(supplies)");
            boolean hasColumn = false;
            while (rs.next()) {
                if ("sort_order".equals(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
            rs.close();

            if (!hasColumn) {
                stmt.execute("ALTER TABLE supplies ADD COLUMN sort_order INTEGER DEFAULT 0");
                System.out.println("Migration: Added sort_order column to supplies table");
            }
        } catch (SQLException e) {
            System.err.println("Warning: Could not migrate sort_order column: " + e.getMessage());
        }
    }

    /**
     * Load lightweight recipe graph for Kahn's algorithm
     * Returns Map<recipeName, List<ingredientNames>>
     */
    public Map<String, List<String>> loadRecipeGraph() {
        Map<String, List<String>> graph = new HashMap<>();

        String sql = "SELECT recipe_name, ingredient_name FROM recipe_dependencies ORDER BY recipe_name";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String recipeName = rs.getString("recipe_name");
                String ingredientName = rs.getString("ingredient_name");

                graph.computeIfAbsent(recipeName, k -> new ArrayList<>()).add(ingredientName);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load recipe graph", e);
        }

        return graph;
    }

    /**
     * Get recipe details using optimized JOIN query (single DB round-trip)
     */
    public RecipeDetails getRecipeDetails(String recipeName) {
        // Single query with LEFT JOIN to get all data in one round-trip
        String sql = """
                SELECT rd.recipe_name, rd.cuisine_type, rd.instructions, rd.image_url,
                       dep.ingredient_name
                FROM recipe_details rd
                LEFT JOIN recipe_dependencies dep ON rd.recipe_name = dep.recipe_name
                WHERE rd.recipe_name = ?
                """;

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, recipeName);

            String cuisineTypeStr = null;
            String instructions = null;
            String imageUrl = null;
            List<String> ingredients = new ArrayList<>();

            try (ResultSet rs = pstmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (first) {
                        cuisineTypeStr = rs.getString("cuisine_type");
                        instructions = rs.getString("instructions");
                        imageUrl = rs.getString("image_url");
                        first = false;
                    }
                    String ingredient = rs.getString("ingredient_name");
                    if (ingredient != null) {
                        ingredients.add(ingredient);
                    }
                }
            }

            // If we found the recipe, return it
            if (!ingredients.isEmpty()) {
                CuisineType cuisineType = parseCuisineType(cuisineTypeStr);
                return new RecipeDetails(recipeName, ingredients, cuisineType, instructions, imageUrl);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get recipe details for: " + recipeName, e);
        }

        return null;
    }

    /**
     * Parse cuisine type string to enum with null safety
     */
    private CuisineType parseCuisineType(String cuisineTypeStr) {
        if (cuisineTypeStr == null || cuisineTypeStr.isEmpty()) {
            return CuisineType.OTHER;
        }
        try {
            return CuisineType.valueOf(cuisineTypeStr);
        } catch (IllegalArgumentException e) {
            return CuisineType.OTHER;
        }
    }

    /**
     * Save a recipe with its dependencies using batch operations
     */
    public void saveRecipe(String recipeName, List<String> ingredients) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert recipe and all ingredients into food_items using batch
                String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertFood)) {
                    // Add recipe
                    pstmt.setString(1, recipeName);
                    pstmt.addBatch();

                    // Add all ingredients
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, ingredient);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch(); // Single round-trip for all food items
                }

                // Insert dependencies using batch
                String insertDep = "INSERT OR IGNORE INTO recipe_dependencies (recipe_name, ingredient_name) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDep)) {
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, recipeName);
                        pstmt.setString(2, ingredient);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch(); // Single round-trip for all dependencies
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save recipe: " + recipeName, e);
        }
    }

    /**
     * Save a recipe with full details including cuisine type and instructions
     */
    public void saveRecipeWithDetails(String recipeName, List<String> ingredients,
            String cuisineType, String instructions, String imageUrl) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert recipe and all ingredients into food_items using batch
                String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertFood)) {
                    pstmt.setString(1, recipeName);
                    pstmt.addBatch();
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, ingredient);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Insert dependencies
                String insertDep = "INSERT OR IGNORE INTO recipe_dependencies (recipe_name, ingredient_name) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDep)) {
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, recipeName);
                        pstmt.setString(2, ingredient);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Insert or replace recipe details
                String insertDetails = "INSERT OR REPLACE INTO recipe_details (recipe_name, cuisine_type, instructions, image_url) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDetails)) {
                    pstmt.setString(1, recipeName);
                    pstmt.setString(2, cuisineType != null ? cuisineType : "OTHER");
                    pstmt.setString(3, instructions);
                    pstmt.setString(4, imageUrl);
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save recipe with details: " + recipeName, e);
        }
    }

    /**
     * Delete a recipe and all its dependencies
     */
    public void deleteRecipe(String recipeName) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Delete from recipe_details
                String deleteDetails = "DELETE FROM recipe_details WHERE recipe_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteDetails)) {
                    pstmt.setString(1, recipeName);
                    pstmt.executeUpdate();
                }

                // Delete from recipe_dependencies
                String deleteDeps = "DELETE FROM recipe_dependencies WHERE recipe_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteDeps)) {
                    pstmt.setString(1, recipeName);
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete recipe: " + recipeName, e);
        }
    }

    /**
     * Get current supplies from database with quantities and sort order
     * Returns List of {name, quantity, sortOrder}
     */
    public List<Map<String, Object>> getSuppliesWithDetails() {
        List<Map<String, Object>> supplies = new ArrayList<>();

        // Use IFNULL to handle case where sort_order column might be NULL or missing
        String sql = "SELECT name, quantity, IFNULL(sort_order, 0) as sort_order FROM supplies ORDER BY sort_order ASC, name ASC";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", rs.getString("name"));
                item.put("quantity", rs.getInt("quantity"));
                item.put("sortOrder", rs.getInt("sort_order"));
                supplies.add(item);
            }

        } catch (SQLException e) {
            // If sort_order column doesn't exist, fall back to simple query
            System.err.println("Warning: sort_order query failed, trying fallback: " + e.getMessage());
            return getSuppliesWithDetailsFallback();
        }

        return supplies;
    }

    /**
     * Fallback for getSuppliesWithDetails when sort_order column doesn't exist
     */
    private List<Map<String, Object>> getSuppliesWithDetailsFallback() {
        List<Map<String, Object>> supplies = new ArrayList<>();
        String sql = "SELECT name, quantity FROM supplies ORDER BY name ASC";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int order = 0;
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", rs.getString("name"));
                item.put("quantity", rs.getInt("quantity"));
                item.put("sortOrder", order++);
                supplies.add(item);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get supplies", e);
        }

        return supplies;
    }

    /**
     * Get current supplies from database with quantities (legacy)
     */
    public Map<String, Integer> getSuppliesWithQuantity() {
        Map<String, Integer> supplies = new LinkedHashMap<>();
        for (Map<String, Object> item : getSuppliesWithDetails()) {
            supplies.put((String) item.get("name"), (Integer) item.get("quantity"));
        }
        return supplies;
    }

    /**
     * Get current supplies from database (names only for backward compatibility)
     */
    public Set<String> getSupplies() {
        return getSuppliesWithQuantity().keySet();
    }

    /**
     * Update supplies in database using batch operations
     */
    public void updateSupplies(List<String> supplies) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Clear existing supplies
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM supplies");
                }

                // Insert food items and supplies using batch
                String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
                String insertSupply = "INSERT OR IGNORE INTO supplies (name) VALUES (?)";

                try (PreparedStatement foodStmt = conn.prepareStatement(insertFood);
                        PreparedStatement supplyStmt = conn.prepareStatement(insertSupply)) {

                    for (String supply : supplies) {
                        foodStmt.setString(1, supply);
                        foodStmt.addBatch();
                        supplyStmt.setString(1, supply);
                        supplyStmt.addBatch();
                    }

                    foodStmt.executeBatch();
                    supplyStmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update supplies", e);
        }
    }

    /**
     * Add single supply item with quantity
     */
    public void addSupply(String item, int quantity) {
        try (Connection conn = getConnection()) {
            // Add to food_items
            String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertFood)) {
                pstmt.setString(1, item);
                pstmt.executeUpdate();
            }

            // Check if supply already exists
            String checkSql = "SELECT quantity FROM supplies WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setString(1, item);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Update existing: add to current quantity
                        int currentQty = rs.getInt("quantity");
                        String updateSql = "UPDATE supplies SET quantity = ? WHERE name = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, currentQty + quantity);
                            updateStmt.setString(2, item);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // Insert new
                        String insertSupply = "INSERT INTO supplies (name, quantity) VALUES (?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSupply)) {
                            insertStmt.setString(1, item);
                            insertStmt.setInt(2, quantity);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to add supply: " + item, e);
        }
    }

    /**
     * Add single supply item with default quantity of 1
     */
    public void addSupply(String item) {
        addSupply(item, 1);
    }

    /**
     * Update quantity for a supply item
     */
    public void updateSupplyCount(String item, int quantity) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE supplies SET quantity = ? WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, quantity);
                pstmt.setString(2, item);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update supply count: " + item, e);
        }
    }

    /**
     * Update sort order for all supplies based on ordered list
     */
    public void updateSuppliesOrder(List<String> orderedItems) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                String sql = "UPDATE supplies SET sort_order = ? WHERE name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    for (int i = 0; i < orderedItems.size(); i++) {
                        pstmt.setInt(1, i);
                        pstmt.setString(2, orderedItems.get(i));
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update supplies order", e);
        }
    }

    /**
     * Remove single supply item
     */
    public void removeSupply(String item) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM supplies WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, item);
                pstmt.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove supply: " + item, e);
        }
    }

    /**
     * Get all recipes grouped by cuisine type
     */
    public Map<String, List<RecipeSimple>> getAllRecipesByCuisine() {
        Map<String, List<RecipeSimple>> result = new LinkedHashMap<>();

        // First, get all recipes with their cuisine types
        String sql = """
                SELECT rd.recipe_name, rd.cuisine_type, dep.ingredient_name
                FROM recipe_details rd
                LEFT JOIN recipe_dependencies dep ON rd.recipe_name = dep.recipe_name
                ORDER BY rd.cuisine_type, rd.recipe_name
                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            Map<String, List<String>> recipeIngredients = new LinkedHashMap<>();
            Map<String, String> recipeCuisine = new HashMap<>();

            while (rs.next()) {
                String recipeName = rs.getString("recipe_name");
                String cuisineType = rs.getString("cuisine_type");
                String ingredient = rs.getString("ingredient_name");

                recipeCuisine.put(recipeName, cuisineType != null ? cuisineType : "OTHER");

                if (ingredient != null) {
                    recipeIngredients.computeIfAbsent(recipeName, k -> new ArrayList<>()).add(ingredient);
                }
            }

            // Group by cuisine type
            for (Map.Entry<String, List<String>> entry : recipeIngredients.entrySet()) {
                String recipeName = entry.getKey();
                List<String> ingredients = entry.getValue();
                String cuisine = recipeCuisine.get(recipeName);

                RecipeSimple recipe = new RecipeSimple(recipeName, ingredients);
                result.computeIfAbsent(cuisine, k -> new ArrayList<>()).add(recipe);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get recipes by cuisine", e);
        }

        return result;
    }

    /**
     * Get database connection from pool
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
