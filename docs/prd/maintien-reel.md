# PRD : maintien réel estimé à partir des repas et du poids

**Version** : 1.0, validée par le propriétaire le 2026-11-23
**Date** : 2026-11-23
**Auteur** : fil [Produit]
**Clarté du besoin** : 91/100

---

## Résumé

Le maintien de l'app est une formule (Mifflin-St Jeor × niveau d'activité habituel, recalculée avec la moyenne
des pesées des 7 derniers jours). C'est une estimation de départ : le niveau d'activité est choisi à la main
et le métabolisme varie d'une personne à l'autre, parfois de 10 à 15 %.

L'app peut vérifier cette formule avec ce qu'elle sait déjà : ce que tu manges (les repas notés) et ce que le
poids a fait pendant ce temps. Si on mange en moyenne 2 600 kcal et que le poids ne bouge pas sur trois
semaines, le maintien réel est proche de 2 600. Le calcul est local, sans IA.

Quand l'estimation est prête et s'écarte de la formule, l'app **la propose** (choix du propriétaire, 23/11/2026) : une
carte sur Aujourd'hui, le détail dans Mon profil, et le propriétaire adopte ou ignore. Rien ne change tout seul.

---

## Problème

**Aujourd'hui** : l'anneau des calories, la couleur du jour et la moyenne sur 7 jours se jugent par rapport à un
maintien théorique. S'il est trop haut de 300 kcal, une journée « dans la cible » ne l'est pas vraiment, et
l'inverse. Rien ne permet de s'en rendre compte.

**Effet attendu** : un maintien plus juste pour le propriétaire et ses proches, avec la preuve visible (nombre de jours,
de pesées), sans changer leur objectif sans leur accord.

---

## Mesures de succès

Rien ne sort du téléphone : on vérifie à l'usage avec le propriétaire.

- Au bout de 3 semaines de repas et de pesées, une proposition apparaît, ou une phrase explique ce qui manque.
- La proposition se lit et se décide en moins de 30 secondes.
- Après adoption, le maintien utilisé par l'app correspond à ce que le poids du propriétaire fait vraiment (à vérifier
  avec lui après un mois).

---

## Utilisateurs

- **Le propriétaire**, qui note surtout ses repas par photos (estimations de Claude) et se pèse.
- **Les proches**, qui notent à la main : l'estimation est souvent moins complète chez eux ; les seuils la
  protègent.

---

## Histoires et critères d'acceptation

### 1. Estimer le maintien réel

**En tant qu'utilisateur**, **je veux** que l'app compare mes repas et mon poids **pour** savoir si mon
maintien est juste.

- [ ] L'estimation se calcule sur une fenêtre glissante de **21 jours** (les 21 derniers jours terminés).
- [ ] Un jour est **pris en compte** s'il a au moins 2 repas ou un total d'au moins 50 % du maintien
      calculé (un jour à moitié noté fausserait la moyenne).
- [ ] **Conditions** pour estimer : au moins **80 % des jours** de la fenêtre pris en compte, et au moins
      **4 pesées** dont une dans la première semaine et une dans la dernière.
- [ ] Calcul : apport moyen des jours pris en compte, moins la variation de poids sur la fenêtre
      (pente des pesées lissées, en kg par jour) × 7 700 kcal par kg.
- [ ] Une estimation hors de **±30 %** de la formule, ou hors de 1 200 à 4 500 kcal, est jugée non fiable :
      elle n'est pas proposée (probablement des repas oubliés ou une erreur de saisie).
- [ ] Les calories de la montre (calories actives) ne servent pas au calcul.
- [ ] Les repas « calories seules » comptent dans l'apport (ils ont leurs calories) ; l'estimation reste
      indiquée comme approximative.

### 2. Proposer, et laisser décider

- [ ] La proposition apparaît seulement si l'estimation est prête **et** s'écarte d'au moins **5 %** du
      maintien actuel ; sinon Mon profil dit « Ton maintien semble juste ».
- [ ] **Carte sur Aujourd'hui** : « Ton maintien réel semble être de 2 850 kcal, pas 2 710 » avec les boutons
      « Voir le détail » et « Pas maintenant ». Pas de notification.
- [ ] « Pas maintenant » masque la carte **14 jours** ; elle revient seulement si l'estimation est encore
      prête et écartée de 5 %.
- [ ] **Mon profil** > « Mon maintien » : maintien calculé (formule), estimation, niveau de confiance
      (Moyenne ou Haute), et les chiffres utilisés (21 jours, jours pris en compte, pesées, variation de
      poids, apport moyen).
- [ ] Un bouton « Adopter 2 850 kcal » enregistre un **ajustement** (écart en kcal par rapport à la formule,
      ici +140) ; un bouton « Garder la formule » ferme la proposition.
- [ ] Après adoption, l'ajustement s'applique **par-dessus la formule** : le maintien continue de suivre le
      poids moyen, avec l'écart en plus.
- [ ] « Revenir à la formule » retire l'ajustement à tout moment (avec confirmation).
- [ ] Après adoption ou refus, aucune nouvelle proposition avant que **21 nouveaux jours** de données soient
      disponibles.

### 3. Effets de l'ajustement dans l'app

- [ ] L'anneau des calories, les couleurs de l'écart, la moyenne sur 7 jours, la barre du total des repas,
      le widget et le bilan repas utilisent le **maintien ajusté**.
- [ ] Les repères de macros (protéines selon le poids, lipides ≈ 30 % du maintien, glucides le reste)
      suivent le nouveau maintien.
- [ ] Le bilan repas envoyé à Claude indique le maintien utilisé et si un ajustement est appliqué
      (`maintenance_adjustment_kcal`).
- [ ] Le prompt d'ajustement et le prompt sport mentionnent le maintien réel s'il y en a un.

### 4. Cas limites

- [ ] Pas de profil (sexe, âge, taille) : aucune estimation, message d'invitation à le compléter.
- [ ] Poids très variable (plus de 3 kg d'écart brutal sur la fenêtre) : estimation marquée « peu fiable »
      et non proposée.
- [ ] Changement de niveau d'activité dans le profil : l'ajustement reste, mais la carte rappelle de le
      vérifier ; « Revenir à la formule » est proposé.
- [ ] Une sauvegarde contient l'ajustement ; une ancienne sauvegarde sans ajustement se restaure sans
      erreur (ajustement 0).

---

## Règles fonctionnelles

- L'objectif reste « Forme générale, maintien » : cette fonction **ne propose jamais de perdre ou de
  prendre du poids** et ne donne aucun conseil médical.
- Le sport n'ajoute toujours pas de calories à l'objectif.
- Le texte rappelle que c'est une **estimation** : les calories des repas photographiés sont elles-mêmes
  approximatives (±20 à 30 %), et le poids varie avec l'eau et le sel.
- Aucune couleur de jugement sur l'estimation.

### Hors du périmètre

- Objectif de perte ou de prise de poids (autre PRD si le propriétaire le veut).
- Calcul à partir des calories actives de la montre.
- Estimation sur d'autres durées que 21 jours.
- Ajustement automatique sans validation (écarté par le propriétaire).

---

## Contraintes techniques

- Calcul dans `:core` (Kotlin pur, testé : cas nominal, trop peu de données, valeurs aberrantes,
  poids stable, perte, prise).
- Un champ d'**ajustement du maintien** (kcal) dans le profil : **vraie migration** Room, schéma exporté,
  test de migration ; valeur 0 par défaut ; inclus dans la sauvegarde.
- Un seul endroit applique l'ajustement (là où le maintien est calculé aujourd'hui) pour que anneau,
  widget, bilan et prompts lisent la même valeur.
- Pas de réseau ; calcul en tâche de fond, résultat gardé pour la journée.
- Format d'import inchangé ; le bilan repas gagne `maintenance_adjustment_kcal`
  (`docs/format-import.md`, exemples, lecteur et tests dans le même changement).

---

## Découpage

Une seule PR : calcul dans `:core`, champ et migration, carte Aujourd'hui, section « Mon maintien » du profil,
application de l'ajustement, bilan repas.

---

## Risques

| Risque | Probabilité | Impact | Parade |
|---|---|---|---|
| Repas oubliés ou mal estimés faussent l'apport | Élevée | Moyen | Seuil de 80 % des jours, jours à moitié notés exclus, plage ±30 %, validation par le propriétaire |
| Eau, sel ou règles faussent le poids | Moyenne | Moyen | Fenêtre de 21 jours, pente sur pesées lissées, 4 pesées minimum |
| Proposition vécue comme un jugement | Faible | Moyen | Ton neutre, aucun objectif de poids, « Pas maintenant » |
| Boucle de propositions | Faible | Faible | 14 jours après « Pas maintenant », 21 jours après décision |
| Ajustement oublié après un changement de vie | Moyenne | Moyen | Rappel au changement d'activité, « Revenir à la formule » |

---

## Dépendances

- Les PRD « saisie rapide » (plus de repas notés) et « écran montre » ne sont pas bloquants.
- Pesées et profil existent déjà (suivi du corps, `docs/decisions.md`).
- [Dev] consigne la règle dans `docs/decisions.md` (Repas et calories, Suivi du corps) à la livraison.
