# Modules report — prototype vs current application

Analysis only, 1 Oct 2026. Branch `feature/source-evaluation` (= `develop` after PR #31). Nothing was changed in the code.

**Sources**
- **Prototype:** `docs/prototype/index.html`. One HTML file, front-end only, fictitious data generated in JavaScript, 4 simulated roles. Git-ignored (client file).
- **Specification:** `Documents/RH_TALENT_MANAGEMENT_DC.pdf`. One page: the **class diagram** (DC = diagramme de classes). It has no screens, so it is used for data and behaviour. Where it disagrees with the prototype, the point is listed under [Differences to confirm](#7-differences-to-confirm); nothing was chosen.
- **Calculation reference:** `docs/data/TALENT_360_BANK_Dataset_V1.xlsx` (sheets `00_DASHBOARD` to `12_VIGILANCE`), which is what the engine reproduces.

**Context:** only RH logs in (`UtilisateurRH`). The prototype's collaborateur, manager and comité roles are not in scope unless the client says otherwise (see D1).

---

## 1. Prototype modules

The prototype's sidebar is built from `NAV_DEF` (`index.html:495-503`). The DRH role sees 15 numbered modules (`ROLES.drh`, line 492). The collaborateur role adds 2 unnumbered views ("Mon profil", "Mon engagement", line 490). There is also a bell icon (line 208) and a detail modal used by every module (lines 233-238, 546).

| # | Module (prototype view id) | What it shows | Actions | Data needed |
|---|---|---|---|---|
| 01 | **Tableau de bord RH** (`dashrh`, l. 577-603) | 10 KPIs: population, talents validés, hauts potentiels, viviers actifs, postes critiques, % couverture succession, Ready Now, engagement % (+ response count), postes sans relève, gaps critiques. Clickable 9-box of the whole population. Top 6 priority alerts. Viviers summary table: effectif, perf. moy., pot. moy., Ready Now, postes couverts, dernière revue | Click a 9-box case → modal listing its people (matricule, nom, direction, poste, perf, pot). "Exporter CSV" | Scores, 9-box placement, committee decisions, viviers and members, critical posts and successors, readiness, engagement survey, alerts |
| 02 | **Tableau de bord DG** (`dashdg`, l. 605-634) | 4 KPIs: talents, viviers, postes critiques, % succession. 9-box. "Risque de dépendance": Ready Now, postes sans relève, plans en retard / gaps, then bench strength (number of successors) per *strategic* post as bars. Table "Top talents & successeurs" (8): poste, **poste cible**, matching, readiness | Row → Talent Passport | Same as 01 + post criticality (Stratégique/Sensible) + a target post per talent |
| 03 | **Campagne d'évaluation** (`campagne`, l. 637-718) | DRH: campaign "Talent Review 2026" (status, period 01/09 → 15/10/2026), progress of self-evaluations and of manager evaluations (counts + bars), table per direction (effectif, auto-éval. soumises, éval. manager complétées). Manager: team to evaluate with both statuses. Collaborateur: self-evaluation form (career plan, mobility, horizon, competences 1-5) | DRH: **"Lancer le calcul"**. Manager: "Évaluer" → perf and pot sliders 0-100 + comment. Collaborateur: Enregistrer / Soumettre (then locked) | Campaign (dates, status), evaluation status per person and source, aspirations, competence self-ratings |
| 04 | **Collaborateurs** (`collab`, l. 721-769) | Table: matricule, nom, direction, poste, grade, perf, pot, 9-box case, status (Talent / Haut potentiel), vivier, readiness | Filters: direction, status (talents validés / HP / en vivier), text search (nom or matricule). Row → Talent Passport. "Exporter CSV" (14 columns, `;`, UTF-8 BOM) | Identity, org, grade, scores, case, committee status, vivier, readiness, poste cible, matching |
| 05 | **Matrice 9-Box** (`box9`, l. 771-784) | 3×3 grid, **potential on the Y axis, performance on the X axis**, count per case + label/emoji. "Seuils appliqués" panel (perf and pot: <60 faible, 60-79 moyen, ≥80 élevé; talent proposé = perf ≥80 AND pot ≥80) | Click a case → modal list | Scores, 9-box thresholds |
| 06 | **Compétences & Gaps** (`competences`, l. 787-808) | Table per competence: average current level, average target level, gap (heat colour). 🔴 = competence "critique pour la banque" ("poids renforcé dans le matching") | Filter by direction | Competences per person (current, target), "critical" flag per competence |
| 07 | **Carrière & Mobilité** (`carriere`, l. 811-826) | KPIs: internal mobility rate, mobilities done, mobilities wished, promotions envisageables (= Ready Now). Table: poste, projet, mobilité souhaitée, horizon, poste cible, readiness | Row → Talent Passport | Aspirations (projet, mobilité, horizon), mobilities done, poste cible, readiness |
| 08 | **Engagement & Fidélisation** (`engagementRH`, l. 830-869) | KPIs: global engagement %, response rate, eNPS average /10, number of dimensions <70 %. Result per dimension (6: management, reconnaissance, formation, évolution professionnelle, conditions de travail, communication interne). "Signaux" panel (dimension <70 %). Table per direction (effectif, réponses, score) | Filter by direction | Survey answers per dimension + eNPS + comment + status + date |
| 09 | **Viviers** (`viviers`, l. 904-923) | One card per pool (6: **Direction, Management, Digital, Risques, Expertise, Commercial**): effectif, perf moy., pot moy., Ready Now, postes couverts, gaps identifiés, dernière revue | "Voir les membres" → modal (poste actuel, poste cible, matching, readiness) → Passport | Pools and members, poste cible, matching, readiness, main competence gap, review date |
| 10 | **Postes critiques & Succession** (`postes`, l. 926-942) | Coverage bar (posts with ≥1 successor / total). One card per critical post (15): title, direction, **criticité (Stratégique / Sensible)**, **minimum successors per post**, successors with readiness, matching %, **succession status** ("Successeur proposé") | Successor → Passport | Critical posts, criticality, minimum depth per post, successors (matching, readiness, status) |
| 11 | **Comité Talent** (`comite`, l. 945-991) | KPIs: talents proposés en attente, postes critiques, successions à valider, postes sans relève. List "Talents à valider". List "Successions à valider" | Talent: **Valider / Réévaluer / Ne pas retenir**. Succession: **Valider / Valider avec plan / Maintenir en vivier / Réévaluer / Ne pas retenir**. Each decision is written to the history | Proposed talents, decisions, succession proposals and their status |
| 12 | **Talent Passport** (`passport`, l. 994-1059) | Search (matricule or nom). Header (grade, ancienneté, agence, manager). Perf, pot, engagement, 9-box case. Competences current vs target with gap. Aspirations, talent status, vivier. Survey detail (6 dimensions, eNPS, comment). Matching for the target post (compétences, expérience, perf, pot, mobilité) + readiness + main gap. **Development plan** (objective, actions with % progress) | Search | Everything above, plus the development plan |
| 13 | **Alertes** (`alertes`, l. 1062-1070; rules l. 458-474) | Alerts grouped CRITIQUE / ATTENTION / INFORMATION: post without successor (critical if Stratégique), only one successor, **development plan action late** (<30 %), new talent identified, **strategic competence insufficient** (critical competence, gap < -1) | None (read only) | Critical posts and successors, plans, talents, critical competences |
| 14 | **Historique** (`historique`, l. 1073-1084) | Audit trail: date, matricule, actor (Collaborateur / Manager / Comité), event (status changes, decisions, submissions) | Filter by matricule | A log of every decision and submission |
| 15 | **Notifications** (`notifications`, l. 1087-1121) | Per-role reminders (DRH: posts without successor, HP without plan, comité session scheduled, late action) + counter on the bell | On/off switch per notification (does nothing) | Derived from the above |
| · | **Mon profil** (`moncompte`, collaborateur) | The Talent Passport of the logged-in collaborateur | — | Same as 12 |
| · | **Mon engagement** (`engagement`, collaborateur) | Survey form: 6 dimensions (Likert 1-5), eNPS 0-10, free comment | Enregistrer / Soumettre (locked after) | Survey per quarter |

The prototype also mentions an **"écran Administration"** where the thresholds can be changed (l. 781). The screen doesn't exist in the prototype; our equivalent is Paramètres.

---

## 2. Current state per module

**Legend**
- **Page:** ✅ works with real data · 🟡 exists but placeholder, fake data or partial · ❌ missing.
- **Backend:** what exists, and what is missing for the prototype.
- **Bugs:** see [section 2.1](#21-bugs-and-predictable-failures) for details.

### 01 Tableau de bord RH
- **Page 🟡** `GET /` → `templates/dashboard.html`, `ui/service/DashboardService.java`.
  - 6 of the 9 KPIs are **hard-coded** (`DashboardService.java:43-49`: "9", "18", "1", "23", "5", "100").
  - "Talents valides" counts the **proposed** talents (`DashboardService.java:37,59` → `TalentService.compterTalents`), not the ones validated by the committee.
  - The subtitle says "donnees mockees" (`dashboard.html:19`).
  - No 9-box grid, no alerts panel, no viviers table.
- **Backend:** `TableauDeBordService` + `GET /api/dashboard/synthese?annee=&numero=` (`TableauDeBordController.java:27`) already returns: proposed and validated talents, hauts potentiels, vigilance per level (and "à risque"), number of critical posts, coverage rate, critical-post alerts, 9-box distribution, number not placed.
  - Missing: Ready Now count (successors with readiness READY_NOW, from `PosteCritiqueService`), average engagement + response count (`QuestionnaireEngagement`), active pools, "gaps critiques", the viviers summary (averages per pool).
- **Bugs:** B1, B2, B5.

### 02 Tableau de bord DG
- **Page ❌.**
- **Backend:** everything except two items is in `TableauDeBordService` + `PosteCritiqueService` (successors per post).
  - Missing: a "poste cible" for every talent. We only have candidates for the 15 critical posts (`09_SUCCESSION`), not a next post for each person.
  - Missing: post criticality as Stratégique / Sensible. Ours is free text from the workbook ("Tres elevee"…), `Poste.criticite` (E11).
- **Note:** DG is a separate role in the prototype (`ROLES.comite`). With RH only, this can be a second tab of the dashboard (see D1).

### 03 Campagne d'évaluation
- **Page ❌.**
- **Backend:**
  - Quarters: `GET/POST /api/trimestres`, `PUT /api/trimestres/{a}/{n}` (reference date), `POST /api/trimestres/{a}/{n}/calcul` (= "Lancer le calcul", already locked against double clicks, 409).
  - Import: `POST /api/imports`, `GET /api/imports` (journal).
  - Evaluations by source exist since R1d. `PerformanceRepository/PotentielRepository.findMatriculesEvaluesDuTrimestre(t, source)` can give "self-evaluations / manager evaluations received" per direction. No service or API for those counts yet.
  - Missing: campaign period and status (no `Campagne` entity; `CampagneService` only drives the import + calculation); per-direction progress.
  - The in-app forms (self-evaluation, manager evaluation) are **out of scope**: RH only, evaluations arrive as files (D2).
- **Blocked:** AUTO file import (Dou), client questions 3, 4, 7 (scope, granularity and folder of the files).

### 04 Collaborateurs
- **Page ❌.**
- **Backend:** `GET /api/collaborateurs` (`CollaborateurController.java:29`) returns identity + org only (`CollaborateurResponse`): no scores, case, status, vivier or readiness.
  - `ResultatsCollaborateurs.charger` (used by the manager and entité views) already loads scores, categories, case, talent / HP / relève, engagement and vigilance for any group in a fixed number of queries. A "whole population" list can reuse it (like the entité view does for a subtree).
  - Missing: the list service + filters + CSV export, and readiness / poste cible (only for `09_SUCCESSION` candidates).

### 05 Matrice 9-Box
- **Page ✅ (partial)** `GET /9box` → `templates/9box.html`, `ui/service/NineBoxViewService.java`. Real data (stored placement).
  - Every name is listed inside its cell, with no click → list and no direction / poste / scores.
  - Latest quarter only, no selector (B5).
  - No thresholds panel.
  - Axes are the other way round from the prototype (ours: performance up, potential right; prototype: potential up, performance right; E7).
- **Backend:** `NeufBoxService` (placement, levels per axis with separate thresholds), `CasesNeufBox`, `Matrice9BoxRepository`. Nothing essential missing.
- **Bugs:** B9.

### 06 Compétences & Gaps
- **Page ❌.**
- **Backend:** per person: `CompetenceCollaborateurService`, `GET /api/collaborateurs/{id}/competences?annee=&numero=` (gap, statut MAITRISE / A_DEVELOPPER / PRIORITAIRE, threshold from `Parametre`).
  - Missing: aggregation per competence (average current vs target), with a direction filter.
  - Missing: a "critical competence" flag. `Competence` has `competenceId`, `nom`, `categorie` only (`entity/Competence.java:12-17`). Our matching has no extra weight for critical competences (D9).

### 07 Carrière & Mobilité
- **Page ❌.**
- **Backend ❌.** No data at all: career plan, wished mobility, horizon and mobilities done are neither in our model nor in the workbook (`01_COLLABORATEURS` A-O = identity, org, fonction, grade, manager, statut). The prototype collects them in the self-evaluation form.
- **Blocked:** client (where these answers come from) + Dou (import).

### 08 Engagement & Fidélisation
- **Page ❌.**
- **Backend:** `QuestionnaireEngagement` stores only `scoreEngagement`, `dateReponse`, `statut` (`entity/QuestionnaireEngagement.java:14-32`), imported from `12_VIGILANCE` D. `VigilanceService` gives the departure-risk index.
  - The real questionnaire file (`docs/data/TALENT 360 BANK — Questionnaire d'engagement collaborateur (réponses) - Copy.xlsx`) has **12 Likert items in 6 dimensions + a 0-10 recommendation + a free text**, the same structure as the prototype. None of it is stored.
  - Missing: items per dimension, eNPS, comment, response rate, aggregates per dimension and direction.
- **Blocked:** client question 1 (how items become the /100 score, audit C9) + Dou (questionnaire import).

### 09 Viviers
- **Page 🟡** `GET /viviers` → `templates/viviers.html`, `ui/service/VivierService.java`.
  - Real data, but only the **saved** pool memberships (`AppartenanceVivier`, i.e. the relief pool written by `VivierReleveService`), as one flat table (nom, poste, département, pot, perf, vivier).
  - The 5 thematic pools are not shown.
  - No cards, averages, Ready Now, covered posts, gaps or review date.
- **Backend:**
  - `VivierThematiqueService` + `GET /api/viviers-thematiques[/{code}]`: Commercial, Digital, Expertise, Management, Risques, by direction. Still one query per score, see the guide's to-do.
  - `TalentService.getVivierReleve` + `GET/POST /api/trimestres/{a}/{n}/vivier-releve`.
  - Missing: aggregates per pool (averages, Ready Now, posts covered by a member, members with a gap), the "Direction" pool, a review date.
- **Bugs:** B7.

### 10 Postes critiques & Succession
- **Page 🟡 placeholder** `GET /postes-critiques` → `templates/placeholder.html` (`PagesController.java:49-53`).
- **Backend ✅ complete for reading:**
  - `PosteCritiqueService` + `GET /api/postes-critiques?annee=&numero=`, `/alertes`, `/synthese`, `/{posteId}` (coverage, alert, best successor, readiness).
  - `SuccessionService` + `GET /api/postes/{posteId}/candidats[?limite=]`, `/candidats/{id}` (6-criterion matching, readiness).
  - Missing:
    - succession status per successor (proposé / validé / …);
    - a minimum number of successors **per post** (ours is one setting for all, `SeuilsCouverture.nbMinSuccesseurs`, E10);
    - Stratégique / Sensible (E11), and the succession part of the model (D12).

### 11 Comité Talent
- **Page ✅ (read only)** `GET /comite-talent?trimestre=&statut=` → `templates/comite-talent.html`, `ui/service/ComiteTalentViewService.java`. Real data: quarter and status filters, KPI, table of proposed talents and their decision.
- **Backend:** `ValidationComiteService`, `ValidationsComiteEnBase`, `GET /api/comite-talent`, `/talents-valides` (`ComiteTalentController.java:28,36`). Decisions come **only from the import** (`10_TALENTS` G).
  - Missing: an endpoint to **record a decision** (the prototype's 3 buttons), "Réévaluer" as a status (ours: OUI / NON / EN_ATTENTE).
  - Missing: the whole **succession validation** part (5 statuses: no model, no API).
- **Bugs:** B14.

### 12 Talent Passport (= our Fiche collaborateur)
- **Page ❌.**
- **Backend ✅** `FicheCollaborateurViewService` + `GET /api/trimestres/{a}/{n}/collaborateurs/{matricule}/fiche`. It returns:
  - identity with org path and manager, performance and potential (official score + criteria), 9-box case;
  - talent / HP / relève, committee decision, pools;
  - competences with gap status, engagement score, vigilance with reasons;
  - successions (posts where the person is a successor, matching, readiness);
  - history of earlier quarters;
  - self vs manager evaluation (since R1d).
  - Missing vs prototype: aspirations (07), the **development plan** (`11_DEVELOPMENT_PLAN` not imported, Dou), a matching for people who are not candidates, survey dimensions (08).

### 13 Alertes
- **Page 🟡 placeholder** `GET /alertes` → `placeholder.html` (`PagesController.java:65-69`).
- **Backend (pieces):**
  - vigilance list `GET /api/trimestres/{a}/{n}/vigilance?minimum=ELEVEE`;
  - critical posts in alert `GET /api/postes-critiques/alertes`;
  - manager-view alerts (VIGILANCE_ELEVEE, EVALUATION_MANQUANTE);
  - talents `GET /api/trimestres/{a}/{n}/talents`.
- **Missing:**
  - one alert service that merges these with a severity;
  - "plan en retard" (needs `11_DEVELOPMENT_PLAN`) and "compétence stratégique insuffisante" (needs the critical flag, D9);
  - a persisted `Alerte` with a lifecycle, if the PDF version is wanted (D4).

### 14 Historique
- **Page ❌.**
- **Backend:** the score history per quarter exists: the fiche's `historique` block, `ScoreRepository.findHistorique`, `GET /api/collaborateurs/{id}/scores`. That is the PDF's `HistoriqueTrimestre`.
  - The prototype's history is an **audit trail** of decisions (who, what, when): no entity, no log (D5). `ImportExcel` journals imports only.

### 15 Notifications
- **Page ❌. Backend ❌.** With RH only, these would be counters derived from 10/11/13. Not in the PDF (D10).

### · Mon profil / · Mon engagement
- Collaborateur role: **out of scope** (RH only, D1). If ever needed, Mon profil = fiche page; Mon engagement = survey entry (D8).

### 2.1 Bugs and predictable failures

Found by reading the code; nothing was started. Numbered **B** to avoid confusion with the audit's B1-B11.

| # | Where | Problem | Effect |
|---|---|---|---|
| B1 | `ui/service/DashboardService.java:43-49`, `:37`, `templates/dashboard.html:19` | 6 hard-coded KPI values. "Talents valides" counts proposed talents. Subtitle "donnees mockees" | The first page the client sees shows fake numbers (e.g. "Alertes actives 23") that contradict the other pages |
| B2 | `DashboardService.java:44` | Icon `"kpi-icon-warning"` is neither a Bootstrap Icons name nor defined in `static/css/style.css` | Empty icon on "Collaborateurs a risque" |
| B3 | `ui/controller/PagesController.java:49-53, 65-69, 72-76` | `/postes-critiques`, `/alertes`, `/parametres` render `placeholder.html` ("Cet ecran sera construit a l'etape suivante…") | 3 of the 7 sidebar entries are "under construction" pages |
| B4 | `templates/fragments/sidebar.html:16-38` | No logout link. The only logout form is on `templates/erreur/403.html:20-22` | No way to log out in a normal session (the 15-min timeout is the only exit) |
| B5 | `DashboardService.java:54`, `NineBoxViewService.java:43`, `VivierService.java:46` | Pages always read `findTopByOrderByAnneeDescNumeroDesc()`, with no quarter selector (only Comité has one) | As soon as RH creates the next quarter (e.g. T4 before its import), `/`, `/9box` and `/viviers` show zeros / empty grids while T3 has all the data |
| B6 | branch `origin/iman2`: `9box.html:25,27`, `viviers.html:19` | Still uses `${cell.nombreEmployes}` and `${cell.employes}` (Employe era). `NineBoxCell` only has `getNombreCollaborateurs()`/`getCollaborateurs()` | If Ima works on or merges from this branch, `/9box` fails with a 500 (SpEL "property not found"). `origin/ima-ui` is also stale (it deletes `9box`, `comite-talent`, `login`, `403` compared with `develop`). Both are already merged; rebase on `develop` or delete them |
| B7 | `VivierService.java:48` | `/viviers` lists only saved memberships (relief pool). The thematic pools exist only in the API | The page looks nothing like the prototype's 6 pools. It is empty until a calculation has saved the relief pool |
| B8 | `controller/CollaborateurController.java:43-55` | `POST` takes and returns the JPA entity (no DTO, no `@Valid`). `DELETE` is a hard delete (audit B11) | Deleting anyone who has scores → foreign-key error → 500. Any future CRUD page must not use these as they are |
| B9 | `ui/service/NineBoxViewService.java:45-59` | Groups people by the **stored label** `Score.positionBox`, then looks it up in `Matrice9Box.categorie`. All names are written inside the cells | Renaming a case after placement makes its people disappear from the grid until the next placement (the fiche and views use numbers and are unaffected). With 100 people, the "Confirmé" cell is a long list |
| B10 | All 8 page templates, lines 6-7 | Bootstrap and Bootstrap Icons from `cdn.jsdelivr.net` (11 links) | Offline on the bank's machine: no layout, no icons (already in the guide's to-do) |
| B11 | `templates/login.html:11` | Minimal page, "a restyler (Ima)" | First screen of the demo is unstyled |
| B12 | `templates/fragments/sidebar.html` | No entries for features whose API is done: fiche, vue manager, vue entité, import, settings page | Nothing built in the last weeks can be reached from the UI |
| B13 | `service/ViviersThematiquesEnBase.java` | One query per score (`vivierPourDirection`) | Slow `GET /api/viviers-thematiques` with a real population (known, in the guide) |
| B14 | `controller/ComiteTalentController.java` | GET only. Decisions can only be imported (`10_TALENTS` G) | The prototype's "Valider / Ne pas retenir" buttons have no endpoint |
| B15 | `docs/prototype` vs PDF | `Ponderation {poidsAutoEvaluation, poidsManager, poidsCompetences, poidsEngagement}` in the PDF is the 4-weight block we **removed** on 1 Oct (`poidsSources`). We now only blend manager and self-evaluation (`ponderationSources`) | Not a bug in the code, but the client's diagram expects 4 weights: see D3 before the Paramètres page is built |

Checked and fine:
- No `Employe` leftovers on `develop`: the templates, CSS and Java (except `Employee_ID`, a workbook column name) all use Collaborateur.
- Every sidebar link resolves; no 404.
- All pages and GET endpoints answer without a 500 on test data (`SansSessionOuverteIntegrationTest`).

---

## 3. Pages we need that are not in the prototype

| Page | URL (proposed) | Backend today | Missing | Effort | Blocked by |
|---|---|---|---|---|---|
| **Fiche collaborateur** (= Talent Passport) | `/collaborateurs/{matricule}?trimestre=` | ✅ `FicheCollaborateurViewService`, `GET /api/trimestres/{a}/{n}/collaborateurs/{m}/fiche` | Page only. Plan and aspirations later | M | nothing (plan: Dou) |
| **Vue manager** | `/managers`, `/managers/{matricule}` | ✅ `VueManagerViewService`, `GET /api/trimestres/{a}/{n}/managers`, `/managers/{m}/vue` (team, summary, alerts, self vs manager) | Picker + page | M | nothing (N-2 hierarchy: client Q6) |
| **Vue entité** | `/entites`, `/entites/vue?code=` | ✅ `VueEntiteViewService`, `GET /api/entites` (tree), `GET /api/trimestres/{a}/{n}/entites/vue?code=` | Tree picker + page | M | nothing |
| **Import** (folder + report) | `/import` | 🟡 `POST /api/imports` (one uploaded workbook, `annee`, `numero`), `GET /api/imports` (journal), detailed report per sheet (`docs/import-donnees.md`) | Upload form + report display. The **folder** import (watch folder, one file per person or manager, AUTO files) doesn't exist | M (upload) / L (folder) | Dou (endpoint, T2.2/T3.4), client Q4, Q7, Q8 |
| **Paramètres** (weights, blend, thresholds) | `/parametres` (placeholder today, B3) | ✅ `GET/POST/PUT /api/trimestres/{a}/{n}/parametre`. 16 blocks incl. `ponderationSources` and `seuilsAutoEvaluation`. Automatic recalculation, 409 lock, error report in `recalcul.erreur` | Page: one form section per block, show validation messages (400 detail) and the recalculation report | M-L | client D3 for the source weights (form can ship without) |
| **CRUD collaborateurs / managers / entités** | `/admin/...` | 🟡 collaborateurs: GET list/one, POST (entity), hard DELETE (B8). Managers, entités: nothing (read-only `GET /api/entites`) | DTO + validation, soft delete (ARCHIVE), manager and entité endpoints, pages | L | Dou (T3.1), client (which fields RH may edit vs import) |
| **Login / logout** | `/login` | ✅ Spring Security form, BCrypt, first RH account from env vars | Styling (B11), logout button in the layout (B4) | S | nothing |

---

## 4. Summary table

Effort: **S** ≤ 1 day, **M** 2-3 days, **L** 4+ days, for one person.

| Module | Prototype features | Page state | Backend state | What's missing | Effort | Blocked by |
|---|---|---|---|---|---|---|
| 01 Tableau de bord RH | 10 KPIs, 9-box, top alerts, viviers table, CSV | 🟡 fake KPIs (B1) | 🟡 `TableauDeBordService` covers ~6/10 KPIs | Ready Now, engagement avg, active pools, gaps, viviers summary; page | M | nothing |
| 02 Tableau de bord DG | 4 KPIs, 9-box, bench strength, top talents + poste cible | ❌ | 🟡 same + `PosteCritiqueService` | Poste cible per talent, criticality classes; page | M | client (D1, E11) |
| 03 Campagne d'évaluation | Progress auto/manager by direction, period, "Lancer le calcul" | ❌ | 🟡 calcul endpoint, import, counts by source possible | Campaign entity/period, counts API, page | M | Dou (AUTO import), client Q3/Q4/Q7 |
| 04 Collaborateurs | Table, 3 filters, search, CSV, → passport | ❌ | 🟡 identity list only; `ResultatsCollaborateurs` reusable | Population list service + filters + CSV, page | M | nothing |
| 05 Matrice 9-Box | Grid, counts, click → list, thresholds panel | ✅ partial | ✅ | Click → list, quarter selector, thresholds panel, axis choice | S | client (E6, E7) |
| 06 Compétences & Gaps | Avg current vs target per competence, critical flag, direction filter | ❌ | 🟡 per person only | Aggregation API, critical flag, page | M | client (D9) for the flag |
| 07 Carrière & Mobilité | Mobility KPIs, aspirations table | ❌ | ❌ no data | Data model + source + page | L | client + Dou |
| 08 Engagement & Fidélisation | Global %, response rate, eNPS, 6 dimensions, by direction | ❌ | 🟡 one score per person | Items/eNPS/comment model + import + aggregates + page | L (M with the global score only) | client Q1 + Dou |
| 09 Viviers | 6 cards with aggregates, members modal | 🟡 flat table, relief pool only (B7) | 🟡 thematic + relief pools | Aggregates per pool, "Direction" pool, page | M | client (D6, E8) |
| 10 Postes critiques & Succession | Coverage bar, card per post, successors with readiness/matching/status | 🟡 placeholder (B3) | ✅ read | Page; status per successor; min per post | M | client (D12, E10, E11) for status/min |
| 11 Comité Talent | KPIs, talent decisions (3 buttons), succession decisions (5) | ✅ read only | 🟡 read only (B14) | Decision endpoint + buttons; succession decision model | M (talents) / L (+ successions) | client (D7) |
| 12 Talent Passport | Full individual record, matching, plan | ❌ | ✅ fiche API | Page; plan (later) | M | nothing (plan: Dou) |
| 13 Alertes | 3 severities, 5 rules | 🟡 placeholder (B3) | 🟡 pieces in 4 APIs | Alert service merging them; page; lifecycle if PDF | M (computed) / L (persisted) | client (D4); Dou for "plan en retard" |
| 14 Historique | Audit trail of decisions | ❌ | 🟡 score history only | Event log (entity + writes), page | M | client (D5) |
| 15 Notifications | Bell + list | ❌ | ❌ | Derived counters, bell in layout | S | client (D10) |
| · Mon profil / Mon engagement | Collaborateur views | ❌ out of scope | — | — | — | client (D1) |
| Fiche collaborateur | (= 12) | ❌ | ✅ | Page | M | nothing |
| Vue manager | — | ❌ | ✅ | Page | M | nothing |
| Vue entité | — | ❌ | ✅ | Page | M | nothing |
| Import | — | ❌ | 🟡 upload only | Page; folder import | M / L | Dou, client |
| Paramètres | (prototype "Administration") | 🟡 placeholder (B3) | ✅ | Page | M-L | client (D3) for the source weights |
| CRUD | — | ❌ | 🟡 unsafe (B8) | DTOs, endpoints, pages | L | Dou (T3.1), client |
| Login / logout | — | ✅ unstyled, no logout (B4, B11) | ✅ | Styling, logout button | S | nothing |

---

## 5. Proposed split (whole modules, no shared files)

**Rule:** a module = its page controller + template(s) + view service + view model + its CSS file. Each person owns their modules end to end. Backend engine services (`service/*`) stay Jasmine's. Ima's view services call them; if she needs a new engine method, Jasmine adds it.

### Jasmine — backend-heavy modules
| Module | Files (new unless stated) |
|---|---|
| 01 Tableau de bord RH (+ 02 DG as a tab) | `ui/controller/DashboardPageController`, `templates/dashboard.html` (existing), `ui/service/DashboardService` (existing, rewritten on `TableauDeBordService`), `css/dashboard.css` |
| 04 Collaborateurs (list, filters, CSV) | `ui/controller/CollaborateursPageController`, `templates/collaborateurs.html`, `ui/service/ListeCollaborateursViewService` (reuses `ResultatsCollaborateurs`), CSV endpoint |
| 10 Postes critiques & Succession | `ui/controller/PostesCritiquesPageController`, `templates/postes-critiques.html`, `ui/service/PostesCritiquesViewService` |
| 11 Comité Talent (decisions) | `ComiteTalentController` (existing, + POST), `ui/service/ComiteTalentViewService` (existing), `templates/comite-talent.html` (existing) |
| 13 Alertes | `service/AlerteService`, `controller/AlerteController`, `ui/controller/AlertesPageController`, `templates/alertes.html` |
| 06 Compétences & Gaps | aggregation in `CompetenceCollaborateurService`, `ui/controller/CompetencesPageController`, `templates/competences.html` |
| 03 Campagne / Import page | `ui/controller/CampagnePageController`, `templates/campagne.html` (upload + journal + "Lancer le calcul"). Coordinate with Dou, who owns `ImportController` |
| Later (blocked) | 08 Engagement, 07 Carrière, 14 Historique (event log) |

### Ima — UI-heavy modules
| Module | Files |
|---|---|
| Layout, sidebar, CSS, login/logout | `templates/fragments/sidebar.html`, new `templates/fragments/layout.html` (head, CSS/JS links, logout button), `static/css/style.css`, `templates/login.html`, `templates/erreur/403.html`, local Bootstrap in `static/vendor/` |
| 12 Fiche collaborateur / Talent Passport | `ui/controller/FichePageController`, `templates/fiche.html` (uses `FicheCollaborateurViewService` as is) |
| 05 Matrice 9-Box | `PagesController` `/9box` mapping (existing), `templates/9box.html`, `ui/service/NineBoxViewService` (existing) |
| 09 Viviers | `PagesController` `/viviers` mapping (existing), `templates/viviers.html`, `ui/service/VivierService` (existing) |
| Paramètres | `ui/controller/ParametresPageController`, `templates/parametres.html` (+ a small JS file calling the existing API with the `X-Talent360` header, see `docs/requetes-ecriture.md`) |
| Vue manager, Vue entité | `ui/controller/VueManagerPageController`, `VueEntitePageController`, `templates/vue-manager.html`, `templates/vue-entite.html` |
| 15 Notifications (bell), 14 Historique page (score history part) | in `layout.html` + `templates/historique.html` |

### Shared files and who owns them
| File | Owner | Rule for the other person |
|---|---|---|
| `templates/fragments/sidebar.html`, `templates/fragments/layout.html` | **Ima** | Jasmine asks for her entries; Ima adds all entries on day 1 (links to future pages are fine) |
| `static/css/style.css` | **Ima** | Jasmine puts module styles in her own `static/css/<module>.css`, included by her templates |
| `ui/controller/PagesController.java` | **Ima** | **Day 1, one small PR by Jasmine** removes the `/`, `/postes-critiques`, `/alertes` mappings (moved to her own controllers) and Ima removes `/parametres` → no duplicate mapping, then Jasmine never touches it again |
| `ui/model/*` | per module | New records per module; `KpiCard`, `OptionFiltre` reused read-only (change = ask owner: Ima) |
| `service/*`, `repository/*`, `entity/*` | Jasmine (Dou for import/CRUD) | Ima requests engine changes |
| Branches `origin/ima-ui`, `origin/iman2` | Ima | Delete or rebase on `develop` before any new work (B6) |

---

## 6. Priority order (~10 working days)

Ordered by what the client notices first in a demo: the first screen, then clicking through the sidebar, then the "wow" flows (9-box → person → succession → comité).

| Day | Jasmine | Ima | Why first |
|---|---|---|---|
| 1 | Split `PagesController` (PR with Ima). **Dashboard RH on real data** (remove B1/B2), quarter selector (B5) | Layout fragment + full sidebar + **logout** (B4) + styled login (B11) + local Bootstrap (B10) | First screen of the demo; fake numbers and "under construction" pages are noticed immediately |
| 2 | Dashboard: 9-box mini-grid, top alerts, viviers summary, DG tab | **Fiche collaborateur** page (API done) | The person record is the hub every other page links to |
| 3 | **Postes critiques & Succession** page (API done, removes a placeholder) | Fiche (end), links from 9-box / comité / viviers rows | Succession is the core of the product ("Vivier & Succession") |
| 4 | **Collaborateurs** list + filters + CSV | **9-Box**: click → list, thresholds panel, quarter selector | The two pages RH opens most |
| 5 | **Comité**: record a decision (talents) | **Paramètres** page (blocks, validation, recalculation report) | Removes the last placeholder; client asked for configurable weights |
| 6 | **Alertes** (computed: vigilance, posts without successor, missing evaluations, new talents) | Paramètres (end) | Removes the last placeholder |
| 7 | Compétences & Gaps (aggregation + page) | **Viviers** cards (thematic + relief, averages, members) | Visible gap vs prototype |
| 8 | Import page (upload + report + "Lancer le calcul") with Dou | **Vue manager** page | Shows the data flow end to end |
| 9 | Buffer / client answers (D3, D7…) | **Vue entité** page, notifications counter | — |
| 10 | Demo rehearsal on the real dataset, fixes | Demo rehearsal, visual pass | — |

**Not in the 10 days unless the client answers first:** 07 Carrière & Mobilité, 08 Engagement dimensions, 14 audit trail, succession decisions (5 statuses), folder import, CRUD, collaborateur/manager self-service screens.

---

## 7. Differences to confirm

### 7.1 Prototype vs PDF specification (class diagram)

Nothing was chosen. Each line needs a client answer.

| # | Topic | Prototype | PDF (`RH_TALENT_MANAGEMENT_DC.pdf`) |
|---|---|---|---|
| D1 | Users and roles | 4 roles with their own views: Collaborateur, Manager, DRH / Talent Manager, Comité / Direction (`index.html:489-494`) | A single `UtilisateurRH` (login, password hash, role, `authentifier()`), no collaborateur/manager/comité user |
| D2 | How evaluations are entered | In the app: collaborateur form (aspirations + competences 1-5) and manager form (perf and pot sliders 0-100 + comment), with statuses Non commencé / Enregistré / Soumis / Complétée | `AutoEvaluation` (score, date, statut, `saisir()`) and `EvaluationManager` (score, date, commentaire, `saisir()`): one score each; no form described. (Our app imports files with 5/7 criteria per source.) |
| D3 | Weights | No weighting screen; the 9-box thresholds are "modifiables dans l'écran Administration" | `Ponderation {poidsAutoEvaluation, poidsManager, poidsCompetences, poidsEngagement}` with `configurer()`/`valider()`. Our app only blends manager + self (`ponderationSources`); the 4-weight block was removed on 1 Oct because no workbook formula used it |
| D4 | Alerts | Computed on the fly, 3 severities, read-only | Persisted `Alerte` (type, message, `dateCreation`, `dateCloture`) with `StatutAlerte` A_TRAITER / EN_COURS / CLOTUREE and `traiter()`, `cloturer()` |
| D5 | History | Audit trail of events (date, matricule, acteur, événement) | `HistoriqueTrimestre` (trimestre, scorePerformance, scorePotentiel, position9Box, `consulterEvolution()`): score history per quarter |
| D6 | Pools | 6 computed pools (Direction, Management, Digital, Risques, Expertise, Commercial); membership derived from talent status and direction | `VivierTalent` (nom, categorie, description) with `ajouterEmploye()` / `retirerEmploye()`: manual membership |
| D7 | Committee | Talent decisions (Valider / Réévaluer / Ne pas retenir) and succession decisions (5 statuses) | No committee or decision class |
| D8 | Engagement | 6 dimensions (Likert 1-5) + eNPS 0-10 + comment, statuses, aggregates per dimension | `QuestionnaireEngagement` (trimestre, scoreEngagement, dateReponse, `saisirReponses()`, `calculerEngagement()`): one score |
| D9 | Competences | Per-person competences, critical competences "with a reinforced weight in the matching" | No competence class at all |
| D10 | Notifications, campaign, career/mobility, development plan | Present (modules 03, 07, 15, plan in the passport) | Absent |
| D11 | Reports | "Exporter CSV" only | `Rapport` (type, format, `exporterPDF()`, `exporterExcel()`) |
| D12 | Critical posts and succession | 15 critical posts, criticality Stratégique / Sensible, minimum successors per post | No `Poste`, `PosteCritique` or `Succession` class |
| D13 | 9-box per quarter | Computed from the current scores | `Matrice9Box` has a `trimestre` and `positionner()`: positions stored per quarter |

### 7.2 Prototype vs our engine (the client's workbook)

Not asked, but the demo will show these numbers side by side with the prototype, so they need the same answer.

| # | Topic | Prototype | Engine / workbook (`00_PARAMETRES`) |
|---|---|---|---|
| E1 | 9-box thresholds | ≥80 high, ≥60 medium (`index.html:288`, 777-778) | ≥85 high, ≥70 medium, per axis (configurable) |
| E2 | Talent proposed | perf ≥80 AND pot ≥80 (l. 322) | perf ≥85 AND pot ≥85 |
| E3 | High potential | The "Haut potentiel" 9-box case (medium perf, high pot) | pot ≥85 AND perf ≥75 (any case) |
| E4 | Readiness | 3 levels: ≥90 Ready Now, ≥75 "<2 ans", else ">2 ans" (l. 365) | 4 levels: ≥90 Ready Now, ≥80 <1 an, ≥65 1-2 ans, else >2 ans |
| E5 | Matching | 0.4 perf + 0.35 pot + 0.15 engagement + random (l. 363) | 6 weighted criteria (compétences 25, perf 20, pot 20, expérience 15, leadership 10, mobilité 10) |
| E6 | 9-box labels | Potentiel à révéler, Haut potentiel, Pilier solide (l. 289-294) | Potentiel à confirmer, Potentiel / Haut potentiel, Performant (`config/Matrice9BoxInitializer.java:36-46`), editable |
| E7 | 9-box axes | Potential vertical, performance horizontal | Performance vertical, potential horizontal (`templates/9box.html:19`) |
| E8 | Pools | Includes a "Direction" pool | 5 thematic pools by direction + relief pool (no "Direction") |
| E9 | Vigilance / departure risk | Not shown anywhere | 7-signal index from `12_VIGILANCE` (fiche, views, API) |
| E10 | Minimum successors | Per post (1 or 2) | One setting for every post (`SeuilsCouverture.nbMinSuccesseurs`, default 1) |
| E11 | Criticality | Stratégique / Sensible | Free text from `08_POSTES_CRITIQUES` D ("Tres elevee", "Elevee"…) |
