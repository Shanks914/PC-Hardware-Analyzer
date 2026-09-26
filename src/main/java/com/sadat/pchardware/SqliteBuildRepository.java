package com.sadat.pchardware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqliteBuildRepository implements BuildRepository {
    private final String jdbcUrl;

    public SqliteBuildRepository() {
        Path database = Path.of("data", "pc-hardware-analyzer.db").toAbsolutePath();
        try {
            Files.createDirectories(database.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the database folder", e);
        }
        jdbcUrl = "jdbc:sqlite:" + database;
        initializeSchema();
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void initializeSchema() {
        String createBuilds = """
                CREATE TABLE IF NOT EXISTS pc_builds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        String createItems = """
                CREATE TABLE IF NOT EXISTS build_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    build_id INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    part_name TEXT NOT NULL,
                    specs TEXT NOT NULL,
                    price_bdt REAL NOT NULL,
                    FOREIGN KEY (build_id) REFERENCES pc_builds(id) ON DELETE CASCADE
                )
                """;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(createBuilds);
            statement.execute(createItems);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize the SQLite database", e);
        }
    }

    @Override
    public long save(Long existingId, String name, List<Part> parts) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long id;
                if (existingId == null) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO pc_builds(name, updated_at) VALUES(?, CURRENT_TIMESTAMP)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        insert.setString(1, name);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("SQLite did not return a build ID");
                            id = keys.getLong(1);
                        }
                    }
                } else {
                    id = existingId;
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE pc_builds SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                        update.setString(1, name);
                        update.setLong(2, id);
                        if (update.executeUpdate() == 0) throw new SQLException("Saved build no longer exists");
                    }
                    try (PreparedStatement removeOldItems = connection.prepareStatement(
                            "DELETE FROM build_items WHERE build_id = ?")) {
                        removeOldItems.setLong(1, id);
                        removeOldItems.executeUpdate();
                    }
                }

                try (PreparedStatement insertItem = connection.prepareStatement(
                        "INSERT INTO build_items(build_id, category, part_name, specs, price_bdt) VALUES(?, ?, ?, ?, ?)")) {
                    for (Part part : parts) {
                        insertItem.setLong(1, id);
                        insertItem.setString(2, part.category());
                        insertItem.setString(3, part.name());
                        insertItem.setString(4, part.specs());
                        insertItem.setDouble(5, part.price());
                        insertItem.addBatch();
                    }
                    insertItem.executeBatch();
                }
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    @Override
    public List<SavedBuild> findAll() throws SQLException {
        List<SavedBuild> builds = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT id, name FROM pc_builds ORDER BY updated_at DESC, id DESC");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong("id");
                builds.add(new SavedBuild(id, rows.getString("name"), findParts(connection, id)));
            }
        }
        return builds;
    }

    @Override
    public SavedBuild findById(long id) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement query = connection.prepareStatement("SELECT name FROM pc_builds WHERE id = ?")) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                return new SavedBuild(id, row.getString("name"), findParts(connection, id));
            }
        }
    }

    private List<Part> findParts(Connection connection, long buildId) throws SQLException {
        List<Part> parts = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT category, part_name, specs, price_bdt FROM build_items WHERE build_id = ? ORDER BY id")) {
            query.setLong(1, buildId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    parts.add(new Part(rows.getString("category"), rows.getString("part_name"),
                            rows.getString("specs"), rows.getDouble("price_bdt")));
                }
            }
        }
        return parts;
    }

    @Override
    public void delete(long id) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM pc_builds WHERE id = ?")) {
            delete.setLong(1, id);
            delete.executeUpdate();
        }
    }
}
