-- Tables de SHCodex avec SHNetwork. Le plugin les crée seul au démarrage si l'utilisateur a le droit CREATE ;
-- ce fichier sert à les créer à la main et à ne donner à l'utilisateur que les droits de lecture et d'écriture.
--
-- Utilisation (base bo_net pour les essais multi-Opale) :
--   mysql -u root < sql/schema.sql   puis remplacer CHANGE_ME par le mot de passe de config.yml

CREATE DATABASE IF NOT EXISTS bo_net;

-- Une ligne par région découverte : la clé (uuid, region) fait que deux serveurs ne comptent jamais deux fois
-- la même découverte (INSERT IGNORE). Le nombre de joueurs par région (stats) se compte dans cette table.
CREATE TABLE IF NOT EXISTS bo_net.shcodex_discoveries (
    uuid          CHAR(36)     NOT NULL,          -- uuid « hors ligne » du joueur, le même sur tous les serveurs
    region        VARCHAR(100) NOT NULL,          -- identifiant de la région dans regions.yml
    discovered_at BIGINT       NOT NULL DEFAULT 0, -- date de la découverte (epoch ms)
    PRIMARY KEY (uuid, region),
    KEY idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'shcodex'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, DELETE ON bo_net.shcodex_discoveries TO 'shcodex'@'127.0.0.1';
-- Facultatif : vérifier qu'un joueur hors ligne existe (commande /codex admin), voir reseau.table-premieres-connexions
-- GRANT SELECT ON Global.sh_track_firstseen TO 'shcodex'@'127.0.0.1';
