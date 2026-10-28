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
La version de l'API SuperiorSkyblock2 se règle avec la propriété `ssb.api.version` du `pom.xml`.

## Installation
1. Déposer le jar dans `plugins/`.
2. Créer votre schématique d'hôtel de ville (`//copy` puis `//schem save hotel`), à l'endroit où vous voulez que la shulker posée soit le point d'origine (là où vous vous tenez au moment du `//copy`).
3. La placer dans `plugins/HotelDeVille/schematics/hotel.schem` (nom modifiable dans `config.yml > hotel.schematic`).
4. Rien à régler pour le bloc générateur : sa position est lue dans `plugins/SuperiorSkyblock2/modules/OneBlock/config.yml` (`block-offset`). Si besoin : `config.yml > oneblock.offset-source: CONFIG`.

## Fonctionnement
- À la création d'une île, le joueur reçoit la shulker. La poser sur son île construit l'hôtel (une seule fois par île, commun à tous les membres).
- Suppression de l'île : hôtel, minion et stockage sont réinitialisés.
- `/hotel` : menu. Chaque slot = une ressource du prochain niveau (ITEM, MONEY, XP, PLAYTIME). Clic = validation. Quand tout est validé, le timer d'amélioration démarre.
- Le minion est invisible (aucune entité) : il casse le bloc générateur du OneBlock selon ses capacités et range les drops dans son stockage.
- Bouton du menu : mettre le minion en route / l'arrêter. Quand le stockage est plein (plus aucun slot libre), il s'arrête automatiquement et prévient les membres en ligne.
- Facteurs du minion (vitesse, stockage, monnaie, xp) : 12 valeurs chacun, indexées sur le niveau de l'hôtel (`config.yml > minion.factors`).
- Le stockage s'ouvre via le menu ou `/hotel storage`.
- `minion.break-mode` : `DIRECT` (outil du minion, fonctionne hors-ligne) ou `EVENT` (simule la casse par un membre connecté).

## Commandes et permissions
- `/hotel` (alias `/hdv`) — `hotel.use` (par défaut : tous)
- `/hotel storage`
- `/hotel admin give|setlevel|finish|reset <joueur>`, `/hotel admin reload` — `hotel.admin`

## Fichiers de configuration
- `config.yml` : général, shulker, hôtel, OneBlock, minion, facteurs (12 niveaux)
- `levels.yml` : ressources et durée pour chacun des niveaux 2 à 12
- `menus.yml` : menu entièrement libre (aucune valeur par défaut réinjectée) : ajoutez/supprimez/déplacez des items, `action` (STORAGE, TOGGLE_MINION, PICKUP, CLOSE, NONE), `commands`, `states`, `slots`...
- `messages.yml` : tous les messages

Couleurs partout : `&c`, `&#RRGGBB`, `<#RRGGBB>`, `#RRGGBB`. Items partout : `MATERIAL` ou `nexo:nom_item`.

## Déplacer l'hôtel de ville
- Bouton **Déplacer l'hôtel de ville** (`action: PICKUP`) dans `/hotel` : la structure disparaît et le joueur reçoit la shulker. Niveau, amélioration en cours et stockage sont conservés à la repose.
- À la récupération, seuls les blocs de la schématique sont retirés : coffres, fours, etc. posés par les joueurs dans le bâtiment restent en place avec leur contenu (`config.yml > hotel.pickup-keep-other-blocks`, `true` par défaut).
- À la pose, l'air de la schématique n'efface rien et la schématique s'adapte à ce qui est déjà présent (`hotel.paste-keep-existing` : `NONE`, `CONTAINERS` par défaut, `ALL`).
- Les blocs de la structure ne peuvent pas être cassés (pioche, explosions, pistons...) hors du menu. Permission de contournement : `hotel.bypass`.
- Un seul hôtel par île (posé ou en cours de déplacement).
- Si le porteur quitte / est expulsé de l'île, ou si l'île est supprimée, l'hôtel récupéré est supprimé et la shulker devient invalide.
- Le stockage du minion permet de retirer des items mais pas d'en déposer.
