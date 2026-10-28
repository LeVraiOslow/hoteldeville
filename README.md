# HotelDeVille

Plugin Paper 26.2 pour îles OneBlock (SuperiorSkyblock2) : hôtel de ville à 12 niveaux + minion qui mine automatiquement le bloc générateur.

## Dépendances (serveur)
- Paper 26.2 (Java 25)
- SuperiorSkyblock2 (obligatoire) et Vault (obligatoire)
- Essentials (ou tout autre plugin d'économie compatible Vault) pour la monnaie
- WorldEdit ou FastAsyncWorldEdit pour coller la schématique
- Nexo (optionnel) : items `nexo:nom_item` partout (menus, ressources, équipement du minion, shulker)

## Compilation
```
mvn clean package
```
Le jar est généré dans `target/HotelDeVille-1.0.0.jar`. Java 25 requis.
Si SuperiorSkyblock2 sur votre serveur est une autre version que `2024.4`, adaptez la version dans le `pom.xml`.

## Installation
1. Déposer le jar dans `plugins/`.
2. Créer votre schématique d'hôtel de ville (`//copy` puis `//schem save hotel`), à l'endroit où vous voulez que la shulker posée soit le point d'origine (là où vous vous tenez au moment du `//copy`).
3. La placer dans `plugins/HotelDeVille/schematics/hotel.schem` (nom modifiable dans `config.yml > hotel.schematic`).
4. Régler `config.yml > oneblock.offset` pour viser votre bloc générateur (relatif au centre de l'île) et `minion.spawn-offset` pour la position du minion.

## Fonctionnement
- À la création d'une île, le joueur reçoit la shulker. La poser sur son île construit l'hôtel (une seule fois par île, commun à tous les membres).
- Suppression de l'île : hôtel, minion et stockage sont réinitialisés.
- `/hotel` : menu. Chaque slot = une ressource du prochain niveau (ITEM, MONEY, XP, PLAYTIME). Clic = validation. Quand tout est validé, le timer d'amélioration démarre.
- Facteurs du minion (vitesse, stockage, monnaie, xp) : 12 valeurs chacun, indexées sur le niveau de l'hôtel (`config.yml > minion.factors`).
- Le stockage s'ouvre via le menu, `/hotel storage` ou un clic droit sur le minion.

## Commandes et permissions
- `/hotel` (alias `/hdv`) — `hotel.use` (par défaut : tous)
- `/hotel storage`
- `/hotel admin give|setlevel|finish|reset <joueur>`, `/hotel admin reload` — `hotel.admin`

## Fichiers de configuration
- `config.yml` : général, shulker, hôtel, OneBlock, minion, facteurs (12 niveaux)
- `levels.yml` : ressources et durée pour chacun des niveaux 2 à 12
- `menus.yml` : menu principal, titres, slots, items, noms, descriptions, remplissage
- `messages.yml` : tous les messages

Couleurs partout : `&c`, `&#RRGGBB`, `<#RRGGBB>`, `#RRGGBB`. Items partout : `MATERIAL` ou `nexo:nom_item`.
