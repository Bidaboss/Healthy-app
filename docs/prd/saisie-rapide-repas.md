# PRD : saisie des repas plus rapide

**Version** : 1.0, validée par le propriétaire le 2026-11-22
**Date** : 2026-11-22
**Auteur** : fil [Produit]
**Clarté du besoin** : 91/100

---

## Résumé

Aujourd'hui, ajouter un aliment demande de le chercher sur Open Food Facts à chaque fois, même pour
ce qu'on mange tous les jours, et la recherche est lente et limitée à 10 requêtes par minute. Les
favoris, « Récents », « Refaire » et « Mes plats » aident, mais ne couvrent que des repas entiers.

Ce PRD ajoute trois choses, toutes locales, sans nouveau réseau :
1. la recherche d'aliment regarde d'abord **ce que tu as déjà mangé** ;
2. une **suggestion de repas habituel** selon l'heure, sur Aujourd'hui et dans un nouveau repas ;
3. un **repas en calories seules** pour ceux qu'on ne détaille pas.

Il complète le scan de code-barres (`docs/prd/scan-code-barres.md`) : un produit scanné entre dans
l'historique comme les autres.

---

## Problème

**Aujourd'hui** : chaque aliment se recherche ou se tape. Un repas déjà mangé se refait en entier
(« Refaire », favoris), mais pas un seul aliment ni une habitude du matin. Un repas au restaurant ne peut
pas être noté sans inventer des aliments.

**Effet attendu** : moins de saisie pour l'habituel, plus de repas notés, donc un bilan plus fiable (et
un maintien réel plus fiable, voir le PRD suivant).

---

## Mesures de succès

Rien ne sort du téléphone : on vérifie à l'usage avec le propriétaire.

- Un aliment déjà mangé s'ajoute en moins de 5 secondes (quelques lettres, un toucher).
- Un petit-déjeuner habituel s'ajoute en un toucher depuis Aujourd'hui.
- Après deux semaines, le propriétaire n'utilise plus la recherche Open Food Facts que pour les aliments nouveaux.
- Les repas notés par jour augmentent pour les proches qui ne passent pas par Claude (retour du propriétaire).

---

## Utilisateurs

- **Le propriétaire** : mange souvent la même chose au petit-déjeuner et au déjeuner, veut aller vite.
- **Les proches** : saisie 100 % manuelle, besoin d'un geste simple et de français simple.

---

## Histoires et critères d'acceptation

### 1. Mes aliments d'abord

**En tant que** personne qui ajoute un aliment, **je veux** retrouver ceux que j'ai déjà mangés **pour**
ne pas les chercher sur Open Food Facts.

- [ ] En ouvrant la recherche d'aliment, une liste « Déjà mangés » montre les aliments récents et les
      plus fréquents (les 28 derniers jours d'abord), chacun avec sa **dernière quantité**.
- [ ] En tapant, la liste se filtre tout de suite (sans réseau, sans attendre une validation).
- [ ] Sous cette liste, un bouton « Chercher aussi sur Open Food Facts » lance la recherche existante,
      seulement quand on le touche (la limite de 10 requêtes par minute est respectée).
- [ ] Toucher un aliment l'ajoute au repas avec sa dernière quantité, valeurs recalculées au prorata ;
      quantité, puces ½ ¾ ×1,5 et correction restent comme aujourd'hui.
- [ ] Le même aliment saisi plusieurs fois n'apparaît qu'une fois (regroupé par nom, insensible à la
      casse et aux accents).
- [ ] Les produits scannés (PRD scan) et « Mes plats » y figurent aussi.
- [ ] Sans historique (nouveau téléphone), la liste est remplacée par le comportement actuel.

### 2. Suggestion de repas habituel

**En tant que** personne qui mange souvent pareil, **je veux** qu'on me propose mon repas habituel
**pour** le noter en un toucher.

- [ ] La suggestion se calcule sur les 28 derniers jours : même type de repas (petit-déjeuner, déjeuner,
      dîner, collation), commencé à ±1 h 30 de l'heure actuelle, même contenu ; le plus fréquent gagne,
      à partir de **2 occurrences** au moins.
- [ ] **Sur Aujourd'hui** : une carte « Ton petit-déjeuner habituel ? » montre le titre et les calories ;
      « Ajouter » l'enregistre pour maintenant (nouveau repas, sans `import_id`), « Pas aujourd'hui »
      la masque jusqu'au lendemain.
- [ ] La carte n'apparaît pas si un repas de ce type est déjà noté aujourd'hui, ni sans suggestion fiable.
- [ ] **Dans un nouveau repas** : les suggestions sont en haut, avec Favoris et Récents (jusqu'à 3).
- [ ] Un repas ajouté ainsi s'ouvre dans l'éditeur pour être corrigé, comme « Refaire ce repas ».
- [ ] Aucune notification : la suggestion n'existe que dans l'app.

### 3. Repas en calories seules

**En tant que** personne au restaurant, **je veux** noter un repas par ses calories **pour** ne pas
inventer des aliments.

- [ ] Dans un nouveau repas, un bouton « Juste les calories » demande un nom (facultatif : « Repas
      au restaurant » par défaut) et un nombre de calories.
- [ ] Le repas est créé avec un seul aliment (source manuelle) ; protéines, glucides, lipides et fibres
      restent **vides** ; la pastille « à vérifier » est affichée.
- [ ] Les calories comptent dans le total, l'anneau et la moyenne sur 7 jours.
- [ ] Les barres de protéines et de fibres ne comptent rien pour ce repas, et le jour affiche un petit
      texte « 1 repas sans détail » pour expliquer l'écart.
- [ ] Le repas se complète plus tard : ajouter des valeurs à la main retire la pastille (règle existante).
- [ ] Le bilan repas envoyé à Claude marque ces repas (`macros` absentes), pour qu'il n'en tire pas de
      fausses conclusions sur les protéines.

---

## Règles fonctionnelles

- Tout est calculé **sur le téléphone** à partir des repas enregistrés : pas de nouvelle donnée à
  synchroniser, pas de nouveau réseau.
- L'historique « Déjà mangés » se déduit des aliments des repas ; si [Dev] a besoin d'une table (par
  exemple pour la rapidité), elle est reconstruite à partir des repas et ne change pas la sauvegarde.
- Un repas supprimé sort de l'historique ; un aliment corrigé garde sa dernière version.
- Les suggestions respectent le fuseau du téléphone (« date locale »).

### Hors du périmètre

- Notification « ton repas habituel » (écarté, pas de notification de plus).
- Estimation automatique de macros à partir des calories (écartée par le propriétaire, le 22/11/2026 : pas de
  fausses protéines).
- Recherche vocale, saisie d'un repas en langage libre (demanderait une IA dans l'app).
- Suggestions selon la météo, le sport ou le poids.

---

## Contraintes techniques

- Aucun écran n'utilise une entité Room : repository + ViewModel dans le paquet `meals`.
- Si les calories seules demandent des macros vides plutôt que zéro, [Dev] vérifie le modèle des
  aliments et la migration associée (vraie migration, schéma exporté). À défaut, un indicateur « sans
  détail » stocké sur l'aliment.
- Performance : liste « Déjà mangés » affichée en moins de 300 ms avec un an d'historique.
- Format d'import : inchangé. Le bilan repas gagne seulement le marquage des repas sans macros
  (`docs/format-import.md`, exemples, lecteur et tests dans le même changement).

---

## Découpage

### Phase 1 (une PR)
- « Déjà mangés » dans la recherche, avec le bouton Open Food Facts.
- Repas en calories seules.

### Phase 2 (une PR)
- Suggestions de repas habituel (Aujourd'hui et nouveau repas).
- Marquage des repas sans détail dans le bilan repas.

---

## Risques

| Risque | Probabilité | Impact | Parade |
|---|---|---|---|
| Suggestion à côté de la plaque | Moyenne | Faible | Seuil de 2 occurrences, « Pas aujourd'hui », jamais d'ajout automatique |
| Doublons dans l'historique (« Yaourt », « yaourt nature ») | Moyenne | Faible | Regroupement par nom normalisé, sinon deux entrées visibles |
| Moyenne faussée par des repas sans macros | Moyenne | Moyen | Pastille, texte sur le jour, marquage dans le bilan |
| Historique vide chez un nouvel utilisateur | Certaine | Faible | Retour au comportement actuel |

---

## Dépendances

- Le PRD du scan de code-barres (les produits scannés alimentent l'historique) : pas bloquant.
- Le PRD « maintien réel » dépend du nombre de repas notés : cette saisie l'aide.
- [Dev] consigne les règles dans `docs/decisions.md` (Repas et calories) à la livraison.
