package org.Cry_Legende.sHCodex.network;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Régions découvertes en MariaDB, partagées par tous les serveurs. Chargée seulement avec SHNetwork
 * (HikariCP et le pilote MariaDB viennent de {@code libraries:} dans plugin.yml).
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal.</p>
 */
public final class CodexDatabase {

    private static final String TABLE = "shcodex_discoveries";

    private final HikariDataSource pool;

    public CodexDatabase(String url, String user, String password, int timeoutMs) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SHCodex");
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(timeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(timeoutMs));
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(timeoutMs);
        // le serveur démarre même si la base est absente : le plugin reste alors en mode local
        hikari.setInitializationFailTimeout(-1);
        hikari.setKeepaliveTime(60_000);
        this.pool = new HikariDataSource(hikari);
    }

    /** Crée la table si elle manque. Une table déjà là ne demande aucun droit CREATE à l'utilisateur. */
    public void createTable() throws SQLException {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + TABLE + " LIMIT 1")) {
                return;
            } catch (SQLException missing) {
                // la table n'existe pas encore : création ci-dessous
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "uuid CHAR(36) NOT NULL, "
                + "region VARCHAR(100) NOT NULL, "
                + "discovered_at BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (uuid, region), "
                + "KEY idx_region (region)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
    }

    /** Régions découvertes par le joueur et leur date (vide sans ligne). */
    public Map<String, Long> load(UUID uuid) throws SQLException {
        Map<String, Long> regions = new HashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT region, discovered_at FROM " + TABLE + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    regions.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return regions;
    }

    /**
     * Enregistre une découverte. L'instruction ne touche jamais une ligne déjà là : si deux serveurs la voient en même
     * temps, un seul l'écrit.
     *
     * @return vrai si cette découverte est nouvelle en base
     */
    public boolean discover(UUID uuid, String region, long at) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + TABLE + " (uuid, region, discovered_at) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, region);
            ps.setLong(3, at);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Reverrouille une région (commande d'administration).
     *
     * @return vrai si la région était bien découverte
     */
    public boolean lock(UUID uuid, String region) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE uuid = ? AND region = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, region);
            return ps.executeUpdate() > 0;
        }
    }

    /** Nombre de joueurs qui ont découvert chaque région (régions jamais découvertes absentes). */
    public Map<String, Integer> counts() throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        try (Connection c = pool.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT region, COUNT(*) FROM " + TABLE + " GROUP BY region")) {
            while (rs.next()) {
                counts.put(rs.getString(1), rs.getInt(2));
            }
        }
        return counts;
    }

    /**
     * Reprend les découvertes des anciens fichiers playerdata/. Une région déjà en base pour ce joueur n'est pas
     * touchée, donc l'importation peut être relancée sans rien écraser ni compter deux fois.
     *
     * @return {régions ajoutées, déjà présentes}
     */
    public int[] importAll(Map<UUID, Map<String, Long>> byPlayer) throws SQLException {
        int imported = 0;
        int present = 0;
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + TABLE + " (uuid, region, discovered_at) VALUES (?, ?, ?)")) {
                int pending = 0;
                for (Map.Entry<UUID, Map<String, Long>> player : byPlayer.entrySet()) {
                    for (Map.Entry<String, Long> region : player.getValue().entrySet()) {
                        ps.setString(1, player.getKey().toString());
                        ps.setString(2, region.getKey());
                        ps.setLong(3, region.getValue() == null ? 0L : region.getValue());
                        if (ps.executeUpdate() > 0) {
                            imported++;
                        } else {
                            present++;
                        }
                        if (++pending % 500 == 0) {
                            c.commit();
                        }
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return new int[]{imported, present};
    }

    /**
     * Pseudo exact d'un joueur déjà venu sur le réseau d'après le registre des premières connexions (colonnes name, en
     * minuscules, et display_name), ou null. Un registre absent ou illisible rend null.
     */
    public String firstSeen(String table, String name) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT display_name FROM " + table + " WHERE name = ? LIMIT 1")) {
            ps.setString(1, name.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void close() {
        pool.close();
    }
}
