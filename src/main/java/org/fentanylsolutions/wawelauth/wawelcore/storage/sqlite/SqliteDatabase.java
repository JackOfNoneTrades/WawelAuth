package org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.fentanylsolutions.wawelauth.WawelAuth;

/**
 * Manages the SQLite database connection and schema initialization.
 * <p>
 * Uses a single shared connection with WAL mode. SQLite in WAL mode allows
 * concurrent reads alongside a single writer. The busy_timeout PRAGMA ensures
 * that concurrent writes wait (up to 5 seconds) instead of immediately failing,
 * which is sufficient for the expected load of a Minecraft server's auth traffic.
 * <p>
 * All access to the connection is serialized via {@code synchronized} methods
 * ({@link #query}, {@link #execute}, {@link #runInTransaction}). DAOs must
 * use these entry points instead of accessing the connection directly, which
 * guarantees that transactions cannot be interleaved by concurrent threads.
 */
public class SqliteDatabase {

    /**
     * Callback that returns a value from a database operation.
     */
    @FunctionalInterface
    public interface SqlFunction<T> {

        T apply(Connection conn) throws SQLException;
    }

    /**
     * Callback that performs a void database operation.
     */
    @FunctionalInterface
    public interface SqlConsumer {

        void accept(Connection conn) throws SQLException;
    }

    private final String dbPath;
    private Connection connection;

    public SqliteDatabase(String dbPath) {
        this.dbPath = dbPath;
    }

    public SqliteDatabase(File dbFile) {
        this(dbFile.getAbsolutePath());
    }

    /**
     * Open the connection and initialize schema.
     */
    public void initialize() {
        try {
            // Ensure parent directory exists
            File dbFile = new File(dbPath);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw new RuntimeException("Failed to create database directory: " + parentDir);
                }
            }

            DriverManager.registerDriver(new org.sqlite.JDBC());
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA busy_timeout=5000");
            }
            createTables();
            WawelAuth.LOG.info("SQLite database initialized at {}", dbPath);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Executes a read operation under the connection lock.
     * Use this for SELECT queries and other operations that return a value.
     */
    public synchronized <T> T query(SqlFunction<T> action) {
        try {
            return action.apply(connection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes a write operation under the connection lock.
     * Use this for INSERT/UPDATE/DELETE operations.
     */
    public synchronized void execute(SqlConsumer action) {
        try {
            action.accept(connection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes the given action inside a BEGIN/COMMIT transaction.
     * If the action throws, the transaction is rolled back.
     * <p>
     * Holds the connection lock for the entire transaction, so concurrent
     * threads cannot interleave their operations with this transaction.
     * The lock is reentrant, so DAO calls inside the action (which also
     * acquire the lock via {@link #query}/{@link #execute}) work correctly.
     */
    public synchronized void runInTransaction(Runnable action) {
        try {
            connection.setAutoCommit(false);
            try {
                action.run();
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException sqlEx) {
            throw new RuntimeException("Transaction control failed", sqlEx);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                WawelAuth.LOG.warn("Error closing database", e);
            }
        }
    }

    protected void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    uuid TEXT PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    admin INTEGER NOT NULL DEFAULT 0,
                    locked INTEGER NOT NULL DEFAULT 0,
                    preferred_language TEXT,
                    created_at INTEGER NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS profiles (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    owner_uuid TEXT NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    offline_uuid TEXT,
                    skin_model TEXT NOT NULL DEFAULT 'CLASSIC',
                    skin_hash TEXT,
                    cape_hash TEXT,
                    elytra_hash TEXT,
                    uploadable_textures TEXT,
                    created_at INTEGER NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tokens (
                    access_token TEXT PRIMARY KEY,
                    client_token TEXT,
                    user_uuid TEXT NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    profile_uuid TEXT REFERENCES profiles(uuid) ON DELETE SET NULL,
                    issued_at INTEGER NOT NULL,
                    last_used_at INTEGER NOT NULL,
                    version INTEGER NOT NULL DEFAULT 1,
                    state TEXT NOT NULL DEFAULT 'VALID'
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS invites (
                    code TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL,
                    created_by TEXT REFERENCES users(uuid) ON DELETE SET NULL,
                    uses_remaining INTEGER NOT NULL DEFAULT 1
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stored_textures (
                    hash TEXT PRIMARY KEY,
                    texture_type TEXT NOT NULL,
                    uploaded_by TEXT,
                    uploaded_at INTEGER NOT NULL,
                    content_length INTEGER NOT NULL,
                    width INTEGER NOT NULL,
                    height INTEGER NOT NULL
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS admin_player_list_provider_bindings (
                    list_type TEXT NOT NULL,
                    profile_uuid TEXT NOT NULL,
                    provider_key TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (list_type, profile_uuid)
                )""");

            // Indexes for FK lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_profiles_owner ON profiles(owner_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tokens_user ON tokens(user_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tokens_profile ON tokens(profile_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tokens_state ON tokens(state)");
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_admin_player_list_bindings_profile ON admin_player_list_provider_bindings(profile_uuid)");

            // Schema migrations: add columns if they don't exist yet.
            // SQLite has no IF NOT EXISTS for ALTER TABLE ADD COLUMN,
            // so we check the column list via PRAGMA first.
            migrateProfilesCapeAnimated(stmt);
        }
    }

    private void migrateProfilesCapeAnimated(Statement stmt) throws SQLException {
        try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(profiles)")) {
            while (rs.next()) {
                if ("cape_animated".equals(rs.getString("name"))) {
                    return; // column already exists
                }
            }
        }
        stmt.execute("ALTER TABLE profiles ADD COLUMN cape_animated INTEGER DEFAULT 0");
        WawelAuth.LOG.info("Migrated profiles table: added cape_animated column.");
    }
}
