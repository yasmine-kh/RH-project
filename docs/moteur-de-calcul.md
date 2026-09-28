# TALENT 360 BANK — Moteur de calcul

Ce document décrit ce que calcule le moteur, comment il le calcule et d'où viennent ses données. Il s'adresse au client et à l'encadrement autant qu'aux développeurs : les règles sont décrites en français, sans code.

## Principes communs

- **Aucun seuil ni aucune pondération n'est écrit dans le code.** Toutes les valeurs viennent du jeu de réglages du trimestre (le « Paramètre »), modifiable par les RH. Changer une pondération est une opération de configuration, pas de développement.
- **Un jeu de réglages par trimestre.** Chaque trimestre (par exemple T3 2026) a ses propres réglages. Un nouveau trimestre reçoit les valeurs par défaut ci-dessous, qui reprennent celles du classeur `TALENT_360_BANK_Dataset_V1.xlsx` (feuille `00_PARAMETRES`).
- **Seuls les collaborateurs actifs sont évalués.** Les collaborateurs inactifs ou archivés sont écartés et comptés à part, jamais ignorés en silence.
- **Arrondi à deux décimales** (au centième le plus proche). C'est cette valeur arrondie qui est comparée aux seuils : un score de 84,995 compte comme 85,00.
- **Les bornes sont inclusives** : un score égal au seuil atteint le niveau (85 ≥ 85). Seule exception : les seuils de vigilance et d'engagement, où l'on compare « strictement en dessous ».
- **Une donnée absente ne se devine pas.** Un questionnaire manquant ne signifie pas un engagement faible ; un fait inconnu ne lève pas de signal.
- **Écrans.** Seuls l'accueil (`/`), la matrice 9-Box (`/9box`) et les viviers (`/viviers`) ont un écran. Les pages Postes critiques, Comité Talent, Alertes et Paramètres existent dans le menu mais sont encore vides : ces résultats se consultent pour l'instant par les points d'accès décrits ci-dessous.
- **Validation par le classeur.** Un test automatique rejoue le classeur dans le moteur et compare les résultats, colonne par colonne. Les modules décrits ci-dessous donnent les mêmes résultats que le classeur, sans écart connu. Seule différence de méthode : le classeur compare aux seuils le score non arrondi, le moteur le score arrondi au centième. Aucun collaborateur du jeu de données n'est concerné.

## Vue d'ensemble

| Module | Produit | Calculé à partir de |
|---|---|---|
| Performance | Score /100 et catégorie (Exceptionnelle à Insuffisante) | 5 notes d'évaluation |
| Potentiel | Score /100 et catégorie (Élevé, Moyen, Faible) | 7 notes d'évaluation |
| 9-Box | Case de la matrice | Scores de performance et de potentiel |
| Talent | Oui / Non | Les deux scores |
| Haut potentiel | Oui / Non | Les deux scores |
| Vivier de relève | Liste | Talents et hauts potentiels |
| Succession | Score de matching /100 et délai de disponibilité | 6 critères candidat / poste |
| Postes critiques | Niveau de couverture et alertes | Successeurs identifiés et leur matching |
| Comité Talent | Talents validés | Talents proposés et décisions du comité |
| Viviers thématiques | Un vivier par collaborateur | Direction du collaborateur |
| Vigilance RH | Indice /100 et niveau | 7 signaux de risque |

Les calculs se font dans cet ordre : les scores de performance et de potentiel sont calculés et enregistrés, puis chaque collaborateur est placé dans la 9-Box. Tous les autres modules lisent ces scores enregistrés et appliquent les seuils du trimestre au moment de la consultation.

**Après une modification des réglages**, il faut relancer le recalcul des scores puis le placement 9-Box du trimestre (voir « Recalcul » en fin de document). Les autres modules prennent les nouveaux seuils en compte immédiatement.

---

## 1. Performance

**Ce qu'il fait.** Calcule le score de performance de chaque collaborateur sur 100, par moyenne pondérée de cinq notes d'évaluation.

**Formule.** Score = somme (note × poids) ÷ somme des poids.

| Critère | Poids par défaut |
|---|---|
| Atteinte des objectifs | 40 |
| Compétences | 20 |
| Comportement | 20 |
| Contribution collective | 10 |
| Développement | 10 |

Les poids doivent totaliser 100. La division se fait par la somme réelle des poids, pour que le résultat reste juste même si des réglages ont été saisis hors de l'application.

**Source dans le classeur.** `02_PERFORMANCE`, colonnes D à H (les cinq notes, sur 100). Le résultat correspond à la colonne I (Score Performance). Pondérations : `00_PARAMETRES`, section 1.

**Catégorie de performance.** Chaque score reçoit une étiquette individuelle :

| Score de performance | Catégorie |
|---|---|
| ≥ 90 | Exceptionnelle |
| ≥ 80 | Élevée |
| ≥ 70 | Solide |
| ≥ 60 | À renforcer |
| < 60 | Insuffisante |

Les quatre seuils sont réglables et doivent rester strictement décroissants. La catégorie est calculée et enregistrée avec le score, lors du recalcul : changer ces seuils demande donc de relancer le recalcul. Sur le jeu de données : 20 Exceptionnelle, 18 Élevée, 40 Solide, 19 À renforcer, 3 Insuffisante.

**Source dans le classeur.**
- Notes : `02_PERFORMANCE`, colonnes D à H (sur 100).
- Score : colonne I (Score Performance).
- Catégorie : colonne J (Catégorie Performance).
- Pondérations : `00_PARAMETRES`, section 1 ; seuils des catégories : section 4.

**Service et points d'accès.** `CalculService` calcule, `ScoreService` enregistre.
- `POST /api/trimestres/{année}/{numéro}/scores/recalcul` : recalcule et enregistre les scores et catégories de performance de tout le trimestre.
- `GET /api/trimestres/{année}/{numéro}/scores` : scores du trimestre, avec leurs deux catégories.
- `GET /api/employes/{matricule}/scores` : historique d'un collaborateur, du plus récent au plus ancien.

Toutes les réponses qui listent des scores (talents, hauts potentiels, talents validés…) portent aussi les deux catégories. Une catégorie encore non calculée est rendue vide.

**Données attendues de l'import.** Pour chaque collaborateur et chaque trimestre : les cinq notes sur 100 (aucune ne peut manquer). Un collaborateur sans notes de performance n'est pas scoré et apparaît dans le bilan du recalcul avec son motif.

## 2. Potentiel

**Ce qu'il fait.** Calcule le score de potentiel sur 100, par moyenne pondérée de sept notes.

**Formule.** Même principe que la performance.

| Critère | Poids par défaut |
|---|---|
| Agilité d'apprentissage | 20 |
| Leadership | 20 |
| Adaptabilité | 15 |
| Gestion de la complexité | 15 |
| Mobilité | 10 |
| Vision stratégique | 10 |
| Autonomie | 10 |

Les poids doivent totaliser 100.

**Catégorie de potentiel.** Chaque score reçoit une étiquette : **Élevé** (≥ 85), **Moyen** (≥ 70) ou **Faible**. C'est le niveau de l'axe potentiel de la 9-Box, avec les mêmes seuils (voir section 3) : il n'y a pas de réglage propre. La catégorie est enregistrée lors du placement 9-Box. Sur le jeu de données : 24 Élevé, 55 Moyen, 21 Faible.

**Source dans le classeur.**
- Notes : `03_POTENTIEL`, colonnes D à J.
- Score : colonne K (Score Potentiel).
- Catégorie : colonne L (Catégorie Potentiel).
- Pondérations : `00_PARAMETRES`, section 2.

**Service et points d'accès.** Les mêmes que pour la performance : les deux scores sont calculés et enregistrés ensemble. La catégorie de potentiel est posée par le placement 9-Box (section 3).

**Données attendues de l'import.** Les sept notes sur 100, par collaborateur et par trimestre. Les notes de leadership et de mobilité servent aussi au matching de succession.

## 3. Matrice 9-Box

**Ce qu'il fait.** Place chaque collaborateur dans l'une des neuf cases de la matrice, selon son niveau de performance et de potentiel.

**Règle.** Chaque axe est découpé en trois niveaux, avec ses propres seuils :

| Niveau | Axe performance | Axe potentiel |
|---|---|---|
| Élevé | score ≥ 85 | score ≥ 85 |
| Moyen | score ≥ 70 | score ≥ 70 |
| Faible | en dessous de 70 | en dessous de 70 |

Les deux axes ont aujourd'hui les mêmes valeurs, comme dans le classeur, mais se règlent séparément. Le niveau de l'axe potentiel est aussi la catégorie de potentiel du collaborateur (section 2).

| Performance ↓ / Potentiel → | Faible | Moyen | Élevé |
|---|---|---|---|
| **Élevée** | Expert | Performant | Talent clé |
| **Moyenne** | À accompagner | Confirmé | Potentiel / Haut potentiel |
| **Faible** | À surveiller | À développer | Potentiel à confirmer |

Les libellés des neuf cases sont une table de référence, renommable par les RH sans toucher au code.

**Source dans le classeur.** `04_9BOX` : bandes en colonnes E et G, catégorie en colonne I. Seuils : `00_PARAMETRES`, section 3 (lignes 24 et 25 pour la performance, 26 et 27 pour le potentiel).

**Service et points d'accès.** `NeufBoxService`.
- `POST /api/trimestres/{année}/{numéro}/9box/placement` : place tous les collaborateurs scorés du trimestre et enregistre leur catégorie de potentiel.
- Écran : `/9box`.

**Données attendues de l'import.** Aucune donnée supplémentaire : les deux scores suffisent.

## 4. Talent

**Ce qu'il fait.** Propose automatiquement comme **talent** tout collaborateur dont la performance **et** le potentiel atteignent leurs seuils.

**Règle.** Talent si performance ≥ 85 **et** potentiel ≥ 85. C'est un « et », pas une moyenne : un score exceptionnel ne compense pas l'autre.

**Source dans le classeur.** `10_TALENTS`, colonne E (Talent proposé). Seuils : `00_PARAMETRES`, section 5, lignes 38 et 39.

**Service et points d'accès.** `TalentService`.
- `GET /api/trimestres/{année}/{numéro}/talents` : talents du trimestre.
- `GET /api/trimestres/{année}/{numéro}/talents/{matricule}` : statut d'un collaborateur.

**Données attendues de l'import.** Aucune donnée supplémentaire.

## 5. Haut potentiel

**Ce qu'il fait.** Propose comme **haut potentiel** tout collaborateur au potentiel très élevé et à la performance au moins solide. Ce statut est indépendant de celui de talent : un collaborateur peut cumuler les deux.

**Règle.** Haut potentiel si potentiel ≥ 85 **et** performance ≥ 75.

**Source dans le classeur.** `10_TALENTS`, colonne F (Haut Potentiel proposé). Seuils : `00_PARAMETRES`, section 5, lignes 40 et 41.

**Service et points d'accès.** `TalentService`.
- `GET /api/trimestres/{année}/{numéro}/hauts-potentiels`
- `GET /api/trimestres/{année}/{numéro}/hauts-potentiels/{matricule}`

**Données attendues de l'import.** Aucune donnée supplémentaire.

## 6. Vivier de relève

**Ce qu'il fait.** Réunit les talents **et** les hauts potentiels du trimestre dans un vivier unique, chacun une seule fois avec la raison de sa présence, du plus performant au moins performant.

**Règle.** Membre du vivier si talent **ou** haut potentiel. La décision du Comité Talent n'intervient pas.

**Source dans le classeur.** `10_TALENTS`, colonne J (Dans le vivier de relève ?).

**Service et points d'accès.** `TalentService` calcule le vivier ; `VivierReleveService` l'enregistre, pour que les écrans le lisent comme les autres viviers.
- `GET /api/trimestres/{année}/{numéro}/vivier-releve` : vivier calculé à la demande.
- `POST /api/trimestres/{année}/{numéro}/vivier-releve` : enregistre le vivier du trimestre. La réponse indique le nombre de lignes remplacées, le nombre de lignes écrites et les collaborateurs déjà présents par une autre origine.
- Écran : `/viviers`.

**Enregistrement.**
- **Rejouable sans risque :** chaque passage remplace les lignes produites par le moteur pour ce trimestre. Le relancer donne le même vivier, sans doublon.
- **Import et saisie RH préservés :** un collaborateur ajouté par un import ou par les RH n'est jamais retiré, ni ajouté une seconde fois.
- **Unicité garantie par la base :** un collaborateur ne peut figurer qu'une fois dans un même vivier pour un même trimestre, quelle que soit l'origine.

**Données attendues de l'import.** Aucune donnée supplémentaire.

## 7. Succession (matching candidat / poste)

**Ce qu'il fait.** Mesure, sur 100, l'adéquation d'un candidat à un poste cible. Il en déduit le délai avant que le candidat soit prêt (sa « readiness ») et classe tous les candidats d'un poste du meilleur au moins bon. Le titulaire actuel est écarté : il ne peut pas être son propre successeur.

**Formule.** Moyenne pondérée de six critères, chacun sur 100.

| Critère | Poids par défaut | Mesure |
|---|---|---|
| Compétences | 25 | Couverture des compétences exigées par le poste (voir ci-dessous) |
| Performance | 20 | Score de performance |
| Potentiel | 20 | Score de potentiel |
| Expérience | 15 | Ancienneté × 8 points par an, plafonnée à 100 |
| Leadership | 10 | Note de leadership du potentiel |
| Mobilité | 10 | Note de mobilité du potentiel |

Les poids doivent totaliser 100. Un critère non évaluable (par exemple une date d'entrée inconnue) compte pour zéro et garde son poids, comme une cellule vide dans le classeur.

**Critère compétences.**
- Pour chaque compétence exigée par le poste, le candidat part de 100 points et perd 20 points par niveau manquant, sans descendre sous 0.
- Une compétence absente de son profil est supposée au niveau 3.
- Dépasser le niveau exigé ne rapporte pas de bonus.
- Le critère est la moyenne sur les compétences exigées.

**Critère expérience.** L'ancienneté est comptée en années décimales, arrondies au dixième, à la date du calcul.

**Readiness.**

| Matching | Délai |
|---|---|
| ≥ 90 | Ready Now |
| ≥ 80 | Ready < 1 an |
| ≥ 65 | Ready 1-2 ans |
| < 65 | Ready > 2 ans |

**Source dans le classeur.**
- Critères, score et readiness : `09_SUCCESSION`, colonnes E à J (critères), K (Score Matching), L (Readiness).
- Postes et compétences exigées : `07_POSTES`.
- Niveaux des collaborateurs : `06_EMPLOYEE_SKILLS` (niveau actuel en colonne E).
- Barèmes : `00_PARAMETRES`, sections 6, 7 et 10.

**Service et points d'accès.** `SuccessionService`.
- `GET /api/postes/{poste}/candidats?annee=…&numero=…` : classement des candidats (paramètre facultatif `limite`).
- `GET /api/postes/{poste}/candidats/{matricule}?annee=…&numero=…` : matching d'un candidat, avec le détail des six critères.

**Données attendues de l'import.**
- **Postes :** jusqu'à cinq compétences exigées avec leur niveau, et le titulaire.
- **Compétences des collaborateurs :** niveau actuel de chaque compétence.
- **Collaborateurs :** date d'entrée.
- **Scores :** les notes de potentiel déjà importées.

## 8. Postes critiques

**Ce qu'il fait.** Évalue la couverture de chaque poste critique par ses successeurs identifiés et lève une alerte sur les postes insuffisamment couverts. Il fournit aussi le taux de couverture du tableau de bord.

**Règles.**
- Un poste est critique quand son indicateur « Poste critique » vaut Oui ; sa criticité (Très élevée, Élevée) n'entre pas dans le calcul.
- La couverture se juge sur les successeurs **identifiés par les RH**, pas sur le classement de tous les collaborateurs : ce classement propose toujours quelqu'un et ne déclencherait jamais d'alerte.
- Un successeur est écarté s'il est le titulaire, s'il est inconnu ou s'il n'est pas actif.

| Situation | Couverture |
|---|---|
| Moins de 1 successeur identifié | Successeurs insuffisants — ALERTE |
| Meilleur matching ≥ 90 | Couverte — Ready Now |
| Meilleur matching ≥ 80 | Couverte — < 1 an |
| Sinon | Partielle — à renforcer |

Le meilleur matching est recalculé par le moteur, avec les règles de la section 7 ; il n'est pas repris du classeur. Les seuils 90 et 80 sont ceux de la readiness.

**Taux de couverture** = postes critiques hors alerte ÷ postes critiques × 100. Sur le jeu de données : 15 postes critiques, 1 alerte (PST13, Responsable Cybersécurité), soit 93 %.

**Source dans le classeur.**
- Couverture : `08_POSTES_CRITIQUES`, colonnes G (nombre de successeurs), H (meilleur matching) et I (couverture).
- Successeurs identifiés : `09_SUCCESSION`, colonnes A (Poste_ID) et B (Employee_ID).
- Indicateur « poste critique » : `07_POSTES`, colonne P.
- Tableau de bord : `00_DASHBOARD`.

**Service et points d'accès.** `PosteCritiqueService`.
- `GET /api/postes-critiques?annee=…&numero=…` : couverture de tous les postes critiques.
- `GET /api/postes-critiques/alertes?annee=…&numero=…` : postes en alerte.
- `GET /api/postes-critiques/synthese?annee=…&numero=…` : nombre de postes, d'alertes et taux de couverture.
- `GET /api/postes-critiques/{poste}?annee=…&numero=…` : un poste.

**Données attendues de l'import.**
- **Par poste :** l'indicateur « Poste critique » (Oui / Non) et le titulaire.
- **Par poste critique :** la liste des successeurs identifiés, par matricule. Cette liste n'est pas encore enregistrée en base.

Tant qu'aucun import ne fournit ces successeurs, tous les postes critiques sont en alerte.

## 9. Comité Talent

**Ce qu'il fait.** Distingue les talents **proposés** par le moteur des talents **validés** par le Comité Talent.

**Règle.** Talent validé = talent proposé **et** décision du comité « Oui ».
- Le comité ne statue que sur les talents.
- Les hauts potentiels ne sont pas soumis à validation.
- Le vivier de relève ne dépend pas de la décision du comité.

Une décision non saisie vaut « En attente ». Sur le jeu de données : 10 talents proposés, 8 validés.

**Source dans le classeur.** `10_TALENTS` : colonne G (Validation Comité Talent, saisie Oui / Non / En attente) et colonne H (Talent validé).

**Service et points d'accès.** `ValidationComiteService`.
- `GET /api/comite-talent?annee=…&numero=…` : décision du comité pour chaque talent proposé, en attente compris.
- `GET /api/comite-talent/talents-valides?annee=…&numero=…` : talents validés.

**Données attendues de l'import.** Pour chaque collaborateur et chaque trimestre, la décision du comité : Oui, Non ou En attente. Ces décisions ne sont pas encore enregistrées en base : tant qu'aucun import ne les fournit, toutes sont en attente et aucun talent n'est validé.

## 10. Viviers thématiques

**Ce qu'il fait.** Range chaque collaborateur du trimestre, talent ou non, dans le vivier thématique de sa direction, avec ses statuts de talent et de haut potentiel pour permettre le filtrage.

**Règle.** Chaque direction est rattachée à un vivier. Le rattachement du classeur :

| Vivier | Directions |
|---|---|
| Vivier Commercial | Réseau Retail, Corporate Banking |
| Vivier Digital | IT & Digital, Marketing & Communication |
| Vivier Expertise | Finance, Audit Interne, Juridique |
| Vivier Management | RH |
| Vivier Risques | Risques, Conformité |

Ce rattachement est une donnée, pas une règle du moteur. Un collaborateur dont la direction n'est rattachée à aucun vivier est rendu à part, pas écarté.

**Source dans le classeur.** `10_TALENTS`, colonnes C (Direction) et I (Vivier thématique).

**Service et points d'accès.** `VivierThematiqueService`.
- `GET /api/viviers-thematiques?annee=…&numero=…` : les cinq viviers, avec leurs effectifs, talents et hauts potentiels.
- `GET /api/viviers-thematiques/{code}?annee=…&numero=…` : un vivier (`COMMERCIAL`, `DIGITAL`, `EXPERTISE`, `MANAGEMENT`, `RISQUES`).

**Données attendues de l'import.**
- **Par collaborateur :** la direction.
- **Référentiel :** le rattachement direction / vivier. Il n'est pas encore enregistré en base ; tant qu'aucun import ne le fournit, personne n'est classé.

## 11. Vigilance RH

**Ce qu'il fait.** Calcule un indice de vigilance sur 100 et un niveau (Faible, Modérée, Élevée) pour prioriser les actions d'accompagnement RH. Comme le rappelle le classeur, cet indice **n'est pas une prédiction de départ** : c'est un outil de priorisation, fondé sur des facteurs transparents.

**Formule.** Indice = somme des points des signaux présents.

| Signal | Points par défaut | Origine |
|---|---|---|
| Engagement faible | 25 | Score du questionnaire d'engagement strictement inférieur à 60 |
| Aucun mouvement depuis 4 ans | 20 | Import |
| Demande de mobilité non traitée | 15 | Import |
| Aucune action de développement récente | 15 | Import |
| Baisse de performance | 10 | Historique des scores, sinon import |
| Faible reconnaissance | 10 | Import |
| Formation prévue non réalisée | 5 | Import |

**Baisse de performance.**
- Si le collaborateur a un score au trimestre précédent, le moteur compare les deux : toute baisse, même faible, lève le signal. Cet historique prime sur l'information importée.
- Sinon, c'est l'information importée qui décide.

**Niveau.**

| Indice | Niveau |
|---|---|
| Moins de 30 | Faible |
| De 30 à moins de 60 | Modérée |
| 60 et plus | Élevée |

Un collaborateur est « à risque » à partir du niveau Modérée. Sur le jeu de données : 55 Faible, 35 Modérée, 10 Élevée.

**Source dans le classeur.** `12_VIGILANCE` :
- colonne D : score d'engagement ;
- colonnes E à K : les sept signaux ;
- colonne L : indice ;
- colonne M : niveau.

Points et seuils : `00_PARAMETRES`, sections 8 et 9.

**Service et points d'accès.** `VigilanceService`.
- `GET /api/trimestres/{année}/{numéro}/vigilance` : tous les collaborateurs, du plus au moins à risque. Le paramètre facultatif `minimum` (`FAIBLE`, `MODEREE` ou `ELEVEE`) filtre par niveau.
- `GET /api/trimestres/{année}/{numéro}/vigilance/{matricule}` : un collaborateur, avec ses signaux.

**Données attendues de l'import.** Par collaborateur et par trimestre :
- **Score d'engagement :** sur 100, issu du questionnaire. Absent : pas de signal.
- **Six indicateurs Oui / Non :**
  - aucun mouvement depuis 4 ans ;
  - demande de mobilité non traitée ;
  - aucune action de développement récente ;
  - baisse de performance (utilisée seulement sans score au trimestre précédent) ;
  - faible reconnaissance ;
  - formation non réalisée.

  Un indicateur inconnu ne lève pas de signal.

Tant qu'aucun import ne fournit ces six indicateurs, seuls l'engagement et la baisse mesurée sur l'historique peuvent être levés : l'indice ne dépasse alors pas 35 et le niveau Élevée est inatteignable. Le moteur prévoit de pouvoir remplacer plus tard ces Oui / Non par des faits bruts, par exemple la date du dernier mouvement, évalués avec des seuils ajoutés aux réglages.

---

## Tableau de bord

**Ce qu'il fait.** Rassemble en un seul appel les chiffres clés du trimestre produits par les modules ci-dessus : talents proposés et validés, hauts potentiels, répartition de la vigilance, collaborateurs à risque, couverture des postes critiques et répartition 9-Box.

**Service et points d'accès.** `TableauDeBordService`.
- `GET /api/dashboard/synthese?annee=…&numero=…`
- Écran : `/`. Plusieurs indicateurs de cet écran sont encore des valeurs fixes et ne lisent pas ce point d'accès.

## Recalcul

Après un import de notes ou une modification des réglages :

1. `POST /api/trimestres/{année}/{numéro}/scores/recalcul` : recalcule et enregistre les scores de performance et de potentiel, et la catégorie de performance.
2. `POST /api/trimestres/{année}/{numéro}/9box/placement` : replace chaque collaborateur dans la matrice et enregistre sa catégorie de potentiel.
3. `POST /api/trimestres/{année}/{numéro}/vivier-releve` : enregistre le vivier de relève, si les écrans doivent le relire.

Les talents, hauts potentiels, matching, couverture des postes critiques, validations du comité, viviers thématiques et vigilance sont calculés à la consultation, avec les réglages du moment.

## Réglages modifiables

Les réglages d'un trimestre se consultent, se créent et se modifient par :
- `GET /api/trimestres/{année}/{numéro}/parametre` : lire les réglages.
- `POST /api/trimestres/{année}/{numéro}/parametre` : créer les réglages par défaut.
- `PUT /api/trimestres/{année}/{numéro}/parametre` : les modifier.

Toute modification incohérente est refusée avec un message explicite. Par exemple : des poids qui ne totalisent pas 100, ou un seuil élevé inférieur au seuil moyen.

Trois blocs sont facultatifs dans une modification : le seuil de couverture, les seuils 9-Box de l'axe potentiel et les seuils des catégories de performance. Absents, ils gardent leur valeur. Ainsi, un écran qui n'envoie que les seuils 9-Box « historiques » ne modifie que l'axe performance ; l'axe potentiel garde les siens.

| Bloc | Réglage | Défaut | Règle de validation | Module | Classeur (`00_PARAMETRES`) |
|---|---|---|---|---|---|
| Poids performance | Atteinte des objectifs | 40 | 0 à 100 ; total du bloc = 100 | Performance | Section 1 |
| | Compétences | 20 | | | |
| | Comportement | 20 | | | |
| | Contribution collective | 10 | | | |
| | Développement | 10 | | | |
| Poids potentiel | Agilité d'apprentissage | 20 | 0 à 100 ; total du bloc = 100 | Potentiel | Section 2 |
| | Leadership | 20 | | | |
| | Adaptabilité | 15 | | | |
| | Gestion de la complexité | 15 | | | |
| | Mobilité | 10 | | | |
| | Vision stratégique | 10 | | | |
| | Autonomie | 10 | | | |
| Catégories de performance (facultatif) | Exceptionnelle | 90 | 0 à 100 ; strictement décroissants | Performance | Section 4 |
| | Élevée | 80 | | | |
| | Solide | 70 | | | |
| | À renforcer | 60 | | | |
| Seuils 9-Box, axe performance | Seuil élevé | 85 | Supérieur au seuil moyen | 9-Box | Section 3, lignes 24 et 25 |
| | Seuil moyen | 70 | | | |
| Seuils 9-Box, axe potentiel (facultatif) | Seuil élevé | 85 | Supérieur au seuil moyen | 9-Box, catégorie de potentiel | Section 3, lignes 26 et 27 |
| | Seuil moyen | 70 | | | |
| Seuils talent | Talent — performance minimale | 85 | 0 à 100 | Talent | Section 5 |
| | Talent — potentiel minimal | 85 | 0 à 100 | Talent | |
| | Haut potentiel — potentiel minimal | 85 | 0 à 100 | Haut potentiel | |
| | Haut potentiel — performance minimale | 75 | 0 à 100 | Haut potentiel | |
| Poids succession | Compétences | 25 | 0 à 100 ; total du bloc = 100 | Succession | Section 6 |
| | Performance | 20 | | | |
| | Potentiel | 20 | | | |
| | Expérience | 15 | | | |
| | Leadership | 10 | | | |
| | Mobilité | 10 | | | |
| Barème expérience | Points par année d'ancienneté | 8 | 0 à 100 | Succession | Section 10 |
| | Plafond | 100 | 0 à 100 | | |
| Barème compétences | Points retirés par niveau manquant | 20 | 0 à 100 | Succession | Feuille `09_SUCCESSION` |
| | Niveau supposé d'une compétence absente | 3 | 0 à 5 | | |
| Seuils readiness | Ready Now | 90 | Strictement décroissants | Succession, Postes critiques | Section 7 |
| | Ready < 1 an | 80 | | | |
| | Ready 1-2 ans | 65 | | | |
| Couverture | Nombre minimum de successeurs | 1 | 1 à 10 | Postes critiques | Feuille `08_POSTES_CRITIQUES` |
| Points vigilance | Engagement faible | 25 | Positif ou nul | Vigilance | Section 8 |
| | Aucun mouvement depuis 4 ans | 20 | | | |
| | Mobilité non traitée | 15 | | | |
| | Aucun développement récent | 15 | | | |
| | Baisse de performance | 10 | | | |
| | Faible reconnaissance | 10 | | | |
| | Formation non réalisée | 5 | | | |
| Seuils vigilance | Seuil modéré | 30 | Élevé supérieur au modéré, et au plus égal au total des points | Vigilance | Section 9 |
| | Seuil élevé | 60 | | | |
| | Seuil d'engagement faible | 60 | | Vigilance | Section 8, ligne 60 |
| Poids des sources | Auto-évaluation | 25 | 0 à 100 ; total du bloc = 100 | Aucun pour l'instant | Absent du classeur |
| | Évaluation manager | 25 | | | |
| | Compétences | 25 | | | |
| | Questionnaire d'engagement | 25 | | | |

**Note sur les poids des sources.** Ce bloc est prévu pour consolider plusieurs sources d'évaluation en un score unique. Ses valeurs, réparties à parts égales, sont à arbitrer avec les RH, et aucun module ne les utilise encore.

**Note sur les trimestres existants.** Lors de la mise à jour, les seuils de l'axe potentiel d'un trimestre déjà configuré sont repris de ses seuils de performance, et non des valeurs par défaut : aucun collaborateur ne change de case. Les seuils des catégories de performance reçoivent les valeurs du classeur.

**Note sur la feuille `00_PARAMETRES`.** Les réglages ne sont pas encore lus depuis le classeur : un nouveau trimestre reçoit les valeurs par défaut ci-dessus, identiques à celles du classeur, puis se modifie dans l'application.
