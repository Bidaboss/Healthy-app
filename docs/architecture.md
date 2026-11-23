# Architecture

Règles techniques du dépôt. Chaque règle est vérifiée par un outil (indiqué entre parenthèses)
ou par la relecture des PR.

## Modules

| Module | Contenu | Dépend de |
|---|---|---|
| `:core` | Kotlin pur, sans Android : lecture et validation du format d'import. | rien |
| `:app` | L'application : écrans Compose, base Room, import. | `:core` |

`:core` est séparé pour que le contrat d'import soit testable en quelques secondes, sans SDK Android.

## Paquets de `:app` (par fonctionnalité)

```
com.bidaboss.healthy
├── today/data         bilan de la semaine, état du jour (réunit repas, eau et sport)
├── today/ui           onglet Aujourd'hui (tableau de bord)
├── today/widget       widget d'écran d'accueil (Glance), passe par today/data
├── meals/{data,ui}    journal des repas
├── health/{data,ui}   montre (Garmin) lue par Health Connect, en lecture seule ; onglet Santé
├── workout/{data,ui}  plan sportif et séances (+ reminder : alarme et notification du rappel)
├── body/{data,ui}     profil, pesées et tour de taille ; onglet Moi
├── settings/data      thème et ton de motivation (préférences Android), journée pour la motivation
├── settings/reminder  alarmes et notifications de motivation
├── settings/ui        Réglages (ouverts depuis Moi) et écran Apparence
├── importexport/{data,ui}  import (et plus tard export)
├── update/{data,ui}   mise à jour de l'app depuis le dossier Drive
├── data/              HealthyDatabase (seul point d'entrée Room)
├── ui/                navigation, thème, outils d'affichage communs
└── AppContainer       création des objets partagés (injection manuelle)
```

## Sens des dépendances

- `ui` dépend de `data`, jamais l'inverse.
- Les entités et DAO Room ne sortent pas de `data` : un **repository** les convertit en modèles simples,
  un **ViewModel** expose un état d'écran (`StateFlow`), l'écran Compose l'affiche.
  (Vérifié par `ArchitectureRulesTest` : `ui` n'importe aucune classe d'un fichier qui importe `androidx.room`.)
- Les transactions passent par `TransactionRunner`, pour que les repositories ne dépendent pas de Room directement.
- Exception : `ui` peut afficher les modèles `Imported*` de `:core`, uniquement pour l'aperçu d'import.
- `today` est le tableau de bord : il peut importer depuis `meals/ui`, `workout/ui`, `body/ui` et `health`.
  `meals`, `workout`, `body` et `health` ne s'importent jamais entre eux, ni `today`. `settings` peut ouvrir
  les écrans des autres fonctionnalités (profil, rappel). Un composable ne monte dans `ui/` que si
  deux fonctionnalités autres que le tableau de bord en ont besoin.
- `ui` peut aussi utiliser `core.workout` (`SessionRun`, `PlannedExercise`) : le déroulé d'une séance
  est une logique pure, testée dans `:core` (`SessionRunTest`), sans Android ni Room.
- Les ViewModels sont créés via `healthyViewModelFactory` à partir de `AppContainer`.
- Quand une fonctionnalité a besoin d'une donnée d'une autre (Repas affiche le maintien du profil),
  `AppContainer` lui passe une fonction (`maintenanceKcal`) plutôt que le repository de l'autre paquet.
- Santé et Moi lisent ce qui vient d'autres paquets par des fonctions que fournit `AppContainer` (`healthWeek`,
  `weeklyRecap`, `meProgress`, `maintenanceKcal`, `maintenanceInfo`), calculées dans `today/data`
  (`HealthWeekRepository`, `MeProgressRepository`) : `health` et `body` ne s'importent pas entre eux.
- Navigation : 5 onglets (Aujourd'hui, Repas, Sport, Santé, Moi), routes et rattachement de chaque écran à son
  onglet dans `ui/HealthyApp.kt` (`tabRouteOf`), avec un emplacement par lot de la refonte (« LOT n »).
- Fond photo des onglets : `PhotoBackground(onglet, thème)` et `PhotoTitle` (`ui/PhotoBackground.kt`) ; les 12
  décors sont des WebP dans `res/drawable-nodpi` (nommés `decor_<thème>_<onglet>`), moins de 3 Mo au total
  (vérifié par `PhotoBackgroundTest`). Un nouvel écran d'onglet pose le composant dans son contenu qui défile.
- Typographie et formes : `ui/theme/Type.kt` (Nunito, police variable dans `res/font`, licence dans
  `licenses/`). Couleurs des macros : `ui/theme/MacroColors.kt`, contraste vérifié par `ThemesTest`.

## Format d'import

Le contrat est [`format-import.md`](format-import.md), ses exemples sont dans [`examples/`](examples/).
Le lecteur est `ImportParser` dans `:core`. (Vérifié par `ImportParserTest`, qui relit tous les exemples.)
L'app n'enregistre rien sans aperçu et confirmation, et un même `id` n'est jamais importé deux fois.

Les prompts à copier (`Prompts` dans `:core`, testé par `PromptsTest`) reprennent les parties du
contrat telles quelles : la tâche Gradle `copyFormatDocs` copie `docs/format-import.md` et deux exemples
dans les assets (`format/`) à chaque build, pour que l'app et le document ne divergent jamais.
`PlanWriter` réécrit le plan actuel au format d'import pour le prompt d'ajustement (testé par aller-retour).

## Base de données (Room)

- `exportSchema = true` ; les schémas sont versionnés dans `app/schemas/`.
- Tout changement de schéma = version + 1 (`HealthyDatabase.VERSION`) et une migration écrite dans
  `Migrations`. (Vérifié par `MigrationTest`, qui rejoue chaque migration sur les schémas versionnés.)
- L'historique des séances est une **copie** (nom du plan, titre, exercices) : changer ou supprimer
  un plan ne modifie jamais les séances déjà faites.
- `fallbackToDestructiveMigration` est interdit : les données des proches doivent survivre aux mises à jour.
  (Vérifié par `ArchitectureRulesTest`.)
- On enregistre les aliments de chaque repas, pas seulement les totaux ; chaque repas garde son
  horodatage et sa date locale (pour le regroupement par jour).

## Montre (Health Connect)

- `health/data` : `WatchSource` (interface) et `HealthConnectSource` (la vraie lecture, bibliothèque
  `androidx.health.connect:connect-client`, qui demande `compileSdk` 36). Les tests utilisent une fausse source.
- Lecture seule : permissions `READ_STEPS`, `READ_ACTIVE_CALORIES_BURNED`, `READ_EXERCISE`, `READ_SLEEP` et `READ_HEART_RATE` (ces deux-là
  facultatives : `CORE_PERMISSIONS` suffit pour être connecté, `EXTRA_PERMISSIONS` remplit sommeil et cœur). Rien n'est écrit dans Health Connect, rien ne sort du téléphone.
- Une séance de la montre ajoutée au journal garde son identifiant (`activities.health_id`, index unique) :
  elle ne peut pas être ajoutée deux fois. Les séances « Ignorer » sont dans les préférences `watch`
  (pas dans la sauvegarde : elles n'ont de sens que sur ce téléphone).
- **Relevés par jour** (écran « Ma montre ») : table `watch_days` (`health/data/WatchHistory.kt`, base v13), une ligne par
  jour, mesure absente = `null` (jamais zéro). `WatchRepository.syncHistory` les écrit en tâche de fond : 30 jours à la
  première connexion, puis les 3 derniers jours relus. Ils sont dans la sauvegarde (`BackupRepository.TABLES`).
  Les tendances (moyenne, écart avec la période précédente) sont calculées dans `:core` (`WatchTrends`). Ma montre et
  Sport ne s'importent pas : `AppContainer` donne à l'un `sessionSummary` (séances d'une période) et à l'autre
  `isWatchConnected`. Graphiques dessinés en Compose (`WatchCharts.kt`), sans bibliothèque.
- Le manifeste déclare l'écran de confidentialité (`WatchPrivacyActivity`) exigé par Health Connect.

## Repas : Mes aliments, import et partage

- « Mes aliments » : table `saved_foods` (`meals/data/SavedFoodEntities.kt`, base v15, dans la sauvegarde), un aliment
  par nom réduit (`name_key`, calculé par `FoodHistory.normalize`). `SavedFoodRepository` l'expose en modèles simples ;
  les repas ne le lisent jamais (un aliment ajouté à un repas est une copie).
- La carte « Importer un repas de mon IA » est dans `importexport/ui` (`ImportMealCard`) et réutilise `ImportViewModel` ;
  `ui/HealthyApp.kt` la passe à `MealsScreen` comme composable, pour que `meals` ne dépende pas de `importexport`.
- Partager un repas : `MealRepository.shareMeal` (portion `SharePortion`), envoyé en fichier par `shareFile`.

## Maintien et tableau de bord

- Les calculs sont dans `:core` (`Maintenance`, testé par `MaintenanceTest`) : Mifflin-St Jeor × niveau
  d'activité, poids de référence = moyenne des pesées des 7 derniers jours (sinon la dernière pesée,
  sinon le poids du profil), couleurs, moyenne sur 7 jours, courbe lissée du poids.
- La moyenne des calories ne compte que les jours où au moins un repas est noté.
- Maintien réel : `MaintenanceEstimator` (`:core`) estime, `MaintenanceProposal` décide quoi afficher.
  `MaintenanceRepository` (`today/data`, qui voit repas et pesées) alimente `MaintenanceViewModel` (`body/ui`,
  utilisé par la carte d'Aujourd'hui et le profil). L'ajustement adopté est ajouté à la formule dans
  `Maintenance.maintenanceKcal` seulement : personne d'autre ne le recalcule.
- Le profil est une seule ligne (`profile`, id = 1) ; une pesée par jour au plus (`weigh_ins`, date unique).

## Sauvegarde

- `BackupWriter` et la lecture du type `backup` sont dans `:core` (`BackupTest`). `BackupRepository`
  copie chaque table de `BackupRepository.TABLES` (parents avant enfants) ; `BackupRepositoryTest`
  vérifie que cette liste couvre toutes les tables de la base et qu'export puis restauration rendent
  exactement les mêmes données.
- **Règle pour les migrations** : comme une vieille sauvegarde doit rester restaurable, une nouvelle
  colonne doit accepter `NULL` ou avoir une valeur par défaut. Sinon, il faut ajouter une conversion
  dans `BackupRepository.restore` et un test.
- Le fichier est écrit par le sélecteur de fichiers d'Android (`CreateDocument`) : aucune permission,
  aucun réseau. La sauvegarde cloud d'Android reste désactivée.

## Bilans d'entraînement et repas (export)

- `TrainingReportWriter` (`:core`) écrit le type `training_report` à partir de modèles simples ; son
  résultat est comparé à `docs/examples/bilan-entrainement.json` par `TrainingReportWriterTest`.
- `MealReportWriter` (`:core`) écrit le type `meal_report`, comparé à `docs/examples/bilan-repas.json` par
  `MealReportWriterTest`. `importexport/data/MealReportRepository` réunit repas, eau et profil (le paquet
  `meals` ne dépend pas de `body`) ; Repas le reçoit sous forme de fonction par `AppContainer`.
- Les séries faites (`done_values`, texte séparé par des virgules) ne se lisent et ne s'écrivent que par
  `DoneValues` (`:core`).
- L'export est un partage Android de texte (`ACTION_SEND`) : aucune permission, aucun réseau.

## Séance guidée

- Le déroulé (étapes, séries, repos) est dans `:core` (`SessionRun`), sans Android.
- La séance en cours est gardée dans le `SavedStateHandle` du ViewModel (`SessionRun.encode`) : si
  Android ferme l'app en arrière-plan, on reprend à la même étape. Le schéma de la base n'est pas touché.
- Les comptes à rebours se calculent à partir d'une échéance (`SystemClock.elapsedRealtime`), jamais
  en soustrayant des secondes une à une.
- Le bouton retour demande toujours confirmation ; « Terminer maintenant » enregistre une séance écourtée.

- Vidéos d'exercices : le bouton « Voir la vidéo » confie le lien `https` du plan à une autre app
  (`ACTION_VIEW`, YouTube ou le navigateur). L'app ne télécharge ni n'affiche rien elle-même, ce n'est
  donc pas un accès réseau de l'app ; sans internet, les consignes écrites suffisent.

## Rappel de séance

- `SessionReminderScheduler` programme une alarme AlarmManager **non exacte** (`setAndAllowWhileIdle`) :
  pas de permission d'alarme exacte, Android peut la décaler de quelques minutes. Chaque rappel
  programme le suivant ; `SessionReminderReceiver` reprogramme aussi au redémarrage du téléphone et
  à la mise à jour de l'app (`RECEIVE_BOOT_COMPLETED`).
- La permission `POST_NOTIFICATIONS` (Android 13 et plus) n'est demandée qu'au moment où l'on active
  le rappel dans Réglages.
- Le réglage (activé, heure) est dans les préférences Android, pas dans Room : le schéma ne change pas.
- La décision « rappeler ou non » est une fonction pure (`sessionToRemind`), testée dans `SessionReminderTest`.

## Notifications de motivation

- Même mécanique que le rappel de séance : `MotivationScheduler` programme deux alarmes non exactes
  (12 h 30 et 19 h, `MotivationSlot`), chacune reprogramme la suivante, et `MotivationReceiver`
  reprogramme au redémarrage et à la mise à jour de l'app.
- `MotivationRepository` lit la journée (repas, eau, séance prévue et faite) ; le choix de la situation
  et les textes sont dans `:core` (`Motivation`, `MotivationTexts`, testés par `MotivationTest`).
- Le ton (ou « désactivées ») est dans les préférences Android, pas dans Room ni dans la sauvegarde.

## Signature de l'APK

Android refuse de mettre à jour une app signée avec une autre clé : il y a donc **une seule clé stable**,
créée le 11/11/2026.

- Elle n'est **pas dans le dépôt** (choix du propriétaire, 23/11/2026, pour pouvoir publier le code) : le fichier
  `healthy.jks` (alias `healthy`) et ses mots de passe sont des **secrets de GitHub Actions** (Settings >
  Secrets and variables > Actions) : `ANDROID_KEYSTORE_BASE64` (le keystore encodé en base64),
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Le workflow recrée le fichier
  dans un dossier temporaire et le passe à Gradle par `HEALTHY_KEYSTORE_FILE`, `HEALTHY_KEYSTORE_PASSWORD`,
  `HEALTHY_KEY_ALIAS` et `HEALTHY_KEY_PASSWORD`.
- Le propriétaire garde une copie du keystore et des mots de passe **hors de GitHub** : si les secrets disparaissent,
  plus aucune mise à jour n'est possible sans elle.
- **Ne jamais la régénérer ni la remplacer** : les téléphones déjà installés ne pourraient plus être
  mis à jour sans désinstaller l'app (et perdre les données).
- Ce dépôt est **public** et part d'un seul commit propre (23/11/2026), sans la clé ni le lien Drive. Rien de
  tout cela ne doit y entrer : un scan de secrets est fait avant tout changement de visibilité ou publication.
- `.gitignore` bloque toute clé (`*.jks`, `*.keystore`, `signing.properties`, `app/signing/`).
- Sans ces variables (build local ou fork, sans les secrets), `debug` utilise la clé de debug
  d'Android et `release` reste non signé : seul GitHub Actions, avec les secrets, produit l'APK à installer.
  Il publie l'APK `release` (artefact `healthy-apk`) et refuse un APK dont l'empreinte n'est pas celle
  ci-dessous (écrite dans le workflow : une empreinte est publique, elle ne permet pas de signer).
- Empreinte SHA-256 du certificat :
  `41:0B:C7:35:C1:E2:D3:18:40:55:DD:D3:37:35:C8:72:30:69:98:EE:FE:B0:04:40:F7:F7:8F:18:68:69:41:86`

## Réseau

Seule utilisation autorisée : la recherche d'aliments Open Food Facts (`FoodSearchRepository`, hôtes
`search.openfoodfacts.org` d'abord, `world.openfoodfacts.org` en secours). Seul le texte recherché est envoyé ; aucune donnée personnelle ne sort du
téléphone (sauvegarde cloud Android désactivée). La lecture de la réponse est dans `:core`
(`OpenFoodFacts`, testée). Règle vérifiée à la relecture.

`world.openfoodfacts.org` répond souvent « 503 indisponible » ou très lentement (mesuré le 11/11/2026),
d'où les deux adresses essayées dans l'ordre (`OpenFoodFacts.searchUrls`) : 8 s de délai sur la première,
qui répond en moins d'une seconde quand elle va bien, puis 30 s de lecture sur la seconde (`timeoutsFor`).

Statut des deux recherches (documentation de l'API Open Food Facts, lue le 11/11/2026) : la recherche
plein texte `/cgi/search.pl` est marquée « legacy », la documentation renvoie vers Search-a-licious
(`search.openfoodfacts.org`) pour ce besoin ; elle n'est pas présentée comme « bêta », d'où l'ordre choisi.
Limite : 10 recherches par minute et par adresse IP (donc par téléphone). On ne cherche donc jamais
pendant la frappe, seulement quand l'utilisateur lance la recherche. Chaque panne a sa raison
(`FoodSearchFailure` : serveur injoignable, délai dépassé, code HTTP, réponse illisible, autre) et l'écran
affiche un message simple plus une ligne « Détail : … ». On ne dit « vérifie ta connexion » que si le
serveur est injoignable.

### Scan du code-barres

Même hôte `world.openfoodfacts.org`, pour la fiche d'un produit (`/api/v2/product/<code>.json`, champs limités,
`FoodSearchRepository.lookupProduct`) : une seule requête par produit jamais vu, où seul le code est envoyé.
La lecture est dans `:core` (`OpenFoodFacts.parseProductResponse`, `Barcodes`, testés). Un produit déjà retenu
(`scanned_products`, v11, dans la sauvegarde) ne fait aucune requête. Le lecteur est intégré : CameraX
(aperçu, `BarcodeCamera`) et ZXing (`BarcodeAnalyzer`), tous deux sans réseau ni télémétrie, licences dans
`licenses/barcode-scanner-Apache-2.0.txt`. Guava n'est là que pour le type `ListenableFuture` de CameraX.
Permission `CAMERA` (matériel facultatif), demandée au premier scan.

### Mises à jour (dossier Google Drive)

Deuxième et dernière utilisation du réseau (choix du propriétaire, 13/11/2026) : `update/`. Le propriétaire dépose dans
un dossier Drive partagé par lien (lecture) l'APK publié par GitHub Actions, nommé `healthy-<numéro>.apk`
(le numéro de build, qui est aussi le `versionCode`). Ce numéro est le numéro d'exécution GitHub + 1000 (voir le workflow) : les
téléphones installés viennent de l'ancien dépôt privé (numéros jusqu'à environ 170), et un numéro plus petit serait refusé par
Android. Ne jamais baisser ce décalage. L'app :

1. lit la page publique du dossier `drive.google.com/embeddedfolderview?id=…`, sans compte ni clé
   (`DriveFolder.parseListing` dans `:core`, testé) ; si Google change cette page, la vérification
   échoue sans gêner le reste de l'app ;
2. prend le plus grand numéro au-dessus du sien (`AppUpdate.latest`) ;
3. télécharge l'APK (`drive.usercontent.google.com/download?…&confirm=t`) dans le cache `updates/`,
   vérifie avec Android qu'il s'agit bien de `com.bidaboss.healthy` au numéro annoncé, puis ouvre
   l'installateur d'Android via le `FileProvider` (`res/xml/update_paths.xml`).

Android demande toujours de confirmer l'installation et, une fois, d'autoriser Healthy à installer des
applications (`REQUEST_INSTALL_PACKAGES`). Il refuse un APK signé avec une autre clé : la clé unique
protège donc aussi cette voie. Vérification silencieuse à chaque retour au premier plan
(`LifecycleStartEffect` dans `HealthyApp`), au plus toutes les 30 min ; à la demande dans Réglages. Rien n'est envoyé sauf l'identifiant du dossier dans l'adresse.
Le lien est `UPDATE_FOLDER_LINK` : un secret de GitHub Actions injecté au build (variable d'environnement lue par `app/build.gradle.kts`), donc absent du dépôt ; sans lui (build local) il est vide et l'app dit qu'aucun dossier n'est configuré. Il n'est ni affiché ni modifiable dans l'app. Seuls les
messages d'erreur écrits par l'app (`UpdateException`) sont montrés, jamais ceux du système, qui
peuvent contenir l'adresse du dossier.

**Publication automatique** (choix du propriétaire, 23/11/2026, même méthode que son projet Familia). Après un push sur
`main`, l'étape « Déposer l'APK sur le Drive » du workflow lance `scripts/publish-apk.mjs`. Un compte de service Google
(secret `DRIVE_SERVICE_ACCOUNT_JSON`, donné à cette seule étape) est Éditeur du dossier. Il n'a pas d'espace Drive : il ne
peut pas créer de fichier, seulement remplacer le contenu de l'APK au plus grand numéro, puis le renommer
`healthy-<numéro>.apk`. Le propriétaire a donc déposé une première fois un APK à la main. Le lien du dossier ne change pas
et reste secret. Sans le secret, l'étape est sautée (le CI reste vert) ; elle échoue si le dossier n'a pas de
`healthy-<numéro>.apk` ou si le numéro n'est pas plus récent. Les autres APK du dossier ne sont pas touchés.

**Vérification sans ouvrir l'app** (demande du propriétaire, 23/11/2026) : `UpdateCheckScheduler` programme chaque jour vers 10 h
une alarme non exacte (comme les rappels : elle programme la suivante, et le redémarrage ou la mise à jour de l'app la
reprogramme). `UpdateCheckReceiver` fait la même vérification que l'app (`UpdateRepository.checkAndRecord`) et, s'il y a une
version plus récente jamais annoncée (`UpdateStore.notifiedVersionCode`), envoie une notification qui ouvre l'app. Sans réseau,
rien ne s'affiche, on réessaie le lendemain. Sans lien de dossier (build local), rien n'est programmé. La version trouvée est
gardée (`UpdateStore.availableVersionCode`) : `UpdateBanner`, en haut de l'app, la montre tant qu'elle n'est pas installée,
même après « Plus tard » ou sans permission de notification ; un appui ouvre la fenêtre de mise à jour. Aucun nouveau
domaine réseau : la notification ne contient jamais le lien du dossier.

Téléchargement limité à 100 Mo. L'heure de la vérification automatique n'est notée qu'après une
vérification réussie : sans réseau, on réessaie au retour suivant. « Plus tard » met la version de côté
24 h (`UpdateStore.snoozedVersionCode`) ; le bouton de Réglages la montre toujours.

### Trajets : calcul de distance (OpenStreetMap)

Troisième et dernière utilisation du réseau (choix du propriétaire, 23/11/2026, lot 3 de la refonte) : `workout/data/route/`.
Un trajet enregistré (« Mes trajets ») a un départ, 0 à n étapes et une arrivée, saisis comme adresses. Sa distance se
calcule **une seule fois**, quand l'utilisateur appuie sur « Calculer la distance », puis elle est enregistrée avec le
trajet : ensuite tout marche hors ligne.

Services, gratuits et sans clé ni compte (deux hôtes, tous deux gérés par la communauté OpenStreetMap) :

1. `nominatim.openstreetmap.org/search` : transforme chaque adresse en point GPS. Une requête par adresse, au plus une
   par seconde (règle d'usage de Nominatim), `User-Agent` générique `Healthy-app/<version de l'app>` (jamais un nom, un e-mail ni un
   identifiant de l'utilisateur), `format=jsonv2`, `limit=1`, aucun détail demandé.
2. `routing.openstreetmap.de/routed-bike/route/v1/driving/<lon,lat;…>` (OSRM, profil vélo) : renvoie la distance par la
   route (`overview=false`, rien d'autre). Une requête par trajet, qui n'envoie que les coordonnées des points.

Ce qui sort du téléphone : le texte des adresses saisies (vers Nominatim) puis leurs coordonnées (vers le serveur de
calcul), avec l'adresse IP du téléphone, comme toute requête web. Rien d'autre : ni nom du trajet, ni profil, ni
historique d'activités. Les adresses sont enregistrées **en local seulement** (table `saved_routes`) et ne sont jamais
envoyées ailleurs ; elles sont dans la sauvegarde du propriétaire (fichier qu'il garde lui-même) comme le reste des
données. Une adresse qui change ne déclenche rien : l'utilisateur doit rappuyer sur « Calculer la distance ».

Secours hors ligne : le calcul est facultatif. Sans internet (ou si un service refuse), l'écran dit simplement que le
calcul est impossible pour l'instant et propose « Saisir la distance moi-même » ; un trajet à distance saisie à la main est un
trajet comme un autre. Chaque panne a sa raison (adresse introuvable, serveur injoignable, délai dépassé, code HTTP,
réponse illisible) et un message simple, comme pour Open Food Facts. Délai de 10 s par requête. Ces services n'ont aucune garantie de
disponibilité : l'app n'en dépend jamais pour fonctionner. La lecture des réponses est dans `:core`
(`RouteGeo`, testée avec des réponses d'exemple) ; aucune bibliothèque de cartes, aucun SDK, aucune carte affichée.

## Aperçus d'écrans

`ScreenshotsTest` (Robolectric, rendu natif) lance l'app avec un profil fictif et dessine les 5 onglets dans les 3
thèmes (écran de 390 dp de large, 1500 dp de haut). Il ne vérifie que « rien ne plante » ; les PNG ne sont écrits que si
on donne un dossier : `SCREENSHOTS_OUT=/chemin ./gradlew :app:testDebugUnitTest --tests '*ScreenshotsTest'` (ou
`-Dscreenshots.out`). Servent à la relecture du design avant fusion. Les dépendances `ui-test` ne sont que dans les
tests et le manifeste de debug.

## Contrôles automatiques (GitHub Actions)

Tests de `:core`, tests de `:app`, lint Android, construction de l'APK, à chaque push.
