package org.fentanylsolutions.wawelauth.wawelclient.storage.sqlite;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase;

/**
 * Client-side SQLite database for the account manager.
 * <p>
 * Extends {@link SqliteDatabase} to reuse connection management (WAL mode,
 * synchronized access, etc.) but overrides table creation to produce only
 * the client-specific schema (providers, accounts). Server tables are not
 * created.
 * <p>
 * Uses {@code PRAGMA user_version} for schema migrations from day one.
 */
public class ClientDatabase extends SqliteDatabase {

    private static final int CURRENT_VERSION = 6;

    public ClientDatabase(File dbFile) {
        super(dbFile);
    }

    @Override
    protected void createTables() throws SQLException {
        int version = query(conn -> {
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });

        WawelAuth.debug("Client DB version: " + version + ", target: " + CURRENT_VERSION);

        if (version < 1) {
            migrateToV1();
        }
        if (version < 2) {
            migrateToV2();
        }
        if (version < 3 || !hasColumn("providers", "manual_added")) {
            migrateToV3();
        }
        if (version < 4 || !hasColumn("providers", "proxy_enabled")
            || !hasColumn("providers", "proxy_type")
            || !hasColumn("providers", "proxy_host")
            || !hasColumn("providers", "proxy_port")
            || !hasColumn("providers", "proxy_username")
            || !hasColumn("providers", "proxy_password")) {
            migrateToV4();
        }
        if (version < 5 || !hasColumn("accounts", "local_skin_path") || !hasColumn("accounts", "local_skin_model")) {
            migrateToV5();
        }
        if (version < 6 || !hasColumn("accounts", "local_cape_path")) {
            migrateToV6();
        }

        // Future: if (version < 4) { migrateToV4(); }

        execute(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA user_version = " + CURRENT_VERSION);
            }
        });
    }

    private void migrateToV1() {
        execute(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS providers (
                        name TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        api_root TEXT NOT NULL,
                        auth_server_url TEXT NOT NULL,
                        session_server_url TEXT NOT NULL,
                        services_url TEXT,
                        skin_domains TEXT,
                        public_key TEXT,
                        public_key_fingerprint TEXT,
                        created_at INTEGER NOT NULL,
                        manual_added INTEGER NOT NULL DEFAULT 1,
                        proxy_enabled INTEGER NOT NULL DEFAULT 0,
                        proxy_type TEXT,
                        proxy_host TEXT,
                        proxy_port INTEGER,
                        proxy_username TEXT,
                        proxy_password TEXT
                    )""");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        provider_name TEXT NOT NULL REFERENCES providers(name) ON DELETE CASCADE,
                        user_uuid TEXT NOT NULL,
                        profile_uuid TEXT,
                        profile_name TEXT,
                        access_token TEXT NOT NULL,
                        client_token TEXT,
                        user_properties TEXT,
                        status TEXT NOT NULL DEFAULT 'VALID',
                        last_error TEXT,
                        last_error_at INTEGER NOT NULL DEFAULT 0,
                        last_refresh_attempt_at INTEGER NOT NULL DEFAULT 0,
                        consecutive_failures INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        last_validated_at INTEGER NOT NULL,
                        token_issued_at INTEGER NOT NULL,
                        local_skin_path TEXT,
                        local_skin_model TEXT,
                        local_cape_path TEXT
                    )""");

                // Unique per profile when a profile is bound
                stmt.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_provider_profile "
                        + "ON accounts(provider_name, profile_uuid) WHERE profile_uuid IS NOT NULL");
                // Unique per user when no profile is bound (prevents duplicate unbound entries)
                stmt.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_provider_user_unbound "
                        + "ON accounts(provider_name, user_uuid) WHERE profile_uuid IS NULL");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accounts_provider " + "ON accounts(provider_name)");
            }
        });

        WawelAuth.LOG.info("Client DB migrated to version 1");
    }

    private void migrateToV2() {
        if (!hasColumn("accounts", "refresh_token")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN refresh_token TEXT");
                }
            });
        }
        WawelAuth.LOG.info("Client DB migrated to version 2");
    }

    private void migrateToV3() {
        if (!hasColumn("providers", "manual_added")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN manual_added INTEGER NOT NULL DEFAULT 1");
                }
            });
        }
        WawelAuth.LOG.info("Client DB migrated to version 3");
    }

    private void migrateToV4() {
        if (!hasColumn("providers", "proxy_enabled")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN proxy_enabled INTEGER NOT NULL DEFAULT 0");
                }
            });
        }
        if (!hasColumn("providers", "proxy_type")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN proxy_type TEXT");
                }
            });
        }
        if (!hasColumn("providers", "proxy_host")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN proxy_host TEXT");
                }
            });
        }
        if (!hasColumn("providers", "proxy_port")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN proxy_port INTEGER");
                }
            });
        }
        if (!hasColumn("providers", "proxy_username")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN proxy_username TEXT");
                }
            });
        }
        if (!hasColumn("providers", "proxy_password")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE providers ADD COLUMN proxy_password TEXT");
                }
            });
        }
        WawelAuth.LOG.info("Client DB migrated to version 4");
    }

    private void migrateToV5() {
        if (!hasColumn("accounts", "local_skin_path")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN local_skin_path TEXT");
                }
            });
        }
        if (!hasColumn("accounts", "local_skin_model")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN local_skin_model TEXT");
                }
            });
        }
        WawelAuth.LOG.info("Client DB migrated to version 5");
    }

    private void migrateToV6() {
        if (!hasColumn("accounts", "local_cape_path")) {
            execute(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN local_cape_path TEXT");
                }
            });
        }
        WawelAuth.LOG.info("Client DB migrated to version 6");
    }

    private boolean hasColumn(String tableName, String columnName) {
        return query(conn -> {
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
                while (rs.next()) {
                    String existing = rs.getString("name");
                    if (columnName.equalsIgnoreCase(existing)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }
}
