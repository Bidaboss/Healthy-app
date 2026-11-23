# PRD : repères nationaux dans l'app (partie 1 sur 2)

**Version** : 1.0, validée par le propriétaire
**Date** : 2026-11-23
**Auteur** : fil [Produit]
**Clarté du besoin** : 90/100

---

## Résumé

L'app compare le propriétaire à ses propres objectifs (maintien, protéines 1,6 g/kg, fibres 30 g, eau), jamais à des
repères officiels. On ajoute une **table de repères nationaux et internationaux, embarquée dans l'app** (hors
ligne, aucune donnée envoyée), un écran **Repères** (7 ou 28 jours) qui place chaque valeur de le propriétaire par
rapport au repère, avec **la source écrite sous chaque ligne**, et une carte « Tes repères » sur Aujourd'hui.

La partie 2 (dossier d'analyse pour l'IA tierce et affichage de son retour) est un PRD à part, écrit ensuite.

---

## Problème

Le propriétaire ne sait pas si ses repas et son activité sont « dans la norme ». Ses objectifs sont des choix de l'app,
pas des repères publics. Il ne peut pas non plus savoir ce qui est officiel et ce qui est une règle maison.

**Effet attendu** : voir en moins de 15 secondes où il se situe, avec la preuve (source), sans jugement ni conseil médical.

---

## Mesures de succès

Rien ne sort du téléphone : vérifié à l'usage avec le propriétaire.
- Écran Repères lu et compris en moins de 15 secondes.
- Chaque ligne affiche sa source et son statut (« vérifié » ou « à confirmer »).
- Aucune ligne n'est confondue avec un objectif personnel.

---

## Utilisateurs

Le propriétaire et ses proches (saisie manuelle : les repères sont indicatifs quand les repas sont peu notés, voir cas limites).

---

## Table des repères (contenu, versionnée dans l'app)

**Vérifiés (lus à la source)**
| Repère | Valeur | Source |
|---|---|---|
| Activité | 150 à 300 min d'activité modérée par semaine ; renforcement musculaire bénéfique | OMS |
| Endurance / renforcement | 30 min d'endurance au moins 5 j/sem ; renforcement 1 à 2 j/sem | Santé publique France (PNNS) |
| Fruits et légumes | au moins 5 par jour | SPF (PNNS) |
| Féculents | à chaque repas, dont 1 complet par jour | SPF |
| Produits laitiers | 2 par jour | SPF |
| Poisson | 2 fois par semaine, dont 1 gras | SPF |
| Légumes secs | au moins 2 fois par semaine | SPF |
| Viande (hors volaille) | 500 g par semaine au plus | SPF |
| Charcuterie | 150 g par semaine au plus | SPF |
| Boissons | eau seule boisson recommandée | SPF |

**À confirmer (extraits vus, source non lue en entier : site ANSES bloquant)** : protéines 0,83 g/kg, fibres 25 à 30 g,
lipides, glucides, sel en grammes. **Non trouvés** : durée de sommeil et pas par jour officiels.

---

## Histoires et critères d'acceptation

### 1. Écran Repères
- [ ] Accès par la carte d'Aujourd'hui et par Mon profil ; pas de nouvel onglet.
- [ ] Sélecteur **7 jours / 28 jours** (28 par défaut).
- [ ] Une ligne par repère mesurable avec les données de l'app (calories, protéines, fibres, eau, activité, sommeil, pas) : valeur de le propriétaire, barre avec la zone du repère, texte du repère.
- [ ] Sous chaque ligne : **source** et **statut** (vérifié / à confirmer). Les repères de l'app sont étiquetés « Objectif de l'app », pas « officiel ».
- [ ] Les repères sans chiffre officiel (sommeil, pas) montrent la tendance sans zone.
- [ ] Les repères hebdomadaires (poisson, légumes secs, viande, charcuterie) ne sont affichés que si les repas portent l'information nécessaire ; sinon ils sont masqués, jamais à zéro.

### 2. Carte sur Aujourd'hui
- [ ] Carte « Tes repères · 28 jours » avec 2 ou 3 lignes, le bouton « Voir tous les repères ».
- [ ] Pas de notification.

### 3. Ton et couleurs
- [ ] Mots « dans le repère » / « sous le repère » / « au-dessus du repère », jamais « mauvais ».
- [ ] Aucune couleur rouge ; les minimums (protéines, fibres) ne déclenchent jamais d'alerte ; les plafonds (charcuterie, viande) sont affichés en neutre.

### 4. Cas limites
- [ ] Moins de 80 % des jours de la période avec repas notés : mention « peu de repas notés, repère indicatif ».
- [ ] Profil incomplet (poids, sexe, âge) : les repères liés au poids sont masqués avec une phrase d'invitation.
- [ ] Montre non connectée : activité calculée depuis les séances de l'app ; sommeil et pas absents, lignes masquées.

---

## Règles fonctionnelles

- Les repères **ne remplacent pas un avis médical** ; une phrase l'indique sur l'écran.
- L'objectif reste « Forme générale, maintien » : aucun repère ne pousse à perdre ou prendre du poids.
- La table est une donnée du code (`:core`), avec valeur, unité, source, URL, date de lecture, statut ; mise à jour par une nouvelle version de l'app, jamais en ligne.
- Le maintien, les protéines g/kg, les fibres 30 g et l'eau gardent leurs règles de `docs/decisions.md`.

### Hors du périmètre
- Dossier pour l'IA tierce et analyse reçue (PRD 2).
- Données nutritionnelles tierces en ligne (CIQUAL, etc.).
- Repères par tranche d'âge fine, grossesse, pathologies.

---

## Contraintes techniques

- Table et calcul de position dans `:core` (Kotlin pur, testé : dans / sous / au-dessus, hebdomadaire, données manquantes).
- Aucun changement de base : pas de migration. Aucun réseau.
- Écran via repository et ViewModel, lisible dans les 6 thèmes, contraste 4,5:1, valeurs lues par TalkBack.
- Les valeurs « à confirmer » sont une liste distincte pour pouvoir être passées à « vérifié » sans changer le code d'affichage.

---

## Découpage

Une PR : table `:core`, écran Repères, carte Aujourd'hui, entrée dans Mon profil.

---

## Risques

| Risque | Probabilité | Impact | Parade |
|---|---|---|---|
| Valeur officielle mal recopiée | Moyenne | Moyen | Statut « à confirmer », source et date visibles, relecture [Archi] |
| Repères pris pour des conseils médicaux | Faible | Moyen | Phrase d'avertissement, ton neutre, aucune alerte |
| Repères périmés (nouvelles recommandations) | Moyenne | Faible | Date de lecture affichée, mise à jour par version |
| Repas peu notés faussent la position | Élevée | Faible | Mention « repère indicatif » sous 80 % |

---

## Dépendances

- Données de la montre (sommeil, pas) : déjà livrées, facultatives.
- Confirmation à la source des valeurs ANSES : à faire par le propriétaire ou un fil dédié avant livraison, sinon elles restent « à confirmer ».
- [Dev] consigne la règle dans `docs/decisions.md` à la livraison ; [Archi] relit la table.
