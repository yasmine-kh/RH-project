# TALENT 360 BANK — Technical audit

*Audit date: 30 September 2026. Analysis only: no code was changed, nothing was committed or pushed. This file is the only file written.*

---

## 0. Read this first

1. **`develop` and your working tree are two different projects.** `HEAD` = `develop` = `origin/develop` = `d5b7548`. On top of that, the current branch `feature/import-partag` has a large **uncommitted** change set: 109 tracked files modified or deleted (+1,824 / −2,083 lines) and about 75 new files. It contains the rename from Employé to Collaborateur, the `Manager` and `Entite` entities, the full-workbook import, campaigns and the quarter API. None of this is in Git yet. If the disk fails or someone runs a `git checkout -- .`, the work is lost. Meanwhile Dou and Ima keep branching from a `develop` that still uses `Employe` everywhere, so every day of delay adds merge conflicts.
   → **Priority #1:** commit it, open the PR and merge it into `develop` before anyone starts new work (section 9).
2. In this report, **"develop"** means the committed state and **"WIP"** means the working tree. File paths and line numbers refer to the **WIP** unless stated otherwise, because that is the code you will build on.
3. **No role dropdown exists in the repository.** I searched `develop`, the WIP, `origin/ima-ui`, `origin/iman2` and `origin/douae`. No template has a role or profile selector. It is probably in the client's HTML prototype. See the questions for the client.
4. GitHub's default branch (`origin/HEAD`) is **`douae`**, not `develop`. New PRs will target `douae` by default, and CI (`.github/workflows/ci.yml`) only runs on `develop` and `main`. Switch the default branch to `develop` in the repository settings.

### Build and test results (run in isolated copies under the scratchpad; the project itself was not touched)

| Tree | Compile | Tests | Notes |
|---|---|---|---|
| `develop` (`git archive develop`) + the local `docs/data/*.xlsx` | OK | **475 run, 0 failures, 0 errors, 0 skipped** (≈3 min) | The Excel comparison test (`DatasetExcelComparaisonTest`) **did run**, because I copied the dataset. In CI it is skipped: the dataset is gitignored. |
| WIP (working tree copy, `target/` and `.git` excluded) | OK (186 source files) | **558 run, 0 failures, 0 errors, 0 skipped** (≈4 min) | Deprecation warnings only (`@MockBean`). The WIP is **stable enough to merge now**. |

---

## 1. Project map

### 1.1 Stack
Spring Boot **3.5.16**, Java 17, Spring MVC + Thymeleaf (server-side pages) + REST/JSON API, Spring Data JPA/Hibernate, MySQL (H2 for tests), Apache POI 5.5.1, Bean Validation. **No Spring Security.** Bootstrap 5 and Bootstrap Icons are loaded from the **jsDelivr CDN** (10 references in `templates/`). Schema management uses `ddl-auto=update` plus hand-written `ALTER` initializers.

### 1.2 Packages (WIP): ~186 main classes, ~64 test classes, ≈25k lines

| Package | Role | Main classes |
|---|---|---|
| `entity` (≈45) | JPA model | **Organisation:** `Collaborateur` (PK = matricule `id_collaborateur` VARCHAR(20)), `Manager` (a role held by a collaborateur, 1–1), `Entite` (tree Direction › Département › Région › Agence, unique `code` = path). **Reference data:** `Trimestre`, `Parametre` (≈50 columns in 14 `@Embedded` blocks: `PoidsPerformance`, `PoidsPotentiel`, `PoidsSuccession`, `SeuilsNeufBox`, `SeuilsTalent`, `SeuilsReadiness`, `SeuilsVigilance`, `PointsVigilance`, `BaremeExperience`, `BaremeCompetences`, `SeuilsCouverture`, `SeuilsGapCompetence`, `SeuilsCategoriePerformance`…), `Competence`, `Poste`, `Matrice9Box`, `Vivier`, `RattachementVivier`. **Inputs:** `Performance`, `Potentiel`, `QuestionnaireEngagement`, `DeclarationVigilance`, `ValidationComite`, `SuccesseurIdentifie`, `CompetenceCollaborateur`. **Outputs:** `Score`, `AppartenanceVivier`. **Unused:** `Alerte`, `Rapport`, `UtilisateurRH`. **Log:** `ImportExcel`. |
| `repository` (≈24) | Spring Data | Most have fetch-join queries such as `findByTrimestreAvecCollaborateur`. |
| `service` (calculation) | Engine (Jasmine) | `CalculService` (weighted scores, performance category, skill-gap status), `ScoreService` (persists `Score`), `NeufBoxService`, `TalentService`, `VivierReleveService`, `VivierThematiqueService`, `SuccessionService` (matching + readiness), `PosteCritiqueService` (coverage), `VigilanceService`, `ValidationComiteService`, `TableauDeBordService`, `CalculTrimestreService` (chain: scores → 9-box → succession pool) |
| `service` (import) | Data (Dou) | `ImportService` (upload, format and period checks, log), `ImportClasseurService` (965 lines, one transaction, 10 sheets), `FormatClasseur` (expected headers), `PeriodeClasseur`, `RapportImport`, `CampagneService` (import then calculation), `TrimestreService`; `excel/Cellules` (cell reading) |
| `service/*Source` + `*EnBase` | Ports | `FaitsVigilanceSource`, `SuccesseurIdentifieSource`, `ValidationComiteSource`, `VivierThematiqueSource`, each with a database implementation (`@Fallback`) and a `demo` implementation (`dev/DemoClasseurSources`) |
| `controller` (≈17) + `dto` | REST API `/api/**` | Imports, quarters, parameters, scores, talents, 9-box, succession, critical positions, vigilance, thematic pools, Comité, dashboard, collaborateurs, competences. `ApiExceptionHandler`, `ChargeurRessources` |
| `ui.controller` / `ui.service` / `ui.model` | Thymeleaf pages (Ima) | `PagesController`: `/`, `/9box`, `/viviers`, `/comite-talent`, plus 3 placeholders (`/postes-critiques`, `/alertes`, `/parametres`) |
| `config` | Startup and security | `ProtectionRequetesFilter` (Host check + `X-Talent360` header on writes), `ParametreInitializer`, `Matrice9BoxInitializer`, `VivierReleveInitializer`, `ColonnesObsoletesInitializer` and `ImportExcelInitializer` (hand-written `ALTER TABLE`) |
| `dev` | `demo` profile | `DemoDataLoader`, `DemoClasseurSources` (superseded by the import) |

### 1.3 How data flows today (WIP)

```
RH uploads 1 workbook (dataset V1 format, 10 sheets)
  POST /api/imports (multipart, annee, numero, simulation?, calcul?)
   └─ ImportService: POI read → FormatClasseur (headers at FIXED positions) → PeriodeClasseur
       → TrimestreService.creerSiAbsent (own transaction; copies the previous quarter's parameters)
       → ImportClasseurService.importer (ONE transaction, one sheet after another)
           01 → Collaborateur + Entite + Manager (col. N)   | absent from the file ⇒ INACTIF
           05/06/07/08 → Competence, CompetenceCollaborateur, Poste
           02/03 → Performance / Potentiel (1 row per collaborateur per quarter)
           09/10/12 → SuccesseurIdentifie, ValidationComite + RattachementVivier, Questionnaire + DeclarationVigilance
       → ImportExcel (log, written outside the data transaction)
   └─ CampagneService → CalculTrimestreService
       ScoreService.recalculerTrimestre → NeufBoxService.placerTrimestre → VivierReleveService.constituerViviers
Read side, computed on the fly (nothing persisted): talents, high potentials, succession, coverage, vigilance, thematic pools
Pages: always the "latest quarter" (findTopByOrderByAnneeDescNumeroDesc), for everyone, with no filter.
```

`00_PARAMETRES` is **not** imported: weights live in `Parametre`, one row per quarter, edited through `PUT /api/trimestres/{a}/{n}/parametre`. This matches "weights managed in the app".

---

## 2. State of the code

### 2.1 Build and tests
- **develop:** compiles; 475 tests pass, 0 skipped when the dataset is present.
- **WIP:** compiles; **558 tests pass** (83 more than develop: import, campaign, quarter, entité, real-dataset import on H2), 0 skipped.
- Test quality is good for an intern project: unit tests for each rule, JPA tests on H2, MockMvc tests, and a test that compares the engine with the dataset's Excel formulas (`src/test/.../dataset/DatasetExcelComparaisonTest.java`). **Gap:** in CI that test is skipped because the dataset is gitignored. Consider committing a small anonymised extract (10 rows) so CI checks the formulas.
- Many `@MockBean` annotations (deprecated since Boot 3.4, removed in Boot 4). Not urgent.

### 2.2 What works (KEEP)
- **Calculation engine** (`CalculService`, `NeufBoxService`, `TalentService`, `SuccessionService`, `PosteCritiqueService`, `VigilanceService`). It reads from the database, takes every weight and threshold from `Parametre` (nothing hard-coded), and has clear error handling (`DonneesIncompletesException` → 422, `RessourceIntrouvableException` → 404).
- **Weights per quarter** with inter-block validation: sums must equal 100 (`entity/Poids.java:17`), vigilance thresholds must stay consistent with the points.
- **Import robustness:** errors are reported per row with the Excel row number, a simulation mode exists, the log is kept even on failure, a corrected file replaces data without creating duplicates, and inactive collaborateurs are kept as history.
- **`Score` freezes the entité and manager** at calculation time (`entity/Score.java:65-68`), so past quarters keep their context.
- **Host check and write header** (`config/ProtectionRequetesFilter.java`): protects against CSRF and DNS rebinding while there is no login.
- **Documentation:** `docs/moteur-de-calcul.md`, `docs/import-donnees.md`, `docs/guide-developpeur.md`, `docs/mcd.puml`.

### 2.3 Incomplete
| Item | Evidence |
|---|---|
| Home dashboard: **6 of the 9 KPIs are hard-coded** (9 high potentials, 18 at risk, 1 position without successor, 23 alerts, 5 pools, 100 in the 9-box) and shown as real figures. The subtitle says "données mockées". | `ui/service/DashboardService.java:43-49`, `templates/dashboard.html` |
| The "Talents validés" KPI counts talents **detected** by the engine, not talents **validated by the Comité**. | `DashboardService.java:37,42` (compare with `ComiteTalentViewService`, which does it correctly) |
| Critical positions, Alerts and Parameters pages are placeholders. | `ui/controller/PagesController.java:49-53, 65-76` |
| No page for importing, the import report, CRUD, the collaborateur space or the manager space. | — |
| `11_DEVELOPMENT_PLAN` is not imported (TODO). | `service/ImportClasseurService.java:236-238` |
| The `Alerte` entity is never populated (vigilance and alerts are computed on the fly). | `service/VigilanceService.java:57-58` |
| Changing the weights does not recalculate anything: scores stay stale until someone calls `POST …/calcul`. | `controller/ParametreController.java:84-100` |

### 2.4 Dead or duplicated code (DELETE once the WIP is merged)
| Item | Why |
|---|---|
| `service/ExcelReader.java` (209 lines) | Old reader by fixed index, only used by `dev/DemoDataLoader`. |
| `excel/LecteurXlsx.java` (main) | No references in `main`. A near copy exists in `src/test/.../dataset/LecteurXlsx.java`. |
| `dev/DemoDataLoader.java`, `dev/DemoClasseurSources.java` | `demo` profile, superseded by the import (per `docs/import-donnees.md`). |
| `entity/Alerte`, `entity/Rapport` + repositories | Never used. `UtilisateurRH` is also unused, but **reuse it** for login (§6). |
| `/api/employes/**` aliases | `CollaborateurController.java:14`, `CompetenceCollaborateurController.java:16`, `ScoreController.java:58-59`. Delete them once the UI has migrated. |
| `POST /api/collaborateurs/import?cheminFichier=` + `ImportService.importerCollaborateurs(String)` | Security hole (§4), superseded by `/api/imports`. |
| `ColonnesObsoletesInitializer`, `ImportExcelInitializer` | Hand-written migrations. Useless once the database is recreated (the docs already require a `DROP DATABASE`). |
| Column `competence_collaborateur.statut_gap` | Copied from Excel column H at import (`ImportClasseurService.java:431`), but the engine recalculates the status on read (`CompetenceCollaborateurService.java:59`). Misleading. |
| `decrire(Trimestre)` copied into about 8 services | Minor. Move it to `Trimestre.getLibelle()`. |
| Stale Javadoc: "l'alimentation de Vivier / AppartenanceVivier reste à faire" | `TalentService.java:406-407`. `VivierReleveService` already does it. |
| `HELP.md` | Spring Initializr boilerplate, and it points to Boot 4.1.1 docs. |

---

## 3. Stability and bugs

### 3.1 Calculation differences from the dataset formulas
The formulas were read directly from `TALENT_360_BANK_Dataset_V1.xlsx`.

| # | Rule | Excel | Code | Verdict |
|---|---|---|---|---|
| C1 | Performance score | `D*B6+E*B7+F*B8+G*B9+H*B10`, not rounded | `CalculService.java:214-237`: weighted mean divided by the sum of weights, **rounded to 2 decimals HALF_UP before** the threshold comparisons | Same result while the weights have ≤2 decimals and sum to 100 (enforced). Edge case: weights such as 33.333 could flip a category. **Low.** |
| C2 | 9-box, performance and potential categories | Bands B24/B25 and B26/B27 (≥), then cases 1–9 and labels | `NeufBoxService.java:62-77`, labels from `Matrice9Box` | **OK.** |
| C3 | Talent, high potential, succession pool | E: perf≥B38 AND pot≥B39; F: pot≥B40 AND perf≥B41; J = E OR F; **H "Talent validé" = E AND G(Comité)="Oui"** | `TalentService.java:293-442`; H is in `ComiteTalentViewService` | OK, but the home dashboard KPI ignores H (§2.3). |
| **C4** | **Experience score (matching)** | `MIN(100, ancienneté×B76)` with `ancienneté = ROUND((DATE(2026,9,15)-F)/365.25,1)`, a **fixed reference date** | `SuccessionService.java:190-192` uses **`LocalDate.now()`** | **BUG.** The matching score and readiness **change every day**, a past quarter can no longer be reproduced, and results drift from the workbook. The test hides this by shifting entry dates by `now − 15/09/2026` (`DatasetExcelComparaisonTest.java:474`). Fix: add a reference date per quarter (for example `Trimestre.dateReference` = campaign date or end of quarter) and pass it in. `Collaborateur.getAnciennete()` (`Collaborateur.java:102`) has the same problem. |
| C5 | Competence score (matching) | `MIN(100,MAX(0,100-20*(req-actual)))`, actual = 3 when missing, average over 5 | `SuccessionService.java:125-167` (`BaremeCompetences`) | OK. |
| C6 | Coverage | G=0 → "ALERTE"; H≥B54 Ready Now; ≥B55 <1 yr; otherwise Partial | `PosteCritiqueService.java:125-149`: `nb < nbMinSuccesseurs` → ALERTE | OK with the default minimum = 1. |
| **C7** | **Vigilance: "Baisse performance"** | Column I = imported flag (Oui/Non) | `VigilanceService.java:210-215`: as soon as a previous quarter exists, **history decides and the imported flag is ignored**. **Any** drop, even 0.01, adds 10 points. | **Documented deviation, but a risk:** from the 2nd campaign on, every vigilance index can change without the client knowing. → Question for the client. |
| C8 | Vigilance: who is covered | All 100 rows of `12_VIGILANCE` | `VigilanceService.java:315`: only collaborateurs who have a `Score` (both performance and potential) | Divergence. With the new flow, a collaborateur whose manager has not yet sent their file gets no vigilance index, even though the engagement questionnaire is known. |
| C9 | Engagement | One pre-computed /100 score (column D) | Imported as is | The real questionnaire (`docs/data/…Questionnaire d'engagement…xlsx`) uses **1–5 Likert items and a 0–10 NPS**. The formula to turn it into /100 is not in the dataset. → **Question for the client.** |

### 3.2 Real bugs and exception risks
| # | Severity | Description | Location |
|---|---|---|---|
| B1 | **High** | **Import by merge wipes the columns that are not in the file.** Each row builds a `new Collaborateur()` and calls `save()` (merge), so every field absent from the workbook becomes `null` (manager too, re-attached in the second pass). As soon as RH edits a collaborateur in the app (CRUD) or you add a column (email, user account link), **the next import overwrites it**. | `ImportClasseurService.java:289-304` |
| B2 | **High (new flow)** | "Campaign" semantics: a collaborateur **absent from sheet 01 becomes INACTIF**, and quarterly rows absent from a sheet are **deleted** (`retirerAbsents`). Reused as is on a folder of **per-person** files, importing one file would deactivate everyone else and delete their evaluations. | `ImportClasseurService.java:318-327`, `834-851` |
| B3 | Medium | `Cellules.lignesDonnees` **stops at the first row whose column A is empty**. One blank row in the middle of a hand-filled file silently drops every row below it, with no error. | `excel/Cellules.java:64-69` |
| B4 | Medium | **Duplicates within a sheet are not detected.** The same matricule twice in 01/02/03/12: the second row silently overwrites the first. The requirement asks for duplicates in the report. | `ImportClasseurService.java:512-533`, `739-776` |
| B5 | Medium | A failed import can **open an empty quarter**: `creerSiAbsent` runs in its own transaction before the data transaction. If the data step fails, the empty quarter becomes the "latest quarter" on every page (dashboard, 9-box, pools, Comité), and they all show empty. | `ImportService.java:120-128`, `TrimestreService.java:49` |
| B6 | Medium | **No lock on imports.** A double click on "Importer" runs two transactions in parallel. `questionnaire_engagement` and `competence_collaborateur` have **no unique constraint**, so duplicates can appear, and `findByCollaborateurAndTrimestre` then throws `IncorrectResultSizeDataAccessException` → HTTP 500. | `QuestionnaireEngagement.java`, `CompetenceCollaborateur.java`, `VigilanceService.java:250` |
| B7 | Low | Row saved **before** column N is read: if N cannot be read, the row is reported as rejected but the collaborateur is already saved. | `ImportClasseurService.java:304-310` |
| B8 | Low | A numeric matricule (e.g. `012345` typed as a number) **loses its leading zeros** (`toBigInteger`). Real matricules (`BASE DE DONNEES.xlsx`, column "Matricule") will not match. | `excel/Cellules.java` `brut()`, NUMERIC branch |
| B9 | Low | Formula cells returning an error (`#N/A`) are read as empty, with no message. | `excel/Cellules.java` `type()`/`brut()` |
| B10 | Low | Managers are never cleaned up (a Manager_ID that no longer manages anyone stays), and cycles (A↔B) are not detected. That becomes a problem once "manager sees their team" recurses. | `ImportClasseurService.java:328-349` |
| B11 | Low | `DELETE /api/collaborateurs/{id}` does a hard delete. Foreign keys (score, performance…) make it fail with a generic 500. | `CollaborateurController.java:33-36` |

### 3.3 Transactions
- `CalculTrimestreService.calculer` intentionally has no global transaction (each step is idempotent). **OK**, but a crash between steps leaves scores without 9-box cases until a re-run. Show `erreurCalcul` in the RH UI.
- `ParametreController.modifier` validates and then throws inside the transaction, so it rolls back. **OK.**
- `spring.jpa.open-in-view` is not set (default `true`): lazy loading in views hides N+1 queries. Set it to `false` once the view services load what they need.

### 3.4 N+1 queries
| Where | Mechanism | Size |
|---|---|---|
| `ScoreService.recalculerTrimestre` → `enregistrer` | `scoreRepository.findByCollaborateurAndTrimestre` **per collaborateur** (`ScoreService.java:169`), then `save`. `figerOrganisation` touches the lazy `manager` and then the EAGER `Manager.collaborateur`. | ≈3 queries × N collaborateurs |
| Every `Score`/`Collaborateur` list | `Collaborateur.entite` **EAGER** (`Collaborateur.java:62`) + `Entite.parent` **EAGER** (`Entite.java:55`) + `Score.entite` EAGER (`Score.java:52`), none fetched in the JPQL queries | Up to ~130 extra `SELECT entite` per page (132 entités in the dataset) |
| `PosteCritiqueService.evaluer` | `successeurIdentifieSource.successeursIdentifies(posteId)` per position, plus the 5 lazy `competenceRequiseN` per position | ≈6 × 18 positions |
| Import | 1 `save` per row (IDENTITY, so no batching): ≈2,500 INSERTs for sheet 06 | Acceptable at 100 people. Plan `hibernate.jdbc.batch_size` + `saveAll` if the real population is in the thousands. |

At 100 collaborateurs this is fine. At the bank's real size (unknown → question), the recalculation and the pages will slow down. Fix: pre-load `Score`s into a map, and use `join fetch c.entite e left join fetch e.parent …` or `@EntityGraph`.

### 3.5 Fragility of Excel parsing
- **Columns are read by position, not by header name.** `FormatClasseur.java:36-58` checks that each expected header is **at a fixed index** and rejects the whole workbook if a column moved. `ImportClasseurService` then reads `Cellules.texte(ligne, 13)` and so on. **This contradicts requirement 2 ("columns read by header name").**
- The header row is searched only in the first 10 rows, by column A.
- The workbook-level period check (`PeriodeClasseur`) reads the file name and the title rows. That is useful, but with generated templates the period should come from a hidden metadata sheet instead.
- `WorkbookFactory.create` also accepts `.xls` (HSSF). Decide whether to allow it.

---

## 4. Security

**Context:** "local" deployment on a single machine, but **4 profiles share it**. The threat is no longer only a malicious website. It is also **a logged-in collaborateur reading or changing other people's data**.

| # | Severity | Finding | Location |
|---|---|---|---|
| S1 | **Critical** | **No authentication or authorization at all.** Every page and every `/api/**` endpoint is open to anyone who can reach `localhost:8080`. The "profiles" do not exist. | `pom.xml` (no `spring-boot-starter-security`) |
| S2 | **Critical** | `POST /api/collaborateurs/import?cheminFichier=…` makes the server **open any local path chosen by the caller** (path traversal / file-existence probe; POI then parses it). | `CollaborateurController.java:38-42` → `ImportService.java:179-185` |
| S3 | High | **Mass assignment:** `POST /api/collaborateurs` and `POST /api/competences` bind the **JPA entity** directly (`@RequestBody Collaborateur`), without `@Valid`. `save` = merge, so **any existing collaborateur can be overwritten** (status, entité…). | `CollaborateurController.java:28-31`, `CompetenceController.java:22-25` |
| S4 | High | **Personal data exposed:** `GET /api/collaborateurs` returns the full entity (date of birth, sex, entité) for everyone. The Comité, 9-box and vigilance APIs return every collaborateur's names and scores. With 4 profiles this becomes an **IDOR**: `/api/collaborateurs/{id}/scores`, `/api/trimestres/{a}/{n}/vigilance/{id}` and `/talents/{id}` accept any matricule. | `ScoreController.java:59`, `VigilanceController.java:53`, `TalentController.java:37,56` |
| S5 | High | **Anyone can change the weights** (`PUT …/parametre`), run an import, a recalculation or a placement. The requirement says RH only. | `ParametreController.java:84`, `ImportController`, `TrimestreController.java:53`, `ScoreController.java:28`, `NeufBoxController.java:36`, `TalentController.java:80` |
| S6 | Medium | **CSRF after login:** the `X-Talent360` header is an adequate protection *without cookies*. Once there are session cookies, you need Spring Security's CSRF token for HTML forms (login, CRUD), and the header or token for `fetch`. Also set `SameSite=Strict` on the session cookie. | `ProtectionRequetesFilter.java` |
| S7 | Medium | **Folder import (to build):** the folder must be **configured server-side only** (never a request parameter), resolved with `toRealPath()`, and every file checked to be inside it. Do not follow links, ignore `~$*.xlsx` (Excel lock files) and non-`.xlsx` files, and cap the size and number of files. POI's default `ZipSecureFile` settings are in place (zip bomb). Do not raise them. | — |
| S8 | Medium | **Formula injection** in generated templates and exports: a name or text starting with `=`, `+`, `-` or `@` must be written as a **string cell** (never `setCellFormula`), and user text must be prefixed with `'` if it is ever written to CSV. | Future template and export code |
| S9 | Low | Error messages returned to the client (`IllegalArgumentException`, `requeteMalFormee`) contain `exception.getMessage()`, which can leak internal details. | `ApiExceptionHandler.java:56-59, 92-99` |
| S10 | Low | MySQL connection as **`root`** with an empty default password (`${SPRING_DATASOURCE_PASSWORD:}`). Create a dedicated user limited to the `talent360bank` schema. | `application.properties:11-12` |
| S11 | Low | Third-party CDN (jsDelivr) on every page: this breaks on a bank machine without internet access and adds a supply-chain dependency. Copy Bootstrap into `static/vendor`. | `templates/*.html` |
| ✅ | — | **XSS:** every template uses `th:text` (no `th:utext`), so output is escaped. Keep it that way in the import report, which will show text from cells. | `templates/` |
| ✅ | — | **Secrets:** none in the code or in Git history (checked: no `.xlsx` has ever been committed; `docs/data/` is gitignored). `toString()` methods avoid personal data in logs. | — |

**Recommended security target** (single machine, 4 profiles):
- `spring-boot-starter-security` with a form login, **BCrypt**, and a session timeout of about 15 minutes (shared machine). Log out on the login page.
- A `Utilisateur` table (rework `UtilisateurRH`): `login`, `hash`, `role` enum `{COLLABORATEUR, MANAGER, COMITE, RH}`, an optional FK to `collaborateur`, and `actif`.
- **URL rules:** `/rh/**` and writes to `/api/**` → `RH`; `/comite/**` and the talent, succession and critical-position APIs → `COMITE`, `RH`; `/manager/**` → `MANAGER`; `/moi/**` → `COLLABORATEUR` (and manager, who is also a collaborateur).
- **Object-level checks** in the services (not only on URLs): a `PerimetreService` answering "can the current user see matricule X?" (self, team, all).
- **No default admin password in code.** Create the first RH account at startup from environment variables, or with a one-off command.

---

## 5. Future risks (what will break)

| Change | What breaks | Where |
|---|---|---|
| **Self-evaluation + manager evaluation** ("same structure") | `Performance` and `Potentiel` have a **unique constraint on (collaborateur, trimestre)**, so two evaluations of the same person cannot be stored. Every repository, `ScoreService`, `SuccessionService` (reads `Potentiel.noteLeadership/noteMobilite`) and the import assume a single row. | `Performance.java:16-18`, `Potentiel.java:15-17`, `PerformanceRepository`, `PotentielRepository`, `ScoreService.java:69-73` |
| **Folder import of per-person files** | B2 (mass deactivation and deletion), B4 (duplicates), fixed-position format checks, period read from the file name. `ImportClasseurService` is designed for **one workbook = the whole population**. | `ImportClasseurService`, `FormatClasseur`, `PeriodeClasseur` |
| **RH CRUD + re-import** | B1: the import wipes RH edits. You must decide **who is the source of truth** for each field (file or app). | `ImportClasseurService.java:289-304` |
| **4 profiles** | Every UI service shows "everyone, latest quarter" (`DashboardService`, `NineBoxViewService`, `VivierService`, `ComiteTalentViewService`). The APIs take a matricule without checking it. A single `PagesController` for everything means constant merge conflicts. | `ui/**`, `controller/**` |
| **New campaign** | Creating a quarter turns it into "the latest quarter" on every page even when empty (B5). Weights are copied from the previous quarter (good), but **nothing is recalculated** after a change. The experience score depends on today's date (C4). | `TrimestreRepository.findTopByOrderByAnneeDescNumeroDesc` (4 call sites) |
| **Manager / Entité** | `Manager` must be a collaborateur (1–1). A manager who is missing from the population file (director, external) is rejected ("manager inconnu"). `Manager.entiteGeree` is always empty. `RattachementVivier.direction` is **free text** (not an FK to `Entite`), so renaming a direction breaks the thematic pools. | `Manager.java:25-33`, `RattachementVivier.java:27-28` |
| **Schema** | `ddl-auto=update` never renames or drops anything. Every entity change needs a `DROP DATABASE` (the docs already say so, §7 of `docs/import-donnees.md`) or a hand-written `ALTER` initializer. With 3 people changing entities, local databases will drift. | `application.properties:16` |
| **Real population size** | The N+1 queries in §3.4 and row-by-row import. | — |
| **Spring Boot 4** | `@MockBean` removed. | tests |

---

## 6. Gap analysis against the new requirements

| # | Requirement | Current state (WIP) | Action | Files concerned | Effort |
|---|---|---|---|---|---|
| M1 | Rename Employé → Collaborateur | **Done in the WIP** (entities, repositories, services, DTOs, tests; `/api/employes` aliases). **Not merged.** | **KEEP** (merge now) + DELETE the aliases | whole WIP; `CollaborateurController.java:14`, `CompetenceCollaborateurController.java:16`, `ScoreController.java:58-59` | S |
| M2 | Manager entity | Done in the WIP: 1–1 role on `Collaborateur`, created from `Manager_ID` | **KEEP** + MODIFY (login link, cleanup, cycle detection, CRUD) | `entity/Manager.java`, `repository/ManagerRepository.java`, `ImportClasseurService.java:328-349` | S |
| M3 | Entité entity | Done in the WIP: 4-level tree, unique code = path | **KEEP** + ADD CRUD; MODIFY `RattachementVivier` → FK | `entity/Entite.java`, `entity/RattachementVivier.java` | M |
| R1a | Generate Excel templates (locked cells, no merged cells) | Nothing | **ADD** a template service (POI): input cells `setLocked(false)`, `protectSheet`, lock the workbook structure, `DataValidation` (0–100, 1–5, lists), hidden `_META` sheet (type, template version, campaign, matricule), string cells only | new `excel/modele/ModeleCollaborateurService`, `ModeleManagerService`, `excel/modele/MetaModele`; endpoint `/rh/modeles` | M |
| R1b | Collaborateur file: self-evaluation + engagement | Nothing (engagement = one /100 score; the Google Forms questionnaire has 1–5 items + NPS) | **ADD** + engagement conversion rule (client) | the templates above + `entity/ReponseEngagement` (or columns on `QuestionnaireEngagement`) | M |
| R1c | Manager file: evaluation of each team member, same structure | Nothing | **ADD** (one workbook per manager, one row per team member, prefilled with matricule and name, locked) | `ModeleManagerService` | M |
| R1d | Store self- and manager evaluations separately | Impossible (unique (collab, quarter)) | **MODIFY** the model: `source {AUTO, MANAGER}` on `Performance`/`Potentiel` (unique (collab, quarter, source)) + repositories | `entity/Performance.java`, `entity/Potentiel.java`, `PerformanceRepository`, `PotentielRepository`, `ScoreService`, `SuccessionService` | M |
| R2a | "Importer" button: read the whole folder, validate, load, report (success / errors / duplicates) | Single-workbook multipart upload, dataset format. Report = success, errors, deactivations, **no duplicates** | **ADD** `DossierImportService` (folder from config, one file = one unit, its own report) + **MODIFY** the report (per file: status, errors, duplicates) | new `service/importation/DossierImportService`, `RapportLot`; `ImportExcel` (one row per file + a batch id); `application.properties` (`talent360.import.dossier`) | L |
| R2b | Columns read by header name | By **fixed position** (`FormatClasseur`, `Cellules.texte(ligne, n)`) | **MODIFY**: reader `LecteurEntetes` (map normalised name → index, required and optional columns, duplicate header = error) | `excel/Cellules.java`, `service/FormatClasseur.java`, `ImportClasseurService` | M |
| R2c | Re-importing a new campaign regenerates everything | `CampagneService` = import + full calculation; corrected file replaces data; deactivation of absent people | **MODIFY**: define "regenerate" for a folder (delete and reload the campaign's inputs, then recalculate). **Do not** reuse the deactivation-by-absence rule on per-person files (B2). | `CampagneService`, `ImportClasseurService.java:318-327, 834-851` | M |
| R2d | Keep the dataset workbook import? | Works | **KEEP** as a "reference / demo" import (RH only), or DELETE if the client confirms that only the templates count | `ImportService`, `ImportController` | S |
| R3 | "Matricule manager" column stored in the database | `Manager_ID` column N of sheet 01 → `collaborateur.id_manager` (FK to `manager`) | **KEEP** the storage; MODIFY the reader (by header name "Matricule manager"); decide **which file** carries it (population reference file) | `ImportClasseurService.java:310, 328-349` | S |
| R4 | All calculations run from the database | Yes (`CalculService`, `ScoreService` read `Performance`/`Potentiel`/`Parametre`) | **KEEP** + MODIFY: choose the source (MANAGER, or a blend) + fix C4/C7/C8 + automatic recalculation after weight changes | `CalculService`, `ScoreService`, `SuccessionService.java:191`, `VigilanceService.java:210-215, 315`, `ParametreController.java:84-100` | M |
| R5a | Real login, 4 profiles | None. No role dropdown in the repository. | **ADD** Spring Security (form login, BCrypt, session), `Utilisateur` entity + roles, login page, logout | `pom.xml`, new `config/SecurityConfig`, `entity/Utilisateur` (replaces `UtilisateurRH`), `templates/login.html`, `fragments/sidebar.html` (`sec:authorize`, `thymeleaf-extras-springsecurity6`) | M |
| R5b | Collaborateur: sees only their own results | Nothing (no "my space"; APIs open by matricule) | **ADD** `/moi` pages + scoping | `ui/controller/EspaceCollaborateurController`, `ui/service/EspaceCollaborateurViewService`, `templates/collaborateur/*` | M |
| R5c | Manager: sees their team | Nothing | **ADD** team queries (`ScoreRepository.findByTrimestreAndManager`, `CollaborateurRepository.findByManager`), `/manager` pages, scoping | repositories, `ui/controller/EspaceManagerController`, `templates/manager/*` | M |
| R5d | Comité Talent: talent and succession modules | Comité page, 9-box and pools exist (for everyone). Critical positions = placeholder (API ready). | **MODIFY** (role restriction) + ADD critical positions / succession page | `PagesController`, `templates/postes-critiques.html`, `ComiteTalentViewService` | M |
| R5e | RH = only admin: CRUD on collaborateurs, managers, entités | Collaborateur CRUD without validation (entity binding, hard delete); nothing for Manager/Entité | **MODIFY** (DTO + `@Valid` + soft delete ARCHIVE) + **ADD** Manager/Entité CRUD + user account management | `CollaborateurController`, new `EntiteController`, `ManagerController`, `UtilisateurController`, `templates/rh/*` | L |
| R5f | RH views all profiles | — | **ADD** (RH has every role in the rules; a "view as" page is optional) | `SecurityConfig`, `PerimetreService` | S |
| R5g | RH manages calculation weights | Complete API (`GET/PUT …/parametre`), placeholder page | **KEEP** the API + **ADD** the page + automatic recalculation | `ParametreController`, `templates/rh/parametres.html` | M |
| R5h | RH runs imports | API only | **ADD** an import page + report (button → folder) | `templates/rh/import.html`, `ImportController` | M |
| R6 | Order Collaborateur → Manager → RH; replace the role dropdown | — | See §7 and §8 | — | — |
| X1 | Remove the dangerous endpoints | `POST /api/collaborateurs/import?cheminFichier` | **DELETE** | `CollaborateurController.java:38-42`, `ImportService.java:179-185` | S |
| X2 | Real dashboard | 6 hard-coded KPIs | **MODIFY** (use `TableauDeBordService`, which already exists) | `DashboardService.java:43-49` | S |
| X3 | Offline assets | CDN | **MODIFY** (copy into `static/vendor`) | `templates/*.html` | S |

---

## 7. Work split (Jasmine = calculations, Dou = data/import, Ima = UI)

### 7.1 Rules to avoid merge conflicts
1. **Day 1: merge the WIP into `develop`, then freeze entity renames.** Everyone rebases on it.
2. **One owner per hot file:**
   - `entity/**`, `repository/**`, `ImportClasseurService`, `excel/**` → **Dou**. Jasmine asks for columns or queries through a short PR that Dou reviews.
   - `service/` calculation classes (`CalculService`, `ScoreService`, `NeufBoxService`, `TalentService`, `SuccessionService`, `VigilanceService`, `PosteCritiqueService`) → **Jasmine**.
   - `templates/**`, `static/**`, `ui/**` → **Ima**.
   - `pom.xml`, `application.properties`, `config/SecurityConfig` → **Jasmine**, changed **once on Day 1–2**, then frozen.
3. **Split `PagesController`** into one controller per profile (`EspaceCollaborateurController`, `EspaceManagerController`, `ComiteController`, `RhController`) so that Ima and whoever does the backend for a page never touch the same file.
4. **The contract between backend and UI = view-model DTOs** (`ui/model/*`), agreed on paper on the morning of each phase. Ima can build pages against a stub service while the real one is being written.
5. Small PRs (≤1 day), CI must be green, and one review by another team member.

> Why Jasmine owns the security backend: it is cross-cutting (filter, `pom.xml`, service-level scoping, which is "calculation over a perimeter"), and Dou already carries the biggest load (templates and folder import). If the calculation work falls behind, move `SecurityConfig` to Ima and keep scoping with Jasmine.

### 7.2 Phase 0: foundations (30 Sept – 1 Oct)
| ID | Task | Owner | Files | Depends on | Parallel? |
|---|---|---|---|---|---|
| T0.1 | Commit and PR the WIP (`feature/import-partag`), merge into `develop`, switch the GitHub default branch to `develop`, recreate local databases | Jasmine (WIP owner) + review by Dou | whole WIP | — | **Blocking for everyone** |
| T0.2 | Send the client questions (§10); write down the **template spec** (columns, headers, `_META` sheet, scales) in `docs/modeles-excel.md` | Dou (spec) + Jasmine (scales and formulas) | `docs/modeles-excel.md` | — | yes |
| T0.3 | Delete S2 (`/import?cheminFichier`), the dead code (§2.4) and the hard-coded KPIs | Jasmine (controllers) / Ima (DashboardService) | `CollaborateurController`, `ImportService`, `dev/**`, `ExcelReader`, `DashboardService` | T0.1 | yes |
| T0.4 | Copy Bootstrap locally; layout that is ready for roles | Ima | `static/vendor/**`, `templates/fragments/**` | — | yes |

### 7.3 Phase 1: Collaborateur (1 Oct – 6 Oct), milestone M1 = "a collaborateur logs in and sees their own results computed from their imported file"
| ID | Task | Owner | Files | Depends on | Parallel? |
|---|---|---|---|---|---|
| T1.1 | Evaluation model: `SourceEvaluation {AUTO, MANAGER}` on `Performance`/`Potentiel`, unique (collab, quarter, source); engagement items (per the client's answer) | Dou | `entity/Performance`, `Potentiel`, `QuestionnaireEngagement` (+ `ReponseEngagement`), repositories | T0.1 | with T1.4, T1.6 |
| T1.2 | Header-name reader `LecteurEntetes` (+ tests: moved column, missing column, duplicate header, blank rows → fixes B3, B8, B9) | Dou | `excel/LecteurEntetes.java`, `excel/Cellules.java` | T0.2 | with T1.1 |
| T1.3 | **Collaborateur** template: generation (locked cells, validation lists, `_META`), one file per collaborateur or one combined file (client) | Dou | `excel/modele/ModeleCollaborateurService.java`, `MetaModele.java` | T0.2, T1.2 | with T1.4–T1.7 |
| T1.4 | `SecurityConfig` + `Utilisateur` (role, FK collaborateur) + BCrypt + first RH account from env vars + `PerimetreService` (self / team / all) | Jasmine (Dou reviews the entity) | `pom.xml`, `config/SecurityConfig.java`, `entity/Utilisateur.java`, `repository/UtilisateurRepository.java`, `service/securite/*` | T0.1 | with T1.1–T1.3 |
| T1.5 | Login page, logout, sidebar by role (`sec:authorize`), 403 page | Ima | `templates/login.html`, `templates/erreur/403.html`, `fragments/sidebar.html` | T1.4 (URL + field names only, agreed on day 1) | yes |
| T1.6 | Calculation fixes: quarter reference date (C4), vigilance perimeter independent of `Score` (C8), engagement from items (C9), N+1 in `ScoreService` (§3.4) | Jasmine | `SuccessionService`, `VigilanceService`, `ScoreService`, `Trimestre` (reference date field via Dou) | T1.1 for C9 | yes |
| T1.7 | **Folder import v1**: collaborateur files only. Configured folder, safe listing (S7), one file = one report entry (OK / errors / duplicate matricule / invalid template / wrong campaign), **no mass deactivation** (B2), `ImportExcel` log per file + batch | Dou | new `service/importation/DossierImportService.java`, `RapportLot.java`, `ImportExcel`, `application.properties` (1 line) | T1.1, T1.2, T1.3 | — |
| T1.8 | Read model "my results": scores (source MANAGER, and AUTO for comparison), categories, 9-box case, skill gaps, engagement; `EspaceCollaborateurViewService` | Jasmine | `ui/service/EspaceCollaborateurViewService.java`, `ui/model/MesResultats*.java` | T1.1, T1.4 | with T1.9 (stub) |
| T1.9 | "My space" page `/moi` (results, gaps, history) | Ima | `ui/controller/EspaceCollaborateurController.java`, `templates/collaborateur/*.html` | T1.8 contract | yes (on a stub) |
| T1.10 | End-to-end M1 test: generate → fill → drop in folder → import → calculate → log in as collaborateur → see only my own results; try another person's matricule → 403 | the 3 of you | `src/test/.../e2e/Collaborateur*Test` | everything | — |

### 7.4 Phase 2: Manager (7 Oct – 8 Oct), milestone M2
| ID | Task | Owner | Files | Depends on | Parallel? |
|---|---|---|---|---|---|
| T2.1 | **Manager** template (one workbook per manager, team rows prefilled and locked, same criteria) | Dou | `excel/modele/ModeleManagerService.java` | T1.3 | with T2.3, T2.4 |
| T2.2 | Folder import v2: manager files (source MANAGER), check "this manager evaluates only their team", duplicates (2 files for the same manager) | Dou | `DossierImportService` | T1.7, T2.1 | — |
| T2.3 | Team queries + scoping: `findByManager`, team scores, **self vs manager gap**, team 9-box, team gaps, team vigilance | Jasmine | `repository/*` (Dou reviews), `ui/service/EspaceManagerViewService.java`, `PerimetreService` | T1.4 | yes |
| T2.4 | Manager pages `/manager` (team, team 9-box, per-person detail, self vs manager comparison) | Ima | `ui/controller/EspaceManagerController.java`, `templates/manager/*.html` | T2.3 contract | yes (stub) |
| T2.5 | Security tests: a manager cannot see someone outside the team (API + pages) | Jasmine | `src/test/.../securite/*` | T2.3 | — |

### 7.5 Phase 3: RH (+ Comité) (9 Oct – 13 Oct), milestone M3
| ID | Task | Owner | Files | Depends on | Parallel? |
|---|---|---|---|---|---|
| T3.1 | Collaborateur / Manager / Entité CRUD: DTO + `@Valid`, soft delete (ARCHIVE), **fix B1** (import updates field by field; decide which fields RH owns) | Dou | `CollaborateurController`, new `ManagerController`, `EntiteController`, `dto/*Form.java`, `ImportClasseurService` | T0.1 | with T3.3, T3.4 |
| T3.2 | User account management (create from a collaborateur, reset password, deactivate) | Dou (backend) / Ima (page) | `UtilisateurController`, `templates/rh/utilisateurs.html` | T1.4 | yes |
| T3.3 | Parameters page + **automatic recalculation** after `PUT` + reference date | Jasmine (backend) / Ima (page) | `ParametreController`, `templates/rh/parametres.html` | — | yes |
| T3.4 | Import page (button → folder, report per file, campaign history, "regenerate campaign") | Ima (page) / Dou (endpoint) | `templates/rh/import.html`, `ImportController` | T2.2 | yes |
| T3.5 | Comité: role restriction, critical positions / succession page (API ready), talent validation | Ima (page) / Jasmine (rules) | `ComiteController`, `templates/comite/*.html` | T1.4 | yes |
| T3.6 | Real RH dashboard (plug in `TableauDeBordService`) | Ima | `DashboardService`, `dashboard.html` | — | yes |

### 7.6 What can run in parallel without conflicts
- **Always in parallel:** Dou (`entity`, `excel`, `importation`), Jasmine (`service` calculation, `config/Security`), Ima (`templates`, `static`, `ui/controller` per profile). They do not share files if §7.1 is followed.
- **Sequence points:** T0.1 (everyone), T1.1 (the model: Jasmine and the import wait for it, so do it first, ½ day), T1.4 (the login contract for Ima), and the view-model DTO contracts at the start of each phase.
- **Watch out:** `pom.xml` and `application.properties` (Jasmine only), `sidebar.html` (Ima only), `ImportClasseurService` (Dou only).

---

## 8. Day-by-day plan (today Wednesday 30 Sept → delivery Thursday 15 Oct)

*Assumption: weekdays only (4–5 and 10–11 Oct are off; use them as buffer if needed).*

| Day | Jasmine (calculations + security) | Dou (data / import) | Ima (UI) | End-of-day checkpoint |
|---|---|---|---|---|
| **Wed 30/09** | Commit and PR the WIP (T0.1) | Review the WIP PR; draft the template spec (T0.2) | Copy Bootstrap locally, prepare the role-based layout (T0.4) | WIP PR open; client questions sent |
| **Thu 01/10** | Merge WIP; delete S2 and dead code (T0.3); start `SecurityConfig` + `Utilisateur` (T1.4) | Evaluation model AUTO/MANAGER (T1.1) → PR by noon; `LecteurEntetes` (T1.2) | Login page + 403 + sidebar (T1.5); remove the hard-coded KPIs | `develop` = new model; local databases recreated |
| **Fri 02/10** | Finish T1.4 + `PerimetreService`; calculation fixes C4, C8 (T1.6) | Collaborateur template (T1.3) | "My space" page on a stub (T1.9) | Real login works (RH account from env vars) |
| **Mon 05/10** | Read model "my results" (T1.8); engagement from items (C9, if the client has answered) | Folder import v1 (T1.7) | Plug `/moi` into T1.8 | Collaborateur file import → database OK |
| **Tue 06/10** | M1 E2E test + collaborateur scoping tests | Import report fixes, duplicates | Page polish, import error display | **M1 Collaborateur done** |
| **Wed 07/10** | Team queries + self vs manager gap (T2.3) | Manager template (T2.1) | Manager pages on a stub (T2.4) | Manager template generated |
| **Thu 08/10** | Manager scoping tests (T2.5) | Manager import (T2.2) | Plug in the manager pages | **M2 Manager done** |
| **Fri 09/10** | Automatic recalculation after weights (T3.3 backend); Comité rules (T3.5) | CRUD collaborateurs/managers/entités + B1 fix (T3.1) | Parameters page, import page (T3.3/T3.4) | CRUD API OK |
| **Mon 12/10** | Real dashboard backend / N+1 | Account management (T3.2), "regenerate campaign" | CRUD pages, account page, Comité / critical positions pages | **M3 RH done (features)** |
| **Tue 13/10** | Security pass (every role against every URL), Excel comparison | Import hardening (S7, B5, B6: import lock) | Visual consistency, error messages | Feature freeze at 18:00 |
| **Wed 14/10** | Full rehearsal on a **clean machine** (fresh MySQL, dedicated user, `mvnw package`, run the jar), fixes | RH user guide (import, templates) | Demo screenshots and flow | Release candidate |
| **Thu 15/10** | Buffer / delivery | Buffer / delivery | Buffer / delivery | **Delivery** |

### MUST-HAVE (without these, the delivery fails)
1. WIP merged; `develop` builds and tests are green.
2. Real login with 4 roles, BCrypt, **object-level scoping** (a collaborateur cannot read someone else's data, a manager cannot read outside the team). Dangerous endpoints removed (S2, S3).
3. Generated collaborateur and manager templates (locked cells, no merged cells, `_META` sheet).
4. Folder import: header-name reading, per-file validation, report with success / errors / **duplicates**, loaded into the database.
5. Calculations from the database with the manager evaluation as the source, and C4 fixed.
6. Pages: My space (collaborateur), My team (manager), Comité (9-box, talents), RH (import + report, parameters).
7. RH CRUD for collaborateurs (at least), with validation and soft delete.
8. No fake figures in the UI (hard-coded KPIs removed).

### NICE-TO-HAVE (in this order if time is left)
1. Manager and Entité CRUD screens (they can be maintained through the import in the meantime).
2. Critical positions / succession page (the API already exists).
3. Self vs manager comparison chart.
4. Exports (PDF/Excel) of results.
5. `11_DEVELOPMENT_PLAN`, persisted alerts (`Alerte`).
6. Flyway (instead of `ddl-auto=update` + hand-written initializers).
7. Performance tuning (N+1, JDBC batch) if the real population is large.
8. Anonymised dataset extract in the repository so CI runs the Excel comparison.

---

## 9. Top 10 priorities (ranked)

1. **Commit, PR and merge the WIP** (`feature/import-partag`) into `develop` today or tomorrow, and switch the GitHub default branch to `develop`. Everything else depends on it.
2. **Get the client's answers** to the blocking questions (§10: engagement formula, auto/manager weighting, template granularity, reference file with "Matricule manager", folder location, what each profile may see).
3. **Remove the dangerous endpoints** (S2 `/api/collaborateurs/import?cheminFichier`, S3 entity binding) and the fake KPIs. This is quick and removes the worst exposure.
4. **Evaluation model with source AUTO/MANAGER** (unique (collab, quarter, source)). It is the only schema change that blocks import, calculation and UI. Do it first, in half a day.
5. **Spring Security + Utilisateur + roles + `PerimetreService`** (object-level scoping, not only URLs), with tests for each role.
6. **Header-name reader** (`LecteurEntetes`) + blank-row fix (B3) + duplicate detection (B4). It is the base of every import.
7. **Excel template generation** (collaborateur, then manager): locked cells, validation lists, `_META` sheet, no merged cells.
8. **Folder import** with a per-file report, **without** the "whole population" logic (B2) and with a lock against concurrent imports (B6).
9. **Calculation fixes:** fixed reference date for experience (C4), vigilance perimeter (C8), engagement conversion (C9), automatic recalculation after weight changes; then the M1/M2 end-to-end tests.
10. **Fix B1** (the import must not wipe RH-edited fields) before shipping CRUD, then the RH screens (import, parameters, CRUD).

---

## 10. Questions for the client

**Blocking (needed before 2 Oct)**
1. **Engagement:** how do the Likert answers (1–5) and the NPS (0–10) become the "/100" score used by vigilance (threshold < 60)? Simple average ×20? Which items count? Is the free-text question stored?
2. **Self vs manager evaluation:** which evaluation feeds the official performance and potential scores: the manager's only, or a weighted blend (for example 30% self / 70% manager)? Is the self-evaluation shown to the manager, and the other way round?
3. **Scope of the files:** does the collaborateur rate **both** performance (5 criteria) and potential (7 criteria), or only some of them? Does the manager rate potential?
4. **Granularity:** one collaborateur file **per person** or one combined file? One manager file **per manager** (with the team as rows)?
5. **Population reference:** which file provides identity, entité and **"Matricule manager"** (the `BASE DE DONNEES.xlsx` file has no manager column)? Is it the same file every campaign? What format are the real matricules (leading zeros)?
6. **What each profile may see:** can a collaborateur see their 9-box case, their "talent / high potential" status, their vigilance index? Can a manager see the self-evaluation and the vigilance index of their team? Does a manager see only direct reports, or the whole hierarchy below them (N-2…)?

**Needed before 8 Oct**

7. **Source folder:** where is it (a local path on the machine)? What happens to imported files: moved to an `archive/` subfolder, renamed, or left in place?
8. **Duplicates:** two files for the same person or manager. Keep the latest (by modification date), reject both, or ask RH?
9. **"Regenerate everything" when re-importing a campaign:** does it delete and recalculate the whole campaign (including manual Comité decisions), or only the inputs from the files?
10. **Comité Talent:** do they enter their decisions (Oui / Non / En attente) in the app, or in a file? Do they see names or anonymised data?
11. **Vigilance "baisse de performance"** (C7): an imported flag (as in the dataset) or a comparison with the previous quarter? If the comparison, with what minimum drop?
12. **Reference date** for seniority (experience score): the campaign date or the end of the quarter?

**Organisation and deployment**

13. **Accounts:** who creates them (RH, one by one or by import)? Initial password and forced change on first login? Is RH one person or several?
14. **Target machine:** OS, internet access (CDN), MySQL already installed? Which version? Who runs the application (service, desktop shortcut)?
15. **Real population size** (for performance and import time).
16. **Role dropdown:** it is in no branch of the repository. Is it in the client's HTML prototype? Should we copy its visual design for the login page?
17. The dataset has 13 sheets (Dashboard, 9-box, Development plan…). Must the **dataset workbook** import still be supported, or only the new templates?

---

## Appendix: how this audit was run

- `git archive develop` and a copy of the working tree (without `target/` or `.git`) were extracted to a temporary folder, and `./mvnw -B test` ran on each copy. **develop: 475/475 green; WIP: 558/558 green.** The project's `target/` folder was not touched.
- The dataset formulas were read directly from the XML inside `docs/data/TALENT_360_BANK_Dataset_V1.xlsx` (sheets 00 to 12, cells and formulas). None of the three workbooks has sheet protection, data validation or merged cells in its data sheets; `00_DASHBOARD` has 24 merged cells. So the current files are not templates in the sense of requirement 1.
- Remote branches `origin/ima-ui`, `origin/iman2` and `origin/douae` are fully merged into `develop` (0 commits ahead).
