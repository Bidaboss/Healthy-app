# PRD : les liquides des repas comptent dans l'eau bue

**Version** : 1.0, validée par le propriétaire le 2026-11-23
**Date** : 2026-11-23
**Auteur** : fil [Produit]
**Clarté du besoin** : 92/100

---

## Résumé

Aujourd'hui l'eau bue ne contient que ce qu'on ajoute avec les boutons de l'écran Eau. Un verre de lait au
petit-déjeuner, un chocolat chaud, une soupe ou un jus ajoutés à un repas ne comptent nulle part dans
l'hydratation, et il faut les retaper dans l'eau. Les boissons rapides de l'app (bière, vin, soda, jus)
sont même exclues de l'eau par règle.

Chaque aliment d'un repas peut désormais être marqué **boisson**. Un aliment boisson non alcoolisé, avec
une quantité, compte dans l'eau bue du jour, à son volume (1 g = 1 mL). L'alcool reste hors de l'eau.
Le repère existant (objectif d'eau, barre, widget, rappels) utilise simplement le nouveau total.

Cette fonction remplace la règle « les boissons des repas ne comptent pas dans l'eau »
de `docs/decisions.md` (Repas et calories, Eau).

---

## Problème

**Aujourd'hui** : on boit du lait, du jus, une soupe ou un café dans un repas, on le note dans le repas
(calories) et il faut le noter une seconde fois dans l'eau, sinon la barre d'eau sous-estime ce qu'on a
bu. Résultat : rappels « tu es en retard » à tort, et moins de repas notés en entier.

**Effet attendu** : une seule saisie pour les calories et l'hydratation, une barre d'eau plus juste.

---

## Mesures de succès

Rien ne sort du téléphone : on vérifie à l'usage avec le propriétaire.

- Un liquide ajouté à un repas apparaît dans l'eau du jour sans autre geste.
- Plus de double saisie pour le lait, le jus, le café ou la soupe.
- Les rappels d'eau ne se déclenchent plus quand le total, repas compris, est suffisant.

---

## Utilisateurs

- **Le propriétaire** : petit-déjeuner au lait et café, soupes, boissons au restaurant, ajoute déjà ses repas.
- **Les proches** : saisie manuelle ; ne doivent rien régler.

---

## Histoires et critères d'acceptation

### 1. Marquer un aliment comme boisson

**En tant que** personne qui note un repas, **je veux** dire qu'un aliment est une boisson **pour** qu'il
compte dans l'eau.

- [ ] Chaque aliment de l'éditeur de repas a un réglage à trois choix : **Aliment** (par défaut),
      **Boisson**, **Alcool**.
- [ ] Un aliment marqué Boisson avec une quantité (en grammes ou mL, 1 mL = 1 g) compte dans l'eau du jour
      pour cette quantité ; il n'est pas compté sans quantité (le réglage l'explique : « Ajoute une
      quantité pour la compter dans l'eau »).
- [ ] Un aliment marqué Alcool ne compte pas dans l'eau (règle du propriétaire : tous les liquides sauf l'alcool).
- [ ] Changer la quantité d'un aliment boisson met l'eau à jour ; supprimer le repas ou l'aliment la retire.
- [ ] Les calories et macros du repas ne changent pas.

### 2. Boissons rapides de l'app

- [ ] Les boutons existants posent le bon réglage : soda et jus de fruits = **Boisson**, bière et vin =
      **Alcool**. Soda et jus comptent donc dans l'eau (changement par rapport à aujourd'hui).
- [ ] Les boutons d'eau, café de machine, tasses et bouteille de l'écran Eau restent inchangés.

### 3. Repas importés depuis Claude

- [ ] Le format d'import accepte un champ facultatif `drink` par aliment : `boisson` ou `alcool`
      (absent = aliment). Claude le remplit pour le lait, le café, le thé, le jus, la soupe, le vin, etc.
- [ ] L'aperçu d'import montre les boissons (icône goutte) ; l'utilisateur peut corriger le réglage avant
      d'enregistrer, comme tout autre champ.
- [ ] Un fichier sans `drink` reste valide et ne compte rien dans l'eau.
- [ ] « Partager ce repas » écrit `drink` pour les aliments concernés.
- [ ] Le prompt repas copié depuis Réglages explique le champ à l'IA.

### 4. Voir d'où vient l'eau

- [ ] L'écran Eau garde la liste des ajouts du jour et y ajoute les boissons des repas, sous la mention
      « Dans les repas » (nom, quantité, repas), sans bouton supprimer : on les modifie depuis le repas.
- [ ] Le total, la barre sur Aujourd'hui, l'historique sur 7 jours, le widget et les rappels d'eau utilisent
      le total repas compris ; aucune boisson n'est comptée deux fois.
- [ ] Un jour sans repas se comporte comme aujourd'hui.

### 5. Anciens repas

- [ ] Les repas déjà enregistrés gardent « Aliment » : rien n'est reclassé automatiquement, pour ne pas
      changer l'historique de l'eau sans qu'on le sache.
- [ ] le propriétaire peut régler un ancien aliment à la main dans l'éditeur ; l'eau du jour concerné se recalcule.

---

## Règles fonctionnelles

- Eau comptée par jour = ajouts de l'écran Eau + aliments Boisson des repas du jour (date locale du repas).
- L'objectif d'eau reste calculé comme aujourd'hui (+ 0,5 L par séance enregistrée).
- Pas de coefficient d'hydratation (un jus ou une soupe comptent à leur volume entier) : repère simple,
  assumé.
- Les sauvegardes et restaurations conservent le réglage de chaque aliment.

### Hors du périmètre

- Reclassement automatique des anciens repas par mots-clés (écarté par le propriétaire, indicateur explicite).
- Compter l'eau contenue dans les aliments solides (fruits, légumes).
- Coefficients selon la boisson.
- Notification supplémentaire.

---

## Contraintes techniques

- **Format d'import** (`docs/format-import.md`) : nouveau champ facultatif `drink` (`boisson` | `alcool`)
  sur l'aliment. Contrat : mettre à jour le lecteur dans `:core`, les exemples (dont `repas-dejeuner.json`
  et le test `MealWriterTest`) et les tests dans le même changement.
- **Base** : une colonne sur l'aliment (type de liquide), donc une vraie migration, schéma exporté et test
  de migration ; valeur par défaut « aliment » pour l'existant. Inclus dans la sauvegarde.
- Le calcul de l'eau lit les repas : un seul endroit (`WaterRepository` ou équivalent) additionne les deux
  sources ; aucun écran ne touche Room directement.
- Le widget, la barre d'Aujourd'hui et les rappels lisent ce même total.
- Aucun réseau ajouté.

---

## Découpage

Une seule PR (le champ, la migration, le total, l'éditeur, l'import, les boutons boissons, l'écran Eau).

### Plus tard
- Choix d'un coefficient par boisson si le propriétaire le demande.

---

## Risques

| Risque | Probabilité | Impact | Parade |
|---|---|---|---|
| Le lait ou le jus gonflent l'eau (calories liquides comptées comme eau) | Moyenne | Faible | Règle simple et visible ; liste « Dans les repas » dans l'écran Eau |
| Boisson sans quantité, donc non comptée | Moyenne | Faible | Message dans l'éditeur ; Claude donne toujours `quantity_g` pour une boisson |
| Double comptage si on a noté le verre dans l'eau et dans le repas | Moyenne | Moyen | Liste des deux sources côte à côte, suppression facile |
| Fichiers d'import d'anciens fils sans `drink` | Certaine | Faible | Champ facultatif, valeur par défaut « aliment » |

---

## Dépendances

- Le fil [Repas] (et tout fil qui produit des imports) : une fois la PR livrée, Claude remplit `drink`.
- Le PRD saisie rapide : le repas en calories seules n'est pas concerné ; « Déjà mangés » propose les boissons
  avec leur réglage.
- [Dev] consigne la règle dans `docs/decisions.md` (Eau et Repas) à la livraison.
