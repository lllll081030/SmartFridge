package com.smartfridge.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.stream.Collectors;

/**
 * Database initializer - handles schema creation and migrations
 */
@Component
public class DatabaseInitializer {

    @Autowired
    private DataSource dataSource;

    /**
     * Initialize database schema on startup
     */
    @PostConstruct
    public void initializeDatabase() {
        try (Connection conn = dataSource.getConnection();
                InputStream schemaStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {

            if (schemaStream == null) {
                throw new RuntimeException("schema.sql not found in resources");
            }

            // Read schema and strip comment lines
            String schema = new BufferedReader(new InputStreamReader(schemaStream))
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
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

            // Run migrations
            migrateAddSortOrderColumn(conn);
            migrateAddIsSeasoningColumn(conn);

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
     * Migration: Add is_seasoning column to recipe_dependencies table
     */
    private void migrateAddIsSeasoningColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(recipe_dependencies)");
            boolean hasColumn = false;
            while (rs.next()) {
                if ("is_seasoning".equals(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
            rs.close();

            if (!hasColumn) {
                stmt.execute("ALTER TABLE recipe_dependencies ADD COLUMN is_seasoning INTEGER DEFAULT 0");
                System.out.println("Migration: Added is_seasoning column to recipe_dependencies table");

                // Migrate existing data with 'seasoning:' prefix
                stmt.execute(
                        "UPDATE recipe_dependencies SET is_seasoning = 1 WHERE ingredient_name LIKE 'seasoning:%'");
                stmt.execute(
                        "UPDATE recipe_dependencies SET ingredient_name = SUBSTR(ingredient_name, 11) WHERE ingredient_name LIKE 'seasoning:%'");
                System.out.println("Migration: Migrated existing seasoning data");
            }
        } catch (SQLException e) {
            System.err.println("Warning: Could not migrate is_seasoning column: " + e.getMessage());
        }
    }
}
