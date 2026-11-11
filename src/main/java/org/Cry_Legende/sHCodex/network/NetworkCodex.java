package org.Cry_Legende.sHCodex.network;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHCodex.SHCodex;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Mode réseau : les régions découvertes de chaque joueur sont dans la base partagée au lieu de {@code playerdata/}, et le
 * nombre de joueurs par région (stats) se compte dans cette base au lieu de {@code stats.yml}.
 *
 * <ul>
 *   <li>à la connexion : une fois le départ de l'autre serveur confirmé, les régions du joueur sont lues en base (en
 *       attendant, ses déplacements ne découvrent rien et le menu répond « données non trouvées ») ;</li>
 *   <li>tant que le joueur est sur un serveur, la mémoire de ce serveur fait foi ; chaque découverte ou re-verrouillage
 *       est écrit en base aussitôt, ligne par ligne ({@code INSERT IGNORE}, {@code DELETE}) ;</li>
 *   <li>au départ : la barrière de SHNetwork attend que les écritures en cours soient finies avant que l'autre serveur
 *       ne lise ;</li>
 *   <li>les stats sont relues en base à chaque changement (message du bus aux autres serveurs) et à intervalle régulier.</li>
 * </ul>
 */
public final class NetworkCodex implements Listener {

    /** Joueur retrouvé hors ligne : UUID du réseau et pseudo exact. */
    public record Known(UUID uuid, String name) {
    }

    private static final int WRITE_ATTEMPTS = 3;
    private static final int LOAD_RETRY_SECONDS = 5;
    private static final int LOAD_RETRY_MAX = 60;
    private static final long PRELOAD_KEEP_MS = 120_000L;

    private record Preloaded(CompletableFuture<Map<String, Long>> regions, long at) {
    }

    /** Écriture en base qui rend un résultat. */
    private interface SqlAction<T> {
        T run() throws SQLException;
    }

    private final SHCodex plugin;
    private final CodexDatabase db;
    private final NetworkLink link;
    private final Duration waitMax;
    private final String channel;
    private final int statsRefreshSeconds;
    private final String firstSeenTable;
    private final ScheduledThreadPoolExecutor io = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "SHCodex-reseau");
        thread.setDaemon(true);
        return thread;
    });
    /** Régions lues pendant la pré-connexion, reprises à l'arrivée du joueur. */
    private final Map<UUID, Preloaded> preloaded = new ConcurrentHashMap<>();
    private final AtomicBoolean statsQueued = new AtomicBoolean();
    private volatile Consumer<Map<String, Integer>> statsSink = counts -> {
    };

    public NetworkCodex(SHCodex plugin, CodexDatabase db, NetworkLink link, Duration waitMax, String channel,
                        int statsRefreshSeconds, String firstSeenTable) {
        this.plugin = plugin;
        this.db = db;
        this.link = link;
        this.waitMax = waitMax;
        this.channel = channel;
        this.statsRefreshSeconds = Math.max(5, statsRefreshSeconds);
        this.firstSeenTable = firstSeenTable == null ? "" : firstSeenTable;
        io.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        io.setRemoveOnCancelPolicy(true);
    }

    public NetworkLink link() {
        return link;
    }

    /**
     * Branche le mode réseau : lecture à la connexion, sauvegarde au départ de chaque joueur, stats partagées.
     *
     * @param saveTask  exécutée au départ de chaque joueur (fil principal)
     * @param statsSink reçoit le nombre de joueurs par région à chaque relecture
     */
    public void start(Function<Player, CompletableFuture<Void>> saveTask, Consumer<Map<String, Integer>> statsSink) {
        this.statsSink = statsSink;
        link.registerSaveTask(saveTask);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        link.listen(channel, this::onBus);
        io.scheduleWithFixedDelay(this::loadStats, 0L, statsRefreshSeconds, TimeUnit.SECONDS);
    }

    /** Termine les écritures en cours puis ferme la base. */
    public void stop() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Des écritures du codex n'ont pas pu être terminées à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Lance la lecture des régions dès la pré-connexion, sans l'attendre : le serveur quitté ne sauvegarde le joueur
     * qu'une fois cette connexion terminée (le proxy ferme l'ancienne connexion après), attendre ici bloquerait la
     * connexion jusqu'au délai maximal. La lecture se fait sur le fil de la base une fois le départ confirmé.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        long now = System.currentTimeMillis();
        preloaded.values().removeIf(p -> now - p.at() > PRELOAD_KEEP_MS);
        CompletableFuture<Map<String, Long>> regions = new CompletableFuture<>();
        preloaded.put(uuid, new Preloaded(regions, now));
        link.awaitSaved(uuid, waitMax).whenComplete((saved, error) -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + name + " non confirmé par l'autre serveur"
                    + (error != null ? " (" + error + ")" : "") + " : codex relu quand même.");
            }
            try {
                io.execute(() -> {
                    try {
                        regions.complete(db.load(uuid));
                    } catch (SQLException e) {
                        // le joueur entre quand même : ses régions sont relues dès que la base répond (voir loadLater)
                        plugin.getLogger().warning("Codex de " + name + " non lu en base : " + e.getMessage());
                        regions.completeExceptionally(e);
                    }
                });
            } catch (RejectedExecutionException stopped) {
                regions.completeExceptionally(stopped);
            }
        });
    }

    /** Régions lues à la pré-connexion du joueur qui entre, si c'est déjà fait ; sinon vide (voir loadLater). */
    public Optional<Map<String, Long>> takePreloaded(UUID uuid) {
        Preloaded loaded = preloaded.get(uuid);
        if (loaded == null || !loaded.regions().isDone() || loaded.regions().isCompletedExceptionally()) {
            return Optional.empty();
        }
        preloaded.remove(uuid, loaded);
        return Optional.of(loaded.regions().join());
    }

    /**
     * Lit les régions d'un joueur qui entre : reprend la lecture lancée à sa pré-connexion, sinon (rechargement à chaud,
     * base absente à sa connexion) lit en base. Réessaie tant que la base ne répond pas ; {@code onMain} reçoit les
     * régions sur le fil principal.
     */
    public void loadLater(UUID uuid, Consumer<Map<String, Long>> onMain) {
        Preloaded started = preloaded.remove(uuid);
        if (started != null) {
            // lecture lancée à la pré-connexion, pas encore finie : reprise telle quelle, relancée si elle a échoué
            started.regions().whenComplete((regions, error) -> {
                if (error == null) {
                    toMain(() -> onMain.accept(regions));
                } else {
                    submit(() -> loadOnce(uuid, onMain, 0));
                }
            });
            return;
        }
        submit(() -> loadOnce(uuid, onMain, 0));
    }

    private void loadOnce(UUID uuid, Consumer<Map<String, Long>> onMain, int attempt) {
        try {
            Map<String, Long> regions = db.load(uuid);
            toMain(() -> onMain.accept(regions));
        } catch (SQLException e) {
            plugin.getLogger().warning("Codex de " + uuid + " non lu en base : " + e.getMessage());
            if (attempt + 1 >= LOAD_RETRY_MAX) {
                plugin.getLogger().severe("Codex de " + uuid + " jamais lu en base, abandon.");
                return;
            }
            try {
                io.schedule(() -> loadOnce(uuid, onMain, attempt + 1), LOAD_RETRY_SECONDS, TimeUnit.SECONDS);
            } catch (RejectedExecutionException stopped) {
                // plugin arrêté
            }
        }
    }

    /** Lit les régions d'un joueur sans le charger (liste d'un joueur d'un autre serveur ou hors ligne) ; null si la base ne répond pas. */
    public void read(UUID uuid, Consumer<Map<String, Long>> onMain) {
        submit(() -> {
            Map<String, Long> regions = null;
            try {
                regions = db.load(uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("Codex de " + uuid + " non lu en base : " + e.getMessage());
            }
            Map<String, Long> result = regions;
            toMain(() -> onMain.accept(result));
        });
    }

    // ------------------------------------------------------------------ écriture

    /** Futur terminé quand toutes les écritures déjà demandées sont faites (elles passent toutes par le même fil, dans l'ordre). */
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            io.execute(() -> done.complete(null));
        } catch (RejectedExecutionException stopped) {
            done.complete(null);
        }
        return done;
    }

    /** Écrit la découverte d'un joueur de ce serveur, relit les stats et prévient les autres serveurs. */
    public void recordDiscovery(UUID uuid, String region, long at) {
        submit(() -> {
            Boolean inserted = write("découverte de " + region + " par " + uuid, () -> db.discover(uuid, region, at));
            if (Boolean.FALSE.equals(inserted)) {
                plugin.getLogger().warning("Découverte de " + region + " par " + uuid
                    + " déjà en base : la mémoire de ce serveur était en retard sur la base.");
            }
            loadStats();
            publishStats();
        });
    }

    /** Écrit le re-verrouillage d'une région d'un joueur de ce serveur, relit les stats et prévient les autres serveurs. */
    public void recordLock(UUID uuid, String region) {
        submit(() -> {
            write("re-verrouillage de " + region + " pour " + uuid, () -> db.lock(uuid, region));
            loadStats();
            publishStats();
        });
    }

    /**
     * Débloque une région pour un joueur qui n'est pas sur ce serveur (administration). {@code done} reçoit, sur le fil
     * principal, vrai si elle ne l'était pas, faux si elle l'était déjà, null si la base a refusé.
     */
    public void adminUnlock(UUID uuid, String region, Consumer<Boolean> done) {
        submit(() -> {
            Boolean inserted = write("déblocage de " + region + " pour " + uuid, () -> db.discover(uuid, region, System.currentTimeMillis()));
            if (Boolean.TRUE.equals(inserted)) {
                loadStats();
                publishStats();
            }
            toMain(() -> done.accept(inserted));
        });
    }

    /**
     * Reverrouille une région pour un joueur qui n'est pas sur ce serveur (administration). {@code done} reçoit, sur le
     * fil principal, vrai si elle était découverte, faux sinon, null si la base a refusé.
     */
    public void adminLock(UUID uuid, String region, Consumer<Boolean> done) {
        submit(() -> {
            Boolean deleted = write("re-verrouillage de " + region + " pour " + uuid, () -> db.lock(uuid, region));
            if (Boolean.TRUE.equals(deleted)) {
                loadStats();
                publishStats();
            }
            toMain(() -> done.accept(deleted));
        });
    }

    /** Relit les stats en base dès que possible (plusieurs demandes rapprochées n'en font qu'une). */
    public void refreshStats() {
        if (statsQueued.compareAndSet(false, true)) {
            submit(() -> {
                statsQueued.set(false);
                loadStats();
            });
        }
    }

    private void loadStats() {
        try {
            statsSink.accept(db.counts());
        } catch (SQLException e) {
            plugin.getLogger().warning("Stats du codex non lues en base : " + e.getMessage());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Stats du codex non appliquées", e);
        }
    }

    private void publishStats() {
        JsonObject message = new JsonObject();
        message.addProperty("type", "stats");
        link.publish(channel, message);
    }

    /** Message d'un autre serveur : fil principal, donc seulement une demande de relecture. */
    private void onBus(JsonObject message) {
        if (message.has("type") && "stats".equals(message.get("type").getAsString())) {
            refreshStats();
        }
    }

    /** Exécute une écriture avec quelques essais ; null si la base l'a refusée. */
    private <T> T write(String what, SqlAction<T> action) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                return action.run();
            } catch (SQLException e) {
                if (attempt == WRITE_ATTEMPTS) {
                    plugin.getLogger().log(Level.SEVERE, "Codex non écrit en base (" + what + ") : " + e.getMessage());
                    return null;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ importation

    /**
     * Copie les découvertes des anciens fichiers {@code playerdata/<uuid>.yml} dans la base, sans toucher aux régions
     * déjà en base. {@code done} reçoit {régions ajoutées, déjà présentes, joueurs lus} sur le fil principal, ou null si
     * la base a refusé.
     */
    public void importFolder(File folder, Consumer<int[]> done) {
        submit(() -> {
            int[] result = null;
            try {
                Map<UUID, Map<String, Long>> byPlayer = readFolder(folder);
                int[] counts = db.importAll(byPlayer);
                result = new int[]{counts[0], counts[1], byPlayer.size()};
                loadStats();
                publishStats();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Importation du codex impossible : " + e.getMessage());
            }
            int[] finalResult = result;
            toMain(() -> done.accept(finalResult));
        });
    }

    private Map<UUID, Map<String, Long>> readFolder(File folder) {
        Map<UUID, Map<String, Long>> byPlayer = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return byPlayer;
        }
        for (File file : files) {
            UUID uuid;
            try {
                uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            } catch (IllegalArgumentException notAUuid) {
                plugin.getLogger().warning("Fichier ignoré dans playerdata/ (nom qui n'est pas un UUID) : " + file.getName());
                continue;
            }
            ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("discovered-regions");
            if (section == null) {
                continue;
            }
            Map<String, Long> regions = new HashMap<>();
            for (String region : section.getKeys(false)) {
                regions.put(region, section.getLong(region));
            }
            if (!regions.isEmpty()) {
                byPlayer.put(uuid, regions);
            }
        }
        return byPlayer;
    }

    // ------------------------------------------------------------------ joueurs hors ligne

    /**
     * Retrouve un joueur qui n'est pas connecté : connu de ce serveur, sinon présent dans le registre des premières
     * connexions du réseau. {@code result} reçoit, sur le fil principal, le joueur ou null.
     */
    public void resolveOffline(String name, Consumer<Known> result) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        String localName = cached != null ? cached.getName() : null;
        if (localName != null && Bukkit.getOfflinePlayer(offlineUuid(localName)).hasPlayedBefore()) {
            result.accept(new Known(offlineUuid(localName), localName));
            return;
        }
        if (firstSeenTable.isEmpty()) {
            result.accept(null);
            return;
        }
        submit(() -> {
            String exact = db.firstSeen(firstSeenTable, name);
            toMain(() -> result.accept(exact == null ? null : new Known(offlineUuid(exact), exact)));
        });
    }

    /** UUID « hors ligne » d'un pseudo, celui de tout le réseau (pas de recherche Mojang). */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ fils

    private void toMain(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalPluginAccessException stopped) {
            // plugin arrêté : plus personne à prévenir
        }
    }

    private void submit(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté : plus rien à faire
        }
    }
}
