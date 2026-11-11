package org.Cry_Legende.sHCodex.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protège la règle « une découverte ne compte qu'une fois » de CodexDatabase sur une vraie MariaDB. Ne s'exécute que si
 * SH_TEST_DB_URL, SH_TEST_DB_USER et SH_TEST_DB_PASSWORD sont définis ; la base visée doit être une base de brouillon
 * (jamais bo_net ni une base de production : la table y est créée).
 */
class CodexDatabaseTest {

    private CodexDatabase db;

    @BeforeEach
    void open() throws Exception {
        String url = System.getenv("SH_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "SH_TEST_DB_URL non défini : test de base ignoré");
        db = new CodexDatabase(url, System.getenv("SH_TEST_DB_USER"), System.getenv("SH_TEST_DB_PASSWORD"), 3000);
        db.createTable();
    }

    @AfterEach
    void close() {
        if (db != null) db.close();
    }

    @Test
    void uneDecouverteNeSeCompteQuUneFois() throws Exception {
        UUID a = UUID.randomUUID();
        assertTrue(db.discover(a, "usine", 1000L));
        assertFalse(db.discover(a, "usine", 2000L), "la même découverte rejouée est déjà en base");
        assertEquals(1000L, db.load(a).get("usine"), "la date d'origine est gardée");
    }

    @Test
    void huitEcrituresSimultaneesUnSeulGagnant() throws Exception {
        UUID c = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> db.discover(c, "nether", System.nanoTime())));
        }
        int winners = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) winners++;
        }
        pool.shutdown();
        assertEquals(1, winners);
    }

    @Test
    void lockEtStats() throws Exception {
        UUID b = UUID.randomUUID();
        String region = "test_lock_" + b;
        assertTrue(db.discover(b, region, 3000L));
        assertEquals(1, db.counts().get(region));
        assertTrue(db.lock(b, region));
        assertFalse(db.lock(b, region));
        assertEquals(0, db.counts().getOrDefault(region, 0));
    }

    @Test
    void importRelanceNAjouteRien() throws Exception {
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        Map<UUID, Map<String, Long>> data = new HashMap<>();
        data.put(d, Map.of("usine", 5L, "egout", 6L));
        data.put(e, Map.of("usine", 7L));
        assertArrayEquals(new int[]{3, 0}, db.importAll(data));
        assertArrayEquals(new int[]{0, 3}, db.importAll(data));
        db.lock(d, "usine");
        assertArrayEquals(new int[]{1, 2}, db.importAll(data), "seul ce qui manque est ajouté");
    }
}
