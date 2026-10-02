# TALENT 360 BANK — What is left to build

Read-only audit, 2 Oct 2026. **Updated 2 Oct 2026** after section 3 items 1-4 (PR #40) items 6-7 (PR #41), item 5 (PR #42) and items 8-10 (branch `feature/alertes-notifs-campagne`, not merged yet). Compared against **`develop` @ `54168a2`** (= `origin/develop`, after PR #38 *page-import*).
Nothing in the code was changed. Sources:

- `docs/data/TALENT_360_BANK_Dataset_V1.xlsx`: every formula of the 14 sheets, with the cached values and the comments of `00_PARAMETRES`.
- `docs/prototype/index.html`: `NAV_DEF` and `ROLES.drh`, lines 489-503. The DRH role has 15 modules.
- The code on develop, exported to a scratch folder.

**How "Matches Excel?" was checked**
1. **By hand, on 3 collaborateurs: BP001, BP003 and BP019.** I recomputed each one with the code's rules (2 decimals HALF_UP, default weights and thresholds) and compared the result with the values stored in the Excel file. All 3 match.

   | | Perf | Cat. perf | Pot | 9-box | Talent / HP / Relève / Validé | Vigilance | Gaps prioritaires |
   |---|---|---|---|---|---|---|---|
   | BP001 | 61.60 | À renforcer | 75.40 | 2 À développer | Non / Non / Non / Non | 40 Modérée | 7 |
   | BP003 | 89.30 | Élevée | 58.00 | 7 Expert | Non / Non / Non / Non | 30 Modérée | 4 |
   | BP019 | 93.20 | Exceptionnelle | 91.85 | 9 Talent clé | Oui / Oui / Oui / Non (Comité "En attente") | 20 Faible | 0 |

   BP019 is also a successor on PST01 and PST02. Its matching is 90.23, Ready Now, as in Excel `09!K`. BP001 and BP003 are not successors on any post.
2. **Automatically, on all 100 collaborateurs.** I ran the dataset tests on develop, with the Excel file present (H2): `DatasetExcelComparaisonTest` 10/10, `TableauDeBordDatasetTest` 2/2, `AlertesDatasetTest` 2/2, plus Fiche, VueManager, VueEntite, Import and RequetesMoteur. **30/30 passed, none skipped.**

**Path convention.** Classes are under `src/main/java/com/talent360bank/talent360bank/`.

> ⚠ `MODULES_REPORT.md` (1 Oct) is **out of date** on the dashboard. It says 6 KPIs are hard-coded, but commit `b63bbbb` replaced them with computed values. The tables below supersede it.

---

## 1. Excel → calculations

Inputs that are simply typed in (notes, levels, flags, IDs) are not listed. The import covers them in `service/ImportClasseurService`.
Columns that only copy a name or direction from another sheet with `=` are grouped as "lookups". The app resolves them through entity joins.

### 00_PARAMETRES (50 values, no formulas)

| Sheet | Calculation | Status | Where | Matches Excel? |
|---|---|---|---|---|
| 00_PARAMETRES | All weights and thresholds of sections 1-10 (B6-B77) | **PARTIAL** | Defaults in `entity/Parametre.parDefaut`. Editable per quarter through `ParametreController` (GET/POST/PUT `/api/trimestres/{a}/{n}/parametre`), which also recalculates. | Yes: same values. **The sheet is not imported**, so changing it in Excel has no effect. **No screen** (`/parametres` = placeholder). |
| 00_PARAMETRES | "doit totaliser 100%" (sections 1, 2, 6) | DONE (stricter) | `entity/Poids` + `PoidsPerformance/PoidsPotentiel/PoidsSuccession` validation | Excel does not enforce it; the app rejects anything other than 100 |
| 00_PARAMETRES | Constants written into the formulas: 20 pts per missing level, default level 3, cap 100 | DONE (configurable) | `entity/BaremeCompetences`, `entity/BaremeExperience` | Yes |

### 00_DASHBOARD (KPIs and aggregates)

| Sheet | Calculation | Status | Where | Matches Excel? |
|---|---|---|---|---|
| 00_DASHBOARD A6 | Nb collaborateurs (`COUNTA`) | DONE | `DashboardService.construire` → `CollaborateurRepository.countByStatut(ACTIF)` | Yes: 100 |
| 00_DASHBOARD C6 | Postes critiques suivis (**`=15` hard-coded in Excel**) | DONE (computed) | `TableauDeBordService.synthese` → `nbPostesCritiques` | Yes: 15 |
| 00_DASHBOARD E6 | Talents validés (`COUNTIF 10!H="Oui"`) | DONE | `TableauDeBordService.synthese` → `ValidationComiteService.getTalentsValides` | Yes: 8 |
| 00_DASHBOARD G6 | Hauts potentiels proposés | DONE | `TableauDeBordService.synthese` → `TalentService.detecterHautsPotentiels` | Yes: 21 |
| 00_DASHBOARD A10 | Taux de couverture = `ROUND(COUNTIF(08!G>0)/15*100,0)` | **PARTIAL** | `PosteCritiqueService.tauxCouverture` | Value yes, display no: **93 in Excel, "93.33 %" in the app**. The definition also differs: the app uses "not in alert" and Excel uses "≥ 1 successor". They give the same result while min = 1. |
| 00_DASHBOARD C10 | Successeurs Ready Now (pairs) | DONE | `SyntheseTableauDeBord.nbSuccessionsReadyNow` | Yes: 16 (at date de référence 15/09) |
| 00_DASHBOARD E10 | Postes sans successeur | DONE | `SyntheseTableauDeBord.nbPostesSansSuccesseur` | Yes: 1 (PST13) |
| 00_DASHBOARD G10 | Compétences en gap Prioritaire | DONE | `CompetenceCollaborateurService.compterGapsPrioritaires` | Yes: 141 |
| 00_DASHBOARD A15 | Plans de développement suivis (**`=26` hard-coded in Excel**) | **MISSING** | — (sheet 11 not imported) | — |
| 00_DASHBOARD C15 | Plans en retard (`COUNTIF 11!J="Oui"`) | **MISSING** | — | — (Excel = 0, see the sheet 11 note) |
| 00_DASHBOARD E15 / G15 | Vigilance élevée / modérée | DONE | `TableauDeBordService.synthese` → `vigilanceParNiveau` | Yes: 10 / 35 |
| 00_DASHBOARD B21-B29 | 9-box distribution (9 `COUNTIF`) | DONE | `TableauDeBordService.synthese` → `repartition9Box`; grid in `DashboardService.neufBox` | Yes: 10/12/15/25/8/8/2/15/5 |
| 00_DASHBOARD rows 35-49 | List of critical posts without a successor | DONE | `PosteCritiqueService.detecterAlertes`, shown by `AlertesViewService` (`POSTE_SANS_SUCCESSEUR`) | Yes: PST13 only |
| (app only) | Engagement moyen, viviers actifs, à risque, per-vivier table | DONE (no Excel rule) | `DashboardService.engagement / viviers` | n/a |

### Per-person and per-post sheets

| Sheet | Calculation | Status | Where | Matches Excel? (BP001 / BP003 / BP019 + all 100 in tests) |
|---|---|---|---|---|
| 01_COLLABORATEURS G | Ancienneté = `ROUND((ref − entrée)/365.25, 1)` | DONE | `SuccessionService.ancienneteEnAnnees` (also on the Fiche) | Yes **if the date de référence is 15/09/2026**. The import page sets it (`dateReference` field). Otherwise it defaults to 30/09, which changes the expérience of 20 people (+0.12 on matching, no readiness change). |
| 02_PERFORMANCE I | Score perf = Σ notes × weights | DONE | `CalculService.calculerScorePerformance`, stored by `ScoreService.recalculerTrimestre` | Yes: 61.60 / 89.30 / 93.20 |
| 02_PERFORMANCE J | Catégorie perf (90/80/70/60) | DONE | `CalculService.categoriePerformance` | Yes |
| 03_POTENTIEL K | Score pot = Σ notes × weights | DONE | `CalculService.calculerScorePotentiel` | Yes: 75.40 / 58.00 / 91.85 |
| 03_POTENTIEL L | Catégorie pot (85/70) | DONE | `NeufBoxService.categoriePotentiel` | Yes: Moyen / Faible / Élevé |
| 04_9BOX E, G | Perf band / pot band | DONE | `NeufBoxService.niveauPour` | Yes |
| 04_9BOX H | Case 1-9 | DONE | `NeufBoxService.numeroCase` | Yes: 2 / 7 / 9 |
| 04_9BOX I | Category label | DONE | `NeufBoxService.placerTrimestre` + table `matrice_9box` (`Matrice9BoxInitializer`) | Yes (the app adds accents) |
| 05_REFERENTIEL | — (reference data, no calculation) | n/a | imported | — |
| 06_EMPLOYEE_SKILLS A | Lookup key `ID` & `Compétence` | n/a | the import matches by ID | — |
| 06_EMPLOYEE_SKILLS G | Gap = cible − actuel | DONE | `CompetenceCollaborateurService.evaluer` | Yes |
| 06_EMPLOYEE_SKILLS H | Gap status (≤0 / =1 / ≥2) | DONE | `CalculService.statutGap` (recomputed on every read) | Yes: 7 / 4 / 0 prioritaires |
| 07_POSTES P | Poste critique ? | DONE | `PosteCritiqueService.estCritique` | Yes: 15 |
| 07_POSTES E | Criticité (Très élevée / Élevée) | **not used** | stored on `Poste`, only displayed | — (prototype: Stratégique / Sensible changes the severity and the minimum) |
| 08_POSTES_CRITIQUES B-F | Lookups (name, direction, criticité, holder) | DONE | `Poste` entity + import | Yes |
| 08_POSTES_CRITIQUES G | Nb successeurs | DONE | `PosteCritiqueService.evaluerCouverture` | Yes (the app also excludes the holder and inactive people; no impact on the dataset) |
| 08_POSTES_CRITIQUES H | Meilleur matching (`MAXIFS`) | DONE | `PosteCritiqueService.evaluerCouverture` | Yes |
| 08_POSTES_CRITIQUES I | Couverture (ALERTE / Ready Now / <1 an / Partielle) | DONE | `PosteCritiqueService.couverturePour` | Yes (label "Successeurs insuffisants - ALERTE" vs "Aucun successeur - ALERTE") |
| 09_SUCCESSION N-R | Sub-score per required competence `MIN(100,MAX(0,100−20×(req−IFERROR(act,3))))` | DONE | `SuccessionService.scoreCompetences` | Yes (BP019 = 100 × 5) |
| 09_SUCCESSION E | Compétences score = `AVERAGE(N:R)` | DONE | `SuccessionService.scoreCompetences` | Yes |
| 09_SUCCESSION F, G | Perf / pot of the candidate | DONE | official `Score`, in `SuccessionService.evaluer` | Yes |
| 09_SUCCESSION H | Expérience = `MIN(100, anc × 8)` | DONE | `SuccessionService.scoreExperience` | Yes: 64.8 for BP019 at 15/09 |
| 09_SUCCESSION I, J | Leadership / Mobilité = notes 03!E / 03!H | DONE | `SuccessionService.evaluer` (manager's `Potentiel`) | Yes: 96 / 89 |
| 09_SUCCESSION K | Score matching (6 weights) | DONE | `SuccessionService.evaluer` → `moyennePonderee` | Yes: 90.23 (all 44 rows in tests) |
| 09_SUCCESSION L | Readiness (90/80/65) | DONE | `SuccessionService.readinessPour` | Yes: Ready Now |
| 09_SUCCESSION M | **Plus grand gap** (competence with the lowest sub-score) | DONE | `SuccessionService.plusGrandGap`, carried by `ResultatMatching.plusGrandGap` | Yes: all 44 rows (`SuccessionViviersDatasetTest`). Same tie rule as Excel: first competence of the post, so with no gap it is the 1st competence with `ecart` = 0 (BP019/PST01 → "Leadership") |
| 10_TALENTS D | Lookup of the 9-box label | DONE | `Score.positionBox` | Yes |
| 10_TALENTS E | Talent proposé (perf ≥ 85 AND pot ≥ 85) | DONE | `TalentService.estTalent` | Yes: Non / Non / Oui |
| 10_TALENTS F | Haut potentiel (pot ≥ 85 AND perf ≥ 75) | DONE | `TalentService.estHautPotentiel` | Yes: Non / Non / Oui |
| 10_TALENTS G | Comité decision (input) | DONE (import only) | `ImportClasseurService` → `ValidationComite` | Yes. **Cannot be entered in the app** |
| 10_TALENTS H | Talent validé = E AND G="Oui" | DONE | `ValidationComiteService.estTalentValide` | Yes: Non / Non / Non (BP019 En attente) |
| 10_TALENTS I | Vivier thématique (input, by direction) | DONE | `VivierThematiqueService.vivierDe` | Yes: Commercial ×3 |
| 10_TALENTS J | Vivier de relève = E OR F | DONE | `TalentService.getVivierReleve`, stored by `VivierReleveService` | Yes: Non / Non / Oui |
| 11_DEVELOPMENT_PLAN C, D | Lookups | **MISSING** | sheet not imported (`TODO(PlanDeveloppement)` in `ImportClasseurService`) | — |
| 11_DEVELOPMENT_PLAN J | En retard ? = échéance < 15/09/2026 AND statut ≠ Réalisé | **MISSING** | — | — ⚠ The data contradicts itself: 6 plans have the status "**En retard**", but every due date is ≥ 21/10/2026, so J = "Non" everywhere and C15 = 0. Ask the client which one counts. |
| 12_VIGILANCE E | Engagement faible (< 60) | DONE | `VigilanceService.detecterSignaux` | Yes |
| 12_VIGILANCE F-K | 6 flags (inputs) | DONE | imported. I (baisse perf) is replaced by the score history when a previous quarter exists | Yes (single quarter) |
| 12_VIGILANCE L | Indice = Σ points | DONE | `VigilanceService.calculerIndice` | Yes: 40 / 30 / 20 |
| 12_VIGILANCE M | Niveau (<30 / <60 / ≥60) | DONE | `VigilanceService.niveauPour` | Yes: Modérée / Modérée / Faible |
| 12_VIGILANCE D | Engagement /100 | input only | imported as is | The questionnaire file (Likert 1-5 + eNPS 0-10) **is not used**, and there is no conversion rule |

**Sheet summary:** every formula in the workbook is implemented and matches Excel on the whole dataset (the only condition is the date de référence: 15/09), **except**: PARTIAL: `00_PARAMETRES` is not imported (no screen either), and the taux de couverture display/definition. MISSING: the whole sheet `11_DEVELOPMENT_PLAN` (including `J` en retard), and `00_DASHBOARD` A15/C15.

---

## 2. Modules

Owner key: **Jas** = calculation/import · **Dou** = data/CRUD · **Ima** = UI.
"Real" means the page reads computed data from the engine. **No hard-coded KPI was found on any page of develop.** The literals in the templates are Thymeleaf fallback text that `th:text` overwrites. The only fixed numbers are display constants: 6 alerts on the dashboard (`TableauDeBordView.ALERTES_AFFICHEES`) and the top 10 for gaps (`AlertesViewService.TOP_GAPS_COMPETENCES`).

| # | Module | Backend (service / API) | Page (template + controller) | Real data? | Owner | Missing pieces |
|---|---|---|---|---|---|---|
| 01 | **Dashboard RH** | `TableauDeBordService`, `DashboardService`, `GET /api/dashboard/synthese` | `dashboard.html` + `DashboardController` (`/`), with a quarter selector | **Real.** Checked against `00_DASHBOARD` by `TableauDeBordDatasetTest` | Jas / Ima | Clicking a 9-box case does not open the list; no CSV export; "dernière revue" per vivier (no data); taux de couverture shown as 93.33 % (Excel 93); "plans en retard" KPI |
| 02 | **Dashboard DG** | **`TableauDeBordDgViewService` + `GET /api/dashboard/dg`**: same cards and 9-box as the RH dashboard; per critical post: nb successeurs, Ready Now, best successor, coverage; top talents with their target post | **None** | Real (API, checked against 08/09/10) | Jas (aggregates) / Ima | Page (Ima); "strategic" post criticité (client); late plans (blocked: sheet 11) |
| 03 | **Campagne / Import** | Import: `ImportService`, `ImportClasseurService`, `CampagneService`, `POST /api/imports`, `POST /api/trimestres/{a}/{n}/calcul`, lock `VerrouCalculTrimestre` | Import: `import.html` + `ImportPageController` (`/import`: upload, simulation, date de référence, recalculation, report, history). **Campagne: none** | Import **real** | Jas (import) / Dou / Ima | **`/import` is not in the sidebar.** Progress per direction is in `GET /api/trimestres/{a}/{n}/campagne` (page: Ima); no campaign object (dates, status: not in the data); no import of auto-évaluations (the import forces `MANAGER`); 11_DEVELOPMENT_PLAN not imported |
| 04 | **Collaborateurs** | `GET/POST/DELETE /api/collaborateurs` (CRUD); **`ListeCollaborateursViewService` + `GET /api/trimestres/{a}/{n}/collaborateurs`** (one row per active collaborateur, filters, sort, pagination) | **No list page.** Only the Fiche search by matricule | Real (API, checked against 00_DASHBOARD/10) | Dou (CRUD) / Ima; Jas for the summary columns | Page (Ima); CSV export |
| 05 | **9-Box** | `NeufBoxService`, `POST …/9box/placement` | `9box.html` + `PagesController.neufBox` → `NineBoxViewService` | **Real** | Jas / Ima | Axes differ from the prototype (prototype: potential on Y, performance on X; app: performance on the rows); no "thresholds applied" panel; no click → modal with matricule/perf/pot; no direction filter |
| 06 | **Compétences & gaps** | `CompetenceCollaborateurService` (per person), `GET /api/collaborateurs/{id}/competences`, `/api/competences` CRUD, **`CompetenceSyntheseService` + `GET /api/trimestres/{a}/{n}/competences`** (per skill, filters, top gaps, critical posts' missing skills) | **None** | Real (API, checked against 06/07/09) | Jas (aggregation) / Dou / Ima | Page (Ima); "critical competence for the bank" flag (not in Excel, client) |
| 07 | **Carrière & mobilité** | **None** | **None** | — | Dou (data) / Jas (KPIs) / Ima | Everything. Aspirations, mobility wishes, mobilities done and promotions: none of this is in the Excel file |
| 08 | **Engagement** | `QuestionnaireEngagement` (score /100 from `12!D`); average on the dashboard | **None** | Score only | Jas (scoring) / Dou (import) / Ima | 6 dimensions, eNPS, response rate, per-direction table, "dimension < 70 %" signals; import of the questionnaire file and its /100 conversion rule |
| 09 | **Viviers** | `VivierThematiqueService`, `GET /api/viviers-thematiques[/{code}]`, `VivierReleveService`, **`VivierSyntheseService` + `GET /api/viviers/synthese`** (5 thematic viviers + relève: effectif, talents, HP, Ready Now, averages, posts covered, gaps identified) | `viviers.html` + `PagesController.viviers` → `VivierService` | **Real, but the wrong scope**: it lists only the **vivier de relève** members (`AppartenanceVivier`), not the thematic viviers | Jas / Ima | Page: one card per vivier from `/api/viviers/synthese` (Ima); "Voir les membres" with target post, matching and readiness (data in `postes-cibles`); last review (no data). The prototype has 6 viviers (adds "Direction"); Excel and the code have 5 |
| 10 | **Postes critiques & succession** | `PosteCritiqueService`, `SuccessionService`, `/api/postes-critiques[/alertes,/synthese,/{id}]`, `/api/postes/{id}/candidats` | `postes-critiques.html` + `PagesController.postesCritiques` → `PosteCritiqueViewService` | **Real** | Jas / Ima | The engine part is done: every successor, with matching, readiness and plus grand gap, is in `PosteCritiqueRow.successeurs` and in the API. The page still shows only the best one (Ima). minimum and severity by criticité (Stratégique/Sensible); coverage bar |
| 11 | **Comité Talent** | `ValidationComiteService` (read), `GET /api/comite-talent[/talents-valides]` | `comite-talent.html` + `PagesController.comiteTalent` → `ComiteTalentViewService` (status filter) | **Real, read-only** | Dou (write) / Ima; Jas for the recalculation hook | Recording decisions (Valider / Réévaluer / Ne pas retenir); "Successions à valider" and their 5 decisions; writing to Historique; KPIs "en attente / successions à valider" |
| 12 | **Talent Passport** (Fiche) | `FicheCollaborateurViewService`, `GET /api/trimestres/{a}/{n}/collaborateurs/{m}/fiche` | `fiche-collaborateur.html` + `PagesController.ficheCollaborateur` | **Real** (`FicheCollaborateurDatasetTest`) | Jas / Ima | Quarter selector (always shows the latest quarter); the target post and main gap are in the model (`posteCible`, `successions[].gapCompetence`) but not displayed yet (Ima); development plan; engagement dimensions and eNPS; aspirations |
| 13 | **Alertes** | `AlertesViewService` (9 types, 3 severities) | `alertes.html` + `AlertesController` | **Real** (`AlertesDatasetTest`) | Jas / Ima | Prototype rules not covered: **development plan late** (sheet 11 not imported), **strategic competence insufficient** (no critical-competence flag). "Un seul successeur" and "Nouveau talent" are done |
| 14 | **Historique** | Only the import history (`ImportExcel`, shown on `/import`) | **None** | — | Dou / Ima | Audit trail of decisions and submissions (date, matricule, actor, event) |
| 15 | **Notifications** | **`NotificationsViewService` + `GET /api/notifications`** (counters = Alertes page, 10 alerts with links) and **`GET /api/notifications/badge`** (bell, 2 queries) | **None** | Real (API, derived from alerts) | Jas / Ima | Bell in the layout + Notifications page (Ima); per-notification on/off and read state (not needed: one RH user) |
| — | **Paramètres** (not in the prototype; it mentions an "écran Administration") | `ParametreController` GET/POST/PUT with validation and automatic recalculation | `placeholder.html` (`PagesController.parametres`) | **Placeholder** | Ima (screen) / Jas (import of 00_PARAMETRES, if the client wants it) | The whole screen; editing the 9-box labels (DB only); deciding whether the Excel sheet overrides the settings |
| — | Vue manager / Vue entité (not in the prototype) | `VueManagerViewService`, `VueEntiteViewService` + APIs | **None** (API only) | Real (dataset tests) | Ima | Pages, if wanted; N-2 hierarchy (`TODO` waiting for the client) |

---

## 3. What's left for Jasmine (calculation/import only)

Ordered by what RH needs first in the demo. Size: **S** ≤ ½ day · **M** 1-2 days · **L** > 2 days.

1. ~~**Plus grand gap per successor (`09!M`)**~~ **DONE**: `SuccessionService.plusGrandGap` → `ResultatMatching.plusGrandGap`. Excel tie rule, so with no gap it returns the 1st competence with `ecart` = 0. Checked on all 44 rows. **S**
2. ~~**Postes critiques: every successor**~~ **DONE**: `PosteCritiqueRow.successeurs` and the API's `successeurs[]` (with `plusGrandGap`), best matching first. The best-successor fields are unchanged. **S**
3. ~~**Synthesis per thematic vivier as a reusable service**~~ **DONE**: `VivierSyntheseService` + `GET /api/viviers/synthese` (5 thematic viviers + relève, with gaps identified). `DashboardService` uses it and shows the same numbers. **S**
4. ~~**Target post + best matching per collaborateur**~~ **DONE**: `PosteCibleService` + `GET /api/trimestres/{a}/{n}/postes-cibles`, plus `FicheCollaborateur.posteCible`. It considers every critical post (not only the ones where the person is an identified successor) and gives a `successeurIdentifie` flag. **S**
5. ~~**Compétences & gaps aggregate**~~ **DONE**: `CompetenceSyntheseService` + `GET /api/trimestres/{a}/{n}/competences`. Per skill: averages (current, target = `06!F` as in Excel, gap), nb and % with a gap, Prioritaire, distribution by level. Filters: entité (subtree), vivier, critical post. Sorted by average gap, top gaps, and for each critical post its requirements vs its successors plus their frequent largest gaps. Matches `06`, `07`, `09` and G10. **M**
6. ~~**Collaborateurs list summary**~~ **DONE**: `ListeCollaborateursViewService` + `GET /api/trimestres/{a}/{n}/collaborateurs`. Filters: entité (with subtree), 9-box case, talent, vivier, readiness, vigilance, text. Sort on any score column, pagination, 24 queries whatever the population. CSV export not done (UI side). **S**
7. ~~**Dashboard DG aggregates**~~ **DONE**: `TableauDeBordDgViewService` + `GET /api/dashboard/dg`. Same cards as the RH dashboard; per critical post: successors, Ready Now, best successor, coverage; top talents ranked by perf + pot (no Excel rule, to confirm with the client) with their target post. **S**
8. ~~**Additional alert rules**~~ **DONE**: "Un seul successeur" (Élevée, critical post not already in alert with exactly 1 successor) and "Nouveau talent identifié" (Moyenne, talent now but not in the previous quarter with scores; none on a first import, with an information sentence). On the Alertes page and the dashboard panel. **S**
9. ~~**Notifications**~~ **DONE**: `GET /api/notifications` (same counters as the Alertes page + 10 alerts with links) and `GET /api/notifications/badge` for the bell (2 queries, reuses the last computation for 60 s). No table, no read/unread state. **S**
10. ~~**Campaign progress**~~ **DONE**: `GET /api/trimestres/{a}/{n}/campagne[?entite=]`: per direction (or child entité), active, evaluated by their manager, %, missing, managers with missing evaluations. Self-evaluations and campaign dates are not in the data (said in `remarques`). **S**

### Blocked

| Item | Blocked by | Size once unblocked |
|---|---|---|
| **Development plans**: import `11_DEVELOPMENT_PLAN`, "En retard ?" rule, KPIs A15/C15, "plan late" alert, plan on the Passport | **Client**: is it in scope, and which wins, the "En retard" status or due date < reference date? (6 plans say "En retard" but none is overdue). **Dou**: `PlanDeveloppement` entity. | M |
| **Engagement**: score /100 built from the questionnaire, 6 dimensions, eNPS, response rate | **Client**: conversion rule from Likert 1-5 / eNPS 0-10 to /100. **Dou**: model and import of the answers. | L |
| **Import of `00_PARAMETRES`** (settings from the Excel file) | **Client**: should Excel override the settings, or only the Paramètres screen? | M |
| **Taux de couverture**: definition ("≥ 1 successor" vs "not in alert") and rounding (93 vs 93.33) | **Client** | S |
| **Criticité → minimum successors / severity** (Stratégique / Sensible) | **Client**: mapping from Excel "Très élevée / Élevée", minimum per post | S |
| **Auto-évaluation**: import + blend + écart (the engine part is done and inactive) | **Client**: blend weights, data source. **Dou**: import path / API for `AUTO` evaluations | M |
| **Recalculation after a Comité decision** + succession decisions | **Dou**: write endpoints for `ValidationComite` / successions | S |
| **Carrière & mobilité KPIs** | **Client**: there is no data (aspirations, mobilities). **Dou**: model | M |
| **Strategic competence insufficient alert** | **Client**: list of critical competences (not in Excel) | S |
| **Vue manager N-2** | **Client** (`TODO` in `VueManagerViewService`) | S |

**Demo note:** set the date de référence to **15/09/2026** on the import page. Otherwise the ancienneté and expérience values (and the matching, +0.12) differ from the Excel file for 20 collaborateurs.
