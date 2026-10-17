# Susanoo 2.4 (Fabric 1.20.1)

## Compiler
Java 17 + connexion internet, puis dans ce dossier :
    ./gradlew build        (Windows : gradlew.bat build)
Le mod est généré dans build/libs/susanoo-2.4.0.jar (à mettre dans .minecraft/mods avec Fabric API 0.92.x).

## Touches (modifiables dans Options > Contrôles > Susanoo)
- G : invoquer / renvoyer le Susanoo (il faut un Mangekyō dans l'inventaire ; clic droit sur l'objet fait pareil)
- H : forme suivante (Maj+H : précédente) : Côtes -> Squelette -> Armure -> Parfaite (géante)
- R : attaque (maintenir pour enchaîner)
- V : technique spéciale (flèche / onde de chakra)
- C : piétinement (forme Parfaite seulement) - cratère + onde de choc qui projette tout autour
- B : coup de pied (forme Parfaite seulement) - balayage devant le géant, projette les ennemis loin et haut

## Couleurs
Madara = bleu (objet `mangekyo`, recette d'origine), Itachi = rouge, Sasuke = violet, Shisui = vert, Obito = orange.
Craft : Mangekyō quelconque + teinture (bleue, rouge, violette, verte, orange).

## Physique
Le Susanoo enveloppe le joueur et le suit sans retard. Il absorbe les dégâts (bouclier : 20/40/80/160 PV selon la forme,
se régénère hors combat), annule chute et feu, repousse moins (anti-recul formes 2-4) et consomme de la faim.
Les formes Armure/Parfaite brisent les blocs tendres (si mobGriefing est actif).
Réglages : tableaux de constantes en haut de SusanooManager.java et SusanooEntity.java.

## Nouveautés 2.1
- **Jambes (forme Parfaite)** : le Susanoo se tient debout sur de vraies jambes. Le joueur flotte (vol accordé tant que la forme
  Parfaite est active, restitué ensuite + chute lente) à environ 11 blocs du sol ; les pieds se posent sur le terrain (cinématique inverse),
  les genoux plient selon le relief, les jambes ont de l'inertie (ressorts amortis, elles traînent derrière le mouvement).
  Attaques : piétinement (C), coup de pied (B), et pas écrasant en marchant (blesse ce qui est sous le pied, écrase la végétation).
- **FOV** : le champ de vision grandit à chaque forme (+4 / +10 / +17 / +28), avec un petit coup de pouce à chaque transformation,
  piétinement et technique spéciale. Réglage dans SusanooClient.FOV_BONUS (nécessite le mixin GameRendererMixin).
- **Aura** : flammes sombres à cœur incandescent qui s'élèvent du corps, coques de fumée qui ondulent, anneau de runes et de flammes
  au sol, fumée noire / âmes / étincelles, foudre décorative à chaque montée de forme, battement de cœur sourd (Armure / Parfait),
  bords d'écran assombris qui pulsent. Les créatures proches sont ralenties et affaiblies (rayon 4 / 7 / 12 / 24 selon la forme).
  Réglages : SusanooEntity.AURA_PARTICLES (moins de particules), SusanooManager.AURA_DEBUFF / AURA_SOUNDS, SusanooClient.VIGNETTE.

## Nouveautés 2.2
- **Clic gauche = coup d'épée** : tant que le Susanoo est invoqué, le clic gauche (maintenu pour enchaîner) fait la même attaque que R,
  et ne frappe plus / ne mine plus normalement. La touche R fonctionne toujours.
- **Météorite (touche M, modifiable)** : une météorite tombe du ciel sur le point visé (jamais sur toi : la cible est repoussée devant toi si elle est trop proche).
  Dégâts énormes, enflamme, onde de choc qui projette tout, puis le cratère est effacé couche par couche (haut vers bas) pour ne pas figer le serveur.
  Rayon du cratère : 8 / 11 / 15 / 22 blocs selon la forme. Seul le bedrock résiste ; les blocs à inventaire (coffres...) sont épargnés.
  Recharge : 30 s. Respecte mobGriefing et les zones protégées. Réglages en haut de SusanooManager.java (METEOR_*).
- **Aura plus légère** : plus de nuage de fumée dans le corps ; quelques braises au sol, de rares âmes / étincelles autour du géant,
  et moins de flammes sur le modèle. Réglage : SusanooEntity.AURA_PARTICLES (0 = aucune particule, 1 = normal).

## Nouveautés 2.3
- **Météorite réaliste** : un vrai rocher irrégulier qui tourne, avec des fissures incandescentes, un halo de chaleur et une longue traînée
  de feu et de fumée. Dégâts très augmentés (200 / 300 / 500 / 1000 selon la forme, plein pot dans le cratère), onde de choc plus large.
- **Amaterasu (touche N, modifiable)** : des flammes noires partent du point visé et s'étendent (rayon 9 / 13 / 18 / 28). Elles détruisent la
  surface (6 blocs de profondeur : sol, arbres, constructions), brûlent tout ce qui vit (dégâts de feu qui ignorent l'armure, difficile à éteindre)
  pendant 15 s. Recharge 25 s. Réglages : constantes AMA_* de SusanooManager.java.
- Les touches M et N affichent maintenant un message si le Susanoo n'est pas invoqué.

## Nouveautés 2.4 : le Rinnegan (les six voies de Pain)
Craft : un Mangekyō (n'importe lequel) + étoile du Nether + œil d'Ender + 2 éclats d'améthyste + teinture violette (sans forme).
Garde-le dans l'inventaire. **K** = pouvoir suivant (Maj+K : précédent, le nom s'affiche au-dessus de la barre d'objets), **J** (ou clic droit sur le Rinnegan) = utiliser.
1. **Shinra Tensei** (Déva) : onde de répulsion de 28 blocs : dégâts, projette tout, renvoie les projectiles, aplatit tout ce qui dépasse du sol.
2. **Banshō Ten'in** (Déva) : attire tout vers toi pendant 1,5 s (créatures et objets) et écrase ce qui arrive au contact.
3. **Chibaku Tensei** (Déva) : une planète de roche se forme dans le ciel au point visé, le sol est arraché, tout est aspiré puis écrasé (10 s).
4. **Absorption** (Preta) : 10 s où tous les dégâts sont annulés et transformés en cœurs d'absorption.
5. **Roi de l'Enfer** (Naraka) : soin complet + purge des effets néfastes pour toi, les joueurs et animaux apprivoisés proches ; si tu meurs dans la minute qui suit, tu reviens à la vie.
6. **Invocation** (Animale) : 4 loups enragés et un golem se battent pour toi pendant 30 s.
7. **Extraction d'âme** (Humaine) : vise une créature : son âme est arrachée (mort instantanée) et tu es soigné.
8. **Missiles** (Asura) : salve de 12 boules explosives tirées des épaules.
Recharges : 4 s / 3 s / 30 s / 20 s / 40 s / 30 s / 10 s / 15 s. Réglages : CD[] et constantes en haut de RinneganManager.java.
