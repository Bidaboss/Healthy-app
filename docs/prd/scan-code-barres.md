# PRD : scanner le code-barres d'un produit

**Version** : 1.0, validée par le propriétaire le 2026-11-22
**Date** : 2026-11-22
**Auteur** : fil [Produit]
**Clarté du besoin** : 92/100

---

## Résumé

Aujourd'hui, un produit emballé (barre, yaourt, biscuits, plat préparé) s'ajoute à un repas en le
cherchant par son nom dans Open Food Facts, ou en tapant ses valeurs. C'est lent, et la recherche par nom
renvoie souvent le mauvais produit ou une autre marque.

On ajoute un scan du code-barres : on vise le paquet, l'app retrouve le produit exact sur Open Food Facts
et l'ajoute au repas avec la portion du paquet, modifiable. Les produits scannés sont retenus sur le
téléphone : la deuxième fois, ils reviennent tout de suite, même sans internet. Un produit inconnu
d'Open Food Facts se saisit une fois à la main depuis l'étiquette, puis il est retenu lui aussi.

C'est surtout utile aux proches du propriétaire, qui n'envoient pas de photos à Claude : pour eux, la saisie
manuelle est le seul chemin, et le scan la rend rapide.

Cette fonction remplace la décision « pas de lecture de code-barres pour l'instant » de
`docs/decisions.md`.

---

## Problème

**Aujourd'hui** : trouver un produit par son nom prend plusieurs essais (fautes, marques, formats). Les
valeurs d'un même produit racheté chaque semaine sont recherchées à chaque fois. Sans internet (cantine,
magasin, sous-sol), on ne peut que taper les valeurs.

**Solution** : un bouton « Scanner » dans l'éditeur de repas et un raccourci dans l'onglet Repas. Lecture
du code par la caméra, dans l'app, sans réseau ; une seule requête à Open Food Facts avec le code ;
produit retenu sur le téléphone.

**Effet attendu** : un produit emballé ajouté en un geste, des valeurs exactes (celles du paquet) plutôt
qu'estimées, et moins d'abandons de la saisie chez les proches.

---

## Mesures de succès

Pas de statistiques dans l'app (rien ne sort du téléphone) : on vérifie à l'usage, avec le propriétaire.

- **Rapidité** : un produit connu d'Open Food Facts est ajouté en moins de 15 secondes, du bouton
  « Scanner » à l'aliment dans le repas. Un produit déjà scanné, en moins de 8 secondes.
- **Taux de réussite** : sur 20 produits du placard du propriétaire, au moins 16 sont trouvés avec des
  calories (Open Food Facts couvre bien les produits vendus en France).
- **Usage** : après deux semaines, la recherche par nom n'est plus utilisée pour les produits emballés
  (retour du propriétaire et d'un proche).

---

## Utilisateurs

### Le propriétaire
- Note surtout ses repas par photos envoyées à Claude, mais ajoute à la main les collations et produits
  emballés.
- Veut aller vite, accepte de corriger une quantité.

### Les proches (par exemple Alexandrine)
- N'utilisent pas Claude : toute leur saisie est manuelle.
- Peu à l'aise avec la technique ; il faut un bouton évident et des messages en français simple.

---

## Histoires et critères d'acceptation

### 1. Scanner un produit dans un repas

**En tant que** personne qui note un repas, **je veux** scanner le code-barres d'un produit **pour**
l'ajouter sans le chercher par son nom.

- [ ] Dans l'éditeur de repas, un bouton « Scanner » est à côté de « Rechercher un aliment ».
- [ ] Il ouvre la caméra en plein écran avec un cadre de visée, une lampe à allumer et « Annuler ».
- [ ] Le code est lu sans toucher l'écran (EAN-13, EAN-8, UPC-A, UPC-E), avec une petite vibration.
- [ ] L'app affiche le produit : nom, marque, calories pour 100 g, et la quantité proposée.
- [ ] « Ajouter » met l'aliment dans le repas, source `openfoodfacts`, avec calories, protéines,
      glucides, lipides et fibres calculés pour la quantité.
- [ ] Ensuite, l'aliment se comporte comme les autres : quantité modifiable avec recalcul, puces ½, ¾,
      ×1,5, correction à la main.

### 2. Quantité proposée

**En tant que** personne pressée, **je veux** que la portion du paquet soit déjà remplie **pour** ne
rien taper dans le cas courant.

- [ ] Si Open Food Facts donne une portion en grammes (ou mL, comptés comme des grammes), elle est
      proposée : « 1 portion (30 g) ».
- [ ] Sinon, 100 g sont proposés.
- [ ] Le champ grammes reste modifiable avant d'ajouter.

### 3. Produit introuvable ou incomplet

**En tant que** personne qui scanne un produit inconnu, **je veux** saisir ses valeurs une seule fois
**pour** le retrouver au prochain scan.

- [ ] Si Open Food Facts ne connaît pas le code, ou n'a pas les calories, l'app dit « Produit inconnu
      d'Open Food Facts » et propose « Saisir l'étiquette ».
- [ ] Le formulaire demande le nom, les valeurs pour 100 g (calories obligatoires ; protéines, glucides,
      lipides, fibres facultatives) et une portion facultative. Les valeurs déjà connues d'Open Food
      Facts sont préremplies.
- [ ] Le produit est ajouté au repas (source `manuel`) et retenu sous ce code.
- [ ] Rien n'est envoyé à Open Food Facts (pas de contribution depuis l'app).

### 4. Produits retenus et hors ligne

**En tant que** personne qui rachète les mêmes produits, **je veux** qu'un produit déjà scanné revienne
tout de suite **pour** l'ajouter même sans internet.

- [ ] Tout produit scanné (trouvé ou saisi) est gardé sur le téléphone avec son code.
- [ ] Au scan, l'app regarde d'abord le téléphone : un produit retenu s'affiche sans réseau, avec
      « Déjà scanné ».
- [ ] Un produit retenu venant d'Open Food Facts peut être « Mis à jour depuis Open Food Facts » à la main
      (pas de mise à jour automatique).
- [ ] Sans internet et pour un produit jamais vu : « Pas de connexion. Tu peux saisir l'étiquette ou
      réessayer plus tard. »
- [ ] Une liste « Mes produits scannés » (dans « Mes plats » ou à côté) permet de voir, corriger et
      supprimer un produit retenu.
- [ ] Les produits retenus sont dans la sauvegarde et la restauration.

### 5. Raccourci depuis l'onglet Repas

**En tant que** personne qui mange une collation, **je veux** scanner sans ouvrir un repas **pour** aller
plus vite.

- [ ] L'onglet Repas a un bouton « Scanner un produit ».
- [ ] Après « Ajouter », le produit va au repas d'aujourd'hui du type de l'heure s'il a commencé il y a
      moins de 3 h (même règle que les boissons) ; sinon un nouveau repas de ce type est créé.
- [ ] Un message court confirme : « Ajouté au déjeuner » avec « Annuler ».

### 6. Caméra refusée

- [ ] Android demande l'accès à la caméra au premier scan seulement, avec une phrase qui dit pourquoi.
- [ ] Si l'accès est refusé : message simple, bouton « Ouvrir les réglages », et la recherche par nom
      reste disponible.

---

## Règles fonctionnelles

- Lecture du code sur le téléphone, sans réseau.
- Une seule requête par scan d'un produit non retenu : la fiche produit d'Open Food Facts par son code,
  en ne demandant que les champs utiles (nom, marque, portion, valeurs pour 100 g). Seul le code est
  envoyé. Pas de photo du produit (pas d'autre hôte).
- Valeurs lues : `energy-kcal_100g` (ou kJ converti si les kcal manquent), `proteins_100g`,
  `carbohydrates_100g`, `fat_100g`, `fiber_100g`, `serving_quantity`. Une valeur absente reste vide,
  sauf les calories, obligatoires.
- Un même code scanné deux fois de suite pendant la visée n'est lu qu'une fois.
- Les produits retenus gardent des valeurs **pour 100 g**. La règle « rien n'est enregistré pour 100 g »
  de `docs/decisions.md` reste vraie pour les aliments des repas : un aliment ajouté garde ses valeurs
  calculées, et corriger ensuite le produit retenu ne change pas les repas passés (comme « Mes plats »).

### Hors du périmètre

- Contribuer à Open Food Facts (ajouter un produit ou une photo).
- Nutri-Score, NOVA, additifs, allergènes, sucres et sel (déjà exclus).
- Scanner une photo de la galerie ; scanner plusieurs produits d'affilée sans revenir à l'aperçu.
- Bouton « Scanner » sur le widget (écarté par le propriétaire, 22/11/2026).
- Code-barres dans le format d'import des repas.

---

## Contraintes techniques

### Réseau et vie privée
- Respecte la règle du dépôt : réseau uniquement vers Open Food Facts, sur les hôtes déjà utilisés.
- Le lecteur de code-barres est **intégré à l'app** (choix du propriétaire, 22/11/2026) : il marche dès
  l'installation, hors ligne, sans services Google. Contrainte pour [Dev] : bibliothèque sous licence
  libre et sans aucun appel réseau ni télémétrie (par exemple CameraX + ZXing ; ML Kit à éviter s'il
  envoie des données d'usage). Licence ajoutée dans `licenses/`.
- Nouvelle permission `CAMERA`, demandée au premier scan seulement.

### Performance
- Code lu en moins de 2 s sur un paquet bien éclairé.
- Produit retenu affiché en moins de 0,5 s ; requête Open Food Facts avec le même principe de délais et
  de messages de panne que la recherche (`FoodSearchFailure`).
- Augmentation de l'APK : quelques Mo au plus.

### Données
- Nouvelle table pour les produits retenus (code, nom, marque, valeurs pour 100 g, portion, origine
  Open Food Facts ou saisie, date de dernière utilisation) : nouvelle version de la base avec une vraie
  migration, schéma exporté, test de migration.
- Inclus dans la sauvegarde ; la restauration d'une ancienne sauvegarde sans cette table marche.

### Architecture
- Lecture de la réponse « fiche produit » dans `:core` (`OpenFoodFacts`), testée, comme la recherche.
- Paquet `meals` : repository des produits retenus, ViewModel, écran de scan ; aucun écran n'utilise
  Room directement.

---

## Découpage

### Phase 1 (une PR)
- Scan dans l'éditeur de repas, fiche produit, quantité proposée, ajout.
- Produits retenus (trouvés et saisis), hors ligne, saisie de l'étiquette.
- Caméra refusée.

### Phase 2 (une PR)
- Raccourci « Scanner un produit » dans l'onglet Repas.
- Liste « Mes produits scannés » (voir, corriger, supprimer, mettre à jour) et sauvegarde.

### Plus tard
- Bouton sur le widget, si l'usage le justifie.
- Scan dans l'éditeur de « Mes plats » pour les ingrédients.

---

## Risques

| Risque | Probabilité | Impact | Parade |
|---|---|---|---|
| Produit absent ou incomplet sur Open Food Facts | Moyenne | Moyen | Saisie de l'étiquette, retenue sous le code |
| Open Food Facts lent ou en panne (déjà mesuré) | Moyenne | Faible | Produits retenus d'abord, messages de panne existants |
| Lecteur lent sur un vieux téléphone ou en faible lumière | Moyenne | Moyen | Lampe, cadre de visée, test sur le téléphone d'un proche |
| Bibliothèque qui appelle le réseau en cachette | Faible | Élevé | Choix d'une bibliothèque libre, vérifié à la relecture [Archi] |
| Valeurs d'Open Food Facts fausses | Faible | Faible | Valeurs visibles avant d'ajouter, correction à la main |

---

## Dépendances

- Le fil [Dev] implémente ; [Archi] relit (réseau, licence, migration).
- À consigner dans `docs/decisions.md` (Repas et calories) et `docs/architecture.md` (Réseau,
  permission caméra) dans la même PR.
- Test sur le téléphone du propriétaire et d'un proche une fois les deux phases livrées.
