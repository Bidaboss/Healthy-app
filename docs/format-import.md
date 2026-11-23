# Format d'import Healthy (version 1)

Ce document est **la référence unique** du format des fichiers que l'app Healthy sait importer.
Il sert de contrat entre les fils Claude du projet (photos de repas, plan sportif) et l'application.
Les exemples de [`docs/examples/`](examples/) sont relus par les tests automatiques de l'app :
s'ils deviennent invalides, la construction échoue.

## Comment le fichier arrive dans l'app

1. **Coller du texte** (méthode principale) : Claude répond avec un bloc de code JSON, le propriétaire le copie,
   ouvre l'onglet *Importer* de l'app et appuie sur *Coller*.
2. **Partager vers Healthy** : depuis l'app Claude (ou toute autre app), *Partager* un texte ou un fichier `.json` vers Healthy.
3. **Ouvrir un fichier** : bouton *Choisir un fichier* dans l'onglet *Importer*.

Le texte collé peut contenir du texte autour du JSON ou les balises <code>```json</code> : l'app extrait
l'objet JSON entre la première `{` et la dernière `}`. **Un message = un seul objet JSON.**

## Règles générales

- Encodage UTF-8, JSON standard.
- Les noms de champs sont en `snake_case`, et l'unité est dans le nom (`kcal`, `_g`, `_s`, `_min`).
- Les champs inconnus sont ignorés (on peut en ajouter sans casser les anciennes versions de l'app).
- Les champs marqués *optionnel* peuvent être absents ou `null`.
- Les dates sont en ISO 8601. **En cas de doute sur le fuseau de l'utilisateur, écrire l'heure locale
  sans décalage**, ex. `2026-11-11T12:30:00` : l'app utilise alors le fuseau du téléphone.
  Un décalage (`+01:00`) n'est à mettre que s'il est sûr : un mauvais décalage près de minuit
  range le repas dans le mauvais jour.

## Enveloppe (commune à tous les types)

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `format` | texte | oui | Toujours `"healthy-import"`. |
| `schema_version` | entier | oui | `1` pour ce document. Une version supérieure est refusée avec un message clair (mettre l'app à jour). |
| `type` | texte | oui | `"meal"` (repas) ou `"workout_plan"` (plan sportif). `"training_report"` (bilan sport), `"meal_report"` (bilan repas) et `"backup"` (sauvegarde) sont écrits par l'app (voir plus bas). |
| `id` | texte (UUID) | oui | Identifiant unique de **cet** import. Réimporter le même `id` ne crée pas de doublon : l'app indique qu'il est déjà importé. Une correction doit donc porter un **nouvel** `id` et indiquer l'ancien dans `replaces`. |
| `replaces` | texte (UUID) | optionnel | `id` d'un import précédent que ce fichier **corrige**. L'app remplace alors l'ancien repas au lieu d'en ajouter un deuxième. Pour corriger une correction, indiquer l'`id` de la **dernière** version importée. Si le repas visé est introuvable, l'app le signale et ajoute le repas. Sans effet pour un plan sportif (un nouveau plan remplace toujours l'ancien). |
| `created_at` | date-heure | oui | Moment où Claude a généré le fichier. |
| `meal` | objet | si `type = meal` | Voir ci-dessous. |
| `plan` | objet | si `type = workout_plan` | Voir ci-dessous. |

## Type `meal` : un repas

Un repas peut avoir été décrit par plusieurs photos ; il donne **un seul** import.

### Objet `meal`

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `eaten_at` | date-heure | oui | Heure du repas. L'app la convertit dans le fuseau du téléphone : le jour du journal est toujours la date locale du téléphone. |
| `meal_type` | texte | oui | `petit-dejeuner`, `dejeuner`, `diner` ou `collation`. (Acceptés aussi : `breakfast`, `lunch`, `dinner`, `snack`.) |
| `title` | texte | optionnel | Nom court du repas, ex. « Poulet riz brocolis ». |
| `notes` | texte | optionnel | Remarque libre (incertitudes, contexte des photos). |
| `items` | liste | oui, au moins 1 | Aliments du repas. |

### Aliment (`items[]`)

Les valeurs nutritionnelles sont **pour la quantité indiquée**, pas pour 100 g.

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `name` | texte | oui | Nom de l'aliment, en français. |
| `quantity_g` | nombre | optionnel | Quantité en grammes (pour un liquide : 1 ml ≈ 1 g). |
| `portion` | texte | optionnel | Description de la portion, ex. « 1 bol ». |
| `kcal` | nombre | oui | Énergie en kilocalories, ≥ 0. |
| `protein_g` | nombre | optionnel | Protéines (g). |
| `carbs_g` | nombre | optionnel | Glucides (g). |
| `fat_g` | nombre | optionnel | Lipides (g). |
| `fiber_g` | nombre | optionnel | Fibres (g). |
| `source` | texte | optionnel | `estimation_claude` (par défaut), `estimation_ia` (estimation par une autre IA), `openfoodfacts` ou `manuel`. |
| `confidence` | texte | optionnel | Confiance de l'estimation : `haute`, `moyenne` ou `faible`. (Acceptés aussi : `high`, `medium`, `low`.) |
| `assumptions` | texte | optionnel | Hypothèses faites, ex. « 1 c. à soupe d'huile supposée ». |
| `off_code` | texte | optionnel | Code-barres Open Food Facts si l'aliment en vient. |
| `drink` | texte | optionnel | `boisson` ou `alcool` si l'aliment se boit (lait, café, thé, jus, soupe, soda : `boisson` ; vin, bière, spiritueux : `alcool`). Absent = aliment ordinaire. Une `boisson` avec `quantity_g` compte dans l'eau bue du jour (1 g = 1 ml) ; l'`alcool` n'y compte jamais. Donne toujours `quantity_g` pour une boisson. |

Tous les nombres doivent être ≥ 0 et ≤ 5000 par aliment. Un repas contient au plus 100 aliments ;
les noms font au plus 200 caractères, les textes libres 2000. Il n'y a **pas** de champ total : l'app calcule elle-même les totaux
du repas et de la journée à partir des aliments.

Exemples : [`repas-dejeuner.json`](examples/repas-dejeuner.json), [`repas-collation-minimal.json`](examples/repas-collation-minimal.json).

L'app écrit aussi ce type : « Partager ce repas » (écran d'un repas) produit un fichier `meal` complet
(`MealWriter`, nouvel `id` à chaque partage, jamais de `replaces`, heure avec le décalage du téléphone),
qu'un proche importe tel quel. L'envoi se fait par le partage Android, en fichier `.json`. Pour une autre portion
(La moitié, Un tiers, un pourcentage), l'app multiplie `quantity_g`, `kcal` et les macros et n'écrit plus `portion`
(texte libre) ; le format ne change pas (`SavedFoodsTest`). Réécrire `repas-dejeuner.json` redonne exactement ce
fichier (`MealWriterTest`).

## Type `workout_plan` : un plan sportif

Importer un plan le rend **actif** ; l'ancien plan est archivé (l'historique des séances est conservé).

### Objet `plan`

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `name` | texte | oui | Nom du plan. |
| `description` | texte | optionnel | Objectif, conseils généraux. |
| `weeks` | entier | optionnel | Durée prévue en semaines (≥ 1). |
| `sessions` | liste | oui, au moins 1 | Séances types d'une semaine, dans l'ordre. |

### Séance (`sessions[]`)

Chaque séance existe en **plusieurs versions de durée** préparées par Claude (15, 30 et 45 minutes).
Au début de la séance, l'utilisateur choisit sa durée et l'app charge cette version telle quelle.

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `title` | texte | oui | Nom de la séance. |
| `day_of_week` | entier | optionnel | Jour d'entraînement conseillé : 1 = lundi … 7 = dimanche. |
| `notes` | texte | optionnel | Consignes générales. |
| `variants` | liste | oui, au moins 1 | Versions de la séance, une par durée. |

### Version de séance (`variants[]`)

Chaque version est une séance complète, avec son propre échauffement et retour au calme.

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `duration_min` | entier | oui | Durée de cette version en minutes, ≥ 1. Unique dans la séance. Claude prépare 15, 30 et 45. |
| `warm_up` | texte | optionnel | Échauffement. |
| `cool_down` | texte | optionnel | Retour au calme, étirements. |
| `exercises` | liste | oui, au moins 1 | Exercices, dans l'ordre. |

### Exercice (`exercises[]`)

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `name` | texte | oui | Nom de l'exercice. |
| `sets` | entier | oui | Nombre de séries (≥ 1). |
| `reps` | entier | un des deux | Répétitions par série. |
| `duration_s` | entier | un des deux | Durée par série en secondes (gainage, cardio). |
| `rest_s` | entier | optionnel | Repos entre séries en secondes. |
| `instructions` | texte | optionnel | Comment faire l'exercice. |
| `easier_variant` | texte | optionnel | Variante plus facile. |
| `harder_variant` | texte | optionnel | Variante plus difficile. |
| `notes` | texte | optionnel | Autre remarque. |
| `video_url` | texte | optionnel | Lien vers une vidéo de démonstration, commençant par `https://`, sans espace. Claude met un lien de recherche YouTube sur le nom français de l'exercice : `https://www.youtube.com/results?search_query=<nom+de+l'exercice>` (mots séparés par `+`). Un lien invalide ne bloque pas l'import : l'app répare les petites fautes (espaces, `http://`, `www.` sans `https://`, lien Markdown) ou ignore le lien et le signale dans l'aperçu. |
| `easier_video_url` | texte | optionnel | Pareil, pour la variante plus facile (utile seulement si `easier_variant` est rempli). |
| `harder_video_url` | texte | optionnel | Pareil, pour la variante plus difficile (utile seulement si `harder_variant` est rempli). |

Il faut **exactement un** des deux champs `reps` ou `duration_s`.

Un lien vidéo qui n'est pas une adresse `https://` complète (sans espace, 500 caractères au plus) est
refusé à l'import. Le lien s'ouvre hors de l'app (YouTube ou le navigateur) : les `instructions` doivent
rester suffisantes pour faire l'exercice sans internet.

Exemple : [`plan-sport-maison.json`](examples/plan-sport-maison.json).

La progression du plan ne se fait pas dans l'app : Le propriétaire envoie un bilan d'entraînement à Claude,
qui renvoie un nouveau plan à importer (nouvel `id`).

## Type `training_report` : bilan d'entraînement (export)

Avec `meal_report`, c'est un type qui va **de l'app vers Claude**. Dans l'onglet Sport, « Envoyer mon bilan à Claude »
ouvre le partage Android avec ce texte JSON. L'app refuse de l'importer (message clair). Claude le lit
dans le fil du plan sportif pour ajuster le plan, puis renvoie un `workout_plan` avec un nouvel `id`.

Exemple complet : [`examples/bilan-entrainement.json`](examples/bilan-entrainement.json).

Même enveloppe que les imports (`format`, `schema_version`, `type`, `id`, `created_at`), avec un objet
`report`. Les dates et heures sont en **heure locale du téléphone, sans décalage**.

### Objet `report`

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `period_start` | date | oui | Premier jour couvert, inclus. |
| `period_end` | date | oui | Dernier jour couvert, inclus : le jour de l'export. |
| `sessions` | liste | oui | Séances guidées enregistrées dans la période, de la plus ancienne à la plus récente. Peut être vide. |
| `activities` | liste | oui | Activités libres de la période, de la plus ancienne à la plus récente. Peut être vide. |

**Période couverte : les 28 derniers jours, jour de l'export compris**, quel que soit le plan actif.
Si le plan a changé pendant la période, `plan_name` permet de distinguer les séances de chaque plan.
Une semaine sans séance n'apparaît pas : c'est une information (régularité), pas un oubli.

### Séance (`sessions[]`)

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `started_at` | date-heure | oui | Début de la séance. |
| `plan_name` | texte | oui | Nom du plan au moment de la séance. |
| `session_title` | texte | oui | Titre de la séance dans ce plan. |
| `planned_duration_min` | entier | oui | Version choisie : 15, 30 ou 45. |
| `actual_duration_min` | entier | oui | Temps réellement passé, pauses comprises. |
| `feeling` | texte | non | `"trop_facile"`, `"ok"` ou `"trop_dur"`. Absent si non renseigné. |
| `pain_zone` | texte | non | Zone douloureuse signalée, en texte libre. Absent s'il n'y a pas eu de douleur. |
| `exercises` | liste | oui | Les exercices de la version choisie, dans l'ordre. |

Une séance peut avoir été **terminée plus tôt** : les exercices non atteints ont alors une liste de
séries vide et `skipped: false`.

### Exercice réalisé (`exercises[]`)

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `name` | texte | oui | Nom de l'exercice, tel qu'il était dans le plan. |
| `planned_sets` | entier | oui | Nombre de séries prévues. |
| `planned_reps` | entier | si répétitions | Répétitions prévues par série. |
| `done_reps` | liste d'entiers | si répétitions | Répétitions faites, une valeur par série **faite** (peut être plus courte que `planned_sets`). |
| `planned_duration_s` | entier | si chronométré | Durée prévue par série. |
| `done_duration_s` | liste d'entiers | si chronométré | Secondes tenues, une valeur par série faite. |
| `skipped` | booléen | oui | `true` si l'utilisateur a choisi « Passer cet exercice » (les séries déjà faites restent listées). |

### Activité libre (`activities[]`)

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `date` | date | oui | Jour de l'activité. |
| `type` | texte | oui | Texte libre : « Marche », « Vélo », « Kiné »… |
| `duration_min` | entier | oui | Durée en minutes. |
| `body_parts` | liste de textes | oui | Zones travaillées, dans cet ordre : `abs` (abdos), `glutes` (fessiers), `chest` (pectoraux), `back` (dos), `shoulders` (épaules), `arms` (bras), `legs` (jambes), `cardio`, `full_body` (tout le corps). Peut être vide. |

## Type `meal_report` : bilan repas (export)

Va **de l'app vers Claude**, comme `training_report`. Dans l'onglet Repas, « Envoyer mon bilan repas à
Claude » ouvre le partage Android avec ce texte JSON. L'app refuse de l'importer (message clair). Claude le
lit dans le fil Nutrition pour conseiller le propriétaire ; il ne renvoie pas de fichier en retour.

Exemple complet : [`examples/bilan-repas.json`](examples/bilan-repas.json).

Même enveloppe que les imports (`format`, `schema_version`, `type`, `id`, `created_at`), avec un objet
`report`. Les dates et heures sont en **heure locale du téléphone, sans décalage**.

### Objet `report`

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `period_start` | date | oui | Premier jour couvert, inclus. |
| `period_end` | date | oui | Dernier jour couvert, inclus : le jour de l'export. |
| `profile` | objet | oui | Repères : `maintenance_kcal` (maintien utilisé par l'app, ajustement compris), `maintenance_adjustment_kcal` (part de cet ajustement : écart en kcal avec la formule, 0 si la formule seule, négatif possible) et `protein_target_g` (entiers, absents sans profil), `fiber_target_g` (toujours 30). |
| `days` | liste | oui | **Les 7 derniers jours, jour de l'export compris**, du plus ancien au plus récent, même sans repas. |

Le jour de l'export est souvent incomplet : la journée n'est pas finie.

### Jour (`days[]`)

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `date` | date | oui | Le jour. |
| `kcal` | entier | oui | Total des calories notées (0 si aucun repas : jour non noté, pas jour de jeûne). |
| `protein_g`, `carbs_g`, `fat_g`, `fiber_g` | nombre | oui | Totaux du jour, à 0,1 g près (un champ non renseigné sur un aliment compte 0). |
| `water_ml` | entier | oui | Eau bue ce jour-là : ajouts de l'écran Eau + boissons des repas (0 si rien). |
| `meals_without_macros` | entier | non | Nombre de repas notés **en calories seules** ce jour-là (voir `macros_missing`). Absent s'il n'y en a pas. Leurs calories sont dans `kcal`, mais pas dans les protéines, glucides, lipides ni fibres du jour : ne pas en conclure un manque de protéines ou de fibres. |
| `meals` | liste | oui | Repas du jour, du plus ancien au plus récent. Peut être vide. |

### Repas (`meals[]`)

| Champ | Type | Toujours présent | Description |
|---|---|---|---|
| `meal_type` | texte | oui | `"petit-dejeuner"`, `"dejeuner"`, `"diner"` ou `"collation"`. |
| `eaten_at` | date-heure | oui | Heure du repas. |
| `title` | texte | non | Nom du repas, s'il en a un. |
| `kcal` | entier | oui | Total du repas. |
| `macros_missing` | booléen | non | `true` si le repas est noté en calories seules (restaurant…) : aucun aliment n'a de protéines, glucides, lipides ni fibres. Absent sinon. |
| `items` | liste | oui | Aliments : `name`, `quantity_g` (si connue), `portion` (si connue), `kcal` (entier). |

## Type `backup` : sauvegarde complète (app → app)

Écrit par l'app (Réglages, « Exporter mes données ») dans un fichier choisi par l'utilisateur, relu par
« Importer » sur le même téléphone ou un nouveau. Claude n'a pas à en écrire. Même enveloppe que les
imports (`format`, `schema_version`, `type`, `id`, `created_at`), avec un objet `backup` :

| Champ | Type | Description |
|---|---|---|
| `database_version` | entier | Version de la base de l'app au moment de l'export (`HealthyDatabase.VERSION`). |
| `tables` | objet | Pour chaque table de la base, la liste de ses lignes : `{ "colonne": valeur }`. |

Les valeurs sont celles de SQLite : `null`, nombre ou texte (les booléens valent 0 ou 1). Les noms de
tables et de colonnes sont en minuscules, chiffres et `_` uniquement ; tout autre nom est refusé.

- La restauration **remplace toutes les données** du téléphone, en une seule transaction, après un aperçu
  qui dit combien de repas, séances, activités et pesées seront remis.
- Une sauvegarde d'une version plus **ancienne** de la base se restaure : les colonnes ajoutées depuis
  restent vides, les tables nouvelles restent vides. Une sauvegarde d'une version plus **récente** est
  refusée (« mets l'app à jour »).
- Une table ou une colonne inconnue de la base est ignorée.
- Le réglage du rappel de séance n'est pas sauvegardé (il se règle en deux gestes).

Exemple réduit : [`examples/sauvegarde.json`](examples/sauvegarde.json).

## Évolution du format

- Ajouter un champ optionnel ne change pas `schema_version`.
- Renommer ou supprimer un champ, ou rendre un champ obligatoire, passe à `schema_version: 2`
  et ce document doit être mis à jour en même temps que l'app.
