# Healthy - règles du dépôt

À lire avant tout travail sur ce dépôt.

## Le projet

App Android personnelle (le propriétaire + quelques proches, APK partagé à la main) : journal des repas avec
calories, plan sportif au poids du corps, tableau de bord. Pas d'IA ni de clé d'API dans l'app :
Claude prépare des fichiers (repas, plan) dans les fils du projet, l'app les **importe**.

## Documents de référence (une règle = un seul endroit)

- [`docs/format-import.md`](docs/format-import.md) : format des fichiers importés. C'est un contrat :
  le modifier demande de mettre à jour les exemples, le lecteur et les tests dans le même changement.
- [`docs/architecture.md`](docs/architecture.md) : organisation du code et règles techniques.
- [`docs/decisions.md`](docs/decisions.md) : décisions produit, par thème, à l'état actuel.
- [`docs/prd/`](docs/prd/) : PRD des fonctions à venir, validés par le propriétaire (fil [Produit]). Une fois la
  fonction livrée, ses règles vont dans `decisions.md`, qui fait foi.

## Règles courtes

- Stack : Kotlin, Jetpack Compose (Material 3), Room, injection manuelle (`AppContainer`). Pas de Hilt.
- Toute l'interface est en **français simple** ; le code (noms, identifiants) est en anglais,
  les commentaires en français.
- Deux modules : `:core` (Kotlin pur, format d'import) et `:app` (Android). Paquets par fonctionnalité
  dans `:app` (`meals`, `workout`, `today`, `settings`, `importexport`, `update`, `health`), chacun avec `data/` et `ui/`.
- `ui` dépend de `data`, jamais l'inverse. Aucun écran n'utilise une entité ou un DAO Room :
  on passe par un repository et un ViewModel.
- Room : `exportSchema = true`, une vraie migration à chaque changement de schéma,
  jamais `fallbackToDestructiveMigration`.
- Réseau : uniquement Open Food Facts, le dossier Google Drive des mises à jour (lecture seule, sans
  compte) et, pour le seul calcul de la distance d'un trajet à vélo, OpenStreetMap (voir `docs/architecture.md`,
  « Trajets : calcul de distance »). Tout le reste fonctionne hors ligne.
- Signature : une seule clé, stockée dans les **secrets de GitHub Actions** (voir `docs/architecture.md`,
  « Signature de l'APK »), jamais dans le dépôt. Ne jamais la remplacer ni la régénérer. Aucun secret, clé,
  lien privé ni donnée personnelle réelle (profil, repas) dans le dépôt, ni dans les tests ni les exemples.
- Ce dépôt est public et part d'un seul commit propre (23/11/2026). Ne jamais y commiter la clé, un mot de passe,
  le lien du dossier Drive ni une donnée personnelle réelle ; ils restent dans les secrets de GitHub Actions.
- `applicationId` (`com.bidaboss.healthy`) ne change jamais.

## Commandes

```bash
./gradlew :core:test                 # tests du format d'import (sans SDK Android)
./gradlew :app:testDebugUnitTest     # tests de l'app
./gradlew :app:lintDebug             # lint Android
./gradlew :app:assembleRelease       # APK dans app/build/outputs/apk/release/
```

GitHub Actions (`.github/workflows/android.yml`) lance tout cela à chaque push et publie l'APK.
