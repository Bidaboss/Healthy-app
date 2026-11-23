# Healthy

App Android personnelle : journal des repas et des calories, plan sportif à la maison, tableau de bord.

- Règles du dépôt : [`CLAUDE.md`](CLAUDE.md)
- Format des fichiers d'import : [`docs/format-import.md`](docs/format-import.md)
- Architecture : [`docs/architecture.md`](docs/architecture.md)
- Décisions produit : [`docs/decisions.md`](docs/decisions.md)

## Récupérer l'APK

Sur GitHub, onglet **Actions**, ouvrir la dernière exécution réussie, puis télécharger
`healthy-apk` en bas de page. L'APK est toujours signé avec la même clé : une nouvelle version
s'installe par-dessus l'ancienne en gardant les données.

## Construire soi-même

`./gradlew :core:test :app:testDebugUnitTest` fonctionne sans rien configurer. La clé de signature et le
lien du dossier de mises à jour sont des secrets de GitHub Actions (voir `docs/architecture.md`) : sans eux,
un build local ou un fork, sans les secrets, ne produit pas d'APK `release` signé avec la clé de l'app.
