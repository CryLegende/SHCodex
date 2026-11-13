<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCodex

*Un codex de zones à découvrir avec WorldGuard.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Un codex de zones à découvrir avec WorldGuard.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `WorldGuard`
- Optionnelles : `PlaceholderAPI`, `SHNetwork` (mode réseau)

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/codex` | `/shcodex`, `/regions` | Ouvre le menu du Codex ou gère le plugin. | — |
| `/codex reload` | | Recharge les fichiers de configuration et les stats. | `shcodex.admin` |
| `/codex admin <joueur> <list\|unlock\|lock> [région]` | | Voir, débloquer ou reverrouiller les régions d'un joueur. | `shcodex.admin` |
| `/codex import` | | Mode réseau : copie `playerdata/` dans la base, sans rien écraser. | `shcodex.admin` |

## Plusieurs serveurs Opale

Les régions de `regions.yml` sont dans des mondes qui n'existent que sur un seul serveur (celui du spawn) : c'est lui qui détecte les découvertes et donne les récompenses.

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), les données du codex sont partagées par tous les serveurs de jeu :

- les régions découvertes de chaque joueur sont en MariaDB (table `shcodex_discoveries` : `uuid`, `region`, `discovered_at`) au lieu de `playerdata/` ;
- le nombre de joueurs par région (`{total_discovered}` du menu) se compte dans cette même table au lieu de `stats.yml`, puis il est gardé en mémoire et relu en base après chaque changement (message du bus SHNetwork aux autres serveurs) et toutes les 30 secondes ;
- à la connexion, une fois que l'autre serveur a fini de sauvegarder le joueur, ses régions sont lues en base (la lecture commence à la pré-connexion, sans la bloquer ; en attendant, ses déplacements ne découvrent rien) ;
- chaque découverte est écrite en base tout de suite, ligne par ligne (`INSERT IGNORE` : deux serveurs ne comptent jamais deux fois la même région), avant que le joueur ne puisse changer de serveur ;
- `/codex` et les placeholders `%shcodex_...%` montrent donc les mêmes données sur tous les serveurs, et il n'y a plus besoin de fermer `/codex` sur les îles avec `blacklist-worlds` ;
- `/codex admin <joueur> list|unlock|lock` : un joueur connecté sur un autre serveur reçoit `unlock` et `lock` là où se trouve sa mémoire, `list` lit la base ; un joueur hors ligne est lu et modifié directement en base (il est retrouvé par ce serveur ou par `reseau.table-premieres-connexions`) ;
- `/codex import` copie une fois `playerdata/<uuid>.yml` dans la base (rien n'est écrasé : on peut le relancer). `playerdata/` et `stats.yml` ne sont plus ni lus ni écrits en mode réseau.

Sans SHNetwork, ou avec `reseau.actif: false`, ou sans les clés `reseau` dans la configuration, le plugin se comporte comme avant : `playerdata/` et `stats.yml`. Si la base est injoignable au démarrage, il reste en mode local. Les tables se créent toutes seules, ou à la main avec `sql/schema.sql`.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcodex.use` | Permet d'utiliser la commande /codex pour ouvrir le menu. | `true` |
| `shcodex.admin` | Permet d'utiliser les commandes d'administration du Codex (ex: /codex reload). | `op` |
| `shcodex.discover.*` | Permet de découvrir toutes les régions du Codex. | `true` |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHCodex/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/menu.yml">menu.yml</a>
├── <a href="src/main/resources/regions.yml">regions.yml</a>
├── playerdata/ <i>(un fichier par joueur ; mode normal)</i>
└── stats.yml <i>(mode normal)</i>
</pre>

Le fichier `sql/schema.sql` du dépôt crée les tables du mode réseau.

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
