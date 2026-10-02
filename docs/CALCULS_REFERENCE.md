# TALENT 360 BANK — Calculation reference (weights, formulas, thresholds)

Reference for the weighting/calculation review meeting. Every rule below was read from the code and from
`docs/data/TALENT_360_BANK_Dataset_V1.xlsx` (formulas, values, and the "Commentaire" column of `00_PARAMETRES`).
Nothing was run in the application to produce this document. The worked example (section 6) was recomputed by hand
from the code formulas and compared with the values the Excel file has stored.

**Path convention.** All code paths are relative to `src/main/java/com/talent360bank/talent360bank/`.
Example: `service/CalculService.java:98-119` means lines 98 to 119 of
`src/main/java/com/talent360bank/talent360bank/service/CalculService.java`.

**"Configurable" means configurable through the REST API only.** The weights and thresholds live in the `Parametre`
entity (one row per quarter). They can be changed with `PUT /api/trimestres/{annee}/{numero}/parametre`
(`controller/ParametreController.java:120-143`), which also triggers a full recalculation of the quarter. The
**"Paramètres / Pondérations" screen does not exist yet**: `/parametres` renders the generic `placeholder` template
(`ui/controller/PagesController.java:83-87`). The `00_PARAMETRES` sheet of the Excel file is **not imported**.
A new quarter gets a copy of the previous quarter's settings, or the defaults hardcoded in
`entity/Parametre.java:139-180` (`Parametre.parDefaut`) if there is no previous quarter
(`service/TrimestreService.java:95-108`).

---

## 1. Overview — the calculation pipeline

```
Excel import (ImportClasseurService)
   │  01 collaborateurs · 02 notes perf (MANAGER) · 03 notes potentiel (MANAGER)
   │  05/06 compétences · 07/08 postes · 09 successeurs identifiés
   │  10 décision Comité + vivier thématique · 12 engagement + 6 drapeaux vigilance
   ▼
CalculTrimestreService.calculer  (after import, after POST …/calcul, after PUT …/parametre)
   │
   ├─ 1. ScoreService.recalculerTrimestre ──► Score.scorePerformance, scorePotentiel, categoriePerformance   [stored]
   │        uses: notes 02/03 + PoidsPerformance + PoidsPotentiel + PonderationSources + SeuilsCategoriePerformance
   │
   ├─ 2. NeufBoxService.placerTrimestre ────► Score.positionBox (9-box label), categoriePotentiel             [stored]
   │        uses: step 1 scores + SeuilsNeufBox (perf axis) + SeuilsNeufBox potentiel axis + table matrice_9box
   │
   └─ 3. VivierReleveService.constituerViviers ► AppartenanceVivier (vivier de relève)                       [stored]
            uses: step 1 scores + SeuilsTalent (talent OR haut potentiel)

Computed on every read (never stored):
   • Talent / Haut potentiel / Talent validé (Comité)      ← step 1 scores + SeuilsTalent + décision 10_TALENTS!G
   • Viviers thématiques                                    ← step 1 scores + direction → vivier (10_TALENTS!C,I)
   • Statut de gap de compétence                            ← 06 niveaux + SeuilsGapCompetence
   • Matching succession + Readiness                        ← step 1 scores + notes leadership/mobilité 03
                                                              + compétences 06 + exigences 07 + ancienneté 01
                                                              + PoidsSuccession + BaremeCompetences + BaremeExperience
                                                              + SeuilsReadiness
   • Couverture des postes critiques                        ← matching of the identified successors (09) + SeuilsCouverture
                                                              + SeuilsReadiness
   • Indice et niveau de vigilance                          ← engagement 12!D + drapeaux 12!F-K + score of the previous
                                                              quarter + PointsVigilance + SeuilsVigilance
   • Écart auto-évaluation / manager                        ← per-source scores + SeuilsAutoEvaluation
   • Dashboard, Alertes, Vue manager, Vue entité, Fiche     ← counts and averages of all the above
```

Orchestration: `service/CalculTrimestreService.java:43-51` (the three stored steps in order, one calculation at a
time per quarter). Triggered by `service/CampagneService.java:44-60` after an import (if the import status is not
ECHEC and it is not a simulation) and by `controller/ParametreController.java:120-143` after a change of settings.

**Who is calculated.** Only collaborateurs with `statut = ACTIF` (`entity/Collaborateur.java:108-110`). All 100
collaborateurs of the dataset are `Actif`.

**Common rounding rule.** Every score is rounded to **2 decimals, HALF_UP**, *before* it is compared with a
threshold (`service/CalculService.java:37-40`, `PRECISION_SCORE = 2`, `RoundingMode.HALF_UP`). The Excel file never
rounds its scores (for example `02_PERFORMANCE!I5` = 61.60000000000001).

---

## 2. Calculations

### 2.1 Score de Performance (per evaluation source)

| Rule (business side) | Where in code |
|---|---|
| **What**: performance score out of 100 for one evaluation (manager or auto-évaluation). | `service/CalculService.java:98-119` `CalculService.calculerScorePerformance(Performance, Parametre)` |
| **Inputs**: 5 notes /100 — Objectifs, Compétences, Comportement, Contribution, Développement (`02_PERFORMANCE` D–H). | `service/ImportClasseurService.java:505-509` (import, source = MANAGER) |
| **Formula**: `Σ(note_i × poids_i) / Σ(poids_i)`. The code divides by the actual sum of the weights, not by the constant 100. Both give the same result because the weights must total 100. | `service/CalculService.java:261-284` `moyennePonderee` |
| **Weights (default)**: Objectifs **40**, Compétences **20**, Comportement **20**, Contribution **10**, Développement **10**. | `entity/Parametre.java:143-145` |
| **Validation**: each weight is between 0 and 100. The sum must be **exactly 100**, otherwise the settings are rejected. | `entity/PoidsPerformance.java:16-44, 59-64`, `entity/Poids.java:17-26` |
| **Rounding**: 2 decimals, HALF_UP. | `service/CalculService.java:283` |
| **Missing/empty values**: a note outside 0–100, or a missing note, rejects the whole Excel row at import (entity `@NotNull @DecimalMin("0") @DecimalMax("100")`). The collaborateur then has no performance evaluation and is skipped with the reason *"Notes de performance absentes"*. If a null note still reaches the engine, it throws `DonneesIncompletesException`. All weights at 0 also throws. | `entity/Performance.java:57-85`, `service/ImportClasseurService.java:847-862`, `service/ScoreService.java:156-159`, `service/CalculService.java:266-281` |

**Source of the rule**: `02_PERFORMANCE!I` = `D*00_PARAMETRES!B6 + E*B7 + F*B8 + G*B9 + H*B10`. Weights are in
`00_PARAMETRES` section 1 (B6–B10 = 0.4 / 0.2 / 0.2 / 0.1 / 0.1). The code stores them as percentages (40 / 20 / …),
which is equivalent.
**Weights**: configurable through the API (`poidsPerformance` block). The defaults are hardcoded in
`Parametre.parDefaut`. No settings page exists.

### 2.2 Score de Potentiel (per evaluation source)

| Rule (business side) | Where in code |
|---|---|
| **What**: potential score out of 100 for one evaluation. | `service/CalculService.java:122-147` `CalculService.calculerScorePotentiel(Potentiel, Parametre)` |
| **Inputs**: 7 notes /100 — Agilité d'apprentissage, Leadership, Adaptabilité, Gestion de la complexité, Mobilité, Vision stratégique, Autonomie (`03_POTENTIEL` D–J). | `service/ImportClasseurService.java:543-548` |
| **Formula**: `Σ(note_i × poids_i) / Σ(poids_i)` | `service/CalculService.java:261-284` |
| **Weights (default)**: Learning **20**, Leadership **20**, Adaptabilité **15**, Complexité **15**, Mobilité **10**, Stratégie **10**, Autonomie **10**. | `entity/Parametre.java:146-149` |
| **Validation**: each weight is between 0 and 100. The sum must be exactly 100. | `entity/PoidsPotentiel.java:74-79` |
| **Rounding / missing values**: same as 2.1. Missing potential notes skip the collaborateur with the reason *"Notes de potentiel absentes"*. | `service/ScoreService.java:165-168` |

**Source**: `03_POTENTIEL!K` = `D*B14 + E*B15 + F*B16 + G*B17 + H*B18 + I*B19 + J*B20` (`00_PARAMETRES` section 2:
0.2 / 0.2 / 0.15 / 0.15 / 0.1 / 0.1 / 0.1).
**Weights**: configurable through the API (`poidsPotentiel`). The defaults are hardcoded.

### 2.3 Score officiel (manager × auto-évaluation blend)

| Rule (business side) | Where in code |
|---|---|
| **What**: the stored score of each axis, which blends the manager's evaluation and the auto-évaluation. | `service/CalculService.java:169-187` `scoreOfficiel` ; called by `service/ScoreService.java:201-215` |
| **Formula** (both present, auto weight > 0): `(score_manager × poids_manager + score_auto × poids_auto) / (poids_manager + poids_auto)`, 2 decimals HALF_UP. | `service/CalculService.java:185-186` |
| **Weights (default)**: manager **100**, auto **0**, so the manager's score is used alone. | `entity/Parametre.java:154`, `entity/PonderationSources.java:31-43` |
| **Validation**: manager + auto must total exactly 100. | `entity/PonderationSources.java:53-57` |
| **No auto-évaluation**, or auto weight = 0 → the manager's score is used unchanged. | `service/CalculService.java:171-173, 179-181` |
| **No manager evaluation** → the auto score is used only if the manager weight = 0. Otherwise there is **no official score**: the collaborateur is skipped with *"Evaluation du manager absente : auto-evaluation seule, pas de score officiel"*. | `service/CalculService.java:182-184`, `service/ScoreService.java:50-51, 169-173` |
| A score is stored only if **both** axes have an official score. | `service/ScoreService.java:214` |

**Source**: **no rule in the Excel file** (it holds one set of notes per collaborateur, and no auto-évaluation).
**Weights**: configurable through the API (`ponderationSources`, optional block). The defaults are hardcoded.
**Note**: no import path or API currently creates `AUTO` evaluations. The import always writes
`SourceEvaluation.MANAGER` (`service/ImportClasseurService.java:508, 547`). This blend therefore has no effect on
today's data (see D13).

### 2.4 Catégorie de Performance (5 levels)

| Rule (business side) | Where in code |
|---|---|
| **What**: individual label from the official performance score. | `service/CalculService.java:196-219` `categoriePerformance` ; stored by `service/ScoreService.java:279-280` |
| **Rule**: score ≥ 90 → Exceptionnelle; ≥ 80 → Élevée; ≥ 70 → Solide; ≥ 60 → À renforcer; otherwise Insuffisante. **Bounds are inclusive.** The score is compared after rounding to 2 decimals. | `service/CalculService.java:206-218` |
| **Thresholds (default)**: 90 / 80 / 70 / 60. | `entity/Parametre.java:163-165` |
| **Validation**: each threshold is between 0 and 100, strictly decreasing. | `entity/SeuilsCategoriePerformance.java:26-69` |
| **Missing**: no score or no thresholds → `DonneesIncompletesException`. | `service/CalculService.java:198-205` |

**Source**: `02_PERFORMANCE!J` (nested IF on `00_PARAMETRES!B31..B34`). Comment C34: *"< ce seuil = Insuffisante"*.
**Thresholds**: configurable through the API (`seuilsCategoriePerformance`). The defaults are hardcoded.

### 2.5 Matrice 9-Box (bands, case number, label) and Catégorie de Potentiel

| Rule (business side) | Where in code |
|---|---|
| **Band of an axis**: score ≥ seuil élevé → ÉLEVÉ (rank 3); ≥ seuil moyen → MOYEN (2); otherwise FAIBLE (1). Bounds are inclusive and use the rounded score. | `service/NeufBoxService.java:62-77` `niveauPour` |
| **Thresholds (default)**: performance axis élevé **85**, moyen **70**. Potential axis élevé **85**, moyen **70**. The two axes are configured separately. | `entity/Parametre.java:159-162` |
| **Validation**: 0–100, élevé > moyen. | `entity/SeuilsNeufBox.java:21-45` |
| **Case number**: `(rang_perf − 1) × 3 + rang_pot` → 1 to 9 (9 = both élevés, 1 = both faibles). | `service/NeufBoxService.java:91-95` `numeroCase` |
| **Label** (read from table `matrice_9box`, seeded at first start): 9 Talent clé · 8 Performant · 7 Expert · 6 Potentiel / Haut potentiel · 5 Confirmé · 4 À accompagner · 3 Potentiel à confirmer · 2 À développer · 1 À surveiller. | `config/Matrice9BoxInitializer.java:36-46` |
| **Catégorie de potentiel** (Élevé / Moyen / Faible) = the band of the potential axis, stored with the case. | `service/NeufBoxService.java:144-147, 223` |
| **Placement**: for each Score of the quarter. An inactive collaborateur or an incomplete score (one axis null) loses its case (`positionBox` and `categoriePotentiel` are set to null). | `service/NeufBoxService.java:181-231` |
| **Missing**: no thresholds → exception. An empty `matrice_9box` table → exception. | `service/NeufBoxService.java:249-255, 257-262` |

**Source**: `04_9BOX` E (band perf, `00_PARAMETRES!B24/B25`), G (band pot, `B26/B27`), H (case 1–9, nested IF),
I (label). `03_POTENTIEL!L` (catégorie potentiel, same thresholds B26/B27). Comments C24–C27:
*"Perf >= ce seuil = bande Elevee"*, *"70 <= Perf < seuil haut = Moyenne, sinon Faible"*, and the same for Pot.
**Thresholds**: configurable through the API (`seuilsNeufBox`, `seuilsNeufBoxPotentiel`). The labels are editable
only in the database table (no API or screen).

### 2.6 Talent (proposé)

| Rule (business side) | Where in code |
|---|---|
| **Rule**: `score_perf ≥ seuil_talent_perf` **AND** `score_pot ≥ seuil_talent_pot`. Inclusive. This is an AND, not an average. | `service/TalentService.java:56-60, 193-205` |
| **Thresholds (default)**: perf **85**, pot **85**. | `entity/Parametre.java:170-172` (1st and 2nd arguments) |
| **Population**: active collaborateurs with a Score that has both axes. Incomplete scores are counted and logged, not treated as talents. | `service/TalentService.java:221-241` |
| **Order**: performance score, descending. There is no secondary sort key. | `service/TalentService.java:38-39` |

**Source**: `10_TALENTS!E` = `IF(AND(02!I >= 00_PARAMETRES!B38, 03!K >= B39),"Oui","Non")`.
**Thresholds**: configurable through the API (`seuilsTalent.seuilPerformance/seuilPotentiel`). The defaults are
hardcoded.

### 2.7 Haut Potentiel (proposé)

| Rule (business side) | Where in code |
|---|---|
| **Rule**: `score_pot ≥ seuil_HP_pot` **AND** `score_perf ≥ seuil_HP_perf`. Inclusive. Independent of Talent (a collaborateur can be both). | `service/TalentService.java:117-123` |
| **Thresholds (default)**: pot **85**, perf **75**. | `entity/Parametre.java:170-172` (3rd and 4th arguments) |

**Source**: `10_TALENTS!F` = `IF(AND(03!K >= B40, 02!I >= B41),"Oui","Non")`.
**Thresholds**: configurable through the API (`seuilsTalent.seuilHautPotentielPotentiel/Performance`).

### 2.8 Talent validé (Comité Talent)

| Rule (business side) | Where in code |
|---|---|
| **Rule**: Talent proposé **AND** décision du Comité = "Oui". | `service/ValidationComiteService.java:56-59`, `service/resultat/DecisionComite.java:12-14` |
| **Input**: `10_TALENTS!G` (Oui / Non / En attente, accents and case ignored). Any other value rejects the row. | `service/ImportClasseurService.java:682-695` |
| **Missing decision** → EN_ATTENTE (not validated). | `service/ValidationComiteService.java:87` |
| The Comité only rules on **talents**. Decisions on non-talents are imported but never used. | `service/ValidationComiteService.java:66-69, 75-90` |

**Source**: `10_TALENTS!H` = `IF(AND(E="Oui", G="Oui"),"Oui","Non")`. **No weight or threshold.**

### 2.9 Vivier de relève

| Rule (business side) | Where in code |
|---|---|
| **Rule**: Talent **OR** Haut potentiel. Each collaborateur appears once, with both flags. Ordered by performance score, descending. | `service/TalentService.java:173-189` |
| **Storage**: rewritten at each calculation in `AppartenanceVivier` (origin `MOTEUR`, vivier code `RELEVE`). Rows with another origin are kept. | `service/VivierReleveService.java:85-119` |

**Source**: `10_TALENTS!J` = `IF(OR(E="Oui",F="Oui"),"Oui","Non")`. No own threshold (it uses 2.6 and 2.7).

### 2.10 Viviers thématiques

| Rule (business side) | Where in code |
|---|---|
| **Rule**: each scored active collaborateur goes in the vivier linked to their **direction**. The direction is the one frozen on the Score at calculation time. The direction → vivier mapping comes from `10_TALENTS` C/I (the first mapping in the sheet wins; a conflicting one rejects the row). Talent and HP flags are added for filtering. A direction without a vivier puts the collaborateur in "non classés". | `service/VivierThematiqueService.java:109-155`, `service/ImportClasseurService.java:645-678` |
| **Order**: performance descending (null scores last), then matricule. | `service/VivierThematiqueService.java:43-46` |

**Source**: `10_TALENTS!I` (input value). **No calculation and no threshold.**

### 2.11 Gap de compétence (statut)

| Rule (business side) | Where in code |
|---|---|
| **Formula**: `gap = niveau_cible − niveau_actuel` (levels 1–5). | `service/CompetenceCollaborateurService.java:58-64` |
| **Rule**, tested in this order: gap ≤ 0 → Maîtrise; gap = 1 → À développer (whatever the threshold); gap ≥ seuil → Prioritaire; otherwise À développer. | `service/CalculService.java:230-245` `statutGap` |
| **Threshold (default)**: **2**. Allowed range 2–4. | `entity/Parametre.java:166`, `entity/SeuilsGapCompetence.java:25-30` |
| **Missing**: one level null → status null. A level outside 1–5, or a missing level, rejects the row at import. The status in `06!H` is imported but **not used**: it is recomputed on every read. | `service/CalculService.java:231-233`, `service/ImportClasseurService.java:405-420, 923-938` |
| **Count "gaps prioritaires"** (dashboard, alerts): active collaborateurs only. | `service/CompetenceCollaborateurService.java:87-98` |

**Source**: `06_EMPLOYEE_SKILLS!G` = `F−E`. `H` = `IF(G<=0,"Maitrise",IF(G=1,"A developper",IF(G>=00_PARAMETRES!B77,"Prioritaire","A developper")))`.
Comment C77: *"Gap=1 -> A developper ; Gap=0 -> Maitrise"*.
**Threshold**: configurable through the API (`seuilsGapCompetence`).

### 2.12 Matching succession (candidat × poste) and its sub-scores

| Rule (business side) | Where in code |
|---|---|
| **Score matching** = `Σ(sous_score_i × poids_i) / Σ(poids_i)`, 2 decimals HALF_UP. A **missing sub-score counts as 0 and keeps its weight** (the weight is not redistributed). | `service/SuccessionService.java:234-267` `evaluer`, `:369-390` `moyennePonderee` |
| **Weights (default)**: Compétences **25**, Performance **20**, Potentiel **20**, Expérience **15**, Leadership **10**, Mobilité **10**. Sum = 100. | `entity/Parametre.java:150-152`, `entity/PoidsSuccession.java:66-71` |
| **Sub-score Performance / Potentiel** = the **official** scores (2.3) of the quarter. | `service/SuccessionService.java:250-251` |
| **Sub-score Leadership / Mobilité** = the raw notes Leadership and Mobilité of the **manager's** potential evaluation (03 E and H). Null if that evaluation does not exist. | `service/SuccessionService.java:253-254, 399-406` |
| **Sub-score Compétences**: for each competence required by the poste with a level > 0: `max(0, 100 − points_par_niveau_manquant × max(0, requis − actuel))`, then the **average over the required competences**. A competence missing from the candidate's profile counts as level `niveau_par_defaut`. Being above the required level gives no bonus. Competences are matched by ID. Null if the poste has no quantified requirement. | `service/SuccessionService.java:126-168`, `entity/Poste.java:76-88` |
| **Barème compétences (default)**: **20** points per missing level, default level **3**. | `entity/Parametre.java:158`, `entity/BaremeCompetences.java:26-38` |
| **Sub-score Expérience** = `min(plafond, ancienneté × points_par_an)`. Ancienneté = `ROUND((date_référence − date_entrée) / 365.25, 1)`. Ancienneté ≤ 0 gives 0. Unknown entry date gives null, which counts as 0. 2 decimals. | `service/SuccessionService.java:185-203, 213-221` (`JOURS_PAR_AN = 365.25`, line 63) |
| **Barème expérience (default)**: **8** points per year, cap **100**. | `entity/Parametre.java:156-157` |
| **Date de référence** = `Trimestre.dateReference`. If it is not given when the quarter is created, it defaults to the **last day of the quarter** (T3 → 30/09). | `entity/Trimestre.java:49-51`, `service/TrimestreService.java:61-79` |
| **Candidates (full ranking)**: active, score complete, not the current holder. Ties are broken by matricule (ascending). | `service/SuccessionService.java:303-352` |

**Source**: `09_SUCCESSION` E–K and N–R. Note A2: *"Matching = Competences x25% + … (poids modifiables)"*.
E = `AVERAGE(N:R)`. Each of N–R = `MIN(100,MAX(0,100-20*(niveau_requis-IFERROR(niveau_actuel,3))))`.
H = `MIN(100, 01!G × 00_PARAMETRES!B76)`. I = `03!E`. J = `03!H`.
K = `E*B45 + F*B46 + G*B47 + H*B48 + I*B49 + J*B50`. `01_COLLABORATEURS!G` = `ROUND((DATE(2026,9,15)-F)/365.25,1)`.
Comment C76: *"Plafonne a 100"*.
**Weights and barèmes**: configurable through the API (`poidsSuccession`, `baremeCompetences`,
`baremeExperience`). In the Excel file, the 20 points, the default level 3 and the cap 100 are written directly in
the formulas; they are not cells of `00_PARAMETRES`.

### 2.13 Readiness (délai de disponibilité)

| Rule (business side) | Where in code |
|---|---|
| **Rule**: matching ≥ 90 → Ready Now; ≥ 80 → < 1 an; ≥ 65 → 1-2 ans; otherwise > 2 ans. Inclusive, using the rounded matching score. | `service/SuccessionService.java:89-108` `readinessPour` |
| **Thresholds (default)**: 90 / 80 / 65. Strictly decreasing, 0–100. | `entity/Parametre.java:167-168`, `entity/SeuilsReadiness.java:47-51` |

**Source**: `09_SUCCESSION!L` (`00_PARAMETRES` B54/B55/B56). Comment C56: *"< ce seuil = Ready > 2 ans"*.
**Thresholds**: configurable through the API (`seuilsReadiness`).

### 2.14 Couverture des postes critiques

| Rule (business side) | Where in code |
|---|---|
| **Critical poste**: `07_POSTES!P` = "Oui" (case-insensitive). The criticité (Très élevée…) is **not used**. | `service/PosteCritiqueService.java:236-238` |
| **Nb successeurs**: successors identified in `09_SUCCESSION`, without duplicates, **excluding** the holder, unknown collaborateurs and non-active ones. An active successor without a complete score is counted, but is not matched. | `service/PosteCritiqueService.java:169-218` |
| **Meilleur matching**: the highest matching score among the identified successors that were matched (2.12). | `service/PosteCritiqueService.java:211-215` |
| **Niveau**: nb < minimum → ALERTE; no matched successor → PARTIELLE; best ≥ seuil Ready Now → Couverte Ready Now; ≥ seuil < 1 an → Couverte < 1 an; otherwise Partielle. | `service/PosteCritiqueService.java:127-151` `couverturePour` |
| **Thresholds (default)**: minimum **1** successor (range 1–10). It reuses readiness 90 and 80. | `entity/Parametre.java:169`, `entity/SeuilsCouverture.java:25-30` |
| **Taux de couverture** = `nb postes critiques not in ALERTE / nb postes critiques × 100`, 2 decimals HALF_UP. Null if there is no critical poste. | `service/PosteCritiqueService.java:226-234` |

**Source**: `08_POSTES_CRITIQUES` G = `COUNTIF(09!A:A, poste)`, H = `IFERROR(MAXIFS(09!K:K, 09!A:A, poste),0)`,
I = `IF(G=0,"Aucun successeur - ALERTE",IF(H>=B54,"Couverte - Ready Now",IF(H>=B55,"Couverte - <1 an","Partielle - a renforcer")))`.
Dashboard `00_DASHBOARD!A10` = `ROUND(COUNTIFS(08!G5:G19,">0")/(15)*100,0)`.
**Thresholds**: configurable through the API (`seuilsCouverture`, `seuilsReadiness`).

### 2.15 Indice et niveau de vigilance RH

| Rule (business side) | Where in code |
|---|---|
| **Indice** = sum of the points of the signals that are raised. 2 decimals. No signal = 0. | `service/VigilanceService.java:138-154` `calculerIndice` |
| **Points (default)**: Engagement faible **25** · Aucun mouvement 4 ans **20** · Mobilité non traitée **15** · Aucun développement récent **15** · Baisse de performance **10** · Faible reconnaissance **10** · Formation non réalisée **5**. Maximum = **100**. | `entity/Parametre.java:173-176`, `service/enums/SignalVigilance.java:19-38`, `entity/PointsVigilance.java:72` |
| **Validation**: points ≥ 0. The sum does **not** have to be 100. The seuil élevé must be ≤ the sum of all points. | `entity/PointsVigilance.java:10-15`, `entity/Parametre.java:400-412` |
| **Signal ENGAGEMENT_FAIBLE**: `score_engagement < seuil` (**strict**: 60 is not faible). No questionnaire, or a questionnaire without a score → **no signal**. | `service/VigilanceService.java:219-227` |
| **Signal BAISSE_PERFORMANCE**: if the collaborateur has a performance score both this quarter and in the **previous existing quarter** → raised if `score_courant < score_précédent` (any amplitude). Otherwise → the imported flag `12!I`. | `service/VigilanceService.java:229-237, 448-452` |
| **The other 5 signals**: imported flags `12_VIGILANCE` F, G, H, J, K. "Oui" → raised. "Non" or empty → not raised. Any other text rejects the row. | `service/VigilanceService.java:239-243`, `service/ImportClasseurService.java:124-131, 741-744`, `excel/Cellules.java:136-149` |
| **Engagement score**: an input, read from `12!D`, must be between 0 and 100 (otherwise the row is rejected). The app does not compute it. | `service/ImportClasseurService.java:119-121, 735-740` |
| **Niveau**: indice < 30 → FAIBLE; < 60 → MODÉRÉE; ≥ 60 → ÉLEVÉE (lower bound inclusive, upper bound exclusive). | `service/VigilanceService.java:163-178` `niveauPour` |
| **Thresholds (default)**: modéré **30**, élevé **60**, engagement faible **60**. | `entity/Parametre.java:177-178` |
| **Population**: active collaborateurs with **at least one** piece of vigilance data (questionnaire with a score, a fact declaration, or a manager evaluation this quarter). The others get **no indice** (shown as missing, not as 0). | `service/VigilanceService.java:383-432`, `service/EntreesVigilance.java:61-71` |
| **"À risque"** = MODÉRÉE or ÉLEVÉE. Order: indice descending, then matricule. | `service/resultat/ResultatVigilance.java:21-23`, `service/VigilanceService.java:419-421` |

**Source**: `12_VIGILANCE` E = `IF(D<00_PARAMETRES!B60,"Oui","Non")`, L = sum of `IF(x="Oui", B61..B67, 0)`,
M = `IF(L<B71,"Faible",IF(L<B72,"Moderee","Elevee"))`. Section 8 is titled *"(points, max 100)"*. C72:
*">= ce seuil = Vigilance elevee"*. A2: *"Cet indice n'est PAS une prediction de depart…"*.
**Points and thresholds**: configurable through the API (`pointsVigilance`, `seuilsVigilance`).

### 2.16 Écart auto-évaluation / manager

| Rule (business side) | Where in code |
|---|---|
| **Écart** per axis = `score_auto − score_manager` (each computed with 2.1/2.2). Positive means the collaborateur rates themselves higher. Null if one side is missing. | `ui/service/ResultatsCollaborateurs.java:216-218` |
| **Important** if `|écart| ≥ seuil` on performance **or** potential. | `ui/service/ResultatsCollaborateurs.java:227-243` |
| **Threshold (default)**: **15** points. | `entity/Parametre.java:155`, `entity/SeuilsAutoEvaluation.java:24-29` |
| Group average of the écarts: 2 decimals HALF_UP, over the members who have both evaluations. | `ui/service/ResultatsCollaborateurs.java:404-411` |

**Source**: **no rule in the Excel file.** Display-only threshold, configurable through the API.

### 2.17 Dashboard, Alertes and group views (counts and averages)

| Rule (business side) | Where in code |
|---|---|
| Nb talents, talents validés, hauts potentiels, vivier de relève (rules 2.6–2.9). | `service/TableauDeBordService.java:75-81` |
| Vigilance per level (FAIBLE/MODÉRÉE/ÉLEVÉE). À risque = MODÉRÉE + ÉLEVÉE. | `service/TableauDeBordService.java:83-93`, `service/resultat/SyntheseTableauDeBord.java:43-48` |
| Postes sans successeur = critical postes with nb successeurs = 0. Successions Ready Now = number of (poste, successeur) pairs at READY_NOW (one person on two postes counts twice). | `service/resultat/SyntheseTableauDeBord.java:51-65` |
| 9-box distribution = number of Scores per stored `positionBox`. Scores with no case are counted as "non placés". | `service/TableauDeBordService.java:98-110` |
| Engagement moyen = average of the engagement scores of **active** collaborateurs who have one, 2 decimals HALF_UP. | `ui/service/DashboardService.java:154-163, 224-230` |
| Per thematic vivier: headcount, average perf/pot (2 decimals), nb talents, nb members who are Ready Now on a critical poste, nb critical postes covered by a member. | `ui/service/DashboardService.java:189-216` |
| Group synthesis (Vue manager / Vue entité): averages over the known values, 2 decimals. % talents (Vue entité) = `nbTalents × 100 / nbAvecScore`, **1 decimal**. | `ui/service/ResultatsCollaborateurs.java:352-387`, `ui/service/VueEntiteViewService.java:204-206` |
| **Alerts and their severity**: poste with 0 successors → CRITIQUE. Poste under the minimum → ÉLEVÉE. Vigilance ÉLEVÉE → ÉLEVÉE. Manager evaluation missing (perf or pot) → ÉLEVÉE. Écart auto/manager ≥ seuil → MOYENNE. Talent with Comité EN_ATTENTE → MOYENNE. **Top 10** collaborateurs by number of prioritaire gaps → MOYENNE. Sorted by severity, then type. | `ui/service/AlertesViewService.java:177-296` (`TOP_GAPS_COMPETENCES = 10` line 76, sort line 136) |

**Source**: `00_DASHBOARD` for the shared KPIs (A6, E6, G6, A10, C10, E10, G10, E15, G15, B21–B29). Engagement
moyen, À risque, viviers actifs, alert severities and the top-10 rule have **no rule in the Excel file**. All the
values in this table are hardcoded in the code (counts, rounding precision, top 10).

---

## 3. Summary of all weights and thresholds

"API" means configurable via `PUT /api/trimestres/{a}/{n}/parametre`, stored per quarter. There is no screen
(see the note at the top). "Hardcoded" means it can only be changed by changing the code.

| Name | Value | Used in | Hardcoded / configurable | Default set at (file:line) | Excel source |
|---|---|---|---|---|---|
| Poids perf — Objectifs | 40 | 2.1 | API | `entity/Parametre.java:144` | `00_PARAMETRES!B6` = 0.4 |
| Poids perf — Compétences | 20 | 2.1 | API | `entity/Parametre.java:144` | B7 = 0.2 |
| Poids perf — Comportement | 20 | 2.1 | API | `entity/Parametre.java:144` | B8 = 0.2 |
| Poids perf — Contribution | 10 | 2.1 | API | `entity/Parametre.java:145` | B9 = 0.1 |
| Poids perf — Développement | 10 | 2.1 | API | `entity/Parametre.java:145` | B10 = 0.1 |
| Poids pot — Agilité d'apprentissage | 20 | 2.2 | API | `entity/Parametre.java:147` | B14 = 0.2 |
| Poids pot — Leadership | 20 | 2.2 | API | `entity/Parametre.java:147` | B15 = 0.2 |
| Poids pot — Adaptabilité | 15 | 2.2 | API | `entity/Parametre.java:147` | B16 = 0.15 |
| Poids pot — Complexité | 15 | 2.2 | API | `entity/Parametre.java:148` | B17 = 0.15 |
| Poids pot — Mobilité | 10 | 2.2 | API | `entity/Parametre.java:148` | B18 = 0.1 |
| Poids pot — Vision stratégique | 10 | 2.2 | API | `entity/Parametre.java:148` | B19 = 0.1 |
| Poids pot — Autonomie | 10 | 2.2 | API | `entity/Parametre.java:149` | B20 = 0.1 |
| Pondération source — manager | 100 | 2.3 | API | `entity/Parametre.java:154` | — (no rule) |
| Pondération source — auto-évaluation | 0 | 2.3 | API | `entity/Parametre.java:154` | — (no rule) |
| Seuil 9-box perf élevé | 85 | 2.5 | API | `entity/Parametre.java:160` | B24 = 85 |
| Seuil 9-box perf moyen | 70 | 2.5 | API | `entity/Parametre.java:160` | B25 = 70 |
| Seuil 9-box pot élevé (= catégorie potentiel Élevé) | 85 | 2.5 | API | `entity/Parametre.java:162` | B26 = 85 |
| Seuil 9-box pot moyen (= catégorie potentiel Moyen) | 70 | 2.5 | API | `entity/Parametre.java:162` | B27 = 70 |
| Catégorie perf Exceptionnelle | ≥ 90 | 2.4 | API | `entity/Parametre.java:164` | B31 = 90 |
| Catégorie perf Élevée | ≥ 80 | 2.4 | API | `entity/Parametre.java:164` | B32 = 80 |
| Catégorie perf Solide | ≥ 70 | 2.4 | API | `entity/Parametre.java:165` | B33 = 70 |
| Catégorie perf À renforcer | ≥ 60 | 2.4 | API | `entity/Parametre.java:165` | B34 = 60 |
| Talent — perf min | 85 | 2.6 | API | `entity/Parametre.java:171` | B38 = 85 |
| Talent — pot min | 85 | 2.6 | API | `entity/Parametre.java:171` | B39 = 85 |
| Haut potentiel — pot min | 85 | 2.7 | API | `entity/Parametre.java:172` | B40 = 85 |
| Haut potentiel — perf min | 75 | 2.7 | API | `entity/Parametre.java:172` | B41 = 75 |
| Poids matching — Compétences | 25 | 2.12 | API | `entity/Parametre.java:151` | B45 = 0.25 |
| Poids matching — Performance | 20 | 2.12 | API | `entity/Parametre.java:151` | B46 = 0.2 |
| Poids matching — Potentiel | 20 | 2.12 | API | `entity/Parametre.java:151` | B47 = 0.2 |
| Poids matching — Expérience | 15 | 2.12 | API | `entity/Parametre.java:152` | B48 = 0.15 |
| Poids matching — Leadership | 10 | 2.12 | API | `entity/Parametre.java:152` | B49 = 0.1 |
| Poids matching — Mobilité | 10 | 2.12 | API | `entity/Parametre.java:152` | B50 = 0.1 |
| Readiness — Ready Now | ≥ 90 | 2.13, 2.14 | API | `entity/Parametre.java:168` | B54 = 90 |
| Readiness — < 1 an | ≥ 80 | 2.13, 2.14 | API | `entity/Parametre.java:168` | B55 = 80 |
| Readiness — 1-2 ans | ≥ 65 | 2.13 | API | `entity/Parametre.java:168` | B56 = 65 |
| Vigilance — seuil engagement faible | < 60 | 2.15 | API | `entity/Parametre.java:178` | B60 = 60 |
| Vigilance — points engagement faible | 25 | 2.15 | API | `entity/Parametre.java:174` | B61 = 25 |
| Vigilance — points aucun mouvement 4 ans | 20 | 2.15 | API | `entity/Parametre.java:174` | B62 = 20 |
| Vigilance — points mobilité non traitée | 15 | 2.15 | API | `entity/Parametre.java:174` | B63 = 15 |
| Vigilance — points aucun développement récent | 15 | 2.15 | API | `entity/Parametre.java:175` | B64 = 15 |
| Vigilance — points baisse de performance | 10 | 2.15 | API | `entity/Parametre.java:175` | B65 = 10 |
| Vigilance — points faible reconnaissance | 10 | 2.15 | API | `entity/Parametre.java:175` | B66 = 10 |
| Vigilance — points formation non réalisée | 5 | 2.15 | API | `entity/Parametre.java:176` | B67 = 5 |
| Vigilance — seuil modérée | ≥ 30 | 2.15 | API | `entity/Parametre.java:178` | B71 = 30 ("faible <") |
| Vigilance — seuil élevée | ≥ 60 | 2.15 | API | `entity/Parametre.java:178` | B72 = 60 ("modérée <") |
| Expérience — points par année | 8 | 2.12 | API | `entity/Parametre.java:157` | B76 = 8 |
| Expérience — plafond | 100 | 2.12 | API | `entity/Parametre.java:157` | hardcoded `MIN(100,…)` in 09!H, comment C76 |
| Compétences matching — points par niveau manquant | 20 | 2.12 | API | `entity/Parametre.java:158` | hardcoded `100-20*(…)` in 09!N–R |
| Compétences matching — niveau par défaut si absent | 3 | 2.12 | API | `entity/Parametre.java:158` | hardcoded `IFERROR(…,3)` in 09!N–R |
| Gap compétence — seuil Prioritaire | ≥ 2 (range 2–4) | 2.11 | API | `entity/Parametre.java:166` | B77 = 2 |
| Couverture — nb minimum de successeurs | 1 (range 1–10) | 2.14 | API | `entity/Parametre.java:169` | implicit (`G=0` in 08!I) |
| Écart auto/manager important | ≥ 15 points | 2.16 | API | `entity/Parametre.java:155` | — (no rule) |
| Sum of each weight block | must be exactly 100 | 2.1, 2.2, 2.3, 2.12 | Hardcoded | `entity/Poids.java:8, 17-26` | "doit totaliser 100%" (title only, not enforced) |
| Score precision / rounding | 2 decimals, HALF_UP | all scores | Hardcoded | `service/CalculService.java:38, 40` | Excel does not round |
| Ancienneté | `/365.25`, 1 decimal HALF_UP | 2.12 | Hardcoded | `service/SuccessionService.java:63, 219-220` | `01!G` same formula |
| Date de référence (ancienneté) | last day of the quarter (30/09 for T3) unless set | 2.12 | Per quarter (API: create / modify trimestre) | `entity/Trimestre.java:49-51` | `DATE(2026,9,15)` hardcoded in 01!G |
| Notes perf / pot scale | 0–100 | import | Hardcoded | `entity/Performance.java:57-85`, `entity/Potentiel.java:48-88` | headers "(/100)" |
| Engagement scale | 0–100 | import | Hardcoded | `service/ImportClasseurService.java:120-121` | `12!D` "Engagement (/100)" |
| Competence levels scale | 1–5 | import, 2.11, 2.12 | Hardcoded | `service/ImportClasseurService.java:116-117` | `05` A2 "1=Debutant … 5=Referent" |
| Poste critique marker | "Oui" | 2.14 | Hardcoded | `service/PosteCritiqueService.java:66, 236-238` | `07!P` |
| Top N collaborateurs listed for gaps | 10 | 2.17 Alertes | Hardcoded | `ui/service/AlertesViewService.java:76` | — |
| Averages (dashboard, groups) | 2 decimals HALF_UP | 2.17 | Hardcoded | `ui/service/DashboardService.java:64`, `ui/service/ResultatsCollaborateurs.java:67` | — |
| % talents (Vue entité) | 1 decimal HALF_UP | 2.17 | Hardcoded | `ui/service/VueEntiteViewService.java:204-206` | — |
| Taux de couverture | 2 decimals HALF_UP | 2.14 | Hardcoded | `service/PosteCritiqueService.java:232-233` | `ROUND(…,0)` in 00_DASHBOARD!A10 |
| 9-box labels | see 2.5 | 2.5 | DB table only (no API/screen) | `config/Matrice9BoxInitializer.java:36-46` | `04_9BOX!I` |

---

## 4. Discrepancies between code and Excel

Checks done on the dataset for this document: all 100 performance scores were tested against 60/70/75/80/85/90 and
all 100 potential scores against 70/85, comparing the Excel float with the exact decimal (what the code uses after
rounding). **No collaborateur changes category.** All 100 collaborateurs are `Actif`, and the dataset has no empty
engagement cell.

| # | Topic | Excel | Code | Impact on the current dataset |
|---|---|---|---|---|
| D1 | **Date de référence for ancienneté (Expérience)** | `DATE(2026,9,15)` hardcoded in `01!G` (and in `11!J`) | `Trimestre.dateReference`. If not given at creation, it is the **last day of the quarter = 30/09/2026** (`entity/Trimestre.java:49-51`) | 20 collaborateurs get a different expérience score. Among the successors: BP019 (64.8 → 65.6), BP059 (67.2 → 68.0), BP062 (99.2 → 100), BP087 (46.4 → 47.2), BP089 (58.4 → 59.2), BP090 (12.8 → 13.6). Their matching rises by +0.12 points. **No readiness changes.** The dataset test uses 15/09 explicitly (`src/test/.../dataset/DatasetExcelComparaisonTest.java:100`). |
| D2 | **Rounding before thresholds** | Unrounded floats (e.g. 61.60000000000001) | 2 decimals HALF_UP, then compared | No flip today. A score of 84.995 would be "Moyen" in Excel and "Élevé" in the app. |
| D3 | **`00_PARAMETRES` is not imported** | "Tous les seuils et ponderations sont modifiables ici" (A2) | Defaults hardcoded in `Parametre.parDefaut` (same values today), or a copy of the previous quarter | Changing a weight in the Excel file has **no effect** on the app. |
| D4 | **Barème compétences and expérience cap** | 20 pts / niveau, default level 3, cap 100: hardcoded in the formulas, not in 00_PARAMETRES | Configurable (`BaremeCompetences`, `BaremeExperience.plafond`) | Same values. Rule ownership differs. |
| D5 | **Compétences sub-score (number of competences)** | Always `AVERAGE` of 5 cells N–R. The competence names are written into each row's formula. | Average over the poste's required competences that have a level > 0. A poste with none → null → 0 with its 25 % weight kept. | No difference with today's 07_POSTES (every poste has 5 levels). |
| D6 | **Nb successeurs / meilleur matching (08 G, H)** | `COUNTIF` of all 09 rows. `MAXIFS` over all rows. 0 when none. | Excludes the holder, unknown and non-active successors. Best = only among successors with a complete score. Null when none. | None today (no holder, no inactive successor in 09). |
| D7 | **Alert threshold and labels (08 I)** | Alert iff `G=0`, label "Aucun successeur - ALERTE" | Alert iff `nb < nbMinSuccesseurs` (default 1). Label "Successeurs insuffisants - ALERTE". Severity CRITIQUE (0) vs ÉLEVÉE (under minimum). "Couverte - <1 an" vs "Couverte - < 1 an". | Same result with min = 1 (PST13 is the only alert). |
| D8 | **Taux de couverture (dashboard)** | `ROUND(COUNTIFS(G>0)/(15)*100,0)` → **93**. The 15 is hardcoded. | Postes not in alert / number of critical postes × 100, 2 decimals → **93.33 %** (14/15) | Displayed value differs (93 vs 93.33). The meaning also differs if the minimum is set above 1. |
| D9 | **"Plus grand gap" (09!M)** | `INDEX(names, MATCH(MIN(N:R),N:R,0))` | **Not implemented** | When there is no gap at all, Excel still returns the first competence (BP019/PST01 → "Leadership" while all 5 = 100). |
| D10 | **Plans de développement (11) and their KPIs** | `11!J` "En retard ?" = `AND(H<DATE(2026,9,15), I<>"Realise")`. `00_DASHBOARD!A15` = 26 (hardcoded), `C15` = count of late plans | **Not imported, not implemented** (TODO in `service/ImportClasseurService.java:237-239`) | Two dashboard KPIs are missing from the app. |
| D11 | **Baisse de performance** | Input flag `12!I` only | The score history decides when a previous quarter score exists (strict decrease, any amplitude). Otherwise the flag `12!I`. | None with a single quarter. With two quarters, the imported flag can be overridden. **No Excel rule.** |
| D12 | **Vigilance population and empty engagement** | Computed for every row. An empty `D` makes `D<60` TRUE (empty = 0), so "Engagement faible" + 25 pts. | Only active collaborateurs with at least one piece of data. No questionnaire → no engagement signal. No data → no indice (not 0). | None today (no empty D, all have data). |
| D13 | **Auto-évaluation blend (2.3) and écart threshold (2.16)** | No rule, no data | Implemented (100/0 and 15 by default). But **no import or API creates AUTO evaluations** (import forces MANAGER). | Dormant feature. Note: `docs/moteur-de-calcul.md:476` still says the source weighting was removed, which is no longer true. |
| D14 | **Comité decisions on non-talents** | `10!G` = "Oui" for 76 non-talents. `H` still requires `E`. | Same result: decisions on non-talents are ignored | None. EN_ATTENTE talents (BP019, BP051) raise a "Talent sans décision" alert, which has no Excel rule. |
| D15 | **Alert rules and severities** | Only "Postes sans successeur" (00_DASHBOARD rows 32–49) | Also: vigilance élevée, manager evaluation missing, écart auto/manager, talent sans décision, top 10 gaps prioritaires, with CRITIQUE / ÉLEVÉE / MOYENNE levels | Logic in code with no rule behind it. |
| D16 | **Missing note** | An empty cell counts as 0 in the weighted sum (lower score) | The row is rejected at import, so the collaborateur is not scored | None today. |
| D17 | **Status filter** | No status column used | Only `ACTIF` collaborateurs are calculated | None (all 100 are Actif). |
| D18 | **Matching perf/pot source** | `02!I` / `03!K` (single source) | Official score (2.3). Leadership/Mobilité = manager notes only | Same values while manager weight = 100. |
| D19 | **9-box labels** | Without accents ("Talent cle", "A surveiller") | With accents ("Talent clé", "À surveiller"). Editable in DB. | Cosmetic. |
| D20 | **Weights must total 100** | Section titles say "doit totaliser 100%", but nothing enforces it | Rejected unless exactly 100. The code also divides by the actual sum. | None. |
| D21 | **Engagement score source** | `12!D` is a given value /100 | Imported as is. The questionnaire file (`docs/data/TALENT 360 BANK — Questionnaire d'engagement…xlsx`: 1–5 scales + a 0–10 recommendation question) is **not used** | No conversion rule exists anywhere. |
| D22 | **Previous quarter (for baisse de perf)** | n/a | "The most recent existing quarter before this one" (`service/VigilanceService.java:448-452`). If T2 is missing, T3 is compared with T1. | Rule not specified anywhere. |
| D23 | **Dashboard counts hardcoded in Excel** | `C6` = 15 (postes critiques), `A15` = 26 (plans) | Counted from the data | None today. |

---

## 5. Open questions for the meeting

**Weights and sources**
1. **Auto-évaluation vs manager**: keep 100 / 0? If a blend is wanted, which split (e.g. 70 / 30)? Where will the
   auto-évaluations come from (no import or API exists yet)?
2. **Manager evaluation missing**: today the collaborateur gets **no score at all** (not even the auto-évaluation)
   and an ÉLEVÉE alert is raised. Is that the rule?
3. **Matching Leadership / Mobilité** use the manager's raw notes only, while Performance / Potentiel use the
   official (possibly blended) score. Should they be consistent?
4. Should the weights be read from `00_PARAMETRES` at import, or only from the (not yet built) settings screen?
   Today the Excel values are ignored (D3).
5. Should the 20 pts / missing level, the default level 3 and the 100 cap be official parameters (they are
   configurable in the app but not listed in 00_PARAMETRES)?

**Boundary values** (all bounds are inclusive "≥" unless noted)
6. **Score exactly on a threshold**: 85.00 is Élevé / Talent; 70.00 is Moyen; 90.00 is Exceptionnelle. Confirm.
   In the dataset, PST14 / BP085 has a matching of **exactly 90 → Ready Now**.
7. **Rounding**: is rounding to 2 decimals before comparing acceptable (84.995 → 85.00 → Élevé)? Or should the raw
   value be compared, as Excel does?
8. **Engagement exactly 60** is *not* "faible" (strict <). Dataset: BP056 and BP093 = 60. Confirm.
9. **Vigilance indice exactly 30** → Modérée, **exactly 60** → Élevée. Dataset: BP003, BP005, BP023, BP038, BP061,
   BP065, BP085 at 30 (Modérée); BP007 and BP053 at 60 (Élevée). Confirm.
10. **Écart auto/manager exactly 15** counts as important (≥). Confirm.

**Missing data**
11. **No engagement questionnaire**: the app raises no signal (Excel would add 25 points). Which is right?
12. **Collaborateur with no vigilance data**: the app shows no indice; Excel shows 0 / Faible. Which is right?
13. **Missing criterion in matching** (no entry date, no potentiel evaluation, poste without required levels):
    today it counts 0 and **keeps its weight** (a penalty). Should the weight go to the other criteria instead?
14. **Competence absent from the candidate's profile** is assumed to be level 3. Is 3 the right default?
15. **Missing note** in 02/03: reject the row (app) or count it as 0 (Excel)?

**Rules to confirm**
16. **Date de référence**: 15/09 (Excel) or the end of the quarter (app default)? It changes the expérience score
    of 20 people (D1).
17. **Baisse de performance**: is any decrease (even 0.01 point) enough? Compared with the immediately previous
    quarter only, or with the last one that exists? Should the imported flag or the history win?
18. **Engagement score**: how is the /100 score built from the questionnaire (1–5 scales, 0–10 recommendation)?
19. **Talent thresholds vs 9-box "Talent clé"**: both are 85/85 today but they are set separately. If they diverge,
    a "Talent clé" may not be a Talent. Should they be linked?
20. **Haut potentiel** includes all talents (perf ≥ 85 also satisfies perf ≥ 75). Is HP meant to be "talent or
    near-talent", or "high potential but not yet a talent"?
21. **Couverture**: should the minimum stay at 1 successor? Should the criticité (Très élevée / Élevée) change the
    minimum or the severity? Today it is ignored.
22. **Taux de couverture**: "postes with ≥ 1 successor" (Excel) or "postes not in alert" (app)? Rounded to the unit
    or to 2 decimals?
23. **Ties**: talents and viviers are ordered by performance only (no tie-break). Matching and vigilance are broken
    by matricule. Is any business tie-break wanted (e.g. potential, then ancienneté)?
24. **Comité decisions on non-talents** (76 "Oui" in 10!G) are ignored. Is that data meaningful?
25. **Above-required competence level** gives no bonus (capped at 100 per competence). Confirm.
26. **Gap = 1 is always "À développer"**, whatever the threshold. Confirm.
27. **Development plans** (sheet 11) and the "plans en retard" KPI: are they in scope?
28. **Inactive collaborateurs** are excluded everywhere, including as identified successors. Confirm.
29. **Alert severities and the "top 10" gap list** come from the app, not from the business rules. Validate or
    adjust.

---

## 6. Worked example — BP019, Youssef El Khatib (T3 2026)

Chosen because he goes through every rule: talent, haut potentiel, Comité "En attente", successor on two critical
postes, and a vigilance signal. Values are from `TALENT_360_BANK_Dataset_V1.xlsx`. The computation follows the code
formulas with the default settings (section 3). It was done by hand, not by running the app.

**Raw inputs**

| Sheet | Data |
|---|---|
| 01_COLLABORATEURS (row 23) | Entrée 31/07/2018 · Direction Réseau Retail · Statut Actif · Ancienneté (Excel) 8.1 |
| 02_PERFORMANCE | Objectifs 92 · Compétences 92 · Comportement 96 · Contribution 91 · Développement 97 |
| 03_POTENTIEL | Learning 94 · Leadership 96 · Adaptabilité 88 · Complexité 87 · Mobilité 89 · Stratégie 99 · Autonomie 88 |
| 06_EMPLOYEE_SKILLS | All 25 competences: actuel 5, cible 5 |
| 10_TALENTS | Comité = **En attente** · Vivier thématique = Vivier Commercial |
| 12_VIGILANCE | Engagement 95 · Aucun mouvement 4 ans **Oui** · all other flags Non |
| 09_SUCCESSION | Identified successor on **PST01** (Directeur régional) and **PST02** (Directeur d'agence) |
| 07_POSTES | PST01 requires Leadership 5, Management d'équipe 4, Développement commercial 4, Risque bancaire 3, Digital Banking 3. PST02 requires Management 4, Dév. commercial 4, Risque 3, Leadership 4, Digital 3. |

**Step 1 — Score de performance** (2.1)
`(92×40 + 92×20 + 96×20 + 91×10 + 97×10) / 100 = (3680 + 1840 + 1920 + 910 + 970) / 100 = 9320 / 100 =` **93.20**
Excel `02!I` = 93.2 ✔

**Step 2 — Score de potentiel** (2.2)
`(94×20 + 96×20 + 88×15 + 87×15 + 89×10 + 99×10 + 88×10) / 100 = (1880 + 1920 + 1320 + 1305 + 890 + 990 + 880) / 100 = 9185 / 100 =` **91.85**
Excel `03!K` = 91.85000000000001 ✔

**Step 3 — Score officiel** (2.3): there is no auto-évaluation, so the official scores are the manager's:
perf **93.20**, pot **91.85**.

**Step 4 — Catégorie de performance** (2.4): 93.20 ≥ 90 → **Exceptionnelle**. Excel `02!J` ✔

**Step 5 — 9-box** (2.5)
Perf 93.20 ≥ 85 → ÉLEVÉ (rank 3). Pot 91.85 ≥ 85 → ÉLEVÉ (rank 3), so catégorie potentiel = **Élevé**.
Case = (3 − 1) × 3 + 3 = **9 → "Talent clé"**. Excel `04!H` = 9, `04!I` = "Talent cle" ✔

**Step 6 — Talent / HP / Comité / Viviers** (2.6–2.10)
- Talent: 93.20 ≥ 85 AND 91.85 ≥ 85 → **Oui** (Excel `10!E` ✔)
- Haut potentiel: 91.85 ≥ 85 AND 93.20 ≥ 75 → **Oui** (Excel `10!F` ✔)
- Talent validé: Comité = En attente → **Non** (Excel `10!H` ✔). The app raises an alert
  *"TALENT_SANS_DECISION"*, severity MOYENNE.
- Vivier de relève: Talent OR HP → **Oui** (Excel `10!J` ✔)
- Vivier thématique: direction Réseau Retail → **Vivier Commercial**

**Step 7 — Gaps de compétence** (2.11): every gap = 5 − 5 = 0 → **Maîtrise** × 25, so 0 prioritaire.

**Step 8 — Matching PST01** (2.12, 2.13)

| Criterion | Calculation | Sub-score | × weight |
|---|---|---|---|
| Compétences | Leadership 5 vs 5, Management 5 vs 4, Dév. comm. 5 vs 4, Risque 5 vs 3, Digital 5 vs 3 → no missing level → 100 each → average | 100.00 | × 25 = 2500 |
| Performance | official score | 93.20 | × 20 = 1864 |
| Potentiel | official score | 91.85 | × 20 = 1837 |
| Expérience | ancienneté at 15/09/2026 = 2968 days / 365.25 = 8.126 → **8.1** × 8 = 64.8 (< 100) | 64.80 | × 15 = 972 |
| Leadership | note 03!E | 96 | × 10 = 960 |
| Mobilité | note 03!H | 89 | × 10 = 890 |

Matching = 9023 / 100 = **90.23** ≥ 90 → **Ready Now**. Excel `09!K8` = 90.23, `L8` = "Ready Now" ✔
PST02 gives the same result (all required levels ≤ 5): **90.23, Ready Now** (Excel row 12 ✔).

With the app's **default** date de référence (30/09/2026, see D1): 2983 days → 8.167 → **8.2** years → expérience
**65.60** → matching = (2500 + 1864 + 1837 + 984 + 960 + 890) / 100 = **90.35**. Still Ready Now.

**Step 9 — Couverture of PST01 and PST02** (2.14)
- PST01: successors BP035 (95.20), BP013 (95.10), BP019 (90.23), BP005 (84.92). The holder BP022 is not among them.
  Nb = 4 ≥ 1. Best = 95.20 ≥ 90 → **Couverte – Ready Now** (Excel `08!I5` ✔).
- PST02: successors BP013 (95.10), BP019 (90.23), BP005 (84.92). Nb = 3, best 95.10 → **Couverte – Ready Now**
  (Excel `08!I6` ✔).
- BP019 counts **twice** in "Successeurs Ready Now" on the dashboard (once per poste).

**Step 10 — Vigilance** (2.15)
- Engagement faible: 95 < 60? No → no signal.
- Baisse de performance: there is no previous quarter, so the imported flag `12!I` = Non → no signal.
- Aucun mouvement 4 ans: Oui → **+20**. All other flags: Non.
- Indice = **20.00** < 30 → **FAIBLE** (not "à risque"). Excel `12!L` = 20, `M` = "Faible" ✔

**Final result for BP019**: Perf 93.20 (Exceptionnelle) · Pot 91.85 (Élevé) · 9-box case 9 "Talent clé" · Talent
Oui, HP Oui, validated No (Comité pending, MOYENNE alert) · Vivier de relève Oui, Vivier Commercial · Ready Now on
PST01 and PST02 (90.23 at 15/09, 90.35 at 30/09) · Vigilance 20 / Faible.
