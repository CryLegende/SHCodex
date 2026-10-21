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

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcodex.use` | Permet d'utiliser la commande /codex pour ouvrir le menu. | `true` |
| `shcodex.admin` | Permet d'utiliser les commandes d'administration du Codex (ex: /codex reload). | `op` |
| `shcodex.discover.*` | Permet de découvrir toutes les régions du Codex. | `true` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`
- `menu.yml`
- `regions.yml`

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
