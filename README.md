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
- Optionnelles : `PlaceholderAPI`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/codex` | `/shcodex`, `/regions` | Ouvre le menu du Codex ou gère le plugin. | — |

## Plusieurs serveurs Opale

SHCodex n'a pas de mode réseau, et n'en a pas besoin pour découvrir les régions : les 21 régions de `regions.yml` sont dans les mondes `spawn`, `arene` et `christmas`, qui n'existent que sur le serveur du spawn, de l'arène et des events. C'est lui qui détecte les découvertes, donne les récompenses et garde seul `playerdata/<uuid>.yml` et `stats.yml`. Aucune région ne peut être découverte sur un autre serveur, donc une récompense de découverte n'y est jamais donnée deux fois.

Sur un autre serveur, `/codex` et les placeholders `%shcodex_...%` ne lisent qu'une copie de `playerdata/` qui n'est pas mise à jour (aucune découverte récente, parfois aucune). Le plus simple est d'y ajouter le monde des îles (`superiorworld`) à `blacklist-worlds` dans `config.yml` : la commande répond alors « Vous ne pouvez pas ouvrir le Codex dans ce monde ». Montrer le codex à jour sur plusieurs serveurs demanderait un stockage partagé des régions découvertes (`INSERT IGNORE` sur le couple joueur et région pour décider qui reçoit la récompense).

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcodex.use` | Permet d'utiliser la commande /codex pour ouvrir le menu. | `true` |
| `shcodex.admin` | Permet d'utiliser les commandes d'administration du Codex (ex: /codex reload). | `op` |
| `shcodex.discover.*` | Permet de découvrir toutes les régions du Codex. | `true` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHCodex/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/menu.yml">menu.yml</a>
└── <a href="src/main/resources/regions.yml">regions.yml</a>
</pre>

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
