# Décisions produit

État actuel des décisions, par thème (ce n'est pas un historique). Dernière mise à jour : 2026-11-23.

## Public et distribution

- Pour le propriétaire et quelques proches. APK partagé à la main, pas de Play Store.
- Chaque téléphone garde ses propres données. Pas de compte, pas de serveur.
- App en français.

## Données

- Tout est stocké sur le téléphone (Room), avec un bouton d'export de sauvegarde : un fichier complet,
  enregistré où l'on veut (Drive, Téléchargements). Il se restaure par « Importer » et remplace alors
  toutes les données du téléphone, après confirmation.
- Les mises à jour de l'app ne doivent jamais effacer les données.

## Repas et calories

- Pas d'IA ni de clé d'API dans l'app. Le propriétaire envoie ses photos à Claude dans un fil du projet ;
  Claude répond avec un fichier d'import. Un repas = un seul fichier, même avec plusieurs photos.
- Saisie et correction manuelles : recherche par nom dans Open Food Facts, scan d'un code-barres, ou valeurs
  tapées à la main.
- **Scan du code-barres** (choix du propriétaire, 22/11/2026, PRD `docs/prd/scan-code-barres.md`) : bouton « Scanner » dans
  l'éditeur de repas. La lecture est faite par l'app (CameraX + ZXing, sans services Google ni réseau). La
  caméra n'est demandée qu'au premier scan ; refusée, un message propose d'ouvrir les réglages d'Android.
  Le produit est cherché sur le téléphone d'abord (« Déjà scanné », hors ligne), sinon sur Open Food Facts avec
  le seul code ; il est retenu pour la prochaine fois. La quantité proposée est la portion du paquet si elle
  est connue, sinon 100 g, modifiable avant d'ajouter. Un produit inconnu d'Open Food Facts (ou sans calories)
  se saisit une fois depuis l'étiquette (valeurs pour 100 g, calories obligatoires) : il est retenu sous son code
  et l'aliment ajouté est « manuel ». Rien n'est envoyé à Open Food Facts. Les produits retenus gardent des
  valeurs pour 100 g, mais l'aliment ajouté à un repas garde ses valeurs calculées : corriger un produit ne
  change jamais les repas passés. Un code UPC-A est cherché sur 13 chiffres (comme Open Food Facts).
  Pas de bouton sur le widget.
- **Scan depuis l'onglet Repas** : « Scanner un produit » (aujourd'hui seulement) ajoute l'aliment au repas en cours
  (même règle que les boissons : repas d'aujourd'hui du type de l'heure, commencé il y a moins de 3 h) ; sans
  repas en cours, un nouveau repas **du type de l'heure** est créé (une boisson, elle, crée une collation). Un
  message « Ajouté au déjeuner » propose « Annuler » (retire l'aliment, ou le repas qu'il a créé).
- **Mes produits scannés** (bouton « Mes produits » de l'onglet Repas) : voir, corriger, supprimer, et « Mettre à
  jour depuis Open Food Facts » (à la demande seulement, jamais automatique ; si Open Food Facts n'a pas de
  valeurs complètes, le produit reste tel quel). Corriger ou supprimer un produit ne change jamais les repas notés.
  Les produits retenus sont dans la sauvegarde ; restaurer une sauvegarde ancienne (sans cette table) les vide.
- **Déjà mangés** (choix du propriétaire, 22/11/2026, PRD `docs/prd/saisie-rapide-repas.md`) : en ouvrant « Chercher un aliment »,
  la liste des aliments déjà notés passe avant Open Food Facts : les 28 derniers jours d'abord (les plus
  fréquents en tête), chacun avec sa **dernière quantité**, puis les plus anciens. La liste se filtre à chaque
  lettre (sans accents ni majuscules, mot par mot), sans réseau ; un toucher ajoute l'aliment tel qu'il
  avait été mangé. Le bouton « Chercher aussi sur Open Food Facts » lance la recherche existante, seulement
  quand on le touche. L'historique est **déduit des repas** (pas de table, rien à sauvegarder en plus) : un repas
  supprimé en sort, un aliment corrigé garde sa dernière version, le même aliment écrit autrement
  (« Yaourt nature », « YAOURT  nature ») n'apparaît qu'une fois. Les produits scannés et les plats y figurent
  une fois ajoutés à un repas. Sans historique (nouveau téléphone), la recherche garde son aspect habituel.
- **Juste les calories** (même PRD) : dans un repas, un nom (« Repas au restaurant » par défaut) et des
  calories créent un seul aliment « manuel » dont protéines, glucides, lipides et fibres restent **vides**
  (jamais estimés, jamais à zéro), avec la pastille « à vérifier » (elle part dès qu'on ajoute une valeur).
  Les calories comptent partout ; Aujourd'hui et l'onglet Repas écrivent « 1 repas sans détail : ses
  protéines et ses fibres ne sont pas comptées ». Pas de nouvelle migration pour cela.
- **Repas habituel** (même PRD) : sur les 28 derniers jours, un repas du même type, commencé à 1 h 30 près de
  l'heure actuelle et avec le même contenu (peu importe l'ordre), noté au moins 2 fois, est proposé en un
  toucher : carte « Ton déjeuner habituel ? » sur Aujourd'hui (« Ajouter » ou « Pas aujourd'hui », masquée
  jusqu'au lendemain) et liste « Ton repas habituel ? » dans un nouveau repas (3 au plus, le plus fréquent
  d'abord). Sur Aujourd'hui, pas de carte pour un type de repas déjà noté ce jour. Tout est déduit des repas
  (pas de table, pas de réseau, pas de notification) ; « Pas aujourd'hui » est une préférence du téléphone, hors
  sauvegarde.
- **Repas sans détail dans le bilan** : le bilan de repas exporté marque `macros_missing: true` sur un repas dont
  aucun aliment n'a de macros, et `meals_without_macros` sur le jour (absent s'il n'y en a pas), pour que Claude
  ne prenne pas ces macros vides pour des zéros (voir `docs/format-import.md`).
- Changer la quantité d'un aliment recalcule ses calories et ses macros en proportion ; corriger une valeur
  à la main fixe la nouvelle base du calcul. Rien de plus n'est enregistré (pas de valeurs « pour 100 g »).
- Supprimer un repas importé permet de réimporter le même fichier.
- Le jour d'un repas est toujours la date locale du téléphone.
- Par aliment : calories, protéines, glucides, lipides, fibres. Pas de sucres ni de sel.
- Objectif : maintien, calculé dans l'app (Mifflin-St Jeor × niveau d'activité habituel, à partir
  du profil sexe, âge, taille, poids, activité). Le sport n'ajoute pas de calories à l'objectif.
  Niveaux d'activité : sédentaire (× 1,2), un peu actif (× 1,375), actif (× 1,55), très actif (× 1,725).
  Le profil garde un champ « objectif » : seul « Forme générale, maintien » existe pour l'instant.
- Couleur selon l'écart au maintien : vert à ±10 %, orange de 10 à 20 %, rouge au-delà. Les mêmes couleurs
  pour chaque jour et pour la moyenne des 7 derniers jours. En mots, on parle de « tes besoins » et du
  sens de l'écart : « proche de tes besoins », « un peu plus / moins que tes besoins », « bien plus /
  moins que tes besoins » (« un peu loin du maintien » n'était pas clair, le propriétaire, 12/11/2026). Graphique de la moyenne sur 7 jours.
  Le jour en cours reste en couleur neutre (anneau d'Aujourd'hui, barre du total dans Repas, barre du jour
  dans les 7 derniers jours) et son texte ne juge pas l'écart (« 2 repas notés ») : manger peu en journée
  est normal (choix du propriétaire, 12/11/2026). Seule exception : un total qui dépasse déjà le maintien de plus
  de 10 % prend sa couleur. Les jours terminés et la moyenne gardent leur couleur.
  Pas de compteur précis « reste à manger ». Cible de protéines d'environ 1,6 g/kg affichée à côté.
- Repères de macros sous l'anneau (fil Nutrition, 12/11/2026) : protéines = un minimum (~1,6 g/kg,
  « objectif atteint » une fois passé, jamais d'alerte au-delà) ; lipides ≈ 30 % du maintien ; glucides =
  le reste du maintien. Glucides et lipides sont de simples repères. Une couleur par macro, qui ne juge
  pas la journée : seule la couleur des calories le fait.

### Repas : évolutions validées, pas encore faites

Validées par le propriétaire avec le fil Nutrition (12/11/2026), à faire dans cet ordre. **Toutes faites**
(13/11/2026). « Partager ce repas » partage la version enregistrée. Une boisson va au repas du type de l'heure commencé il y a moins de 3 h, sinon en collation.
« Mes plats » : bouton dans Repas et « Ajouter un de mes plats » dans un repas (½, 1 ou 2 parts, ou grammes) ;
le plat entre dans le repas comme un aliment (source manuelle), le modifier ensuite ne change pas les repas passés. Choix de mise en œuvre : « Refaire ce repas » est dans l'écran du repas et remplit un
nouveau repas à enregistrer ; « Mes favoris » et « Récents » (6 derniers repas différents) sont en haut
d'un nouveau repas ; l'étoile est sur chaque carte de repas. La barre des fibres s'affiche même sans
profil, et une cible minimale atteinte (protéines, fibres) passe au vert.
1. **À vérifier** : dans la liste des repas et l'éditeur, une pastille « à vérifier » sur les aliments de
   confiance faible (`low`), et l'hypothèse (`assumptions`) en petit sous le nom (tous niveaux). La
   pastille disparaît quand l'aliment est corrigé à la main (source manuelle, confiance effacée).
2. **Portions en un geste** : puces « ½ », « ¾ », « ×1,5 » sur chaque aliment de l'éditeur. Elles
   multiplient la quantité (recalcul proportionnel existant), ou directement les valeurs sans `quantity_g`.
3. **Refaire un repas et favoris** : « Refaire ce repas » copie un repas pour aujourd'hui, à l'heure
   actuelle (type déduit de l'heure, modifiable, nouvel enregistrement sans `import_id`). Étoile
   « Favori » sur un repas, et liste « Mes favoris » dans « Ajouter un repas ».
4. **Fibres** : 4e barre sur Aujourd'hui à côté des protéines, cible 30 g/j, cible minimale comme les
   protéines (verte une fois atteinte, jamais rouge). Fibres aussi dans le résumé de chaque repas.
5. **Bilan repas** : export `meal_report` sur le modèle de `training_report` (même enveloppe) : 7 derniers
   jours dont celui de l'export ; par jour date, kcal, protéines, glucides, lipides, fibres et eau (mL),
   et ses repas (type, heure, titre, kcal, aliments avec nom, quantité, kcal) ; profil utile (maintien,
   cible protéines). Bouton « Envoyer mon bilan repas à Claude » dans Repas (partage Android, en fichier joint `bilan-repas-AAAA-MM-JJ.json` : collé en texte, il était coupé vers 8 Ko ; choix du propriétaire, 16/11/2026). L'import
   refuse ce type avec un message clair. À documenter dans `format-import.md`.

Ajoutées ensuite par le propriétaire avec le fil Nutrition (13/11/2026), après les 5 premières :
6. **Partager un repas** : « Partager ce repas » ouvre le partage Android avec un JSON `healthy-import`
   de type `meal` (nouvel `id`, sans `replaces`, mêmes aliments, sans champs propres au téléphone).
7. **Boissons en un geste** : dans Repas, boutons de boissons caloriques qui ajoutent un aliment (source
   manuelle) au repas en cours ou créent une collation : bière 25 cL 105 kcal (9 g glucides), vin 12 cL
   85 kcal (3 g), soda 33 cL 140 kcal (35 g), jus de fruits 20 cL 90 kcal (20 g glucides, 1 g protéines).
   Le soda et le jus sont réglés « Boisson » (ils comptent dans l'eau), la bière et le vin « Alcool » (jamais).
8. **Bilan de la semaine** : fusionné avec le B4 du fil Design, voir plus bas.
9. **Mes plats** : recette enregistrée (nom, ingrédients, poids total cuit, nombre de parts facultatif),
   ajoutée à un repas en « 1 part » ou en grammes, au prorata. Incluse dans la sauvegarde.

### Design : évolutions validées, pas encore faites

Validées par le propriétaire avec le fil Design (13/11/2026), ordre conseillé A, B puis C :
- **A1** (fait) Illustrations des exercices dans la séance guidée : Pousse fait le mouvement (tenue du thème),
  dessiné en Compose, une illustration par famille (pompe, squat, fente, gainage, abdos, pont fessier,
  burpee/jumping, étirement, générique) choisie par mots-clés ; boucle de 2 positions, coupée si les
  animations du téléphone sont désactivées.
- **A2** (fait) Écran de séance : gros chrono circulaire qui se vide (repos et exercices en durée), vibration
  et bip court à la fin du repos et à 3-2-1 (son coupé en silencieux), carte « Ensuite : … » pendant le
  repos, barre de progression de la séance (exercice n / total).
- **B3** (fait, refait au lot 5 de la refonte, 23/11/2026) Pousse grandit avec les semaines actives d'affilée :
  feuille (0-1 sem.), jeune pousse (2-3), arbuste avec tronc (4-7), arbre en fleur avec fruits (8+). Si la série
  casse, il retombe d'un seul palier, jamais sous la feuille, sans message culpabilisant.
- **B4** (fait) Bilan de la semaine (version accordée entre Design et Nutrition, 13/11/2026) : story plein
  écran qui s'ouvre le lundi à la première ouverture, et se rouvre depuis une carte sur Aujourd'hui (du
  dimanche 18 h au mardi). Pages : 1) nutrition de la semaine lun-dim : moyenne kcal face au maintien
  (couleur de la bande 7 jours), jours avec protéines atteintes, fibres ≥ 30 g et eau atteinte, sur les
  jours avec au moins un repas noté ; 2) sport : séances, activités, flamme ; 3) Pousse (palier) et
  badge du moment. « Partager » crée une image avec seulement le sport, la flamme, Pousse et le badge :
  rien sur les calories, le poids ou la santé. L'ouverture du lundi est retenue sur le téléphone
  (pas dans la sauvegarde) ; ouvrir le bilan depuis la carte compte aussi.
- **C5** (fait) Widget d'écran d'accueil (Glance) : calories du jour (neutre), barre d'eau, bouton
  « + verre 20 cL » sans ouvrir l'app, couleurs du thème. Ajouts du propriétaire (13/11/2026) : ajouter
  facilement ce qu'il boit (les boissons de l'app, pas seulement l'eau), coller rapidement un repas
  (ouvre l'import, Android interdit de lire le presse-papiers sans ouvrir l'app), et voir l'état de
  sa journée. Refait après son retour (13/11/2026, fil Design) : Pousse bien visible (humeur et
  taille comme sur Aujourd'hui), anneau des calories avec la règle d'Aujourd'hui (neutre, couleur de la
  bande en cas de dépassement net), espace des milliers, eau avec un verre qui se remplit par quarts,
  motif du thème très léger en fond mais jamais sous les chiffres, coins de 24 dp, texte secondaire
  à la couleur du texte principal (contraste). Mise en page selon la taille : 4×1 en ligne, 2×2
  Pousse au centre, 4×2 Pousse, anneau et eau côte à côte, 4×3 avec protéines, café, bouteille,
  bière, vin, soda, jus et « Coller un repas ». Un bouton touché affiche « ✓ +20 cL » ou « ✓ Bière »
  un court moment, avec une petite vibration.
- **C6** Refaire un repas, « Récents » et « Favoris » : fait avec la n°3 des évolutions repas.
- **C7** (fait) Eau : vibration légère à chaque ajout, petit verre qui se remplit en animation.

## Eau

- Suivi de l'eau bue par jour (boissons seulement : eau, thé, café, tisanes, lait, jus, soupe ; pas l'alcool).
- Objectif = moyenne entre la base selon le sexe (1,6 L femme, 2,0 L homme) et 30 mL par kilo de poids
  de référence, bornée entre 1,5 et 3 L, puis + 0,5 L par séance enregistrée dans la journée.
  Affiché en cL sous 1 L (un verre : 20 cL), sinon en litres à 0,01 L près (ex. femme de 60 kg : 1,7 L ;
  homme de 80 kg : 2,2 L ; 1,75 L).
- Saisie (refonte, 23/11/2026) : quatre boutons, un appui ajoute : Verre 20 cL, Café 15 cL, Thé 25 cL,
  Bouteille 50 cL ; une quantité libre dans l'écran Eau, et l'annulation du dernier ajout. Les anciens boutons
  « petite tasse » et « grande tasse » ont disparu : un ajout déjà noté de 15 cL ou 25 cL s'affiche maintenant
  « Café » ou « Thé », et l'ancien café de 12 cL garde son nom.
- Historique du jour (demande du propriétaire, 13/11/2026, pour savoir si un verre est déjà noté) : la carte
  Eau affiche le dernier ajout et son heure ; l'écran Eau liste les ajouts du jour (heure, nom du bouton
  ou quantité) et chacun peut être supprimé ; un message « Ajout supprimé » propose « Annuler » (l'ajout
  revient avec son heure d'origine).
  Barre de progression sur Aujourd'hui, historique de 7 jours. Inclus dans la sauvegarde.
- **Liquides des repas** (choix du propriétaire, 23/11/2026, PRD `docs/prd/eau-depuis-repas.md`) : chaque aliment d'un repas
  a un réglage **Aliment** (par défaut), **Boisson** ou **Alcool** (colonne `meal_items.drink`, base v12). Un aliment
  Boisson **avec une quantité** compte dans l'eau du jour à son volume (1 g = 1 mL, pas de coefficient) ; sans
  quantité, l'éditeur l'indique (« Ajoute une quantité pour la compter dans l'eau ») et rien n'est compté ;
  l'alcool et les aliments ordinaires ne comptent jamais. Eau du jour = ajouts de l'écran Eau + boissons des
  repas du jour : **un seul endroit** additionne (`BodyDao.observeDailyWater`), donc la barre, l'historique de
  7 jours, le widget, les rappels, le bilan de la semaine et `water_ml` du bilan de repas utilisent le même total,
  sans double compte. L'écran Eau ajoute « Dans les repas » (heure, nom, quantité, repas, sans bouton supprimer :
  on modifie le repas). Les anciens repas ne sont pas reclassés (rien ne change sans qu'on le sache) ; on règle
  un ancien aliment à la main dans l'éditeur. Changer le réglage ne change ni les calories ni la source de
  l'aliment. Le réglage est gardé par « Déjà mangés », le partage de repas et la sauvegarde.
- Format d'import : champ facultatif `drink` (`boisson` | `alcool`) par aliment, absent = aliment ; l'aperçu
  d'import marque les boissons d'une goutte (l'alcool d'un verre) ; le prompt repas demande à l'IA de le
  remplir et de toujours donner `quantity_g` pour une boisson. Les recettes (« Mes plats ») n'ont pas ce réglage.

## Profil et premier lancement

- Un seul profil par téléphone (chaque proche a l'app sur son propre téléphone).
- Accueil obligatoire mais court au premier lancement : prénom, sexe, date de naissance (pas l'âge,
  pour qu'il se mette à jour), taille, poids (devient la première pesée datée), niveau d'activité.
  Puis une partie sport (niveau, séances par semaine, jours, durée, objectif, douleurs, suivi médical,
  matériel, exercices à éviter), avec des réponses par défaut pour aller vite.
  Résumé à la fin (maintien, protéines, eau) ; tout reste modifiable dans Réglages, « Mon profil »
  et « Mon profil sportif ».
- Nouveau téléphone : le premier écran propose « J'ai une sauvegarde : la restaurer », qui ouvre
  l'import sans remplir le profil.

## Prompts à copier

- Réglages propose « Copier le prompt repas », « Copier le prompt programme sport » et « Copier le
  prompt d'ajustement ». Le texte est copié dans le presse-papiers, à coller dans Claude ou une autre IA ;
  l'app n'appelle aucune IA.
- Le prompt repas contient la partie repas du format d'import. Le prompt sport contient le profil, le
  profil sportif et la partie plan du format. Le prompt d'ajustement ajoute le plan actuel et le bilan
  des 28 derniers jours.
- Les prompts sport et ajustement contiennent des données personnelles (santé, douleurs) : l'app
  prévient avant de les copier.
- Une estimation faite par une IA est marquée `estimation_ia` comme source des aliments.

## Maintien réel estimé (Le propriétaire, 23/11/2026, PRD `docs/prd/maintien-reel.md`)

- Le maintien de la formule (Mifflin-St Jeor × activité) est vérifié avec les repas notés et les pesées.
  Calcul dans `:core` (`MaintenanceEstimator`), local, sans IA ni réseau. L'objectif reste « maintien » :
  la fonction ne propose jamais de perdre ou de prendre du poids.
- **Estimation** : fenêtre de 21 jours terminés (la veille et les 20 jours d'avant). Un jour est pris en compte
  s'il a au moins 2 repas ou au moins 50 % du maintien de la formule. Il faut 80 % de jours pris en compte (17 sur 21)
  et au moins 4 pesées, dont une dans la première semaine et une dans la dernière. Maintien estimé = apport moyen
  − pente des pesées lissées (moyenne sur 7 jours) × 7 700 kcal par kg, arrondi à 10. Poids qui varie de plus de 3 kg
  sur la fenêtre, ou estimation hors de ±30 % de la formule ou de 1 200 à 4 500 kcal : non fiable, jamais proposée.
  Les calories de la montre ne comptent pas ; les repas « calories seules » comptent dans l'apport.
  Confiance « haute » avec au moins 19 jours et 8 pesées, sinon « moyenne ».
- **Proposition** seulement si l'estimation s'écarte d'au moins 5 % du maintien actuel. Rien ne change seul.
  Carte sur Aujourd'hui (« Voir le détail », « Pas maintenant » = masquée 14 jours, sans notification), détail dans
  Mon profil > « Mon maintien » (formule, estimation, confiance, chiffres utilisés). « Adopter » enregistre un
  **ajustement** en kcal (estimation − formule) ; « Garder la formule » ferme la proposition ; « Revenir à la formule »
  (avec confirmation) retire l'ajustement. Après une décision, pas de nouvelle proposition avant 21 jours.
- L'ajustement s'applique **par-dessus la formule** (le maintien suit toujours le poids moyen) et **à un seul endroit** :
  `Maintenance.maintenanceKcal`. Anneau, couleurs, moyenne sur 7 jours, repères de macros, widget, bilan de la semaine,
  bilan repas le lisent donc tous. Si le niveau d'activité change après l'adoption, la carte rappelle de vérifier.
- Stockage : colonnes du profil (`maintenance_adjustment_kcal`, `adjustment_activity`, `proposal_decided_on`,
  `proposal_snoozed_until`), base v14, dans la sauvegarde ; une ancienne sauvegarde se restaure avec un ajustement de 0.
  Enregistrer le profil garde l'ajustement.
- Le bilan repas indique `maintenance_adjustment_kcal` ; les prompts sport et d'ajustement mentionnent le maintien
  réel seulement s'il y a un ajustement. Rappel affiché : l'estimation est approximative (repas photographiés à ±20-30 %,
  poids qui varie avec l'eau et le sel).

## Suivi du corps

- Taille dans le profil (Le propriétaire, 11/11/2026).
- Pesées datées, avec tour de taille facultatif à chaque mesure (choix du propriétaire, 11/11/2026).
- Courbe du poids avec la moyenne sur 7 jours.
- Le maintien est recalculé à partir de la moyenne des pesées des 7 derniers jours (le poids du profil
  sert tant qu'il n'y a aucune pesée).

## Sport

- Plan au poids du corps, à la maison, généré par Claude et importé.
- Chaque séance existe en 3 versions préparées par Claude : 15, 30 et 45 minutes. L'utilisateur choisit
  au début de la séance ; l'app ne raccourcit rien elle-même.
- Séance = échauffement, exercices, retour au calme. Exercice = nom, consignes, séries × répétitions
  ou durée, repos, variante plus facile et plus difficile.
- Mode séance guidée avec chrono de repos, saisie du réalisé et des exercices sautés.
- Vidéos : chaque exercice (et ses variantes plus facile/plus difficile) peut avoir un lien vidéo,
  rempli par Claude avec une recherche YouTube sur le nom français. Bouton « Voir la vidéo » dans la
  séance guidée, ouvert hors de l'app ; les consignes écrites restent le secours hors ligne.
  Illustrations intégrées à l'app : après le MVP (demande du propriétaire, 11/11/2026).
- Fin de séance : ressenti (trop facile / ok / trop dur) et signalement de douleur avec zone du corps.
- Jours d'entraînement avec notification de rappel : désactivé par défaut, activé dans Réglages avec
  une heure au choix (18 h par défaut). Il sonne les jours prévus par le plan (`day_of_week`), sauf si
  une séance est déjà enregistrée ce jour-là. Ce réglage n'est pas dans la sauvegarde : sur un nouveau
  téléphone, il faut le réactiver.
- Historique et régularité par semaine, sans série stricte.
- Journal d'activités libres (kiné, renforcement, marche, vélo, vélo électrique (distinct du vélo, demande du propriétaire du 13/11/2026), course, natation ou autre, + durée).
  Jour au choix : aujourd'hui, hier ou un autre jour passé (calendrier), jamais dans le futur.
  Zones travaillées facultatives, plusieurs au choix : abdos, fessiers, pectoraux, dos, épaules, bras,
  jambes, cardio, tout le corps (demande du propriétaire, 12/11/2026). Elles sont dans le bilan
  d'entraînement envoyé à Claude. Les activités sont comptées à part des séances du plan dans la semaine.
  Une activité ajoutée se modifie (toucher la carte, ou menu ⋮ > Modifier) avec les mêmes limites
  que l'ajout (demande du propriétaire, 13/11/2026).
  Chaque activité montre ses calories approximatives « ≈ N kcal » : MET (Compendium, intensité modérée,
  par mot-clé du type, 4 par défaut) × poids de référence × durée, arrondi à 5. Rien sans profil.
  Affichage seulement : ces calories ne s'ajoutent pas à l'objectif du jour (demande du propriétaire, 13/11/2026).
  Si un jour elles entrent dans le budget du jour, prendre (MET − 1) pour ne pas compter deux fois la
  maintenance (remarque [Archi], 13/11/2026).
- Une séance interrompue (appel, app fermée) reprend là où elle en était ; on peut aussi la terminer
  plus tôt et enregistrer ce qui a été fait.
- La progression passe par Claude : l'app exporte un bilan d'entraînement (les 28 derniers jours,
  envoyé par le partage Android), Claude renvoie un plan ajusté.
  Pas de règle de progression automatique dans l'app.
- Health Connect (compteur de pas) : hors MVP.

## Montre (Garmin, Health Connect)

Choix du propriétaire, 22/11/2026, étude du fil [Garmin]. L'app lit la montre par Health Connect, en lecture
seule, en deux étapes :

- **Étape 1** : pas, calories actives et séances de sport. Affichés dans une carte « Ma montre » sur
  Aujourd'hui ; les séances des 7 derniers jours (10 minutes ou plus) sont **proposées** pour préremplir
  le journal des activités libres (« Ajouter » ou « Ignorer »). Une séance ajoutée n'est plus reproposée.
- **Étape 2** : sommeil (la nuit qui vient de finir) et fréquence cardiaque (moyenne et plus bas du jour),
  dans la même carte. Ces deux lectures sont **facultatives** : la connexion marche sans, et un bouton
  « Ajouter le sommeil et le cœur » dans Réglages les demande à qui s'était connecté à l'étape 1.
- Les calories actives sont **indicatives** : elles ne s'ajoutent jamais à l'objectif (le sport n'ajoute
  pas de calories, voir « Sport »).
- Garmin Connect n'écrit **pas** la variabilité cardiaque, la Body Battery ni le stress dans Health Connect :
  l'app ne les affiche pas.
- La connexion est facultative : rien n'apparaît tant que le propriétaire n'a pas touché « Connecter ma montre » dans
  Réglages. Les types de données réellement écrits par Garmin sont à vérifier sur le téléphone
  (Paramètres, Health Connect, Accès aux données, Garmin Connect) : l'étude s'appuie sur des sources tierces.

### Écran « Ma montre » : tendances et historique

Choix du propriétaire, 23/11/2026 (PRD `docs/prd/ecran-montre.md`).

- L'écran **« Ma montre »** s'ouvre en touchant la carte « Ma montre » d'Aujourd'hui, ou par « Mes stats montre »
  dans Sport (visible seulement si la montre est connectée). Retour par la flèche ; depuis la refonte du 23/11/2026,
  l'écran se range sous l'onglet Santé.
- Sélecteur **7 jours** (par défaut) / **30 jours**. Quatre blocs : Pas et Calories actives (barres, avec la mention
  « Indicatif : n'entre pas dans ton objectif »), Sommeil (barres par nuit, en heures et minutes), Cœur (deux
  courbes : moyenne du jour en trait plein, plus bas du jour en pointillés). Chaque bloc donne la **moyenne de la
  période** et l'**écart avec la période précédente** de même durée, sans couleur de jugement ni seuil ni conseil.
  Toucher une barre ou un point donne la valeur exacte et la date. Un jour sans donnée est un **trou**, jamais un
  zéro, et ne compte pas dans la moyenne ; un bloc vide dit « Pas de données sur cette période ». Un bloc
  « Séances » résume la période : nombre (plan et activités, dont celles de la montre ajoutées au journal) et durée.
- **Historique gardé sur le téléphone** : un relevé par jour (pas, calories actives, sommeil de la nuit qui se
  termine ce jour-là, cœur moyen et plus bas), sans limite de durée, **dans la sauvegarde** (une sauvegarde
  ancienne, sans cette table, se restaure sans erreur mais la vide). À la première connexion, jusqu'à 30 jours de
  passé sont récupérés ; ensuite, à chaque lecture (ouverture, retour au premier plan, ouverture de l'écran), le
  jour est enregistré et les 3 derniers jours sont relus (synchronisation en retard). Une mesure absente ne remplace
  jamais une mesure déjà gardée ; un jour entièrement vide n'écrit rien.
- Montre non connectée et aucun relevé : l'écran propose « Connecter ma montre ». Sommeil et cœur non autorisés :
  ces deux blocs proposent « Ajouter le sommeil et le cœur ». Autorisation retirée : l'historique reste visible avec
  le message « La lecture de la montre est coupée ».
- Réglages, « Ma montre » : « Effacer l'historique de la montre » (avec confirmation) vide les relevés, sans toucher
  aux repas, séances ni activités. La sauvegarde dit qu'elle contient des données de santé.
- Pas de rapprochement avec les repas ou le poids, pas de 90 jours, pas d'envoi à Claude : plus tard.

## Rappels pour boire

- Demande du propriétaire (13/11/2026). Interrupteur dans Réglages, désactivé par défaut.
- Quatre vérifications : 11 h 30 (30 % de l'objectif d'eau), 14 h (50 %), 17 h 30 (70 %), 20 h 30 (90 %).
  Notification seulement si l'on est en dessous : où l'on en est, ce qu'il faudrait avoir bu, et le
  nombre de verres de 20 cL qui manquent. Ton neutre, quel que soit le ton des notifications de motivation.

## Notifications de motivation

- Demande du propriétaire (12/11/2026). Désactivées par défaut ; dans Réglages, quatre choix : désactivées,
  Gentil, Trash, Super trash. Le texte de chaque choix dit à quoi s'attendre.
- Deux par jour, vers 12 h 30 et 19 h. Le midi : repas pas notés, sinon eau sous 40 % de l'objectif.
  Le soir : séance prévue et pas faite, sinon repas, sinon eau sous 80 %, sinon un bravo.
- Tons choisis par le propriétaire : Trash sans filtre, physique compris ; Super trash avec insultes, toujours
  sur le ton de l'humour et du sarcasme. Textes non genrés (l'app est partagée).
- Bouton « Essayer maintenant ». Réglage hors sauvegarde, comme le rappel de séance.

## Design

- Material Design 3, palette fixe « vert frais », thème clair et sombre selon le téléphone.
- Refonte visuelle (fil Design, 12/11/2026) : police Nunito (fournie dans l'app, licence OFL dans
  `licenses/`), coins très arrondis, icônes arrondies. Aujourd'hui dans cet ordre : calories et macros,
  séance du jour, eau, semaine, poids. Écrans vides avec Pousse et un message propre au thème. Repas :
  une icône colorée par type de repas, barre du total face au maintien, import en lien discret.
  Sport : frise des jours de la semaine et flamme, bouton « + » pour ajouter une activité ou importer,
  « Supprimer » d'une activité derrière un menu ⋮. Réglages (voir « Refonte UI/UX »). Les boutons d'import disent « ton IA » : n'importe quelle IA peut produire le fichier.
- Couleur pleine du thème pour l'onglet actif et les chips sélectionnées (contraste ≥ 3:1 face à la barre) ;
  les boutons secondaires (eau, +/- des répétitions, « + » de Sport) restent en teinte claire avec texte
  foncé, pour ne jamais peser autant que le bouton principal (fil Design, 12/11/2026).
- Import : « Ouvrir avec Healthy », partage, collage ou choix d'un fichier, toujours avec un aperçu
  et une confirmation avant d'enregistrer.
- L'écran d'import s'ouvre aussi depuis Repas (« Importer un repas ») et Sport (« Importer un plan »),
  pas seulement depuis Réglages ; « Terminer » ramène à l'onglet d'origine (Le propriétaire, 12/11/2026).
  Le JSON peut venir de n'importe quelle IA du moment qu'il suit le format.
- Gros chiffres lisibles, textes en français simple.
- Ambiance ludique (choix du propriétaire, 11/11/2026, maquette du fil Design) : mascotte « Pousse », une graine
  à deux feuilles, avec trois humeurs : fière (séance faite aujourd'hui), encourageante (séance prévue
  pas encore faite), contente (sinon). En-tête vert (#1F7A47) sur Aujourd'hui avec le prénom.
  Accent corail (#FF8A65) réservé au bouton principal d'un écran.
- Flamme = nombre de semaines d'affilée (lundi à dimanche) avec au moins une séance du plan ou une
  activité ajoutée à la main (kiné, marche… ; choix du propriétaire, 12/11/2026) ; la semaine en cours ne casse
  pas la série tant qu'elle n'est pas finie (11/11/2026). Les badges de semaines suivent la même règle ;
  « première séance » et « 10 séances » ne comptent que les séances du plan. Une activité du jour
  s'affiche aussi sur Aujourd'hui, dans la carte « Séance du jour ».
- La série monte d'un cran par semaine, pas par séance (confirmé par le propriétaire le 13/11/2026). Une phrase
  sous le bonjour d'Aujourd'hui le dit : semaine validée, séance à faire avant dimanche, ou série à lancer.
- Badges (toucher la flamme) : premier repas, repas sur 7 jours différents, première séance, 10 séances,
  2, 4 et 12 semaines actives d'affilée (meilleure série, même finie). Recalculés à partir des repas et
  des séances notés : rien de plus dans la base ni dans la sauvegarde.
- Animations : l'anneau des calories se remplit et le chiffre défile ; après l'enregistrement d'une séance,
  écran « Bravo ! » avec Pousse fière et confettis. Dessinés en Compose, sans bibliothèque ni fichier
  d'animation (donc rien à licencier) ; ils suivent le réglage « animations » du téléphone.
- Pousse en relief (refonte, lot 5, validé par le propriétaire le 23/11/2026) : dégradés pour le volume, reflet,
  ombre au sol, dessinée en Compose à partir des vecteurs du prototype (`ui/Pousse.kt`). API : `Pousse(growth, theme,
  size, mood)`. Tenue par thème : Pirate = bandana rouge et cache-œil, Voyage = chapeau de paille et sac à dos,
  Classique = rien. Message de montée de niveau (« Pousse a grandi ! », bouton OK) une seule fois quand l'étape monte :
  l'app retient la dernière étape vue dans les préférences du téléphone (aucune table, pas dans la sauvegarde) ;
  aucun message au premier lancement ni quand l'étape redescend.
- Icône de l'appli « Capitaine Pousse » (choix du propriétaire, 11/11/2026) : Pousse en pirate, fourchette et
  haltère croisés, fond #17301F ; couche monochrome pour les icônes à thème. Le nom reste « Healthy ».
- Un motif discret par espace (choix du propriétaire, 11/11/2026), en vectoriel : Aujourd'hui, carte au trésor
  dans l'en-tête vert seulement ; Repas, fond #F3FAF5 semé d'aliments au trait ; Sport, fond pêche #FFF4EC
  avec diagonales corail. Jamais sous l'anneau, les macros ou le chrono (cartes unies, blanches en clair).
  En thème sombre : fond du thème, motif plus discret.
- Thèmes (choix du propriétaire, 11/11/2026, réduits à trois le 23/11/2026) : **Classique** (le vert
  ludique ci-dessus, par défaut), **Pirate** et **Voyage**. Espace, Arcade rétro et Montagne sont retirés ; un
  réglage enregistré sur l'un d'eux revient à Classique (valeur inconnue = Classique dans `AppearanceStore`).
  Choisis dans Moi > Réglages > Apparence, appliqués tout de suite, sans redémarrer ;
  le choix est gardé dans les préférences du téléphone (pas dans Room, pas dans la sauvegarde).
  Chaque thème change cinq choses, et seulement elles : palette, tenue de Pousse, décors photo, récompenses,
  titres et messages. **Les libellés des onglets et des boutons ne changent jamais.** Tous les textes
  atteignent un contraste de 4,5:1 (vérifié par un test).
  - Pirate : fond parchemin #F4E9D2, encre marine #1E2A3A (texte, en-tête, barres), accent rouge #C0392B,
    or #E0A526 ; sombre = nuit en mer #14202E, texte #F4E9D2. Pousse en bandana et cache-œil. Carte au
    trésor dans l'en-tête et sur les fonds de Repas et Sport. Pluie de pièces d'or ; badges en coffres
    (séances, repas) et pièces d'or (semaines). Textes : « Journal de bord du jour », « Ohé moussaillon ! »,
    « Cap tenu ! » quand la moyenne sur 7 jours est dans la cible, « Séance terminée, moussaillon ! », « Mon trésor ».
  - Voyage : fond sable #FBF3E4, bleu océan #1C6E8C (en-tête, barres), accent coucher de soleil #F28C38,
    turquoise #2BB3A3 pour les réussites ; sombre = ciel de nuit #0F2433, texte #FBF3E4. Pousse en chapeau
    de paille et sac à dos. Trajets d'avion en pointillés, petits avions et boussole. Pluie de cartes
    postales et d'autocollants ; badges en tampons de passeport ; la flamme compte des « étapes ».
    Textes : « Carnet de voyage du jour », « Étape franchie ! », « Belle escale, on repart ? »,
    « Mon passeport ».
  - Ajouter un thème : une valeur de `AppTheme`, ses deux palettes et son `Ambiance` (Theme.kt), ses 4 décors
    et son voile (`PhotoBackgrounds`, PhotoBackground.kt), puis une branche dans Pousse.kt, Patterns.kt,
    Confetti.kt, BadgesScreen.kt et ThemeTexts.kt.

## Refonte UI/UX (23/11/2026)

Choix du propriétaire, découpée en 6 lots (cahier des charges dans les fichiers du projet, `design/refonte/`).
Cette section est tenue à jour par chaque lot, dans son emplacement.

### Lot 0 : navigation, thèmes, fond photo (livré)

- **5 onglets** : Aujourd'hui, Repas, Sport, Santé, Moi. Chaque fonction a une seule maison ; ses écrans
  secondaires s'ouvrent depuis son onglet et l'onglet reste allumé (`tabRouteOf`, ui/HealthyApp.kt). Il n'y a
  plus d'onglet Réglages.
- **Moi** a une icône à curseurs en haut à droite qui ouvre **Réglages**, en trois groupes : Personnaliser
  (Apparence, Rappels et motivation, Mon profil sportif), Mes données (Sauvegarde et export, Importer depuis
  mon IA, Ma montre connectée), Aide (Prompts à copier, Mises à jour). Chaque ligne ouvre son écran.
  Mon profil, Mon poids et Mes badges sont sur Moi.
- **Apparence** : les 3 thèmes avec l'aperçu de leur décor d'Aujourd'hui (voir « Thèmes »).
- **Fond photo** : composant `PhotoBackground(onglet, thème)` (ui/PhotoBackground.kt). La photo couvre le haut
  de l'écran derrière les cartes, défile avec le contenu et se fond dans la couleur de fond ; les titres posés
  dessus sont blancs avec une ombre douce (`PhotoTitle`). 12 décors, 4 par thème (Aujourd'hui, Repas, Sport,
  Santé ; Moi n'en a pas), en WebP 720 × 960 dans `res/drawable-nodpi` (environ 1 Mo en tout, un test vérifie
  moins de 3 Mo). Cartes opaques à coins de 28 dp, police Nunito.
- Les écrans Aujourd'hui, Repas et Sport gardent leur en-tête actuel jusqu'à leur lot, qui adopte le fond photo.
  Santé et Moi sont des **coquilles** (raccourcis vers les écrans déjà là : Ma montre, Mon eau, Mon profil,
  Mon poids, Mes badges) que le lot 4 remplit.
- Aucune table, aucune migration.

### Lot 1 : Aujourd'hui (livré)

- **Fond photo** de l'onglet (`PhotoBackground(PhotoTab.TODAY)`) : Pousse, le bonjour et la série sont posés
  dessus en blanc avec ombre douce, les cartes sont opaques à coins de 28 dp.
- **Ordre des cartes** : calories (anneau neutre le jour en cours, 4 barres de macros), Eau, Ma montre (si
  montre connectée), Séance du jour, bilan de la semaine (carte « prêt » du dimanche soir au mardi ; il s'ouvre
  aussi tout seul le lundi). Les cartes d'avant la refonte (maintien, repas habituel, 7 derniers jours, Mon
  poids) restent en dessous jusqu'au lot 4, qui les met dans Santé et Moi.
- **Eau** : 4 boutons de même largeur (Verre 20 cL, Café 15 cL, Thé 25 cL, Bouteille 50 cL), un appui ajoute,
  avec la vibration et le verre animé déjà prévus. Le café, le thé et les boissons sans alcool des repas comptent
  dans l'eau (règle `drink`, voir « Eau »). Le café du widget passe aussi à 15 cL.
- **Ma montre** : calories brûlées, pas, distance et cœur moyen en quatre mesures ; un appui ouvre l'écran Ma
  montre (Santé). La carte est masquée sans montre connectée. La distance affiche « – » tant que le lot 4 ne la
  fournit pas. Les séances à ajouter au journal restent proposées dans la carte.
- Aucune table, aucune migration.

### Lot 2 : Repas

(à remplir par le lot 2)

### Lot 3 : Sport et trajets

(à remplir par le lot 3)

### Lot 4 : Santé et Moi

(à remplir par le lot 4)

### Lot 5 : mascotte Pousse

(à remplir par le lot 5)

## Mises à jour de l'app

- Le propriétaire dépose l'APK à jour dans un dossier Google Drive partagé par lien, sans le renommer
  (`healthy-<numéro>.apk`). L'app vérifie ce dossier chaque fois qu'on l'ouvre ou qu'on y revient (au plus
  toutes les 30 min ; 13/11/2026, le propriétaire ne voyait pas la vérification au démarrage) et propose
  « Mettre à jour » ; « Plus tard » ne la repropose pas avant 24 h ; Android demande toujours de confirmer l'installation (choix du propriétaire, 13/11/2026 :
  dossier Drive à dépôt manuel, plutôt qu'une publication automatique).
- Réglages, carte « Mises à jour » : version installée et bouton « Vérifier les mises à jour ».
  Le lien du dossier est fourni avec l'app : il n'est jamais affiché (même dans un message d'erreur)
  ni modifiable dans l'app ; le changer passe par le code (demandes du propriétaire, 13/11/2026). Ses proches
  n'ont rien à régler.

## Technique

- Kotlin, Jetpack Compose, Room, injection manuelle. Détails dans [`architecture.md`](architecture.md).
- Clé de signature et lien du dossier de mises à jour dans les secrets de GitHub Actions, pas dans le dépôt
  (choix du propriétaire, 23/11/2026, pour publier le code ; le dépôt public part d'un seul commit propre). Détails dans
  [`architecture.md`](architecture.md).
- Import par collage en premier (le plus simple depuis l'app Claude sur le téléphone).
- Plan en 4 étapes : 1) structure + format d'import, 2) suivi des calories, 3) plan sportif,
  4) tableau de bord + APK installable.
