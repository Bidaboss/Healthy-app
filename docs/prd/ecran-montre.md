# PRD : écran de suivi de la montre (tendances)

**Version** : 1.0, validée par le propriétaire le 2026-11-23
**Date** : 2026-11-23
**Auteur** : fil [Produit]
**Clarté du besoin** : 91/100

---

## Résumé

L'app lit déjà la montre Garmin par Health Connect : une carte « Ma montre » sur Aujourd'hui montre les pas,
les calories actives, le sommeil de la nuit et le cœur du jour. Mais rien ne se suit dans le temps : on ne
sait pas si on dort mieux ou si on marche plus que le mois dernier.

On ajoute un **écran « Ma montre »** avec les tendances sur **7 et 30 jours** (pas, sommeil, cœur,
calories actives, séances). Pour que les courbes ne soient pas limitées aux 30 jours que Health Connect
rend, l'app **garde un relevé par jour sur le téléphone** (choix du propriétaire, 23/11/2026) : l'historique
grandit tant que la montre est connectée, hors ligne, et fait partie de la sauvegarde.

L'écran s'ouvre depuis la carte « Ma montre » d'Aujourd'hui et depuis un bouton dans l'onglet Sport. La
barre garde ses 4 onglets.

---

## Problème

**Aujourd'hui** : on voit le chiffre du jour, jamais l'évolution. Les données de la montre vivent dans
l'app Garmin, loin des repas et du sport de Healthy. Health Connect ne rend que 30 jours en arrière.

**Effet attendu** : voir en un coup d'œil si la semaine est meilleure ou moins bonne que la précédente, sans
quitter l'app, et retrouver ces tendances à côté du sport et des repas.

---

## Mesures de succès

Rien ne sort du téléphone : on vérifie à l'usage avec le propriétaire.

- Depuis Aujourd'hui, les tendances de la semaine s'ouvrent en un toucher et se lisent en moins de 10 secondes.
- Après un mois, l'écran affiche sans trou les jours où la montre était portée et connectée.
- Le propriétaire n'ouvre plus l'app Garmin pour voir ses moyennes (retour à l'usage).

---

## Utilisateur

- **Le propriétaire**, qui porte sa montre Garmin en continu et veut suivre pas, sommeil et cœur.
- Les proches avec une autre montre compatible Health Connect : même écran, rien à régler.
- Sans montre connectée, l'écran explique comment la connecter et n'affiche aucun chiffre.

---

## Histoires et critères d'acceptation

### 1. Voir les tendances

**En tant que** porteur de montre, **je veux** voir mes courbes sur 7 ou 30 jours **pour** savoir si je
progresse.

- [ ] L'écran « Ma montre » a un sélecteur **7 jours / 30 jours** (7 par défaut).
- [ ] Quatre blocs, un par mesure : **Pas** (barres par jour), **Sommeil** (barres par nuit, en heures et
      minutes), **Cœur** (deux courbes : moyenne du jour et plus bas du jour), **Calories actives** (barres,
      mention « indicatif : n'entre pas dans ton objectif »).
- [ ] Chaque bloc montre la **moyenne de la période** et l'écart avec la période précédente de même durée
      (par exemple « 8 450 pas par jour, +520 »), sans couleur de jugement.
- [ ] Toucher une barre ou un point affiche la valeur exacte du jour et sa date.
- [ ] Un jour sans donnée est un **trou** dans le graphique, jamais un zéro ; il ne compte pas dans la
      moyenne.
- [ ] Un bloc sans aucune donnée sur la période affiche « Pas de données sur cette période ».
- [ ] Le bloc **Séances** résume la période : nombre de séances (plan et activités, y compris celles de la
      montre ajoutées au journal) et durée totale.

### 2. Historique gardé sur le téléphone

**En tant que** personne qui veut un suivi long, **je veux** que l'app garde mes relevés **pour** ne pas
dépendre des 30 jours de Health Connect.

- [ ] À chaque lecture de la montre (ouverture de l'app, retour au premier plan, ouverture de l'écran),
      le relevé du jour est enregistré ou mis à jour : pas, calories actives, sommeil de la nuit, cœur
      moyen et plus bas.
- [ ] À la première connexion, l'app récupère jusqu'à **30 jours** de passé disponibles et les enregistre.
- [ ] Un jour terminé n'est plus modifié, sauf si la montre se synchronise en retard : un relevé des 3
      derniers jours est relu et corrigé.
- [ ] Les relevés sont conservés, sans limite de durée (un jour = quelques octets).
- [ ] Ils sont inclus dans la sauvegarde et restaurés avec elle, sans réécrire ce que Health Connect rend
      déjà plus récent.
- [ ] Un bouton « Effacer l'historique de la montre » (Réglages > Ma montre, avec confirmation) vide ces
      relevés ; il ne touche pas aux repas, séances ni activités.

### 3. Ouvrir l'écran

- [ ] La carte « Ma montre » d'Aujourd'hui s'ouvre sur l'écran en la touchant (en plus de son contenu
      actuel).
- [ ] L'onglet Sport a un bouton « Mes stats montre » (visible seulement si la montre est connectée).
- [ ] Retour par la flèche ; pas de nouvel onglet.

### 4. Cas limites

- [ ] Montre non connectée : l'écran affiche le bouton « Connecter ma montre » (même parcours que
      Réglages) et une phrase simple.
- [ ] Sommeil et cœur non autorisés (facultatifs) : ces deux blocs proposent « Ajouter le sommeil et
      le cœur », les autres blocs restent affichés.
- [ ] Autorisation retirée dans Health Connect : l'historique déjà gardé reste visible ; les nouveaux jours
      n'arrivent plus, avec un message « La lecture de la montre est coupée ».
- [ ] Sommeil : un relevé par **nuit qui se termine ce jour-là** (règle déjà utilisée par la carte).
- [ ] Fuseau et changement d'heure : les jours suivent la date locale du téléphone.

---

## Règles fonctionnelles

- Lecture seule : l'app n'écrit rien dans Health Connect.
- Les calories actives restent **indicatives** (règle de `docs/decisions.md` : le sport n'ajoute pas de
  calories à l'objectif).
- Aucune mesure que Garmin n'écrit pas dans Health Connect (variabilité cardiaque, Body Battery, stress).
- Ton neutre : l'app ne juge pas, ne donne **aucun conseil médical**, aucune alerte « santé ». Les
  courbes montrent, c'est tout.
- Les données de santé restent sur le téléphone ; la sauvegarde les contient (l'app le dit avant l'export,
  comme pour les prompts).

### Hors du périmètre

- Rapprochements avec les repas, le poids ou les séances (sommeil les jours de sport, pas et calories) :
  écarté par le propriétaire pour cette première version (« tendances » choisies).
- Période de 90 jours ou « tout » dans le sélecteur : possible plus tard, l'historique sera déjà là.
- Envoi des relevés de la montre à Claude dans le bilan d'entraînement.
- Objectifs de pas ou de sommeil, notifications, badges.
- Lecture des anciennes données au-delà de 30 jours (autorisation d'historique de Health Connect).

---

## Contraintes techniques

- Paquet `health` : relevés enregistrés dans Room (une ligne par jour : date, pas, calories actives,
  sommeil en minutes, cœur moyen, cœur minimal, dernière mise à jour), accès par un repository et un
  ViewModel ; l'écran n'utilise jamais une entité ou un DAO.
- Nouvelle table : **vraie migration**, schéma exporté, test de migration ; les mesures absentes sont
  `null`, pas zéro.
- Sauvegarde : table ajoutée à l'export et à la restauration ; une ancienne sauvegarde sans cette table
  se restaure sans erreur.
- Graphiques dessinés en Compose, sans bibliothèque supplémentaire si possible (barres et lignes simples) ;
  accessibles (valeurs lues par TalkBack) et lisibles dans les 6 thèmes, clair et sombre, avec un
  contraste de 4,5:1.
- Performance : écran affiché en moins de 500 ms avec un an de relevés ; la relecture de Health Connect
  se fait en tâche de fond, sans bloquer l'écran.
- Aucun réseau ajouté.

---

## Découpage

### Phase 1 (une PR)
- Table des relevés, enregistrement du jour, récupération de 30 jours à la première connexion, sauvegarde.
- Écran avec sélecteur 7 / 30 jours, les quatre blocs et le bloc séances, états vides.
- Accès depuis la carte d'Aujourd'hui et le bouton de Sport ; « Effacer l'historique ».

### Plus tard
- 90 jours et « tout », rapprochements avec repas et sport, bilan envoyé à Claude.

---

## Risques

| Risque | Probabilité | Impact | Parade |
|---|---|---|---|
| Garmin n'écrit pas toutes les mesures dans Health Connect (vérification faite sur le téléphone du propriétaire pas encore faite) | Moyenne | Moyen | Blocs vides expliqués ; on n'affiche que ce qui arrive |
| Trous (montre non portée, synchro en retard) | Élevée | Faible | Trous visibles, jamais de zéro, relecture des 3 derniers jours |
| Données de santé dans la sauvegarde | Faible | Moyen | Avertissement avant l'export, bouton pour effacer l'historique |
| Graphiques illisibles dans un thème | Moyenne | Faible | Test de contraste existant étendu aux graphiques |
| Écran vu comme un avis médical | Faible | Moyen | Aucun seuil, aucune couleur de jugement, aucun conseil |

---

## Dépendances

- La montre Garmin via Health Connect, déjà livrée (étapes 1 et 2 de `docs/decisions.md`, « Montre »).
- À vérifier avec le propriétaire : quels types Garmin Connect écrit réellement (Paramètres > Health Connect >
  Accès aux données > Garmin Connect).
- [Dev] consigne les règles dans `docs/decisions.md` (Montre) à la livraison ; [Archi] relit la migration.
