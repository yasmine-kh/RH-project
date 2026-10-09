# TALENT 360 BANK — Developer Guide

This guide explains the whole project: what it is for, how it is built, how the calculations work, how to run it, and what is still missing. It describes the code on `develop` as of commit `d5b7548` (pull request #21). Where the project is incomplete, the guide says so.

The detailed business formulas, with every default value and workbook column, are in [`moteur-de-calcul.md`](moteur-de-calcul.md) (French). How a screen must send write requests is in [`requetes-ecriture.md`](requetes-ecriture.md).

---

## Contents

1. [The project](#1-the-project)
2. [Functional view](#2-functional-view)
3. [Architecture](#3-architecture)
4. [Tech stack](#4-tech-stack)
5. [Data model](#5-data-model)
6. [The calculation engine](#6-the-calculation-engine)
7. [Key design choices](#7-key-design-choices)
8. [Request lifecycle](#8-request-lifecycle)
9. [Security](#9-security)
10. [How to run](#10-how-to-run)
11. [Git workflow](#11-git-workflow)
12. [Current status and what's left](#12-current-status-and-whats-left)
13. [Glossary](#13-glossary)

---

## 1. The project

### Context

TALENT 360 BANK is a talent-management application for a bank's HR department.

- **Offline and single-workstation ("monoposte").** It runs on one HR workstation, against a local MySQL database. It is not a web service for many users.
- **Only HR logs in.** One account type, RH, which sees everyone's results. Employees and managers never use the app: they only fill in the Excel files that HR imports.
- **Sensitive personal data.** It holds employee records (identity, birth date, evaluations, engagement). This drives the security choices in [section 9](#9-security).

### The problem it solves

Every quarter, HR evaluates about a hundred employees and must answer a few questions:

- Who performs well, and who has potential? (performance and potential scores, 9-Box matrix)
- Who are our talents and high potentials? Which of them does the Talent Committee validate?
- For each critical position, do we have a successor ready, and how soon?
- Which employees are at risk of leaving, and why? (vigilance index)
- Which skills does each employee need to develop, and how urgently?

Today this is done in an Excel workbook full of formulas. The application replaces the formulas with an engine whose rules are explicit, tested and configurable.

### The client's workbook is the reference

The file `docs/data/TALENT_360_BANK_Dataset_V1.xlsx` is the specification. It is **gitignored** (it contains realistic, if fictitious, personal data), so each developer receives it separately.

| Sheet | Content |
|---|---|
| `00_PARAMETRES` | Every weight and threshold (rows 6–77) |
| `01_COLLABORATEURS` | 100 employees |
| `02_PERFORMANCE`, `03_POTENTIEL` | Evaluation marks, scores, categories |
| `04_9BOX` | 9-Box placement |
| `05_REFERENTIEL_COMPETENCES`, `06_EMPLOYEE_SKILLS` | 25 skills, 2,500 employee × skill levels |
| `07_POSTES`, `08_POSTES_CRITIQUES`, `09_SUCCESSION` | Positions, critical positions, successors and matching |
| `10_TALENTS` | Talent / high-potential flags, committee decision, thematic pool |
| `11_DEVELOPMENT_PLAN` | Development plans (not used by the engine) |
| `12_VIGILANCE` | Engagement, risk signals, vigilance index |

The rule is simple: **the engine must produce the same results as the workbook.** The test `DatasetExcelComparaisonTest` replays the workbook through the engine and compares the results column by column (see [section 7](#the-excel-comparison-test)).

---

## 2. Functional view

### Screens

Sidebar order (RH): Tableau de bord RH, Campagne d'évaluation, Collaborateurs, Matrice 9-Box, Compétences & Gaps, Engagement & Fidélisation, Viviers, Postes critiques & Succession, Comité Talent, Talent Passport, Alertes, Historique, Notifications, Managers, Organigramme, Import, Paramètres (17 entries, numbered 01–17). Every screen by quarter has the quarter selector (`?trimestre=AAAA-N`) and, when no quarter exists yet, the same empty state with a link to `/import` (`fragments/commun.html` : `aucunTrimestre`, `choixTrimestre`). Text is always rendered with `th:text` (never `th:utext`, checked by `PagesSansTrimestreTest`).

| URL | Status | What HR does there |
|---|---|---|
| `/` | Working | **Interactive** home dashboard ([below](#home-dashboard)): the prototype's 10 cards, charts (9-Box, entité, vigilance, performance, potential, viviers, alert types) and lists (collaborateurs, alertes, viviers), cross-filtered by clicking, filters kept in the URL (`?case=9&entite=…`). Quarter selector |
| `/dashboard-dg` | Redirect | **Removed**: merged into `/`. `DashboardController` redirects to `/` (keeping `?trimestre=`) so old links don't 404. The JSON API `GET /api/dashboard/dg`, `TableauDeBordDg` and `TableauDeBordDgViewService` are removed too (audit round 1, #24). |
| `/carriere-mobilite` | Redirect | **Removed** from every menu (no data source); the route redirects to `/` for now (`PagesEnDeveloppementController`). |
| `/engagement`, `/historique` | In development | Placeholder pages (`templates/placeholder.html`), the only pages with "En cours de développement" (checked by `ModulesPagesDatasetTest` and `FinitionsAuditTest`). |
| `/mon-engagement`, `/auto-evaluation`, `/evaluation-manager` | Removed (404) | Only HR logs in, so no form for employees or managers: "Mon engagement" opens the fiche's imported questionnaire answers, the Vue manager shows the imported manager evaluations. |
| `/collaborateurs` | Working | Employee list: filters (entité with subtree, 9-Box case, talent, pool, readiness, vigilance, name/matricule), sort by clicking the column headers (name, performance, potential, matching, vigilance), 20 rows per page; each name links to the fiche. `ListeCollaborateursViewService` → `templates/collaborateurs.html` |
| `/9box` | Working | The matrix on the left (about 60 %), a **detail panel** on the right (below it on a phone). Cells show only case number + label, the count and the % (no names), all the same height; the whole cell is a link to `/9box?trimestre=…&case=N#detail` (works without JavaScript). The panel shows the case's label, a one-line meaning from the placement rule (`04_9BOX` has no description column), the count and its people (name → fiche, matricule, entité, performance, potential) with a search field (`q`) and a sort (`tri` = NOM / PERFORMANCE / POTENTIEL), and a **🖨 Imprimer** button next to the search field: it prints only the open case's list (label, count, table) with the current search and sort, under a print-only header with the quarter, the print date and the search / sort used (`.impression-entete`). `neufbox.js` applies the search/sort then calls `window.print()`; `@media print` in `app.css` (scoped by `body.impression-9box`) hides the sidebar, the top bar, the matrix, the thresholds panel, the forms and the buttons. No library. Talent clé (case 9) is open by default. `static/js/neufbox.js` (vanilla) switches the panel, filters and sorts in place with the 9 panels already in the page. Shared matrix fragment: calm cells, only Talent clé tinted, selected cell outlined. On `/`, the Vue manager and the Vue entité the same compact matrix links each cell to `/collaborateurs?case=N` with the page's filters (the Vue manager filters its own team table, `?case=N#equipe`). |
| `/viviers` | Working | One card per thematic pool then the relief pool (`VivierSyntheseService`: members, talents, high potentials, Ready Now, averages, positions covered, gaps identified), then the saved pool members. Each card shows its **Dernière revue** ([below](#comité-talent-decisions)). Quarter selector |
| `/postes-critiques` | Working | Every critical position of the displayed quarter: direction, criticality, holder, number of successors, best candidate and matching, coverage status, **Dernière revue** ([below](#comité-talent-decisions)), and under each position **all** its successors (matching, readiness, largest gap). Quarter selector. `PosteCritiqueViewService` → `templates/postes-critiques.html` |
| `/competences` | Working | Per-skill table sorted by average gap (current / target / gap averages, with a gap, Prioritaire, level distribution as small bars), the top gaps, and each critical position's requirements against its successors; filters entité, pool, critical position. `CompetenceSyntheseService` → `templates/competences.html` |
| `/comite-talent` | Working | Chooses a quarter and a committee status; **records the committee's decisions** ([below](#comité-talent-decisions)): Valider / Réévaluer / Ne pas retenir on each proposed talent, the five decisions on each successor of a critical position, an optional comment, "Modifier" on any decision. Cards: talents en attente, Talents validés (Comité), Successions à valider. |
| `/alertes` | Working | Alerts of the displayed quarter ([below](#alerts)), computed on the fly by the engine: counters per type and severity, table sorted by severity, filters by type, severity, direction and name. Quarter selector |
| `/notifications` | Working | Counters per severity and type and the 10 most severe alerts with their links (`NotificationsViewService`, same figures as `/alertes`). No preference switches (nothing would store them). `templates/notifications.html` |
| `/campagne` | Working | Evaluation progress per direction (progress bars), click a direction to see its child entités, managers with missing evaluations, and what the data cannot show (`remarques`). Self-evaluations show "Non importé" while none is imported, like the dashboard. `SuiviCampagneViewService` → `templates/campagne.html` |
| `/managers`, `/managers/{matricule}` | Working | Choice page (managers with team size), then the **Vue manager**: manager, entité, team size and averages, the team's 9-Box (shared matrix, with names), team table (name → fiche, scores, case, talent / HP, readiness on the target post, vigilance), the team's alerts and missing evaluations. `ProfilsPagesController` → `ProfilsPagesService` (reuses `VueManagerViewService` as is, plus `PosteCibleService` for readiness) → `templates/vue-manager.html` |
| `/entites`, `/entites?code=…` | Working | **Organigramme** (RH menu): one card per direction (name, headcount, talents, alerts of the quarter), click → its départements as a collapsible tree (`<details>`, headcount on the right, each line links to its Vue entité), name search on top; then the **Vue entité**: clickable breadcrumb up the hierarchy, child entités with their figures (clickable), subtree KPIs (talents link to the filtered list), 9-Box (shared matrix; a box opens `/collaborateurs?entite=…&case=N`), critical positions of the subtree, high-vigilance alerts, managers. The code is a **query parameter** because it contains `/`. `VueEntiteViewService` as is → `templates/vue-entite.html` |
| `/import` | Working | Uploads the dataset workbook (.xlsx) for a quarter, with simulation, "calculate after import" and an optional reference date; sees the report (status, rows per sheet, rejected rows with sheet + Excel row + reason, deactivations, calculation result or error, link to the quarter's dashboard) and the last 20 imports. [Below](#import-page) |
| `/parametres` | Placeholder | — (settings are editable through the API only; see [`requetes-ecriture.md`](requetes-ecriture.md)) |

**Profile selector and per-profile menu** (top of the sidebar, `fragments/sidebar.html`, on every page). The "profiles" are views RH opens, not logins: one RH account, no role or security change. The chosen profile (RH, Collaborateur, Manager, Comité Talent) and the chosen person are kept in the **HTTP session** (`ui/model/ProfilActif`, attribute `profilActif`), written only by `GET /profil?profil=&cible=&trimestre=&retour=` (`ProfilsPagesController`; one lookup of the person when choosing, via `ProfilsService.profil`).

- Picking **Manager** or **Collaborateur** applies the profile at once, before any person: the request stays on the current page (`retour`, the page path set by `ProfilsAdvice.pageCourante`; only a local path is accepted, never `//…`, `/profil` or `/api`, otherwise `/`). The menu of the profile shows right away; the items that need a person are greyed, without link, with "choisir un manager" / "choisir un collaborateur" (`ElementMenu.grisee`). Under the selector, the field **Choisir un manager** / **Choisir un collaborateur** (datalist of that profile only); picking someone opens his Vue manager / fiche and every item then links to his filtered page. Choosing another person keeps the profile; picking the same profile again keeps the person. The `/managers` list stays an RH page (RH menu "Managers"); the Manager profile never goes through it.
- **Comité Talent** and **RH** need no person. **Revenir à la vue RH** resets profile and person.
- There is no Entité profile (the prototype has none): entités are the RH menu item **Organigramme**. The old link `/profil?profil=ENTITE&cible=…` still opens the Vue entité, in the RH view.
- An unknown person leaves the profile unchanged.

The sidebar then shows, under the selector, a banner "Vue : Manager — Nom Prénom" with **Revenir à la vue RH** (`/profil?profil=RH`), and the menu of the profile, from the prototype (`docs/prototype/index.html`, `ROLES`; same labels and order), built by `ui/service/MenuProfilService` from the session only. **Numbering:** every menu runs 01, 02, 03… in the order shown, with no gap, greyed items and app pages included (`MenuProfilService.numeroter`; the prototype's numbers are not kept since "02 Tableau de bord DG" was removed); `ProfilsMenuDatasetTest` checks it for RH, Collaborateur and Manager (with and without a person) and Comité (no query; `ProfilsAdvice` adds `profilActif` and `menu` to every page). Every page stays reachable by URL; only the menu changes. The current item is highlighted (`ElementMenu.page` = `activePage`).

| Profile | Menu (prototype) → page |
|---|---|
| RH (DRH / Talent Manager) | the full menu (no "02 Tableau de bord DG": merged into the RH dashboard): Accueil, Collaborateurs, Managers (`/managers`), Organigramme (`/entites`), 9-Box … Paramètres |
| Collaborateur | Mon profil → his fiche · Mon engagement → the fiche's imported questionnaire answers (`/fiche-collaborateur?matricule=…#questionnaire`) · Notifications → `/alertes?q=<matricule>` (his alerts). No form and no self-evaluation page: only HR logs in. |
| Manager | Campagne d'évaluation → Vue manager `#evaluations` (missing evaluations) · Collaborateurs → Vue manager `#equipe` · Matrice 9-Box → Vue manager `#neufbox` · Talent Passport → `/fiche-collaborateur` · Alertes → Vue manager `#alertes` · Notifications → `/notifications` |
| Comité Talent / Direction | Comité Talent · Postes critiques & Succession · Historique (`/historique`, in development) |

`static/js/profil.js` (vanilla) submits as soon as a profile or a listed person is picked; without JavaScript the **Appliquer** button does the same (when the profile changes, the previous person sent along by the field is ignored). Options: `ProfilsService`, two queries per page (people, managers); the profile and its menu add none (`ProfilsMenuDatasetTest` compares the query counts of the same page under each profile).

**Organigramme** (`/entites`, `OrganigrammeViewService` → `ui/model/Organigramme`, `templates/entites.html`, `fragments/arbre-entites.html`, `static/js/organigramme.js`). The tree is `VueEntiteViewService.listerEntites()` as is, with useless levels collapsed: an entité whose single child has the same headcount is shown on one line ("Siege - Casablanca / Siege", linking to the deepest one); headcounts are unchanged. Talents (`TalentService.detecterTalents`) and alerts (`AlertesViewService.alertes`) are counted per direction label; without settings for the quarter, only the tree and headcounts show. Fixed number of queries. Without JavaScript every card and branch is open and the search field is hidden; the script folds everything on load and filters by name (accents ignored), showing the matching lines, their parents and their children.

*Open question (not changed):* Région and Agence are mixed into the hierarchy under each département (`Direction / Département / Région / Agence`, from `01_COLLABORATEURS` H–K). The supervisor still has to say whether they should be a separate axis (a geographic view next to the functional one).

**Cross links.** A manager's name links to the Vue manager (Fiche, Collaborateurs, Campagne, Alertes, Notifications — `AlerteVue.managerMatricule`), an entité to the Vue entité (`AlerteVue.entiteCode` on alerts); the Fiche shows the entité breadcrumb and "Voir l'équipe de son manager". No page links to `/api/**` (`LiensEtMatriceDatasetTest`). Links are built in `ui/service/LiensPages`.

**Not found.** An unknown matricule, entité code or quarter on a page gives `templates/erreur/404.html` (status 404, French message, no stack trace), via `ProfilsAdvice`. The JSON API keeps its own JSON errors.

The module screens (`/collaborateurs`, `/competences`, `/notifications`, `/campagne`) are served by `ui/controller/ModulesPagesController`, which only reads the filters and builds the pagination / sort links; the filter menus come from `ui/service/OptionsFiltresService`. Each one calls its view service directly (the same as its JSON endpoint). A fixed number of queries whatever the population (`SansSessionOuverteIntegrationTest`); `ModulesPagesDatasetTest` checks every screen on the workbook.

All screens are read-only except `/import`. Everything else is available through the REST API (`/api/...`), listed in [section 3](#rest-api).

### Displayed quarter (all screens)

Every screen that shows a quarter (`/`, `/9box`, `/viviers`, `/postes-critiques`, `/comite-talent`, `/alertes`) accepts **`?trimestre=AAAA-N`** (e.g. `?trimestre=2026-3`). `ui/service/TrimestreCourantService` resolves it:

- given → that quarter; **404** if it is unknown or badly written (`2030-1`, `T3 2026`, `abc`);
- not given → the **most recent quarter that has scores**, not simply the most recent one created. A quarter opened before its import, or left empty by a failed import (audit B5), no longer empties every screen while the previous quarter has all the data;
- no quarter calculated yet → the most recent created; no quarter at all → nothing (the screens say so).

The API uses the same rule when a quarter is optional and omitted (`GET /api/collaborateurs/{id}/competences` without `annee` / `numero`, through `ChargeurRessources.exigerTrimestreOuDernier`), so screens and API always show the same quarter.

It costs two queries (the quarters and those with scores), whatever their number. Each screen's controller calls `trimestreCourant.selectionner(trimestre)`, gives `selection.trimestre()` to its view service and calls `selection.exposer(model)`, which sets two **model attributes for the templates**:

| Attribute | Type | Content |
|---|---|---|
| `trimestre` | `ui.model.OptionTrimestre`, or `null` when there is no quarter | the displayed quarter: `valeur` ("2026-3", the value of `?trimestre=`), `libelle` ("T3 2026"), `annee`, `numero`, `hasData` (true when the quarter has scores) |
| `trimestres` | `List<OptionTrimestre>`, newest first | every quarter, for a selector. Mark the selected option with `option.valeur == trimestre.valeur`; show the ones with `hasData = false` as "not calculated yet" |

A selector is a GET form with a `<select name="trimestre">`, as on `dashboard.html` and `comite-talent.html`.

### Home dashboard

`GET /` → `ui/controller/DashboardController` → `ui/service/DashboardService` (only the engine figures the page shows) and `ui/service/TableauDeBordInteractifService` (the interactive view) → `templates/dashboard.html` + `static/js/tableau-de-bord.js` + Chart.js 4.4.4 (`static/vendor/chartjs/chart.umd.js`, served locally, MIT licence header kept). `GET /dashboard-dg` redirects here. No engine figure is computed in the screen code.

**Layout** (Power BI / Qlik style, prototype tokens and cards): **cards** on top, **charts** in the middle, **lists** at the bottom.

- *Cards*: exactly the prototype's 10 (`renderDashRH`), in its order, 2 rows of 5, same style (coloured left border, icon, label): 👥 Population, ⭐ Talents validés, 🚀 Hauts potentiels, 🔄 Viviers actifs, 👔 Postes critiques / 🔗 Couverture succession, 🟢 Successions Ready Now (engine card *Successeurs Ready Now*), ❤️ Engagement (N réponses), 🔴 Postes sans relève, 🧩 Gaps critiques (engine card *Postes critiques en alerte*: fewer successors than the minimum, the prototype's `gapsCritiques`). Population, Talents validés, Hauts potentiels, Viviers actifs and Engagement follow the filters (recounted on the filtered people with the engine's definitions; engagement = average of their questionnaire scores, 2 decimals HALF_UP like `DashboardService`; card colour by the prototype rule ≥ 75 teal, ≥ 60 gold, else rust). The 5 critical-position cards are the engine's cards as is and do not follow the filters (they count positions, not people). Without filter every card equals the engine card. The other engine figures (talents proposés, talents clés, relève, à risque, alertes, compétences en gap, postes couverts, campaign) are not on this page and are no longer built by `DashboardService`; their figures are on `/collaborateurs` (filters), `/notifications`, `/campagne`, `/competences` and `/postes-critiques`.
- *Charts*: 9-Box matrix (HTML cells, counts only), collaborateurs by entité (direction, horizontal bar), vigilance levels (doughnut), performance category, potential category, viviers (members per pool, the relief pool in gold), alertes by type (horizontal bar). All six requested charts have a data source; none was skipped. Performance and potential are shown **by engine category** (Exceptionnelle… / Élevé…), not by score band: bands would be a new calculation.
- *Lists*: collaborateurs (performance + potential descending, the former top-talents rule; name → fiche), alertes (same lines as `/alertes`; link to the fiche, the position or the committee), viviers (each pool and its filtered members, names → fiche).

**Cross-filtering.** Filters live in the URL: `case` (1–9), `entite` (direction code, e.g. `DIR:RESEAU_RETAIL`), `vigilance` (`FAIBLE`/`MODEREE`/`ELEVEE`), `perf`, `pot` (category codes), `vivier` (`COMMERCIAL`…`RELEVE`), `alerte` (`TypeAlerte` name); an unknown value is ignored. Rules (identical in `TableauDeBordInteractifService.Regles` and `tableau-de-bord.js`):

- a person passes when they match every active filter; for `alerte`, when they have at least one alert of that type;
- an alert about a person passes when the person passes (ignoring `alerte`) and its type matches; an alert about a position passes only when no person-only filter (`case`, `vigilance`, `perf`, `pot`, `vivier`) is active, its direction is the `entite` filter's and its type matches;
- each chart counts with every filter **except its own**, so the clicked element stays highlighted and the others are dimmed; cards and lists use every filter.

Clicking an element toggles its filter (click again to remove); filters stack and show as chips with ✕ above the charts, plus **Réinitialiser**. No reload: the page carries the whole quarter once (`#tdb[data-donnees]`, JSON written by Jackson and escaped by `th:attr`), the script refilters in memory and updates the URL (`history.pushState`; back/forward re-apply). The server renders the same filtered view from the URL, so a bookmarked or refreshed link opens filtered, and the 9-Box cells, chips and Réinitialiser are plain links without JavaScript (the other charts need it; a `<noscript>` note says so). Changing the quarter reloads the page without filters.

Everything the page shows is computed by the service before rendering: card values, 9-Box cells in grid order with their emoji and background (`CaseCompte.etiquette`, `classe`), each person's case label and pools text (`Personne.caseEtiquette`, `viviersTexte`); a missing value (no entité, no name, no 9-Box case, no pool, unknown vigilance code, engine card without value) becomes "—" or "Sans direction" instead of failing (`TableauDeBordInteractifDonneesManquantesTest`; `TableauDeBordPageIntegrationTest` renders a collaborateur without entité or data, filtered and not). The template only displays.

Data: `ListeCollaborateursViewService.population` (all calculated people, unpaged, with the engine's `Membre`: categories, engagement, relief pool), `AlertesViewService.alertes`, the MANAGER / AUTO evaluation matricules (same reads as `/campagne`). A fixed number of queries.

Without settings for the quarter (`tableau.erreur`), only the engine's available cards and the reason show. The former blocks (alerts panel, pools table with averages, DG bench strength and top talents) are replaced by the charts and lists: top talents are the head of the collaborateurs list, positions in alert are in the alert list, and the bench strength per position stays on `/postes-critiques`.

`DashboardService` builds only what the page shows (audit round 1, #25): the 5 critical-position cards and
the 9-Box counts; without settings, the "Collaborateurs actifs" and "Engagement moyen" cards with the reason. Alerts
are computed once, by `TableauDeBordInteractifService` (the alert list).

| Card | Source | Workbook (`00_DASHBOARD`) |
|---|---|---|
| Postes critiques | critical positions followed | C6 |
| Couverture succession | % of critical positions not in alert | A10 (rounded) |
| Successeurs Ready Now (shown as "🟢 Successions Ready Now") | (critical position, identified successor) pairs at Ready Now | C10 |
| Postes critiques sans successeur | critical positions with no identified successor | E10 |
| Postes critiques en alerte | fewer successors than the minimum setting (same as above while the minimum is 1) | — |
| Collaborateurs actifs, Engagement moyen /100 (N réponses) | without settings only | — |

The people cards (Population, Talents validés, Hauts potentiels, Viviers actifs, Engagement) are counted by
`TableauDeBordInteractifService` on the quarter's population; without filter they equal the engine's
`SyntheseTableauDeBord` (talents validés E6, hauts potentiels G6).

`TableauDeBordDatasetTest` checks the cards, the engine figures that are not cards (à risque, gaps prioritaires, vigilance) and the 9-Box counts against the workbook; `TableauDeBordPageIntegrationTest` checks the engine cards and the hand-computed figures on H2, the page layout, and the 10 cards (order, labels, colours, which ones follow the filters, equal to the engine without filter); `ModulesPagesDatasetTest` checks the 10 cards against the workbook (100, 8, 21, 5 viviers, 15, 93.33 %, 16, engagement, 1, 1), the figures of the removed cards on the pages that still show them (10 talents proposés, 21 relève and 10 Talent clé on `/collaborateurs`, 45 at risk by vigilance filter, 23 alerts on `/notifications`, 100 / 100 evaluations and no auto-evaluation on `/campagne`, 14 covered positions on `/postes-critiques`), `?case=9` (exactly the 10 Talent clé people of `04_9BOX`), stacked filters, chip removal links, ignored values, and that the embedded data is escaped; `LiensEtMatriceDatasetTest` checks the 9-Box cell links (`/?trimestre=2026-3&case=9`). The script itself is not run by the Java tests (MockMvc has no JavaScript).

### Comité Talent decisions

`/comite-talent` (`PagesController` → `ui/service/ComiteTalentViewService` → `templates/comite-talent.html`, forms in
`templates/fragments/comite.html`). The forms post to `ui/controller/ComiteDecisionsController`, which calls
`service/DecisionsComiteService`. Plain HTML forms (`th:action` adds the CSRF token), no JavaScript; after saving,
the browser is sent back to the same section (`#talents`, `#successions`) with a French message
("Décision enregistrée : … — Oui.") or the reason for a refusal.

| What | Endpoint | Values written | Table |
|---|---|---|---|
| Proposed talent | `POST /comite-talent/talents` (`trimestre`, `matricule`, `decision`, `commentaire`, `confirme`) | Valider → `OUI`, Réévaluer → `EN_ATTENTE`, Ne pas retenir → `NON` | `validation_comite` |
| Successor of a critical position | `POST /comite-talent/successions` (`trimestre`, `poste`, `matricule`, `decision`, `commentaire`, `confirme`) | `VALIDER`, `VALIDER_AVEC_PLAN`, `MAINTENIR_EN_VIVIER`, `REEVALUER`, `NE_PAS_RETENIR` | `validation_succession` |

- Each decision stores `date_decision` (now), `id_utilisateur` (the logged-in RH account, `securite/UtilisateurCourant`)
  and `commentaire` (optional, 500 characters at most, trimmed). Changing a decision replaces the row (one per talent
  and quarter, one per position, successor and quarter).
- Only a talent proposed by the engine for that quarter, or a successor evaluated on that critical position, can
  receive a decision (`DecisionRefuseeException` otherwise: message, nothing written). Unknown quarter → 404.
- **"Ne pas retenir" asks for confirmation**: without `confirme=oui` nothing is saved and the page comes back with a
  confirmation box on that row (`?confirmer=<matricule>` or `?confirmerSuccession=<poste>:<matricule>`), the comment
  kept. Enforced on the server, so it holds without JavaScript.
- **Modifier**: decided talents (panel "Décisions du comité") and decided successions show the current decision, who
  decided and when ("décidé le 08/10/2026 à 14:05 par rh"), the comment, and a "Modifier" link
  (`?modifier=<matricule>`, `?modifierSuccession=<poste>:<matricule>`) that reopens the buttons. Decisions read from
  the workbook show "issu de l'import (10_TALENTS)".
- **Pending** ("Talents proposés en attente", "Successions à valider"): no decision, or Réévaluer.
- **Effects.** Every "Talents validés" figure is computed when read (`ValidationComiteService`: proposed talent and
  decision OUI): the dashboard card, the Comité card, the Collaborateurs badge and filters change as soon as a decision
  is saved. No cache.
- **Dernière revue** (`service/RevuesComiteService`, two queries): on `/postes-critiques`, the date of the latest
  decision on one of the position's successors; on `/viviers`, the latest decision on one of the pool's members, as a
  talent or as a successor (`VivierSyntheseService.syntheseEtMembres` gives the members). "—" when there is none.
  Decisions imported from the workbook have no date and do not count.

**Import and app decisions.** `10_TALENTS` column G (Oui / Non / En attente) creates or updates
`validation_comite` for the quarter; an empty cell changes nothing; a row whose employee is no longer in the sheet is
removed. A decision entered in the app (`date_decision` set, `ValidationComite.estSaisieApplication`) is an
exception: the import never overwrites it and never removes it. If the workbook says something else, or no longer
lists the employee, the app decision stays and the import result lists it (`decisionsConservees`: sheet, Excel row,
"BP019 : le classeur indique « Oui », la décision saisie dans l'application est conservée (« Non », le 08/10/2026).").
To take the workbook's value, change the decision on `/comite-talent`. `validation_succession` is never touched by
the import (the workbook has no succession decisions).

Tests: `ComiteDecisionsDatasetTest` (real workbook: each decision saved with date, user and comment; confirmation;
changes; Talents validés on the Comité, the dashboard and the list; Successions à valider; Dernière revue on both
pages; re-import through `/import` keeps app decisions and reports the disagreement; refusals, CSRF, 404, anonymous),
`ImportServiceJpaTest` (re-import keeps a conflicting, a matching and a removed app decision; without app decisions the
workbook still decides), `ModulesPagesDatasetTest` and `FinitionsAuditTest` (no "en cours de développement" on
`/comite-talent`).

### Alerts

`/alertes` (`AlertesController` → `ui/service/AlertesViewService` → `templates/alertes.html`). No alert table: the alerts are recomputed from the engine on each request, for the displayed quarter. `AlertesViewService.alertes(trimestre)` is also the source of the dashboard's alert list.

| Type | Severity | Rule (source) | Subject / link |
|---|---|---|---|
| Poste critique sans successeur | Critique | critical position with 0 identified successor (`PosteCritiqueService`) | position → `/postes-critiques` |
| Successeurs insuffisants | Élevée | fewer successors than `seuilsCouverture.nbMinSuccesseurs` (`PosteCritiqueService`) | position → `/postes-critiques` |
| Un seul successeur | Élevée (`SEVERITE_UN_SEUL_SUCCESSEUR`, one level below Critique) | critical position **not already in alert** with exactly `NB_SUCCESSEURS_UN_SEUL` (1) identified successor; under the minimum, "Successeurs insuffisants" is raised instead, never both (`PosteCritiqueService`). No `Parametre` setting carries this count, hence a documented constant | position → `/postes-critiques` |
| Vigilance élevée | Élevée | level ELEVEE, with index and signals (`VigilanceService`, `EntreesVigilance` rule) | employee → fiche |
| Évaluation du manager manquante | Élevée | manager performance or potential evaluation missing for an active employee: same rule as the manager view; the message says whether a self-evaluation exists (`ResultatsCollaborateurs`) | employee → manager view (fiche if no manager) |
| Écart auto-évaluation / manager | Moyenne | self minus manager ≥ `seuilsAutoEvaluation.seuilEcartImportant` (`ResultatsCollaborateurs.autoVsManager`) | employee → manager view |
| Talent sans décision du Comité | Moyenne | proposed talent with committee status EN_ATTENTE (`ValidationComiteService`) | employee → `/comite-talent?statut=EN_ATTENTE` |
| Nouveau talent identifié | Moyenne (`SEVERITE_NOUVEAU_TALENT`, the lowest level; the prototype calls it "info") | proposed talent this quarter who was not a talent (or had no score) in the **most recent earlier quarter that has scores**; each quarter judged with its own `seuilsTalent` (`TalentService`). With no earlier quarter, no alert and a sentence in `informations` | employee → fiche |
| Compétences en gap prioritaire | Moyenne | number of skills with a Prioritaire gap; the `TOP_GAPS_COMPETENCES` (10) employees with the most | employee → fiche |

- Sorted by severity, then type (table order), then each source's order (highest vigilance index first, most gaps first...).
- Counters (per type, per severity, total) cover all alerts of the quarter; filters (`type`, `severite`, `direction` = direction label, `q` = name or ID, case and accents ignored) only narrow the table. An unknown filter value means "all".
- Links always point to pages, never to `/api/**` (checked on every page by `LiensEtMatriceDatasetTest`): a person → `/fiche-collaborateur?matricule=…&trimestre=…` (the Fiche page honours `trimestre`), a position → `/postes-critiques?trimestre=…#poste-<Poste_ID>`. The manager-related alerts lead to the employee's fiche (the manager view has no screen). Built in one place (`AlertesViewService.Liens`).
- Without settings for the quarter, the page shows the reason and no alerts (as the dashboard).
- `AlertesView.informations` (and `AlertesViewService.evaluer(trimestre).informations()`): what a rule could not do without it being an error, shown above the counters, e.g. "Nouveaux talents : aucun trimestre précédent avec des scores, rien à comparer (premier trimestre importé)." or "Nouveaux talents : comparés à T2 2026."
- Fixed number of queries whatever the population (`SansSessionOuverteIntegrationTest`).

Not implemented, because no data source exists: late development-plan actions (`11_DEVELOPMENT_PLAN` not imported), the alert lifecycle of the prototype (new / in progress / treated: needs a persisted alert), "compétence stratégique insuffisante" (no critical-skill flag in the reference data).

`AlertesPageIntegrationTest` (H2) covers each type, the counters, the filters, `?trimestre=`, the 404 and the equality with the dashboard's alert list; `AlertesDatasetTest` checks the workbook (1 position without successor = E10, 10 high vigilance = E15, 23 alerts in total). `AlertesNotificationsCampagneDatasetTest` checks the new rules on the workbook (no position with exactly 1 successor in `08`, no new talent with a single quarter) and a two-quarter scenario (BP019 below the talent threshold in T2 → the only new talent of T3).

### Notifications

`ui/service/NotificationsViewService` → `ui/model/Notifications`; `controller/NotificationsController`. Derived from the alerts of the **latest quarter with scores** (`TrimestreCourantService`, like the screens without `?trimestre=`). No table, no read/unread state (one RH user).

- `GET /api/notifications`: `total`, `parSeverite`, `parType` (the Alertes page's counters, by construction: it calls `AlertesViewService.construire` without filter), `alertes` (the first `NB_ALERTES` = 10, most severe first, each with its `lien`), `lienAlertes` (`/alertes?trimestre=…`), `informations`, `erreur`. Always recomputed (the cost of the Alertes page).
- `GET /api/notifications/badge`, for the layout's bell: {`trimestreLibelle`, `total`, `critiques`, `elevees`, `moyennes`, `lienAlertes`}. It reads the quarter (2 queries) and reuses the last computation of that quarter if it is younger than `DUREE_CACHE` (60 s); otherwise it recomputes. So the badge can lag up to 60 s after an import or a settings change; `/api/notifications` and the Alertes page are always current.

Tests: `NotificationsViewServiceTest` (cache duration), `NotificationsEtCampagneControllerTest` (JSON, 401), `AlertesNotificationsCampagneDatasetTest` (same counters as the Alertes page), `SansSessionOuverteIntegrationTest` (badge = 2 queries).

### Campaign progress

`ui/service/SuiviCampagneViewService` → `ui/model/SuiviCampagne`; `controller/SuiviCampagneController` at `GET /api/trimestres/{annee}/{numero}/campagne[?entite=code]`.

- An active employee is **evaluated** when the manager rated both performance AND potential this quarter (source `MANAGER`): the rule of the "Évaluation du manager manquante" alert, so `nbManquants` = the number of those alerts.
- Without `entite`: one line per direction (`Sans direction` last). With `entite`: one line per child entité of it, plus "Rattachés directement à …" for those attached to it; unknown code or quarter → 404. `total` covers the whole population (bank or entité).
- Each line: `nbActifs`, `nbEvalues`, `pourcentageEvalues` (1 decimal), `nbManquants`, `nbAutoEvaluations` (performance or potential with source `AUTO`), `managers` [{`matricule` (null = no manager), `nom`, `nbManquants`, `manquants` (matricules)}], most late first.
- **Not in the data, so not invented:** campaign dates and status (the workbook has none) and self-evaluations (the workbook import writes `MANAGER` evaluations only: `nbAutoEvaluations` stays 0). `remarques` says so.
- 6 queries (7 with `entite`) whatever the population.

Tests: `AlertesNotificationsCampagneDatasetTest` (actives and evaluated per direction vs `01` / `02` / `03`, 100 %, a direction's children add up to its total), `NotificationsEtCampagneControllerTest`.

### Import page

`/import` (`ImportPageController` → `ui/service/ImportPageService` → `templates/import.html`, report in `templates/fragments/import-rapport.html`). Step-by-step for RH: [`import-donnees.md`](import-donnees.md#importer-depuis-lapplication).

- **No import logic in the page.** POST `/import` calls `CampagneService.importer` exactly like `POST /api/imports` (same fields: `fichier`, `annee`, `numero`, `simulation`, `calcul`), then shows the result. A normal HTML multipart form; the CSRF token is the hidden field added by `th:action` (no fetch, no `X-Talent360` header).
- **Checks before the import** (message on the page, nothing called): no file, not `.xlsx`, file over `spring.servlet.multipart.max-file-size`, invalid quarter or date.
- **Size limit.** The browser checks the size before sending. If a bigger file still reaches the server, Tomcat refuses it before Spring: the CSRF token in the body cannot be read, Spring Security forwards to `/erreur/403`, and the size error is raised there. `TailleImportAdvice` recognises the original URL and redirects to `/import?erreur=taille` (message). Tested on a real server (`ImportPageHttpTest`). A very large file can still end in a connection reset (Tomcat stops reading the upload).
- **Lock.** The whole upload (import + calculation) runs under `VerrouCalculTrimestre` for that quarter: a second upload, or a recalculation started elsewhere, gets "Un calcul est déjà en cours" instead of running in parallel. API imports (`POST /api/imports`) do **not** take it (AUDIT B6, for Dou).
- **Reference date.** Set after a successful import with `TrimestreService.modifierDateReference` (nothing stored depends on it, no recalculation).
- **Empty quarter (audit B5).** Not prevented by the import: format, period and unreadable-file failures stop before the quarter is created, but an import that fails afterwards (no readable row, unexpected error) leaves the new quarter empty. The screens are not affected (the displayed quarter is the latest one **with scores**), and the page warns when it happens. For Dou.
- **One workbook, one quarter.** The workbook announces its own period (`PeriodeClasseur`: file name, rows 1–3 of every sheet; `TALENT_360_BANK_Dataset_V1.xlsx` says "au 15/09/2026" in `00_DASHBOARD` A2, i.e. T3 2026). Choosing another quarter (e.g. T4 2026) is refused before anything is written: report status `ECHEC`, message *Le classeur porte une autre periode que T4 2026, rien n'a ete importe : T3 2026 (00_DASHBOARD A2 : "15/09/2026"). Verifier le fichier ou le trimestre choisi.*, no quarter created, one `ECHEC` line in the journal (`ImportAutreTrimestreDatasetTest`). To load T4, use the T4 workbook (its own date); re-importing the T3 data under T4 would give a T4 identical to T3 except where the history changes the result (see the note in [section 12](#12-current-status-and-whats-left)).
- **Report** (`ImportPageView.Rapport`) does not depend on the form: the coming folder import can render one per file with the same fragment, from a second form and POST in `ImportPageController`.
- **Committee decisions kept.** A decision entered on `/comite-talent` is never replaced or removed by an import; when `10_TALENTS` says otherwise (or no longer lists the employee), the report lists it under "Décisions du Comité conservées" (`ResultatImport.decisionsConservees`, also in the API response). Not an error: the status is unchanged ([details](#comité-talent-decisions)).
- **Statuses** are shown in plain French: Réussi / Partiel / Échec (`StatutImport.libelle`; the stored codes stay `SUCCES` / `PARTIEL` / `ECHEC`).
- **Unexpected errors** show "L'import a échoué. Vérifiez le fichier et réessayez." (`ImportService.MESSAGE_ECHEC`); the technical detail goes only to the log.
- **User.** Each journal row records the logged-in RH account (`ImportExcel.utilisateur`, column `import_excel.id_utilisateur`, via `securite/UtilisateurCourant`), shown in the history's "Utilisateur" column; older rows show "—".
- `th:text` only: sheet names, reasons and messages come from the uploaded file.

- **Questionnaire answers** (second form, `POST /import/questionnaire`, fields `fichier` and `trimestre` = an existing quarter): see [Questionnaire answers](#questionnaire-answers). Its report is shown above the form (`rapportQuestionnaire`, `erreurQuestionnaire`); the import is logged in the same journal with source `QUESTIONNAIRE_ENGAGEMENT`.

#### Questionnaire answers

Before this change the app stored **only the engagement score** (one number per employee and quarter, `QuestionnaireEngagement.scoreEngagement`, read from `12_VIGILANCE` column D of the workbook); the questionnaire answers file (`docs/data/TALENT 360 BANK — Questionnaire d'engagement collaborateur (réponses).xlsx`, a form export) was not imported at all. Now:

- `service/ImportQuestionnaireService` reads the answers file (first sheet; header row = the first of the first 10 rows with a `Matricule` column; `Horodateur` = answer date; every other non-empty header column is a question, coded `Q01`, `Q02`… in column order, its text kept as written, only leading/trailing spaces removed) and stores each non-empty cell **as is** in `entity/ReponseQuestionnaire` (collaborateur, trimestre, `code_question`, `ordre`, `theme` (empty for now), `question` text, `reponse`, `date_reponse`; unique per collaborateur + trimestre + code). No score is computed, and the workbook import (score) is unchanged.
- Rows without matricule or with an unknown matricule are rejected (listed in the report); two rows of the same matricule: the latest `Horodateur` wins. Re-importing replaces the answers of the file's employees for that quarter only.
- **No themes.** The file has no section rows and no theme is guessed: `theme` stays empty. On the fiche the Thème column is hidden while every answer's theme is empty, and comes back on its own as soon as one has a theme (`ReponsesQuestionnaire.avecTheme()`).
- **Empty cells.** Only filled cells are stored. In the sample file (and the test file built from it), columns Q–T (Q15–Q18, a form branch) have no cell at all for any respondent, so BP001 has 16 answers, not 20; `QuestionnaireDatasetTest` checks that every filled cell of BP001's row is stored.
- **Fiche** (`/fiche-collaborateur`): section **Réponses au questionnaire** (`id="questionnaire"`, `ReponsesQuestionnaireViewService` → `ui/model/ReponsesQuestionnaire`), one table in file order (Q01, Q02…; columns Code, Question, Réponse, plus Thème when a theme exists), no grouping, with a quarter selector (`?questionnaire=AAAA-N`, default = the fiche's quarter, 404 if unknown). Without answers: "Aucun questionnaire importé pour ce trimestre."
- **Manager evaluation:** there is no separate manager evaluation file with this structure. Manager evaluations come from the workbook (`02_PERFORMANCE` / `03_POTENTIEL`, one row per employee and one numeric column per criterion); those raw criterion notes are already stored (`Performance`, `Potentiel`) and shown on the fiche ("évaluation du manager" tables), so nothing was added for them.
- The provided answers file has test matricules (`ertyui`…): it is read (20 questions) but every row is rejected as unknown.

Tests: `QuestionnairePassportNeufBoxIntegrationTest` (import, file order, empty theme, re-import, rejected rows, real file, fiche section and selector, escaping); `QuestionnaireDatasetTest` imports `src/test/resources/questionnaire/questionnaire-reponses-BP001-BP003.xlsx` (the sample's sheet, header and answers with the real matricules BP001–BP003, Direction and Ancienneté from `01_COLLABORATEURS`, a readable comment) into T3 2026 of the real workbook and checks the answers on `/fiche-collaborateur?matricule=BP001` (16 answers, Q01…Q14, Q19, Q20).

Tests: `ImportPageIntegrationTest` (MockMvc, H2: report + calculation, simulation saves nothing, non-xlsx, invalid fields, size, CSRF 403, history, lock, rejected rows with Excel row numbers, unreadable file, empty quarter), `ImportPageHttpTest` (real server: CSRF token in a multipart form, 403 without it, size limit), `ImportPageDatasetTest` (the real workbook, if present).

### The quarterly workflow

1. **Import** the quarter's data.
   - HR uploads the client's workbook with `POST /api/imports` (multipart, field `fichier`, plus `annee` and `numero`). One upload loads employees, org chart, managers, skills, positions, critical positions, performance and potential marks, successors, committee decisions and vigilance facts ([`import-donnees.md`](import-donnees.md)).
   - `11_DEVELOPMENT_PLAN` is not imported yet.
   - The quarter's **reference date** (the date seniority is measured at) defaults to the last day of the quarter. Change it with `PUT /api/trimestres/{annee}/{numero}` `{"dateReference": "2026-09-15"}` if the campaign uses another date.
2. **Create the quarter's settings** with `POST /api/trimestres/{year}/{quarter}/parametre`, which copies the workbook defaults. Adjust them with `PUT` if needed: saving them **recalculates the quarter automatically** (steps 3–5), see [Changing the settings](#changing-the-settings).
3. **Recalculate the scores:** `POST .../scores/recalcul`.
4. **Place employees in the 9-Box:** `POST .../9box/placement`.
5. **Save the relief pool** so the pool screen shows it: `POST .../vivier-releve`.
6. **Consult** the screens and read-only endpoints: talents, high potentials, succession, critical positions, committee, thematic pools, vigilance, skill gaps. All of these are calculated at read time with the current settings.
7. **Decide.** HR prepares the Talent Committee and succession plans from these results. The committee's decisions and the identified successors are HR inputs that come back through the import; there is no screen to enter them yet.

```mermaid
flowchart LR
    A[Excel workbook<br/>quarterly data] -->|import| B[(MySQL)]
    P[Quarter settings<br/>Parametre] --> C
    B --> C[1. Score recalculation<br/>performance, potential,<br/>performance category]
    C --> D[2. 9-Box placement<br/>box + potential category]
    D --> E[3. Save relief pool<br/>AppartenanceVivier]
    D --> F{{Calculated on read}}
    F --> F1[Talents / high potentials]
    F --> F2[Succession matching<br/>critical positions]
    F --> F3[Committee: validated talents]
    F --> F4[Thematic pools]
    F --> F5[Vigilance index]
    F --> F6[Skill gap status]
    E --> G[Screens and API]
    F1 & F2 & F3 & F4 & F5 & F6 --> G
    G --> H[HR decisions:<br/>committee, successors,<br/>development actions]
    H -->|next import| A
```

---

## 3. Architecture

### Layers

The application is a classic layered Spring Boot application. Each layer only calls the one below it.

| Layer | Package | Role | Owner |
|---|---|---|---|
| Entities | `entity` | JPA-mapped tables, plus the value blocks of `Parametre` (`Poids*`, `Seuils*`, `Bareme*`) and small enums stored in tables | Jas (engine settings, `Score`), Dou (reference data) |
| Repositories | `repository` | Spring Data JPA interfaces, with explicit `join fetch` queries where the engine needs related entities | Dou, Jas |
| Engine (services) | `service`, `service.enums`, `service.resultat` | All calculations. `service.resultat` holds immutable result records (`ResultatMatching`, `DecisionComite`…). | **Jas** |
| Import | `service.ImportService`, `service.ImportClasseurService`, `service.FormatClasseur`, `service.PeriodeClasseur`, `excel.Cellules` | Reading the workbook | **Dou** |
| REST API | `controller`, `controller.dto` | JSON endpoints, input forms, response DTOs, error handling | Jas (engine endpoints), Dou (`CollaborateurController`, `CompetenceController`) |
| UI | `ui.controller`, `ui.service`, `ui.model`, `templates`, `static/css` | Server-rendered screens. View services assemble data; they never calculate. | **Ima** |
| Configuration | `config` | Startup initializers, `SecurityConfig` (RH login) and the request protection filter | Jas |
| Security | `securite` | Login and 403 pages, `UtilisateurDetailsService` (loads the RH account) | Jas |
| Errors | `exception` | `RessourceIntrouvableException` (→ 404), `DonneesIncompletesException` (→ 422) | Jas |

```mermaid
flowchart TB
    subgraph Browser
        UI[Screens<br/>Thymeleaf HTML]
        CLI[API clients<br/>curl, future JS]
    end
    subgraph App[Spring Boot application - 127.0.0.1:8080]
        F[ProtectionRequetesFilter + Spring Security<br/>Host check, write header, RH login]
        subgraph UIL[UI - Ima]
            PC[PagesController, DashboardController,<br/>AlertesController, ImportPageController] --> VS[View services<br/>ComiteTalentViewService,<br/>NineBoxViewService, VivierService,<br/>PosteCritiqueViewService, DashboardService,<br/>AlertesViewService, ImportPageService,<br/>TrimestreCourantService]
            VS --> UM[ui.model rows]
        end
        subgraph API[REST API - Jas / Dou]
            RC[13 REST controllers + ChargeurRessources<br/>ApiExceptionHandler] --> DTO[controller.dto]
        end
        subgraph ENG[Engine - Jas]
            CS[CalculService] --- SS[ScoreService]
            NB[NeufBoxService] --- TS[TalentService]
            SU[SuccessionService] --- PCS[PosteCritiqueService]
            VC[ValidationComiteService] --- VT[VivierThematiqueService]
            VG[VigilanceService] --- VR[VivierReleveService]
            CE[CompetenceCollaborateurService] --- TB[TableauDeBordService]
            SRC[/...Source interfaces<br/>implemented by the import/]
        end
        subgraph IMP[Import - Dou]
            IS[ImportService] --> ER[ImportClasseurService - Apache POI]
        end
        REPO[Repositories - Spring Data JPA]
        CFG[config: initializers]
    end
    DB[(MySQL<br/>talent360bank)]
    XL[[Excel workbook]]

    UI --> F
    CLI --> F
    F --> PC
    F --> RC
    VS --> ENG
    RC --> ENG
    RC --> IS
    ER --> XL
    ENG --> REPO
    IS --> REPO
    VS --> REPO
    CFG --> REPO
    REPO --> DB
```

### Packages at a glance

```
com.talent360bank.talent360bank
├── Talent360bankApplication        entry point
├── config                          startup initializers + request filter
│   ├── Matrice9BoxInitializer        seeds the 9 reference boxes
│   ├── ParametreInitializer          completes settings created before a block existed
│   ├── ColonnesObsoletesInitializer  makes removed settings columns nullable (MySQL)
│   ├── VivierReleveInitializer       creates the relief pool row
│   ├── ImportExcelInitializer        makes import_excel.id_utilisateur nullable (old DBs)
│   ├── TrimestreDateReferenceInitializer  fills trimestre.date_reference (old DBs)
│   ├── SourceEvaluationInitializer   one evaluation per source: marks old rows MANAGER, drops the old unique key (old DBs)
│   ├── PremierCompteRhInitializer    first RH account from environment variables
│   ├── SecurityConfig                form login, RH only
│   └── ProtectionRequetesFilter      Host check + X-Talent360 header on writes
├── entity (37)                     tables, Parametre blocks, stored enums
├── repository (17)                 Spring Data JPA
├── service (20)                    engine + import
│   ├── enums (7)                     levels, signals, statuses
│   └── resultat (11)                 immutable calculation results
├── controller (13 controllers,      REST API; ChargeurRessources turns URL
│   + 3 helpers) + dto (17)           parts into entities, ApiExceptionHandler
│                                     maps exceptions to ErreurApi
├── exception (2)
├── excel (1)                       Cellules - cell reading helpers for the import
├── securite                        login and 403 pages, UtilisateurDetailsService
└── ui
    ├── controller                    PagesController (9-Box, Viviers, Postes critiques, Comité, Paramètres - Ima),
    │                                 DashboardController, AlertesController, ImportPageController,
    │                                 TailleImportAdvice (Jas)
    ├── service (13)                  view services, TrimestreCourantService
    └── model (17)                    display rows
```

### REST API

Every page and every endpoint needs a logged-in RH ([section 9](#9-security)); without a session the API answers 401 in JSON. Every write (POST, PUT, PATCH, DELETE) also needs the header `X-Talent360: 1`. "Quarter" is always `{annee}/{numero}` in paths, or `?annee=&numero=` in query strings.

| Area | Endpoints |
|---|---|
| Settings | `GET` / `POST` / `PUT /api/trimestres/{annee}/{numero}/parametre` (`PUT` also recalculates the quarter and returns a `recalcul` recap; see [Changing the settings](#changing-the-settings)) |
| Scores | `POST .../scores/recalcul`, `GET .../scores`, `GET /api/collaborateurs/{id}/scores` (history) |
| 9-Box | `POST .../9box/placement` |
| Talents | `GET .../talents[/{id}]`, `GET .../hauts-potentiels[/{id}]` |
| Relief pool | `GET .../vivier-releve` (calculated), `POST .../vivier-releve` (save) |
| Succession | `GET /api/postes/{posteId}/candidats[?limite=]`, `GET /api/postes/{posteId}/candidats/{id}` |
| Critical positions | `GET /api/postes-critiques`, `.../alertes`, `.../synthese`, `.../{posteId}`. Each position lists **all** its evaluated successors (`successeurs`, best matching first), each with `plusGrandGap` |
| Target posts | `GET /api/trimestres/{annee}/{numero}/postes-cibles`: one entry per active employee with a complete score, by matricule ([below](#target-post-and-pool-summaries)) |
| Committee | `GET /api/comite-talent`, `GET /api/comite-talent/talents-valides` |
| Thematic pools | `GET /api/viviers-thematiques`, `GET /api/viviers-thematiques/{code}` |
| Pool summaries | `GET /api/viviers/synthese?annee=&numero=`: the 5 thematic pools then the relief pool ([below](#target-post-and-pool-summaries)) |
| Vigilance | `GET .../vigilance[?minimum=MODEREE]` (every active employee with at least one vigilance input, with or without a score: same rule as the fiche and the views, `EntreesVigilance`), `GET .../vigilance/{id}` |
| Skills | `GET /api/collaborateurs/{id}/competences[?annee=&numero=]` (one employee); `GET /api/trimestres/{annee}/{numero}/competences[?entite=&vivier=&poste=&ordre=&top=]` (Compétences module, [below](#competences-module)) |
| Dashboard | `GET /api/dashboard/synthese` |
| Notifications | `GET /api/notifications` (counters + 10 alerts), `GET /api/notifications/badge` (bell, cheap) ([below](#notifications)) |
| Campaign | `GET /api/trimestres/{annee}/{numero}/campagne[?entite=]` ([below](#campaign-progress)) |
| Employee list | `GET /api/trimestres/{annee}/{numero}/collaborateurs` with optional `entite`, `case`, `talent`, `vivier`, `readiness`, `vigilance`, `q`, `tri`, `ordre`, `page`, `taille` ([below](#collaborateurs-list)) |
| Quarters | `GET /api/trimestres`, `POST /api/trimestres` (`annee`, `numero`, optional `dateReference`), `PUT /api/trimestres/{annee}/{numero}` (`dateReference`), `POST .../calcul` |
| Import | `POST /api/imports` (workbook upload), `GET /api/imports` (journal) |
| Employees | `GET /api/collaborateurs`, `GET /api/collaborateurs/{id}` (both return `CollaborateurResponse`, never the entity), `POST /api/collaborateurs`, `DELETE /api/collaborateurs/{id}` |
| Employee record (fiche) | `GET /api/trimestres/{annee}/{numero}/collaborateurs/{matricule}/fiche` ([below](#fiche-collaborateur)) |
| Manager view | `GET /api/trimestres/{annee}/{numero}/managers` (picker), `GET /api/trimestres/{annee}/{numero}/managers/{matricule}/vue` ([below](#vue-manager)) |
| Entité view | `GET /api/entites` (tree), `GET /api/trimestres/{annee}/{numero}/entites/vue?code={code}` ([below](#vue-entité)) |
| Reference data | `GET` / `POST /api/competences`, `DELETE /api/competences/{id}` |

Errors always have the same JSON shape, `ErreurApi`: `{ statut, erreur, message, details }`. `ApiExceptionHandler` maps the exceptions:

| Exception | HTTP status | `erreur` |
|---|---|---|
| `RessourceIntrouvableException` | 404 | `ressource_introuvable` |
| `DonneesIncompletesException` | 422 | `donnees_incompletes` |
| Bean validation failure | 400 | `corps_invalide` |
| `ParametreInvalideException` (cross-block rule) | 400 | `reglages_invalides` |
| `RecalculEnCoursException` (a recalculation of the same quarter is already running) | 409 | `recalcul_en_cours` |
| `IllegalArgumentException` | 400 | `argument_invalide` |
| Not logged in (`SecurityConfig`) | 401 | `non_authentifie` |
| Logged in without the RH role (`AccessDeniedException`) | 403 | `acces_refuse` |
| Anything unexpected | 500 | `erreur_interne` (generic message, details only in the log) |

### Fiche collaborateur

One call returns everything HR sees for **one** employee in **one** quarter:
`GET /api/trimestres/{annee}/{numero}/collaborateurs/{matricule}/fiche` (RH only, like every endpoint).

- **Talent Passport page** (`/fiche-collaborateur`, `templates/fiche-collaborateur.html`): no engagement score, bar or questionnaire block (engagement stays in the Engagement module, the imports, the vigilance index and the dashboards). It shows the imported **questionnaire answers** instead ([Questionnaire answers](#questionnaire-answers)), `?questionnaire=AAAA-N` choosing their quarter.
- **Backend:** `ui/service/FicheCollaborateurViewService.construire(matricule, annee, numero)` builds `ui/model/FicheCollaborateur`; `controller/FicheCollaborateurController` returns it as JSON. The page (Ima) calls the same service. The service calculates nothing itself: it reads the stored scores and 9-box case, and calls the engine's methods for talent, skill gaps, vigilance and matching.
- **404** only for an unknown quarter or matricule. **Missing data never fails the call:** the block is `null` (or an empty list) and one sentence is added to `donneesManquantes`, ready to display.
- **Seniority** (`identite.anciennete`) is in decimal years rounded to one place at `trimestre.dateReference`, like `01_COLLABORATEURS` column G: the same calculation as the succession experience criterion (`SuccessionService.ancienneteEnAnnees`).
- Every key is always present, with `null` for a missing value. Codes (`categorie`, `statut`, `niveau`, `readiness`, `decisionComite`) are stable enum names; show the matching `...Libelle` field.

| Block | Content | Empty when |
|---|---|---|
| `trimestre` | `annee`, `numero`, `libelle` ("T3 2026"), `dateReference` | — |
| `identite` | `matricule`, `nom`, `prenom`, `fonction` (job), `grade`, `entite` {`code`, `libelle`, `type`, `chemin`: labels from the direction down}, `manager` {`matricule`, `nom`} or null, `statut`, `dateEntree`, `anciennete` | — |
| `performance` / `potentiel` | `score` /100 (the stored **official** score, see [section 6](#6-the-calculation-engine)), `categorie` + `categorieLibelle`, `criteres`: [{`code`, `libelle`, `note` /100, `poids` (quarter's weight)}] — 5 criteria / 7 criteria, the **manager's** marks | no manager evaluation for the quarter (`donneesManquantes` says so, and says "auto-évaluation seule" when only the self-evaluation exists); `score` alone is null when marks exist but the quarter was not calculated |
| `neufBox` | `numero` 1–9 (9 = high performance and potential, as `04_9BOX` column H) + `libelle` (the case's current name). The number comes from the placement rule (column = stored potential category, row = performance score on the quarter's 9-box axis), never from the stored label: renaming a case does not break the fiche or the history | no score, 9-box placement not run, or no settings for the quarter |
| `talent` | `estTalent`, `estHautPotentiel`, `estVivierSuccession` (talent OR high potential), `decisionComite` (`OUI` / `NON` / `EN_ATTENTE`, null if nothing entered) + `decisionComiteLibelle`, `viviers`: [{`code`, `libelle`, `origine`}] (`origine` = `MOTEUR`/`IMPORT`/`SAISIE_RH` for saved pools, `THEMATIQUE` for the direction's pool) | flags null without scores |
| `competences` | [{`competenceId`, `nom`, `categorie`, `niveauRequis`, `niveauActuel`, `gap`, `statut` (`MAITRISE` / `A_DEVELOPPER` / `PRIORITAIRE`) + `statutLibelle`}], by skill id | empty list |
| `engagement` | questionnaire score /100 (kept in the API; **not shown on the Talent Passport page**, which also drops the matching "pas de questionnaire" line from `donneesManquantes`, constant `FicheCollaborateurViewService.MANQUE_ENGAGEMENT`) | no questionnaire |
| `vigilance` | `indice` /100, `niveau` (`FAIBLE` / `MODEREE` / `ELEVEE`) + `niveauLibelle`, `raisons`: [{`code`, `libelle`, `points`}] (the points add up to `indice`) | no settings for the quarter, or **no vigilance input at all** (no engagement questionnaire, no vigilance declaration, no evaluation): then `donneesManquantes` says "Aucune donnée de vigilance pour ce trimestre" instead of showing a misleading 0 / FAIBLE. One input is enough for the index to be calculated as usual |
| `successions` | critical positions where the employee is an identified successor: [{`posteId`, `nomPoste`, `direction`, `criticite`, `scoreMatching`, `readiness` + `readinessLibelle`, `gapCompetence` (skill furthest from the required level, `09_SUCCESSION!M`; null when there is no gap), `gapNiveaux` (missing levels, 0 when none)}] | empty list |
| `posteCible` | the **target post**: the critical position with the employee's best matching (see [below](#target-post-and-pool-summaries)): {`posteId`, `nomPoste`, `direction`, `criticite`, `scoreMatching`, `readiness` + `readinessLibelle`, `successeurIdentifie` (true if HR named them successor of that post), `gapCompetence`, `gapNiveaux`} | no settings, or no complete score for the quarter |
| `historique` | earlier quarters, most recent first: [{`annee`, `numero`, `libelle`, `scorePerformance`, `scorePotentiel`, `neufBox` {`numero`, `libelle`} or null (not placed, or no settings that quarter)}] | empty list |
| `autoEvaluation` | the self-evaluation against the manager's, per axis: `performance` / `potentiel` = {`score` (self-evaluation score, same formula), `categorie` + `categorieLibelle`, `scoreManager` (manager's evaluation, same formula), `ecart` (= `score` − `scoreManager`: positive when the employee rates themselves higher), `criteres`: [{`code`, `libelle`, `note`, `noteManager`, `ecart`, `poids`}]}; an axis is null without a self-evaluation on it | no self-evaluation for the quarter. `scoreManager` / `noteManager` / `ecart` are null without a manager evaluation |
| `donneesManquantes` | sentences, e.g. "Pas d'évaluation de potentiel pour ce trimestre" | empty list |

Shortened example (BP005, dataset T3 2026):

```json
{
  "trimestre": { "annee": 2026, "numero": 3, "libelle": "T3 2026", "dateReference": "2026-09-15" },
  "identite": {
    "matricule": "BP005", "nom": "Chraibi", "prenom": "Meryem",
    "fonction": "Directeur regional", "grade": "Directeur",
    "entite": { "code": "DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD/REG:TANGER_TETOUAN/AGE:AGENCE_TANGER_NORD",
                "libelle": "Agence Tanger Nord", "type": "AGENCE",
                "chemin": ["Reseau Retail", "Reseau Retail - Sud", "Tanger-Tetouan", "Agence Tanger Nord"] },
    "manager": null, "statut": "ACTIF", "dateEntree": "2023-06-07", "anciennete": 3.3
  },
  "performance": { "score": 93.90, "categorie": "EXCEPTIONNELLE", "categorieLibelle": "Exceptionnelle",
                   "criteres": [ { "code": "OBJECTIFS", "libelle": "Objectifs", "note": 99.00, "poids": 40.00 } ] },
  "potentiel": { "score": 93.40, "categorie": "ELEVE", "categorieLibelle": "Eleve", "criteres": [ "..." ] },
  "neufBox": { "numero": 9, "libelle": "Talent clé" },
  "talent": { "estTalent": true, "estHautPotentiel": true, "estVivierSuccession": true,
              "decisionComite": "OUI", "decisionComiteLibelle": "Oui",
              "viviers": [ { "code": "RELEVE", "libelle": "Vivier de relève", "origine": "MOTEUR" },
                           { "code": "COMMERCIAL", "libelle": "Vivier Commercial", "origine": "THEMATIQUE" } ] },
  "competences": [ { "competenceId": "C01", "nom": "Credit", "categorie": "Metier", "niveauRequis": 5,
                     "niveauActuel": 5, "gap": 0, "statut": "MAITRISE", "statutLibelle": "Maitrise" } ],
  "engagement": 57.00,
  "vigilance": { "indice": 30.00, "niveau": "MODEREE", "niveauLibelle": "Moderee",
                 "raisons": [ { "code": "ENGAGEMENT_FAIBLE", "libelle": "Engagement faible", "points": 25.00 },
                              { "code": "FORMATION_NON_FAITE", "libelle": "Formation prevue non realisee", "points": 5.00 } ] },
  "successions": [ { "posteId": "PST01", "nomPoste": "Directeur regional", "direction": "Reseau Retail",
                     "criticite": "Tres elevee", "scoreMatching": 84.92, "readiness": "MOINS_1_AN",
                     "readinessLibelle": "< 1 an", "gapCompetence": null, "gapNiveaux": 0 } ],
  "posteCible": { "posteId": "PST01", "nomPoste": "Directeur regional", "...": "...",
                  "successeurIdentifie": true, "gapCompetence": null, "gapNiveaux": 0 },
  "historique": [],
  "autoEvaluation": null,
  "donneesManquantes": []
}
```

Tests: `FicheCollaborateurViewServiceTest` (H2: complete record checked against the engine services, missing potential, incomplete settings, two-quarter history, unknown matricule), `FicheCollaborateurControllerTest` (JSON contract, 404, 401), `FicheCollaborateurDatasetTest` (BP005 against the workbook, skipped without it).

### Target post and pool summaries

Both are engine services (Jas) with a JSON endpoint; the screens (Ima) only display them.

- **Largest gap** (`SuccessionService.plusGrandGap`, carried by every `ResultatMatching` as `plusGrandGap`): among the skills the position requires, the one where the candidate misses the most levels, `max(0, required − current)`, with the same data as the skills sub-score (missing skill = default level of the scale). **Ties: the first skill in the position's order** (07_POSTES required skills 1 to 5), exactly like `INDEX/MATCH` in `09_SUCCESSION!M`. So when there is no gap at all, the workbook (and the engine) still return the first skill, with `ecart = 0`: show "no gap" in that case (`aUnEcart()` is false; the view models already give `gapCompetence = null`).
- **Target post** (`PosteCibleService`): for each active employee with a complete score, the matching is computed on **every** critical position except the one they hold, with the quarter's `Parametre` and `dateReference`; the best one is the target post (tie: smallest Poste_ID). `successeurIdentifie` says whether HR named them successor there (09_SUCCESSION). The workbook has no target post. `postesCibles(trimestre)` reads the database in 6 queries whatever the population; `meilleurPoste(...)` works on loaded data (used by the fiche).
- **Pool summaries** (`VivierSyntheseService`): one `SyntheseVivier` per thematic pool (in `VivierThematique` order) then the relief pool (`code = RELEVE`, `releve = true`; its members are also in a thematic pool): `effectif`, `nbTalents`, `nbHautsPotentiels`, `nbReadyNow` (members Ready Now on at least one critical position where they are an identified successor, counted once), `performanceMoyenne` / `potentielMoyen` (2 decimals, known values only), `nbPostesCouverts` (critical positions with at least one member among their successors), `gapsIdentifies` [{`competenceId`, `competence`, `nombre`}]: the largest gaps of the members' successions with a real gap (one per position/successor pair), most frequent first. The home dashboard's pool table uses `resumerThematiques` on data it has already read, so its figures and its query count are unchanged.

Tests: `SuccessionViviersDatasetTest` (the workbook: `09!M` on all 44 rows, every successor of PST01 and PST13, every pool summary against `10_TALENTS` / `09_SUCCESSION`, target posts of BP001, BP003, BP019), `PosteCibleServiceTest`, `VivierSyntheseServiceTest`, `SuccessionServiceTest` (largest gap), `PosteCibleEtVivierSyntheseControllerTest` (JSON contract), `SansSessionOuverteIntegrationTest` (fixed number of queries).

### Collaborateurs list

A read-only view (Jas) built from the engine's results, for the Collaborateurs screen (Ima). It adds no formula: it assembles, filters, sorts and counts.

**Employee list**: `ui/service/ListeCollaborateursViewService.construire(annee, numero, criteres)` → `ui/model/ListeCollaborateurs`; `controller/ListeCollaborateursController` returns it at `GET /api/trimestres/{annee}/{numero}/collaborateurs`.

- **Who:** the active employees (`ACTIF`), the ones the engine calculates, like the "Collaborateurs actifs" card.
- **One row** (`lignes[]`): `matricule`, `nom`, `prenom`, `nomComplet`, `entite` {`code`, `libelle`, `type`}, `direction`, `manager` {`matricule`, `nom`} or null, `poste` (job), `grade`, `scorePerformance`, `scorePotentiel`, `neufBox` {`numero`, `libelle`}, `estTalent`, `estHautPotentiel`, `estTalentValide`, `decisionComite` (talents only), `viviers` [{`code`, `libelle`}] (thematic pool, then `RELEVE` if in the relief pool), `posteCible` {`posteId`, `nomPoste`, `scoreMatching`, `readiness` + `readinessLibelle`, `successeurIdentifie`, `gapCompetence`, `gapNiveaux`} or null, `indiceVigilance`, `niveauVigilance` + `niveauVigilanceLibelle`.
- **Sources:** scores, case, talent, high potential and vigilance from `ResultatsCollaborateurs` (the same code as the manager and entité views), committee decision from `ValidationComiteService`, thematic pool from `VivierThematiqueService`, target post from `PosteCibleService`.
- **Filters** (all optional, combined with AND): `entite` (code of an entité: it and its whole subtree), `case` (1–9), `talent` (`true`/`false`; `false` also returns employees without a status), `vivier` (`COMMERCIAL`, `DIGITAL`, `EXPERTISE`, `MANAGEMENT`, `RISQUES`, `RELEVE`), `readiness` (on the target post), `vigilance` (`FAIBLE`/`MODEREE`/`ELEVEE`), `q` (name, first name or matricule, ignoring accents and case). An unknown value → 400; an unknown entité code → empty list.
- **Sort:** `tri` = `NOM` (default), `MATRICULE`, `PERFORMANCE`, `POTENTIEL`, `MATCHING`, `VIGILANCE`; `ordre` = `asc`/`desc` (default asc for names, desc for scores). Missing values are always last; ties by matricule.
- **Pagination:** `page` from 1, `taille` 1–100 (default 20). The response gives `nbTotal` (before filters), `nbFiltres`, `nbPages`, and the `criteres` actually applied.
- **Queries:** a fixed number (24) whatever the population: everything is read in batches, then filtered in memory.

The DG dashboard (page, `GET /api/dashboard/dg`, `TableauDeBordDgViewService`) was merged into the home page and
then removed (audit round 1, #24): its top talents are the head of the dashboard's collaborateurs list (same order:
performance + potential descending, then performance, then matricule, `TableauDeBordInteractifService.ORDRE`) and the
bench strength per position is on `/postes-critiques`.

Tests: `CollaborateursDatasetTest` (the workbook: counts per 9-box case vs `00_DASHBOARD`, talents and validated talents, pools, vigilance, entité subtree, sort and pagination, every critical position's successors / Ready Now / best successor vs 08 and 09, order of the proposed talents on the dashboard), `ListeCollaborateursViewServiceTest` (filters, sort, pagination, invalid criteria, talents order), `ListeCollaborateursControllerTest` (JSON contract, 400, 404, 401), `SansSessionOuverteIntegrationTest` (fixed number of queries).

### Compétences module

`service/CompetenceSyntheseService.synthese(trimestre, criteres)` → `service/resultat/SyntheseCompetences`; `controller/CompetenceSyntheseController` at `GET /api/trimestres/{annee}/{numero}/competences`. Engine (Jas); the screen (Ima) only displays.

- **Target level = `06_EMPLOYEE_SKILLS!F`**, the employee's own target for each skill, exactly as in the workbook: gap = target − current (`06!G`), status with the quarter's threshold (`06!H`, `CompetenceCollaborateurService.evaluer`). It does **not** come from a position: in the dataset, no employee's targets equal the requirements of the position matching their job (0 of 47), and the holders of critical positions have target 5 where the position requires 3 or 4. The positions' requirements (`07_POSTES`) are only used in the critical-position block, as in `09_SUCCESSION`.
- **Population:** active employees (like `00_DASHBOARD` G10), filtered by `entite` (code, with its subtree), `vivier` (thematic code or `RELEVE`) and `poste` (Poste_ID of a critical position: its identified successors that have a matching), combined with AND. Unknown `vivier` / `poste` or `top` outside 1–50 → 400.
- `competences[]`: one row per skill of the reference list (even with nobody evaluated), sorted by `gapMoyen` (`ordre=desc` by default, `asc` possible; unknown values last, ties by skill id): `nbEvalues`, `niveauActuelMoyen`, `niveauCibleMoyen`, `gapMoyen` (2 decimals), `nbAvecGap` (status À développer or Prioritaire), `pourcentageAvecGap` (1 decimal), `nbPrioritaires`, `repartition` [{`niveau` 1–5, `nombre`}] of the current level.
- `topGaps[]`: the `top` skills (default 5) with the largest average gap, whatever `ordre`. `nbGapsPrioritaires`: total Prioritaire gaps of the population (= `00_DASHBOARD` G10 = 141 without filters).
- `postesCritiques[]` (all, or the one in `poste`): `exigences[]` in the position's order (`competence`, `niveauRequis` from `07_POSTES`, `niveauMoyenSuccesseurs`, `nbSuccesseursSousLeNiveau`), from `SuccessionService.ecartsExigences` (the per-skill step of the skills sub-score, columns N–R of `09_SUCCESSION`; `plusGrandGap` is now built from it); `gapsFrequents[]`: the successors' largest gaps (`09!M`) with a real gap, most frequent first.
- **One employee's skills** (current, target, gap, status) already exist: the fiche's `competences` block and `GET /api/collaborateurs/{id}/competences`. Nothing new was needed there.
- **Not derivable from the data:** which skills are "critical for the bank" (the prototype's 🔴 flag) and the skills required by the post each employee actually holds (employees are not linked to a `07_POSTES` row, only holders of critical positions are).
- **Queries:** a fixed number (12 without filter, 14 with a pool filter) whatever the population.

Tests: `CompetencesDatasetTest` (the workbook: averages, gaps, statuses and distribution of all 25 skills vs `06`, G10 = 141, filters by entité / pool / critical position vs `01` / `10` / `09`, PST01 requirements vs `07` and successors under the level vs `09!N–R`, frequent gaps vs `09!M`, BP001 and BP019 in their fiche vs `06`), `CompetenceSyntheseServiceTest`, `SuccessionServiceTest` (`ecartsExigences`), `CompetenceSyntheseControllerTest` (JSON contract, 400, 404, 401), `SansSessionOuverteIntegrationTest` (fixed number of queries).

### Vue manager

HR picks a manager, then sees their team's results for one quarter. Two calls (RH only):

- `GET /api/trimestres/{annee}/{numero}/managers`: the picker. Every manager, sorted by name: [{`matricule`, `nom`, `prenom`, `fonction`, `entite` (same shape as the fiche), `tailleEquipe`}].
- `GET /api/trimestres/{annee}/{numero}/managers/{matricule}/vue`: the team view.

How it works:

- **Backend:** `ui/service/VueManagerViewService` (`listerManagers`, `construire`) builds `ui/model/VueManager`; `controller/VueManagerController` returns it. The team's results and the summary come from `ui/service/ResultatsCollaborateurs`, shared with the entité view. Same rules as the fiche: no calculation in the view service (only counts and averages), stored scores and categories, 9-box case from the placement rule (`NeufBoxService.niveauxDe`, same helper as the fiche), talent and vigilance from the engine. Missing data leaves a value `null` and adds one sentence per cause to `donneesManquantes`.
- **Team = direct reports** (collaborateurs whose manager is this one), excluding `ARCHIVE` (left the company); `INACTIF` stay in the headcount. N-2 is pending the client's answer (TODO in `VueManagerViewService`).
- **404** for an unknown quarter or matricule, or a collaborateur who is not a manager ("Le collaborateur X n'est pas manager"). A manager with no team gets an empty view (effectif 0, no error).
- **Fixed number of queries** (about 13), whatever the team size: each piece of team data is read once with `IN (team matricules)`; vigilance goes through `VigilanceService.evaluerLot` (same rule as for one person). A test checks that a team of 6 costs as many queries as a team of 1.

| Block | Content |
|---|---|
| `trimestre` | as in the fiche |
| `manager` | `matricule`, `nom`, `prenom`, `fonction`, `entite` {`code`, `libelle`, `type`, `chemin`}, `neufBox` {`numero`, `libelle`} (the manager's own case, null if not placed) |
| `synthese` | `effectif`, `nbAvecScore`, `moyennePerformance`, `moyennePotentiel`, `moyenneEngagement` (averages over members who have the value, 2 decimals, null if none), `categoriesPerformance` (5 entries) and `categoriesPotentiel` (3) and `niveauxVigilance` (3): [{`code`, `libelle`, `nombre`}], `neufBox`: always 9 entries [{`numero`, `libelle`, `nombre`}], `nbTalents`, `nbHautsPotentiels`, `nbVivierSuccession`, `nbSansVigilance` (members without vigilance, not counted in `niveauxVigilance`) |
| `membres` | direct reports sorted by name: [{`matricule`, `nom`, `prenom`, `fonction`, `scorePerformance`, `categoriePerformance` + `...Libelle`, `scorePotentiel`, `categoriePotentiel` + `...Libelle`, `neufBox`, `estTalent`, `estHautPotentiel`, `estVivierSuccession`, `engagement`, `indiceVigilance`, `niveauVigilance` + `...Libelle`, `aDesDonnees`}]. `aDesDonnees` is false when the member has no evaluation and no score this quarter (grey the row). Vigilance is `null` for a member with no vigilance input at all (same rule as the fiche, shared helper `EntreesVigilance`); `donneesManquantes` then names them |
| `alertes` | [{`matricule`, `nom`, `type`, `message`}]: `VIGILANCE_ELEVEE` (level ELEVEE) and `EVALUATION_MANQUANTE` (performance and/or potential evaluation missing), in member order |
| `autoVsManager` | self-evaluations of the team against the manager's evaluations, or null if no member has a self-evaluation (or no settings): `membres`: [{`matricule`, `nom`, `prenom`, `performanceAuto`, `performanceManager`, `ecartPerformance`, `potentielAuto`, `potentielManager`, `ecartPotentiel`}] (members with a self-evaluation, in team order; each source scored by the engine's formula; `ecart` = auto − manager), `ecartMoyenPerformance` / `ecartMoyenPotentiel` (average of the known gaps, 2 decimals), `seuilEcartImportant` (the quarter's setting `seuilsAutoEvaluation.seuilEcartImportant`, default 15), `ecartsImportants` (members with a gap ≥ that threshold, either way) |
| `donneesManquantes` | sentences for what affects the whole view, e.g. "Aucun réglage pour T2 2025 : …" |

Shortened example (BP005, dataset T3 2026, 5 direct reports):

```json
{
  "trimestre": { "annee": 2026, "numero": 3, "libelle": "T3 2026", "dateReference": "2026-09-15" },
  "manager": { "matricule": "BP005", "nom": "Chraibi", "prenom": "Meryem", "fonction": "Directeur regional",
               "entite": { "libelle": "Agence Tanger Nord", "type": "AGENCE",
                           "chemin": ["Reseau Retail", "Reseau Retail - Sud", "Tanger-Tetouan", "Agence Tanger Nord"] },
               "neufBox": { "numero": 9, "libelle": "Talent clé" } },
  "synthese": {
    "effectif": 5, "nbAvecScore": 5, "moyennePerformance": 75.86, "moyennePotentiel": 75.47,
    "categoriesPerformance": [ { "code": "EXCEPTIONNELLE", "libelle": "Exceptionnelle", "nombre": 0 },
                               { "code": "SOLIDE", "libelle": "Solide", "nombre": 5 }, "..." ],
    "categoriesPotentiel": [ { "code": "ELEVE", "libelle": "Eleve", "nombre": 0 },
                             { "code": "MOYEN", "libelle": "Moyen", "nombre": 4 },
                             { "code": "FAIBLE", "libelle": "Faible", "nombre": 1 } ],
    "neufBox": [ { "numero": 1, "libelle": "À surveiller", "nombre": 0 },
                 { "numero": 4, "libelle": "À accompagner", "nombre": 1 },
                 { "numero": 5, "libelle": "Confirmé", "nombre": 4 }, "..." ],
    "nbTalents": 0, "nbHautsPotentiels": 0, "nbVivierSuccession": 0, "moyenneEngagement": 73.40,
    "niveauxVigilance": [ { "code": "FAIBLE", "libelle": "Faible", "nombre": 5 },
                          { "code": "MODEREE", "libelle": "Moderee", "nombre": 0 },
                          { "code": "ELEVEE", "libelle": "Elevee", "nombre": 0 } ],
    "nbSansVigilance": 0
  },
  "membres": [ { "matricule": "BP028", "nom": "Bennani", "prenom": "Loubna", "fonction": "Charge d'accueil",
                 "scorePerformance": 78.70, "categoriePerformance": "SOLIDE", "categoriePerformanceLibelle": "Solide",
                 "scorePotentiel": 64.90, "categoriePotentiel": "FAIBLE", "categoriePotentielLibelle": "Faible",
                 "neufBox": { "numero": 4, "libelle": "À accompagner" },
                 "estTalent": false, "estHautPotentiel": false, "estVivierSuccession": false,
                 "engagement": 73.00, "indiceVigilance": 15.00, "niveauVigilance": "FAIBLE",
                 "niveauVigilanceLibelle": "Faible", "aDesDonnees": true }, "..." ],
  "alertes": [],
  "autoVsManager": null,
  "donneesManquantes": []
}
```

Tests: `VueManagerViewServiceTest` (H2: values identical to the fiche and the engine, summary, member with no data and its alerts, empty team, non-manager and unknown → 404, no settings, picker, query count independent of team size), `VueManagerControllerTest` (JSON contract, 404, 401), `VueManagerDatasetTest` (BP005's team against the workbook, skipped without it).

### Vue entité

HR picks an entité of the org chart (Direction › Département › Région › Agence), then sees the aggregated results of its whole subtree for one quarter. Two calls (RH only):

- `GET /api/entites`: the picker. The org chart as a tree, directions first, each level sorted by label: [{`code`, `libelle`, `type`, `effectif` (headcount of the node **and all its descendants**, excluding `ARCHIVE`), `enfants`: [same shape]}].
- `GET /api/trimestres/{annee}/{numero}/entites/vue?code={code}`: the entité view. **The code is a required query parameter** (400 `requete_mal_formee` without it): `/api/trimestres/2026/3/entites/vue?code=DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD/REG:TANGER_TETOUAN`. A code is the path from the direction and contains `/`; in a query value, `/` and `:` are allowed as is or encoded (`%2F`, `%3A`, e.g. from `encodeURIComponent`), whereas an encoded slash in the path would be rejected by Spring Security's firewall and by Tomcat. The last segment alone is not unique (the same région exists under several départements). In Thymeleaf: `th:href="@{/api/trimestres/{a}/{n}/entites/vue(a=${annee}, n=${numero}, code=${entite.code})}"`. Codes only contain `A-Z`, `0-9`, `_`, `:` and `/` (accents, spaces and punctuation are normalised to `_` by `Entite.code`), so nothing else needs escaping.

How it works:

- **Backend:** `ui/service/VueEntiteViewService` (`listerEntites`, `construire`) builds `ui/model/VueEntite`; `controller/VueEntiteController` returns it. The results of the people and the summary come from `ui/service/ResultatsCollaborateurs`, **the same code as the manager view**: for the same people, both views give the same figures (a test checks that an agence and the manager whose team is that agence have identical summaries).
- **Subtree:** the org chart is read in one query and walked in memory; collaborateurs of the entité and all its descendants, excluding `ARCHIVE` (same rule as the manager view). A collaborateur attached to the entité itself (not to a child) counts in the summary but in no child row.
- **404** for an unknown quarter or entité code. An entité with nobody in its subtree gets an empty view (headcount 0, no error).
- **Fixed number of queries** (about 24), whatever the subtree size: org chart, collaborateurs (`IN` entités), their data (`IN` matricules), critical positions and their coverage (`PosteCritiqueService.evaluerPostes`, bulk), managers.

| Block | Content |
|---|---|
| `trimestre` | as in the fiche |
| `entite` | `code`, `libelle`, `type`, `chemin` (labels from the direction), `parent` {`code`, `libelle`, `type`} (null for a direction), `enfants`: direct children [{`code`, `libelle`, `type`, `effectif`}] |
| `synthese` | the whole subtree, **same shape as the manager view's `synthese`**: `effectif`, `nbAvecScore`, averages, `categoriesPerformance`, `categoriesPotentiel`, `neufBox` (9 entries), `nbTalents`, `nbHautsPotentiels`, `nbVivierSuccession`, `moyenneEngagement`, `niveauxVigilance`, `nbSansVigilance` |
| `enfants` | one row per direct child, to compare e.g. the agences of a région: [{`code`, `libelle`, `type`, `effectif`, `nbAvecScore`, `moyennePerformance`, `moyennePotentiel`, `pourcentageTalents` (talents / people with a score × 100, 1 decimal, null if nobody has a score), `moyenneEngagement`, `nbVigilanceElevee`}]. The children's headcounts add up to the entité's, minus the people attached to the entité itself |
| `postesCritiques` | critical positions attached to the subtree (positions are attached to their direction) or held by someone who works in it: [{`posteId`, `nomPoste`, `direction`, `criticite`, `titulaireId`, `titulaireNom`, `rattachement` (`ENTITE` / `TITULAIRE`), `couverture` + `couvertureLibelle`, `alerte`, `nbSuccesseurs`, `meilleurMatching`}], coverage from `PosteCritiqueService` (null without settings) |
| `alertes` | people of the subtree with vigilance ELEVEE or above, highest index first: [{`matricule`, `nom`, `prenom`, `entite` {`code`, `libelle`, `type`}, `indice`, `niveau`, `niveauLibelle`}] |
| `managers` | managers who work in the subtree, by name, same shape as the manager picker (`tailleEquipe` = their direct team, wherever it sits): link to the Vue manager |
| `donneesManquantes` | as in the manager view |

Example (région Tanger-Tetouan under Réseau Retail - Sud, dataset T3 2026; zero counts and two managers trimmed):

```json
{
  "trimestre": { "annee": 2026, "numero": 3, "libelle": "T3 2026", "dateReference": "2026-09-15" },
  "entite": {
    "code": "DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD/REG:TANGER_TETOUAN", "libelle": "Tanger-Tetouan", "type": "REGION",
    "chemin": ["Reseau Retail", "Reseau Retail - Sud", "Tanger-Tetouan"],
    "parent": { "code": "DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_SUD", "libelle": "Reseau Retail - Sud", "type": "DEPARTEMENT" },
    "enfants": [ { "code": "…/AGE:AGENCE_TANGER_MEDINA", "libelle": "Agence Tanger Medina", "type": "AGENCE", "effectif": 1 },
                 { "code": "…/AGE:AGENCE_TANGER_NORD", "libelle": "Agence Tanger Nord", "type": "AGENCE", "effectif": 1 },
                 { "code": "…/AGE:AGENCE_TANGER_SUD", "libelle": "Agence Tanger Sud", "type": "AGENCE", "effectif": 2 },
                 { "code": "…/AGE:AGENCE_TANGER_VILLE_NOUVELLE", "libelle": "Agence Tanger Ville Nouvelle", "type": "AGENCE", "effectif": 1 } ]
  },
  "synthese": {
    "effectif": 5, "nbAvecScore": 5, "moyennePerformance": 85.96, "moyennePotentiel": 80.25,
    "categoriesPerformance": [ { "code": "EXCEPTIONNELLE", "libelle": "Exceptionnelle", "nombre": 3 },
                               { "code": "SOLIDE", "libelle": "Solide", "nombre": 2 }, "…" ],
    "categoriesPotentiel": [ { "code": "ELEVE", "libelle": "Eleve", "nombre": 1 }, { "code": "MOYEN", "libelle": "Moyen", "nombre": 4 }, "…" ],
    "neufBox": [ { "numero": 5, "libelle": "Confirmé", "nombre": 2 }, { "numero": 8, "libelle": "Performant", "nombre": 2 },
                 { "numero": 9, "libelle": "Talent clé", "nombre": 1 }, "…" ],
    "nbTalents": 1, "nbHautsPotentiels": 1, "nbVivierSuccession": 1, "moyenneEngagement": 68.60,
    "niveauxVigilance": [ { "code": "FAIBLE", "libelle": "Faible", "nombre": 4 }, { "code": "MODEREE", "libelle": "Moderee", "nombre": 1 },
                          { "code": "ELEVEE", "libelle": "Elevee", "nombre": 0 } ],
    "nbSansVigilance": 0
  },
  "enfants": [
    { "code": "…/AGE:AGENCE_TANGER_NORD", "libelle": "Agence Tanger Nord", "type": "AGENCE", "effectif": 1, "nbAvecScore": 1,
      "moyennePerformance": 93.90, "moyennePotentiel": 93.40, "pourcentageTalents": 100.0, "moyenneEngagement": 57.00,
      "nbVigilanceElevee": 0 },
    { "code": "…/AGE:AGENCE_TANGER_SUD", "libelle": "Agence Tanger Sud", "type": "AGENCE", "effectif": 2, "nbAvecScore": 2,
      "moyennePerformance": 84.85, "moyennePotentiel": 79.43, "pourcentageTalents": 0.0, "moyenneEngagement": 63.00,
      "nbVigilanceElevee": 0 }, "…"
  ],
  "postesCritiques": [ { "posteId": "PST01", "nomPoste": "Directeur regional", "direction": "Reseau Retail",
    "criticite": "Tres elevee", "titulaireId": "BP022", "titulaireNom": "Rim Filali", "rattachement": "TITULAIRE",
    "couverture": "READY_NOW", "couvertureLibelle": "Couverte - Ready Now", "alerte": false,
    "nbSuccesseurs": 4, "meilleurMatching": 95.20 } ],
  "alertes": [],
  "managers": [ { "matricule": "BP005", "nom": "Chraibi", "prenom": "Meryem", "fonction": "Directeur regional",
                  "entite": { "libelle": "Agence Tanger Nord", "…": "…" }, "tailleEquipe": 5 }, "…" ],
  "donneesManquantes": []
}
```

Tests: `VueEntiteViewServiceTest` (H2: agence, région = sum of its agences, same figures as the manager view, département with a manager attached directly, empty entité, unknown code or quarter → 404, tree, query count independent of subtree size), `VueEntiteControllerTest` (JSON contract with a full code, code as is or encoded, old path form gone, missing code → 400, 404, 401), `VueEntiteDatasetTest` (région Tanger-Tetouan against the workbook, skipped without it).

---

## 4. Tech stack

| Technology | Version | What it is | Why we use it | Where |
|---|---|---|---|---|
| **Java** | 17 (`java.version` in `pom.xml`) | Language and runtime | Long-term-support version required by Spring Boot 3 | Everywhere |
| **Spring Boot** | 3.5.16 | Framework that assembles Spring, an embedded web server and sensible defaults | One executable application with no separate server to install, which fits an offline workstation | `pom.xml` parent, `Talent360bankApplication` |
| Spring Framework | 6.2.19 (via Boot) | Dependency injection, transactions | Services receive their dependencies through constructors; `@Transactional` on the engine | All `@Service`, `@Component` |
| **Spring Web (MVC)** + embedded Tomcat 10.1.55 | via Boot | HTTP server, controllers, JSON (Jackson) | REST API and page routing | `controller`, `ui.controller` |
| **Spring Security** + `thymeleaf-extras-springsecurity6` | via Boot | Form login, BCrypt, CSRF tokens for HTML forms | Only HR may open the app, on a workstation others may use | `config.SecurityConfig`, `securite` |
| **Spring Data JPA** + **Hibernate** 6.6.53 | via Boot | Object–relational mapping; repositories generated from interfaces | Engine code works on objects; queries are declared, not hand-written | `entity`, `repository` |
| **Bean Validation** (Hibernate Validator) | via `spring-boot-starter-validation` | Declarative constraints (`@NotNull`, `@DecimalMax`, `@AssertTrue`) | Settings are validated before saving: sums of 100, decreasing thresholds, ranges | `entity` blocks, `ParametreForm` |
| **Thymeleaf** | 3.1.x (via Boot) | Server-side HTML templates | Screens rendered on the server with no JavaScript framework; escaped output by default (`th:text`) | `src/main/resources/templates` |
| **Spring Boot DevTools** | via Boot, `optional` | Automatic restart during development | Faster development loop. Active only with the `dev` profile, and absent from the packaged jar. | `application-dev.properties` |
| **MySQL** | server 8.4 (local); driver Connector/J 9.7.0 | Relational database | Durable local storage of the quarter data and results | `application.properties` |
| **Apache POI** | 5.5.1 | Reads and writes Excel `.xlsx` files | Import the client's workbook; the Excel comparison test | `ImportClasseurService`, test `dataset/LecteurXlsx` |
| **Chart.js** | 4.4.4, `static/vendor/chartjs/chart.umd.js` (served locally, no CDN) | Charts of the interactive dashboard | Offline app: the library is in the jar; same file as jsDelivr / unpkg (SHA-256 `fed6a739…0d7fea`), MIT licence header kept | `templates/dashboard.html`, `static/js/tableau-de-bord.js` |
| **Prototype CSS** (no CSS framework) | `static/css/prototype.css` = the `<style>` block of `docs/prototype/index.html`, copied verbatim | Look of every page | The client's prototype is the reference: its CSS is used as is, and its markup is reproduced in the templates. Fonts (Fraunces, IBM Plex Sans / Mono) are self-hosted in `static/vendor/fonts` so the app works offline. Additions only in `static/css/app.css`. Banque Populaire logo (`static/images/`, public like the rest of `/images/**`): `BP-Logo.png` is the original (263 × 174); `BP-Logo-Complet.png` is a pixel crop of it without margins (170 × 52, emblem + "BANQUE POPULAIRE"), shown at its real size in the gold-bordered mark box of the sidebar, the login page and the 403 / 404 pages (the box widens to the logo instead of the prototype's 34 × 34 square; 189 px wide in the 208 px of the sidebar; text 11 px high, readable, never scaled up or stretched; `alt="Banque Populaire"`); `BP-Embleme.png` is a square crop of the horse emblem (49 × 49), used as the favicon (`fragments/prototype.html` `tete`). `LogoTest`. | `fragments/prototype.html` (`tete`), `static/css/fonts.css`, `prototype.css`, `app.css` |
| **Maven** (wrapper `mvnw`) | — | Build and dependency tool | Reproducible build; nothing to install besides the JDK | `pom.xml`, `mvnw`, `mvnw.cmd` |
| **JUnit 5**, **Mockito**, **AssertJ** | via `spring-boot-starter-test` | Test framework, mocks, fluent assertions | Unit tests of the engine with mocked repositories | `src/test` |
| **H2** | via Boot, test scope | In-memory database in MySQL mode | Repository, JPA and integration tests run with no MySQL installed, including in CI | `src/test/resources/application.properties` |
| **GitHub Actions** | — | Continuous integration | Runs `mvnw test` on every push and pull request to `develop` | `.github/workflows/ci.yml` |

---

## 5. Data model

### Entities

| Entity (table) | Key fields | Relationships | Notes |
|---|---|---|---|
| `Collaborateur` (`collaborateur`) | `idCollaborateur` (key, workbook `Employee_ID`), `nom`, `prenom`, `sexe`, `dateNaissance`, `dateEntree`, `fonction`, `grade`, `statut` (ACTIF / INACTIF / ARCHIVE) | `entite` → `Entite` (most specific known), `manager` → `Manager` | Only `ACTIF` employees enter the calculations. `getDirection()`, `getDepartement()`, `getRegion()`, `getAgence()` walk up the `Entite` tree |
| `Entite` (`entite`) | `code` (unique: path from the direction, e.g. `DIR:RESEAU_RETAIL/DEP:…/REG:…/AGE:…`), `libelle`, `type` (DIRECTION / DEPARTEMENT / REGION / AGENCE) | `parent` → `Entite` (null for a direction) | Org chart built by the import from `01_COLLABORATEURS` H–K. The same region under two departments is two nodes |
| `Manager` (`manager`) | — | `collaborateur` → `Collaborateur` (1–1, unique), `entiteGeree` → `Entite` (optional, not in the workbook) | One per distinct `Manager_ID` (`01_COLLABORATEURS` N) |
| `Trimestre` (`trimestre`) | `numero`, `annee`, `dateReference` | — | Unique on (`numero`, `annee`). `dateReference` (not null) is the date the quarter is evaluated at: seniority is measured there, never at today's date. Default: last day of the quarter |
| `Parametre` (`parametre`) | `libelle` + 16 embedded blocks: `PoidsPerformance`, `PoidsPotentiel`, `PoidsSuccession`, `PonderationSources` (manager / self-evaluation, `pond_src_*` columns), `SeuilsAutoEvaluation` (large self / manager gap shown in the manager view, `seuil_ecart_auto`), `BaremeExperience`, `BaremeCompetences`, `SeuilsNeufBox` (performance axis), `SeuilsNeufBox` (potential axis, `seuil_box_pot_*` columns), `SeuilsCategoriePerformance`, `SeuilsGapCompetence`, `SeuilsReadiness`, `SeuilsCouverture`, `SeuilsTalent`, `PointsVigilance`, `SeuilsVigilance` | → `Trimestre` | One per quarter (unique) |
| `Performance` (`performance`) | 5 marks /100: objectives, competences, behaviour, contribution, development; `source` (`AUTO` self-evaluation / `MANAGER`, column `source_evaluation`) | → `Collaborateur`, → `Trimestre`, `evaluateur` → `Manager` (optional) | Unique per employee, quarter and source: a self-evaluation and a manager evaluation can coexist. The workbook import writes `MANAGER`. The evaluator is the employee's manager at import time: `02_PERFORMANCE` does not name it |
| `Potentiel` (`potentiel`) | 7 marks /100: learning, leadership, adaptability, complexity, mobility, strategy, autonomy; `source` (`AUTO` / `MANAGER`) | → `Collaborateur`, → `Trimestre` | Unique per employee, quarter and source, like `Performance`. Succession matching reads leadership and mobility from the `MANAGER` evaluation |
| `Score` (`score`) | `scorePerformance`, `scorePotentiel`, `categoriePerformance`, `categoriePotentiel`, `positionBox`, `dateCalcul` | → `Collaborateur`, → `Trimestre`, `entite` → `Entite` and `manager` → `Manager` (snapshot at calculation time) | Written by the engine; unique per employee and quarter. Per-quarter screens read the direction from the snapshot, so past quarters keep their original direction and manager |
| `Competence` (`competence`) | `competenceId` (C01…C25), `nom`, `categorie` | — | Skill reference list |
| `CompetenceCollaborateur` (`competence_collaborateur`) | `niveauActuel`, `niveauCible`, `gap`, `statutGap` (as imported), `cleLookup` | → `Collaborateur`, → `Competence` | Not tied to a quarter; no uniqueness constraint yet |
| `Poste` (`poste`) | `posteId`, `nomPoste`, `gradeCible`, `criticite`, up to 5 required skills + levels, `posteCritique` (Oui/Non), `titulaireId`, `titulaireNom` | → `Competence` ×5, `entite` → `Entite` (its direction) | `getDirection()` reads the linked entity |
| `Matrice9Box` (`matrice_9box`) | `niveauPerformance`, `niveauPotentiel` (1–3), `categorie` (label) | — | 9 reference rows, seeded at startup, renamable by HR |
| `Vivier` (`vivier`) | `code` (unique), `nomCategorie`, `description` | — | Only the relief pool (`RELEVE`) is created today |
| `AppartenanceVivier` (`appartenance_vivier`) | `origine` (MOTEUR, IMPORT, SAISIE_RH…) | → `Collaborateur`, → `Vivier`, → `Trimestre` | Unique on (employee, pool, quarter) |
| `QuestionnaireEngagement` (`questionnaire_engagement`) | `scoreEngagement` /100, `dateReponse`, `statut` | → `Collaborateur`, → `Trimestre` | No uniqueness constraint yet |
| `ReponseQuestionnaire` (`reponse_questionnaire`) | raw questionnaire answer: `codeQuestion` (Q01…), `ordre`, `theme`, `question`, `reponse`, `dateReponse` | → `Collaborateur`, → `Trimestre` | Unique (collaborateur, trimestre, code_question). Written only by `ImportQuestionnaireService`; no score derived |
| `Utilisateur` (`utilisateur`) | `login` (unique), `motDePasseHash` (BCrypt), `role` (RH only), `actif`, `dateCreation` | — | Login account. Only HR has one; it is never deleted, only deactivated |
| `ValidationComite` (`validation_comite`) | `statut` (OUI / NON / EN_ATTENTE), `dateDecision`, `commentaire` (nullable) | → `Collaborateur`, → `Trimestre`, `utilisateur` → `Utilisateur` (nullable) | Committee decision on a proposed talent, unique per (employee, quarter). From `10_TALENTS!G` (no date) or entered on `/comite-talent` (dated): an import never replaces a dated decision |
| `ValidationSuccession` (`validation_succession`) | `decision` (`DecisionSuccession`: VALIDER, VALIDER_AVEC_PLAN, MAINTENIR_EN_VIVIER, REEVALUER, NE_PAS_RETENIR), `dateDecision`, `commentaire` | → `Trimestre`, → `Poste`, `successeur` → `Collaborateur`, `utilisateur` → `Utilisateur` (nullable) | Committee decision on a successor of a critical position, unique per (quarter, position, successor). Entered on `/comite-talent` only; the import never writes it |
| `ImportExcel` (`import_excel`) | `source`, `nomFichier`, `dateImport`, `statut`, `nbLignes`, `nbErreurs`, `message` | → `Trimestre`, `utilisateur` → `Utilisateur` (optional) | Import journal, written by `ImportService` and read by `GET /api/imports` |

The full MCD, with every entity, key and cardinality, is in [`mcd.puml`](mcd.puml) (PlantUML).

```mermaid
erDiagram
    COLLABORATEUR ||--o{ PERFORMANCE : "evaluated in"
    COLLABORATEUR ||--o{ POTENTIEL : "evaluated in"
    COLLABORATEUR ||--o{ SCORE : "gets"
    COLLABORATEUR ||--o{ COMPETENCE_COLLABORATEUR : "has"
    COLLABORATEUR ||--o{ QUESTIONNAIRE_ENGAGEMENT : "answers"
    COLLABORATEUR ||--o{ APPARTENANCE_VIVIER : "belongs"
    MANAGER |o--|| COLLABORATEUR : "held by"
    COLLABORATEUR }o--o| MANAGER : "reports to"
    MANAGER }o--o| ENTITE : "runs"
    PERFORMANCE }o--o| MANAGER : "evaluated by"
    COLLABORATEUR }o--o| ENTITE : "works in"
    POSTE }o--o| ENTITE : "belongs to"
    ENTITE |o--o{ ENTITE : "parent of"
    TRIMESTRE ||--o| PARAMETRE : "configured by"
    TRIMESTRE ||--o{ PERFORMANCE : "has"
    TRIMESTRE ||--o{ POTENTIEL : "has"
    TRIMESTRE ||--o{ SCORE : "has"
    TRIMESTRE ||--o{ QUESTIONNAIRE_ENGAGEMENT : "has"
    TRIMESTRE ||--o{ APPARTENANCE_VIVIER : "has"
    VIVIER ||--o{ APPARTENANCE_VIVIER : "contains"
    COMPETENCE ||--o{ COMPETENCE_COLLABORATEUR : "measured by"
    COMPETENCE ||--o{ POSTE : "required by (x5)"

    COLLABORATEUR {
        string idCollaborateur PK
        string nom
        string prenom
        date dateEntree
        string statut
    }
    ENTITE {
        int idEntite PK
        string code
        string libelle
        string type
    }
    MANAGER {
        int idManager PK
    }
    TRIMESTRE {
        int idTrimestre PK
        int numero
        int annee
    }
    PARAMETRE {
        int idParametre PK
        string libelle
        blocks weights_and_thresholds
    }
    SCORE {
        int idScore PK
        decimal scorePerformance
        decimal scorePotentiel
        string categoriePerformance
        string categoriePotentiel
        string positionBox
    }
    PERFORMANCE {
        string source
        decimal five_marks
    }
    POTENTIEL {
        string source
        decimal seven_marks
    }
    COMPETENCE_COLLABORATEUR {
        int niveauActuel
        int niveauCible
    }
    COMPETENCE {
        string competenceId PK
        string nom
    }
    POSTE {
        string posteId PK
        string posteCritique
        string titulaireId
    }
    VIVIER {
        int idVivier PK
        string code
    }
    APPARTENANCE_VIVIER {
        string origine
    }
    QUESTIONNAIRE_ENGAGEMENT {
        decimal scoreEngagement
    }
    MATRICE_9BOX {
        int niveauPerformance
        int niveauPotentiel
        string categorie
    }
```

Four engine inputs are read through interfaces ([section 7](#the-source-interfaces)): identified successors, committee decisions, the direction → thematic pool mapping and the vigilance flags. Each is implemented by a table read (`SuccesseursIdentifiesEnBase`, `ValidationsComiteEnBase`, `ViviersThematiquesEnBase`, `FaitsVigilanceEnBase`).

---

## 6. The calculation engine

All values below are the **defaults** of `Parametre.parDefaut`, identical to `00_PARAMETRES`. HR can change them per quarter. Scores are rounded to two decimals, half-up, before any comparison. Thresholds are inclusive (≥) unless stated otherwise. Full tables: [`moteur-de-calcul.md`](moteur-de-calcul.md).

| Module | Service | Rule in plain words |
|---|---|---|
| **Performance score** | `CalculService` | Weighted average of 5 marks: objectives 40, competences 20, behaviour 20, contribution 10, development 10. Divided by the actual sum of weights. |
| **Potential score** | `CalculService` | Weighted average of 7 marks: learning 20, leadership 20, adaptability 15, complexity 15, mobility 10, strategy 10, autonomy 10. |
| **Official score** (manager / self-evaluation) | `CalculService.scoreOfficiel`, `ScoreService` | Each source's score is computed with the two formulas above, then blended per axis with `PonderationSources`: (manager × `poidsManager` + self × `poidsAuto`) / 100, rounded. **Default 100 / 0: the official score is the manager's**, exactly as before, until the client decides (audit question 2). No self-evaluation, or `poidsAuto` = 0 → the manager's score. No manager evaluation while `poidsManager` > 0 → no official score: the employee is skipped with "Evaluation du manager absente : auto-evaluation seule, pas de score officiel". |
| **Performance category** | `CalculService.categoriePerformance` | ≥ 90 Exceptionnelle, ≥ 80 Élevée, ≥ 70 Solide, ≥ 60 À renforcer, else Insuffisante. Stored on `Score` at recalculation. |
| **9-Box** | `NeufBoxService` | Each axis: ≥ 85 high, ≥ 70 medium, else low, with separate thresholds per axis. The pair (performance level, potential level) selects one of the 9 `Matrice9Box` rows. |
| **Potential category** | `NeufBoxService.categoriePotentiel` | The potential-axis level: Élevé / Moyen / Faible. Stored at 9-Box placement. |
| **Talent** | `TalentService` | Performance ≥ 85 **and** potential ≥ 85. An AND, never an average. |
| **High potential** | `TalentService` | Potential ≥ 85 **and** performance ≥ 75. Independent of "talent". |
| **Relief pool** | `TalentService`, `VivierReleveService` | Talent **or** high potential. Saving replaces only the engine's rows (`origine = MOTEUR`); imported or HR-entered rows are never touched or duplicated. |
| **Succession matching** | `SuccessionService` | Weighted average of 6 criteria: skills 25, performance 20, potential 20, experience 15, leadership 10, mobility 10. Skills: 100 − 20 per missing level for each required skill, floored at 0; a missing skill is assumed at level 3. Experience: 8 points per year of seniority, capped at 100; seniority is measured at the quarter's `dateReference` (`ROUND(days / 365.25, 1)`, as in the workbook), so a quarter gives the same result whatever day it is computed. The current holder is excluded. |
| **Readiness** | `SuccessionService` | Matching ≥ 90 Ready Now, ≥ 80 < 1 year, ≥ 65 1–2 years, else > 2 years. |
| **Skill gaps per skill** | `CompetenceSyntheseService` | Per skill of the reference list: averages of the current level, the target level (`06!F`) and the gap, number and % with a gap, Prioritaire count, distribution by level. Counts only. |
| **Largest gap** | `SuccessionService.plusGrandGap` | The required skill with the most missing levels; tie → first skill of the position (as `09_SUCCESSION!M`). |
| **Target post** | `PosteCibleService` | The critical position (not held) with the employee's best matching; tie → smallest Poste_ID. |
| **Pool summaries** | `VivierSyntheseService` | Per thematic pool and for the relief pool: headcount, talents, high potentials, Ready Now, averages, positions covered, most frequent largest gaps. Counts only. |
| **Critical positions** | `PosteCritiqueService` | A position flagged "Oui". Fewer than 1 identified successor → **ALERT**; otherwise the best successor's matching gives "Ready Now" (≥ 90), "< 1 year" (≥ 80) or "Partielle". Coverage rate = positions not in alert ÷ critical positions. |
| **Talent Committee** | `ValidationComiteService` | Validated talent = proposed talent **and** committee decision "Oui". A decision not entered counts as "En attente". |
| **Thematic pools** | `VivierThematiqueService` | Every employee goes into the pool of their direction (Commercial, Digital, Expertise, Management, Risques), talent or not. The mapping is data, supplied by a source. |
| **Vigilance** | `VigilanceService` | Index = sum of the points of the signals present: low engagement 25 (score strictly < 60), no move in 4 years 20, unhandled mobility request 15, no recent development 15, performance drop 10, low recognition 10, training not done 5. Level: < 30 Faible, < 60 Modérée, else Élevée. A performance drop is measured from the score history when a previous score exists, otherwise taken from the imported flag. It is a prioritisation tool, **not a prediction of departure**. |
| **Skill gap status** | `CalculService.statutGap`, `CompetenceCollaborateurService` | gap = target − current. ≤ 0 Maîtrise; 1 À développer; ≥ threshold (2) Prioritaire; otherwise À développer. |
| **Dashboard** | `TableauDeBordService` | Assembles the above in one call; calculates nothing itself. |

### Order of recalculation

Some results are **stored**, others are **calculated at read time**:

1. `POST .../scores/recalcul` writes the performance and potential scores and the performance category.
2. `POST .../9box/placement` writes the box and the potential category. It needs step 1.
3. `POST .../vivier-releve` writes the relief pool into `AppartenanceVivier`. It needs step 1.

Everything else (talents, succession, critical positions, committee, thematic pools, vigilance, skill gaps, dashboard) is calculated on each request with the quarter's current settings. `POST .../calcul` runs steps 1–3 in order (`CalculTrimestreService`), and so does every settings change (below): stored results always follow the current settings.

### Worked example

Employee BP035 (Aicha El Ouafi, Réseau Retail), from `02_PERFORMANCE` and `03_POTENTIEL`:

- Performance marks 99, 97, 93, 89, 98 → (99×40 + 97×20 + 93×20 + 89×10 + 98×10) ÷ 100 = **96.30** → category **Exceptionnelle** (≥ 90).
- Potential marks 85, 86, 93, 93, 94, 88, 94 → (85×20 + 86×20 + 93×15 + 93×15 + 94×10 + 88×10 + 94×10) ÷ 100 = **89.70** → category **Élevé** (≥ 85).
- 9-Box: both axes high → **Talent clé**.
- Talent: 96.30 ≥ 85 and 89.70 ≥ 85 → **yes**. High potential: 89.70 ≥ 85 and 96.30 ≥ 75 → **yes**. Therefore in the relief pool.
- Committee decision in `10_TALENTS` column G: "Oui" → **validated talent**.

Every one of these values matches the workbook (`02_PERFORMANCE` I–J, `03_POTENTIEL` K–L, `04_9BOX` I, `10_TALENTS` E–J).

---

## 7. Key design choices

### Settings in `Parametre`, never in the code

Every weight and threshold lives in the quarter's `Parametre`. No number from the specification is written in a service.

- HR changes a rule by configuration, without a developer.
- Each quarter keeps the settings it was calculated with, so past results stay explainable.
- Bean Validation protects consistency: weights sum to 100, thresholds strictly decreasing, the high vigilance threshold reachable with the available points.
- New blocks are added without breaking existing data. Columns carry a database default, `ParametreInitializer` completes older rows at startup, and optional blocks may be omitted from a `PUT` (they keep their value). The latest are `ponderationSources` {`poidsManager`, `poidsAuto`} (sum 100, default 100 / 0) and `seuilsAutoEvaluation` {`seuilEcartImportant`} (0–100, default 15: the self / manager gap from which the manager view flags a member; it changes no score); saving them recalculates the quarter like any other setting.

### Changing the settings

`PUT /api/trimestres/{annee}/{numero}/parametre` saves the quarter's settings, then **recalculates the quarter** with them (`CalculTrimestreService.calculer`: scores, then 9-Box placement, then relief pool). The 9-Box page (stored case), the fiche and the manager view (case derived from the levels) therefore never disagree after a change.

- **In order, never one without the other.** Invalid settings → 400, nothing saved, nothing recalculated. The save commits in its own transaction before the recalculation reads the settings.
- **A failed recalculation does not undo the save.** The response is still 200 with the saved settings; `recalcul.recalcule` is `false` and `recalcul.erreur` says why in plain words, e.g. "Réglages enregistrés, mais le recalcul du trimestre a échoué : … Corriger puis relancer POST /api/trimestres/2026/3/calcul". An unexpected error shows "erreur inattendue (voir le journal)"; the detail stays in the log. Never a 500.
- **One recalculation per quarter at a time** (`VerrouCalculTrimestre`, an in-memory lock per quarter: the app runs on a single machine). A second `PUT` while one is running (double click on "Enregistrer") gets **409** `recalcul_en_cours` ("Recalcul déjà en cours pour T3 2026 : réessayer une fois qu'il est terminé") and saves nothing: the lock is taken **before** the save, so it can never write new settings while the first recalculation runs on the old ones. Every write of calculated results uses the same lock and gets the same 409 when busy: `POST .../calcul`, the calculation after an import, and the single steps `POST .../scores/recalcul`, `POST .../9box/placement` and `POST .../vivier-releve`.
- **Changing only the reference date** (`PUT /api/trimestres/{annee}/{numero}`) recalculates nothing: nothing stored depends on it (seniority is computed at read time in the matching and the fiche).

Response (the settings stay at the top level, as before; `recalcul` is added):

```json
{
  "idParametre": 3, "annee": 2026, "numero": 3, "libelle": "Reglages du comite",
  "poidsPerformance": { "...": "..." },
  "seuilsNeufBox": { "seuilEleve": 75.00, "seuilMoyen": 70.00 },
  "...": "all the other blocks",
  "recalcul": {
    "recalcule": true,
    "nbCollaborateursScores": 100,
    "nbPlaces9Box": 100,
    "dureeMs": 412,
    "erreur": null
  }
}
```

`nbCollaborateursScores` and `nbPlaces9Box` are `null` when the recalculation failed. The screen should show `recalcul.erreur` when `recalcule` is `false`, and offer to retry (`POST .../calcul`) on a 409.

### The `...Source` interfaces, with fallbacks

Four inputs are HR decisions or reference data that the import does not provide yet: identified successors, committee decisions, the direction → pool mapping, and the vigilance flags. The engine does not depend on how they will be stored. It declares a small interface in `service` and uses whatever implementation Spring finds:

| Interface | Provides | Fallback when no implementation exists |
|---|---|---|
| `SuccesseurIdentifieSource` | Successors per position | None: every critical position is in alert |
| `ValidationComiteSource` | Decision per employee and quarter | All "En attente": no validated talent |
| `VivierThematiqueSource` | Pool of a direction | Nobody classified |
| `FaitsVigilanceSource` | 6 Oui/Non vigilance flags | Only engagement and the measured performance drop can raise signals |

The service constructor takes an `ObjectProvider` and logs a warning when the fallback is used. As a result:

- The application always starts, and the engine can be tested with in-memory doubles (`...EnMemoire` classes in `src/test`).
- Dou's import will plug in by implementing these interfaces, with no change to the engine.

`FaitsVigilance` uses a builder so that raw facts (dates, counts) can be added later without breaking existing implementations.

### View services with no calculation

Ima's `ui.service` classes carry the rule "REGLE : aucun calcul ici" (no calculation here). They call the engine or the repositories, then build plain display objects (`ui.model`). Business rules therefore exist in one place only, the engine, which is tested against the workbook; the screens cannot drift from the API.

### Loading: LAZY associations, no open session in views

`spring.jpa.open-in-view=false`: no JPA session stays open while a page or a JSON response is rendered. `Collaborateur.entite`, `Score.entite` and `Entite.parent` are LAZY. Every repository query whose results are displayed loads what the display reads, with `join fetch` (the org chart has four levels: `left join fetch x.entite e left join fetch e.parent e1 left join fetch e1.parent e2 left join fetch e2.parent`). A forgotten association fails loudly with a `LazyInitializationException` instead of silently running one query per row.

- `Entite.ancetre` walks up through the getters: a LAZY parent is a Hibernate proxy, whose fields are empty.
- An entity returned by `save` on a detached object is a merged copy whose associations are proxies; read the response from the object you saved, or reload it with a fetch query (`ParametreController.modifier`, `CollaborateurController.creer`).
- `SansSessionOuverteIntegrationTest` calls every page and every GET endpoint on a four-level org chart, and checks that the main pages (`/`, `/9box`, `/viviers`, `/comite-talent`), the main lists of the API and the recalculation read the database in the same number of queries for a small and a three-times larger population.

### Server-side rendering

Screens are Thymeleaf templates rendered on the server. There's no JavaScript framework and no build step, and every screen is a normal GET page whose filters are ordinary query parameters. Thymeleaf escapes output by default, and no template uses `th:utext`. This is enough for a single-user local tool, and keeps the codebase small for a three-person team.

**No half page.** By default Thymeleaf writes the page while it renders it: an exception in the middle of a template comes after the 200 status and the first part of the HTML were sent, and the browser gets a truncated page (`net::ERR_INCOMPLETE_CHUNKED_ENCODING`). `config/RenduCompletConfig` turns this off for every Thymeleaf view (`setProducePartialOutputWhileProcessing(false)`, same as `spring.thymeleaf.servlet.produce-partial-output-while-processing=false`, but applied in every profile and test): the whole page is rendered in memory (about 200 KB for the dashboard) and written only once complete; a rendering error happens before anything is written, so the error page is rendered instead. `RenduCompletTest` renders a template that fails halfway: nothing is written with the app's resolver, while Thymeleaf's default has already sent the start of the page.

**Restart after a build.** `mvnw spring-boot:run` serves classes and templates from `target/classes`. A `mvnw clean test` (or any rebuild) while the app runs replaces them under it: the running JVM keeps the classes it already loaded but reads new templates and not-yet-loaded classes, and a page whose template no longer matches its model fails mid-render. Stop the app before building, or restart it afterwards.

### The Excel comparison test

`DatasetExcelComparaisonTest` reads the client's workbook with its own small reader (`dataset/LecteurXlsx`, in the test folder). It feeds the inputs into the real services and compares the outputs with the workbook, cell by cell:

- performance and potential scores, 9-Box boxes and categories;
- talent / high-potential / relief-pool flags and validated talents;
- succession matching and critical-position coverage (15 positions, 1 alert, 93 %);
- thematic pools;
- vigilance index and level (55 / 35 / 10);
- all 2,500 skill gap statuses.

The dataset quarter (T3 2026) uses the workbook's reference date, 15/09/2026, as its `dateReference`: entry dates are compared as they are, with no shifting.

It is the proof that the engine implements the client's rules. The workbook is gitignored, so the test is skipped in CI; run it locally before merging engine changes.

---

## 8. Request lifecycle

### Opening `/comite-talent?trimestre=2026-3&statut=EN_ATTENTE`

```mermaid
sequenceDiagram
    participant B as Browser
    participant F as ProtectionRequetesFilter
    participant PC as PagesController
    participant VS as ComiteTalentViewService
    participant VC as ValidationComiteService
    participant TS as TalentService
    participant CS as CalculService
    participant R as Repositories (JPA)
    participant T as comite-talent.html
    B->>F: GET /comite-talent?trimestre=2026-3&statut=EN_ATTENTE
    F->>F: Host is localhost? GET needs no header
    F->>PC: comiteTalent("2026-3", "EN_ATTENTE")
    PC->>R: TrimestreCourantService.selectionner("2026-3"): quarters + quarters with scores
    PC->>VS: build(selection, "EN_ATTENTE")
    VS->>VC: getDecisionsComite(T3 2026)
    VC->>TS: detecterTalents(T3 2026)
    TS->>CS: chargerParametre(T3 2026)
    CS->>R: ParametreRepository.findByTrimestre
    TS->>R: ScoreRepository.findByTrimestreAvecCollaborateur (join fetch)
    TS-->>VC: talents (perf >= 85 and pot >= 85), best first
    VC->>R: ValidationComiteSource.statuts(talents, quarter), one query
    VC-->>VS: List<DecisionComite>
    VS->>VS: count per status, KPI = count of OUI, filter rows
    VS-->>PC: ComiteTalentView
    PC->>T: model "vue", view "comite-talent"
    T-->>B: HTML (KPI card, filters, table with badges)
```

Class by class:

1. **`ProtectionRequetesFilter`** checks that `Host` is `localhost` or `127.0.0.1`. A GET needs no header.
2. **`PagesController.comiteTalent`** receives the two optional query parameters, resolves the quarter with **`TrimestreCourantService`** (404 if `2026-3` is unknown; without the parameter, the most recent quarter that has scores) and sets the `trimestre` / `trimestres` model attributes.
3. **`ComiteTalentViewService.build`**:
   - builds the quarter options from the selection;
   - reads the status filter, where an unknown value means "all";
   - calls the engine.
4. **`ValidationComiteService.getDecisionsComite`** asks **`TalentService.detecterTalents`** for the proposed talents:
   - that loads the settings through **`CalculService.chargerParametre`** (404 if the quarter has none);
   - loads the quarter's scores in one query (`join fetch` employee, quarter, and the frozen entité with its parents, which gives the direction shown in the table);
   - keeps active employees with complete scores that pass both thresholds, sorted by performance.
5. **`ValidationComiteSource.statuts`** gives the committee decision of every talent in one query. Without an implementation, everything is "En attente".
6. The view service counts decisions per status, builds the KPI card (count of "Oui", on the whole quarter), keeps only the rows matching the filter, and maps each `Score` to a `ComiteTalentRow` (badge class `bg-success`, `bg-warning text-dark` or `bg-danger`).
7. **`comite-talent.html`** renders the sidebar fragment, the filter form (a GET form), the cards, the talents and successions with their decision forms ([Comité Talent decisions](#comité-talent-decisions)).

If the settings are missing, the engine's exception is caught by the view service and shown as a yellow warning instead of an error page.

### `POST /api/trimestres/2026/3/scores/recalcul`

```mermaid
sequenceDiagram
    participant C as Client (curl / JS)
    participant F as ProtectionRequetesFilter
    participant SC as ScoreController
    participant CR as ChargeurRessources
    participant SS as ScoreService (@Transactional)
    participant CS as CalculService
    participant R as Repositories
    participant EH as ApiExceptionHandler
    C->>F: POST .../scores/recalcul + X-Talent360: 1
    F->>F: Host local? write header present? else 403
    F->>SC: recalculer(2026, 3)
    SC->>CR: exigerTrimestre(2026, 3)
    CR->>R: TrimestreRepository.findByNumeroAndAnnee (404 if absent)
    SC->>SS: recalculerTrimestre(T3 2026)
    SS->>CS: chargerParametre
    SS->>R: Performance + Potentiel findByTrimestreAvecCollaborateur
    SS->>R: ScoreRepository.findByTrimestreAvecCollaborateur (existing scores, one query)
    loop each employee with performance marks
        SS->>SS: skip if not ACTIF or no potential marks (reason recorded)
        SS->>CS: calculerScorePerformance, categoriePerformance, calculerScorePotentiel
        SS->>R: ScoreRepository.save (insert, or update of the preloaded row)
    end
    SS-->>SC: ResultatRecalcul (saved scores + ignored with reason)
    SC-->>C: 200 RecalculResponse JSON
    Note over SC,EH: Any RessourceIntrouvable / DonneesIncompletes exception becomes a 404 / 422 ErreurApi
```

1. **`ProtectionRequetesFilter`** rejects the request with **403** if `Host` is not local, or if the `X-Talent360` header is missing (`en_tete_manquant`). The controller is never reached.
2. **`ScoreController.recalculer`** resolves the quarter through **`ChargeurRessources.exigerTrimestre`**, which throws a 404 if it doesn't exist.
3. **`ScoreService.recalculerTrimestre`** runs in one transaction:
   - loads the settings once, then all performance and potential marks of the quarter in two queries, and the quarter's existing scores in a third (a map by matricule);
   - for each employee, calls the pure functions of **`CalculService`** and creates or updates the single `Score` row, keeping its 9-Box box. Entité and manager are frozen on the score by reference (`figerOrganisation`), without loading them;
   - reads the database in a fixed number of queries whatever the population (5 on the dataset), plus one INSERT per new score; a second run with nothing changed writes nothing.
   - Employees not ACTIF, or missing one set of marks, are reported with the reason instead of failing the whole run.
4. The controller returns `RecalculResponse`: `nombreCalcules`, `nombreIgnores`, the scores and the ignored employees with their reason.
5. Any exception is turned into the `ErreurApi` JSON by **`ApiExceptionHandler`**, and the transaction is rolled back.

---

## 9. Security

The application holds personal data, so it is designed to be reachable **only from the workstation it runs on**.

| Measure | Where | Effect |
|---|---|---|
| Localhost binding | `server.address=127.0.0.1` in `application.properties` | The server does not listen on the network at all |
| Host check | `ProtectionRequetesFilter` | Any request whose `Host` is not `localhost` or `127.0.0.1` gets 403, reads included. This blocks "DNS rebinding", where a malicious site makes its domain point to 127.0.0.1 to read the data. Configurable with `talent360.securite.hotes-autorises`. |
| Write header (CSRF) | `ProtectionRequetesFilter.EN_TETE_ECRITURE` = `X-Talent360` | POST / PUT / PATCH / DELETE without the header get 403. A browser cannot add a custom header to a cross-site request without a CORS check, which the app refuses (no CORS config), so a malicious page cannot trigger writes. Screens send it with `fetch` ([`requetes-ecriture.md`](requetes-ecriture.md)). |
| Login | `SecurityConfig`, `UtilisateurDetailsService`, `Utilisateur` | Only HR logs in. Every page and every `/api/**` endpoint needs an authenticated RH; after login, HR lands on `/`. Form login, BCrypt hashes, 15-minute session, `HttpOnly` + `SameSite=Strict` cookie. A deactivated account is refused. Not logged in: pages redirect to `/login`, the API answers 401 in JSON |
| First account | `PremierCompteRhInitializer` | Created from environment variables ([section 10](#accounts-and-environment-variables)); there is no default password |
| CSRF on HTML forms | Spring Security | Login and logout forms carry the CSRF token (`th:action`) |
| No secrets in the code | `spring.datasource.username/password=${SPRING_DATASOURCE_…}` (no default) | The database account and password come from environment variables; the app refuses to start without them |
| Escaped output | Thymeleaf `th:text` everywhere, no `th:utext` | No XSS from data |
| Bound parameters | JPQL queries with parameters; the only concatenated SQL uses constants | No SQL injection |
| Generic 500 errors | `ApiExceptionHandler` | No stack trace or internal message is sent to the client |
| No names in logs | `Collaborateur.toString` shows the ID and status only | Logs carry no personal names |
| Database timeouts | `connectTimeout`, pool `connection-timeout`, `lock_wait_timeout=60` | No indefinite hang at startup |

**What's still missing**

- **An old MySQL password is in the public git history** (commits `17a709c`, `414361d`; the repository is public). It must be changed wherever it was used; it's no longer in the code.
- **Write endpoints bind entities** in `CollaborateurController` and `CompetenceController` (`POST` with an entity as `@RequestBody`, no validation) and allow deletions. They are RH only, but should take a validated form.

### Entités manquantes

A column that points to an `entite` row that does not exist (`0`, or a deleted row) must never crash a page.
It happened on the team's MySQL in October 2026: commit `1996c31` (merged with PR #49) changed
`rattachement_vivier.direction` (text) into `entite_id` (`@ManyToOne`, NOT NULL). `ddl-auto=update` added the
column to the existing table and MySQL filled the 10 existing rows with `0`; the foreign key could not be created,
and the old text column stayed (NOT NULL, unique), so the app could no longer insert rows either. `/` then failed
with `EntityNotFoundException: Unable to find Entite with id 0`. **Rule: a new NOT NULL foreign key on an existing
table needs a migration (or a nullable column), never ddl-auto alone.**

What the code does now:

- **Reads.** The getters of every `Entite` association (`Collaborateur.getEntite`, `Score.getEntite`,
  `Poste.getEntite`, `Manager.getEntiteGeree`, `Entite.getParent`, `RattachementVivier.getDirection`) go through
  `entity/EntiteExistante`: a reference to a missing row returns `null` (logged as a warning),
  never an exception. The screens show **"Sans direction"** (dashboard, Collaborateurs, Talent Passport, Viviers,
  Organigramme: a "Sans direction" card counts them, `CollaborateurRepository.compterSansEntite`). No extra query
  when the entity was already loaded by the page's `left join fetch` (`@NotFound` was tried and rejected: it forces
  EAGER loading, `/` went from 115 to 157 queries). Note: `Hibernate.isInitialized` is not a valid check, Hibernate
  can mark the proxy of a missing row "initialized" after a join; the target is checked instead.
- **Writes.** The import reads `rattachement_vivier` through an inner join (`findAllAvecDirection`): a broken row
  is ignored, never dereferenced, and a new rattachement always points to a saved direction (`@PrePersist` check).
  The recalculation copies a collaborator's entity onto the score only if its id exists (`EntiteRepository.findAllIds`,
  one query per recalculation: 6 fixed queries instead of 5, `RequetesMoteurDatasetTest`).
- **Errors.** An unexpected error on a screen shows `erreur/500` ("Une erreur est survenue", a short reference), the
  full exception goes to the log with the same reference (`ProfilsAdvice.erreurInattendue`); errors that already
  carry a status (400, 404, 405) keep it. Other errors reaching Spring's `/error` render `error/5xx.html`, the same
  page. `server.error.include-*` are set to never/false explicitly: DevTools (on the classpath when the app runs from
  the IDE) turns stack traces on otherwise.

Control query (read-only), one line per table:

```sql
SELECT 'collaborateur', COUNT(*) FROM collaborateur c LEFT JOIN entite e ON e.id_entite = c.id_entite
 WHERE c.id_entite IS NOT NULL AND e.id_entite IS NULL
UNION ALL SELECT 'score', COUNT(*) FROM score s LEFT JOIN entite e ON e.id_entite = s.id_entite
 WHERE s.id_entite IS NOT NULL AND e.id_entite IS NULL
UNION ALL SELECT 'manager', COUNT(*) FROM manager m LEFT JOIN entite e ON e.id_entite = m.id_entite
 WHERE m.id_entite IS NOT NULL AND e.id_entite IS NULL
UNION ALL SELECT 'poste', COUNT(*) FROM poste p LEFT JOIN entite e ON e.id_entite = p.id_entite
 WHERE p.id_entite IS NOT NULL AND e.id_entite IS NULL
UNION ALL SELECT 'entite.parent', COUNT(*) FROM entite x LEFT JOIN entite p ON p.id_entite = x.id_entite_parent
 WHERE x.id_entite_parent IS NOT NULL AND p.id_entite IS NULL
UNION ALL SELECT 'rattachement_vivier', COUNT(*) FROM rattachement_vivier rv LEFT JOIN entite e
 ON e.id_entite = rv.entite_id WHERE e.id_entite IS NULL;
```

Tests: `EntitesManquantesIntegrationTest` (keys to missing rows in six tables: every page and the main API reads
answer 200, "Sans direction" shown, the recalculation and a new import write no `0` and no missing key),
`ErreurInattendueTest` (French 500 page with a reference, no message or stack trace, detail in the log; Spring's
`/error` page; 400 and 404 unchanged).

**Conventions (audit round 1)**

- Template comments are Thymeleaf parser comments `<!--/* … */-->`: they are not sent to the browser
  (`FinitionsAuditTest` fails on a plain `<!-- -->`).
- Display labels and messages are in French with accents (Modérée, Élevée, À renforcer, Maîtrisé, À développer…);
  enum codes are unchanged. Counts of talents always say which ones: "Talents proposés" or "Talents validés".
- The original logo `BP-Logo.png` is kept in `docs/assets/`, not served; the app serves `BP-Embleme.png` and
  `BP-Logo-Complet.png`.

---

## 10. How to run

### Prerequisites

- JDK 17
- MySQL 8.x running locally on port 3306
- Git
- The client workbook, received separately, copied to `docs/data/TALENT_360_BANK_Dataset_V1.xlsx`. Only the Excel comparison test needs it.

### Database

Create the database once. The tables are created by Hibernate at the first start (`ddl-auto=update`).

```
mysql -u root -p -e "CREATE DATABASE talent360bank CHARACTER SET utf8mb4;"
```

### Accounts and environment variables

**1. A dedicated MySQL user (never `root`).** Once, as `root`:

```sql
CREATE USER 'talent360'@'localhost' IDENTIFIED BY 'a-long-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON talent360bank.* TO 'talent360'@'localhost';
```

`CREATE`, `ALTER`, `INDEX` and `REFERENCES` are needed because Hibernate creates and updates the tables (`ddl-auto=update`).

**2. Environment variables.** The app has no default values: it refuses to start without the MySQL ones, and nobody can log in without an RH account. Set them as **user** variables, then open a new terminal or restart the IDE:

```powershell
setx SPRING_DATASOURCE_USERNAME "talent360"
setx SPRING_DATASOURCE_PASSWORD "a-long-password"
setx TALENT360_ADMIN_LOGIN "rh.admin"
setx TALENT360_ADMIN_PASSWORD "at-least-12-characters"
```

**3. First RH account.** At startup, if no RH account exists, `PremierCompteRhInitializer` creates one from `TALENT360_ADMIN_LOGIN` / `TALENT360_ADMIN_PASSWORD` (password: 12 characters minimum, stored as a BCrypt hash). If the variables are missing, it logs a warning and creates nothing. Once the account exists, the two variables are no longer read. Remove them (`reg delete HKCU\Environment /v TALENT360_ADMIN_PASSWORD /f`, same for the login) so the password does not stay in the environment.

Then open http://localhost:8080/login. After login, HR lands on `/`.

### Commands

| Goal | Command |
|---|---|
| Run the app | `.\mvnw spring-boot:run` |
| Run with development settings (SQL log, template reload, auto-restart) | `.\mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"` |
| Run all tests (H2, no MySQL needed) | `.\mvnw test` |
| Run one test class | `.\mvnw test -Dtest=VigilanceServiceTest` |

### Profiles

- **default:** safe settings. Local binding, no SQL log, template cache on, DevTools off.
- **`dev`** (`application-dev.properties`): SQL printed, template reload, automatic restart. Never for a client demo.
- **`demo`:** loads the workbook into an empty database, runs the engine, and plugs the workbook in as the four sources. **It exists only on Jas's machine:** the classes (`dev/DemoDataLoader`, `dev/DemoClasseurSources`) are deliberately excluded from git through `.git/info/exclude`. Other developers load the same data with the import ([`import-donnees.md`](import-donnees.md)).

### URLs

- Log in first: http://localhost:8080/login
- Screens: http://localhost:8080/, `/9box`, `/viviers`, `/comite-talent`
- API example (browser, once logged in): http://localhost:8080/api/dashboard/synthese?annee=2026&numero=3
- From `curl`, pass the session cookie of a logged-in RH, plus the header on writes: `curl -X POST -b "JSESSIONID=…" -H "X-Talent360: 1" http://localhost:8080/api/trimestres/2026/3/scores/recalcul`

Use `localhost` or `127.0.0.1`: any other host name is rejected by the Host check.

### Tests

**760 test executions in 75 test classes** (parameterized tests run once per case), all passing (`./mvnw clean test`, 1 Oct 2026).

| Folder | What it covers |
|---|---|
| `service` | Unit tests of every engine module with mocked repositories, and in-memory doubles for the sources |
| `controller` | `@WebMvcTest` slices per controller, plus `ApiIntegrationTest`: full application on H2, real HTTP calls |
| `config` | Initializers, the protection filter and `SecurityConfigTest` (anonymous → `/login` or 401, RH everywhere, login, CSRF, first RH account), including JPA tests on H2 that reproduce old database shapes |
| `entity` | Bean Validation of the settings and marks |
| `ui` | The Comité screen: view service and real template rendering |
| `dataset` | The Excel comparison and query counts on the real workbook, skipped when the workbook is absent |

**Troubleshooting.** If you see "Port 8080 was already in use", an earlier instance is still running: stopping `mvnw` doesn't always stop the Java process it started. Find the leftover `java.exe` and stop it.

---

## 11. Git workflow

What the repository actually does, as checked on GitHub and in the history:

- **Branches:**
  - `develop` is the integration branch; every change reaches it through a pull request.
  - Work happens on short-lived branches: `feature/...` (for example `feature/ecran-comite`, `feature/parametres-finaux`), `fix/...` (for example `fix/tests-apres-douae`, `fix/audit-securite`), and personal branches (`douae`, `iman2`, `ima-ui`).
- **Protection:** `develop` is a protected branch on GitHub.
- **CI:** `.github/workflows/ci.yml` runs `mvnw test` on every pull request and push to `develop`. It is **not yet a required check**, so a red build could still be merged. Declaring it required in the branch rules is a two-minute fix.
- **Merge method:** pull requests are merged with **merge commits** ("Merge pull request #N"), **not squash merges**. If the team wants squash merges, enable only "Squash and merge" in the repository settings.
- **Default branch:** the GitHub default branch is currently **`douae`**, not `develop`. New pull requests therefore target `douae` by default; set the default branch to `develop` to avoid mistakes.
- **Keeping a branch current:** merge `develop` into your branch, resolve conflicts, run the full suite, push, then open the pull request.
- **Never commit** the workbook (`docs/data/` is gitignored), passwords, or the local `dev/` demo classes.

A typical cycle:

```
git fetch origin
git switch --no-track -c feature/my-change origin/develop
# ... work, .\mvnw test ...
git push -u origin feature/my-change
# open a pull request to develop, wait for CI, get a review, merge
```

---

## 12. Current status and what's left

### Done (engine and security — Jas)

- Every workbook calculation, with every `00_PARAMETRES` row (6–77) configurable per quarter.
- Results verified against the workbook for all the modules listed in [section 7](#the-excel-comparison-test).
- REST API for all modules; Comité screen; security fixes.
- RH-only login (Spring Security, BCrypt, first RH account from environment variables), dedicated MySQL account, `CollaborateurResponse` instead of the entity.
- Quarter reference date (`Trimestre.dateReference`): seniority no longer depends on today's date (audit C4).
- Vigilance list covers every active employee with a vigilance input, scored or not (audit C8). No N+1 in the recalculation or the main pages, LAZY entités, `open-in-view=false` (audit 3.3, 3.4).
- Real home dashboard (no hard-coded figure, checked against `00_DASHBOARD`), displayed quarter = most recent with scores and `?trimestre=` on every screen (audit 2.3, B5).
- Vue manager and Vue entité pages, profile selector in the sidebar, cross links manager / entité, French 404 page.
- Alerts "Un seul successeur" and "Nouveau talent identifié", notifications and bell badge (`/api/notifications[/badge]`), campaign progress per direction (`/api/trimestres/{a}/{n}/campagne`).
- Compétences module (`/api/trimestres/{a}/{n}/competences`: per skill, top gaps, what each critical position's successors lack), checked against `06`, `07`, `09` and G10.
- Employee list (filters, sort, pagination: `/api/trimestres/{a}/{n}/collaborateurs`), checked against the workbook.
- Largest gap per successor (`09_SUCCESSION!M`), every successor of each critical position, pool summaries (`/api/viviers/synthese`) and target post per employee (`/api/trimestres/{a}/{n}/postes-cibles`, fiche `posteCible`), checked against the workbook.
- Self-evaluation and manager evaluation stored side by side (`source` on `Performance` / `Potentiel`), official score blended by `PonderationSources` (default manager only), fiche `autoEvaluation` and manager view `autoVsManager` (audit R1d).

### Left, by owner

**Dou — data and import**

- Import `11_DEVELOPMENT_PLAN`.
- Import the self-evaluation files with `source = AUTO` (the workbook import writes `MANAGER`). Read and write through the repository methods that take a `SourceEvaluation`; the uniqueness is (employee, quarter, source).
- Secure `CollaborateurController` / `CompetenceController`: DTOs and validation, and no deletion in the demo build.
- Uniqueness on `CompetenceCollaborateur` (employee, skill) and `QuestionnaireEngagement` (employee, quarter).

**Ima — UI**

- Quarter selector on `/9box` and `/viviers`: the `trimestre` and `trimestres` model attributes are already there ([Displayed quarter](#displayed-quarter-all-screens)).
- Screens still to build: Engagement & Fidélisation (`/engagement`), Historique (`/historique`, could list the dated committee decisions).
- Import (Dou): lock `POST /api/imports` like the page does (B6); avoid the empty quarter left by a failed import (B5).
- Fiche page: show `posteCible` and `successions[].gapCompetence` (the model has them).
- Page tests for `/`, `/9box`, `/viviers`.

**Jas — engine and platform**

- Schema migrations (Flyway) instead of `ddl-auto=update`.
- Plan the move to Spring Boot 4.x: 3.5 no longer receives free security fixes after June 2026.
- Make CI a required check on `develop`, and set `develop` as the default branch.
- Delete `ColonnesObsoletesInitializer` and its test once every MySQL database has been recreated (`DROP DATABASE`, see [`import-donnees.md`](import-donnees.md)): it only makes the old `src_*` settings columns nullable on databases created before they were removed.

### Known limitations

- **Same dataset in two quarters.** Tried on H2 with a copy of the dataset re-dated to 15/12/2026 imported into T4 after T3: the import and calculation succeed (100 scores), scores, 9-Box, talents, high potentials, Ready Now and coverage are identical to T3, but the history now compares T4 with T3: "baisse de performance" is decided by the scores (equal, so no drop) instead of the imported flag (`12_VIGILANCE` I), so vigilance moves (T3: 55 / 35 / 10, T4: 56 / 35 / 9) and alerts go from 23 to 22; seniority is measured at 31/12/2026 instead of 15/09/2026 (BP002: 18.2 → 18.5 years). The fiche shows T3 in the history with the same scores. Useful only to test the history screens, not as real data.
- The official score is the manager's (`PonderationSources` 100 / 0) until the client answers audit question 2.
- Vigilance and the "evaluation missing" alerts look at the manager's evaluation only; a self-evaluation alone does not count as an evaluation.

- Skills are not tied to a quarter; the quarter only selects the gap threshold.
- The engine compares rounded scores with thresholds, while the workbook compares unrounded ones. No employee in the dataset is affected.
- `ddl-auto=update` never drops or alters columns. Removed settings leave unused columns behind (the `src_*` columns are made nullable at startup for this reason).

---

## 13. Glossary

| French term | Meaning |
|---|---|
| **Collaborateur** | Employee |
| **Trimestre (T1–T4)** | Quarter; every evaluation and result belongs to one |
| **Paramètre / réglages** | The quarter's set of weights and thresholds |
| **Pondération / poids** | Weight in a weighted average |
| **Seuil** | Threshold |
| **Barème** | Scale for turning a quantity into points (e.g. 8 points per year of seniority) |
| **Score de performance** | How well the employee does the current job (/100) |
| **Score de potentiel** | Capacity to grow into bigger roles (/100) |
| **Catégorie de performance** | Label from the performance score: Exceptionnelle, Élevée, Solide, À renforcer, Insuffisante |
| **9-Box (matrice 9-Box)** | 3×3 grid, performance × potential, each box with a label (Talent clé, Expert, À surveiller…) |
| **Talent** | Employee with high performance **and** high potential (both ≥ 85) |
| **Haut potentiel (HP)** | Employee with very high potential (≥ 85) and at least solid performance (≥ 75) |
| **Vivier** | Pool of employees |
| **Vivier de relève** | Relief (succession) pool: talents and high potentials, the bench for future key roles |
| **Vivier thématique** | Pool by business family, from the direction: Commercial, Digital, Expertise, Management, Risques |
| **Comité Talent** | Talent Committee, the body that validates (Oui / Non / En attente) the talents proposed by the engine |
| **Talent validé** | Proposed talent **and** committee decision "Oui" |
| **Poste critique** | Critical position: one whose vacancy would hurt the bank; flagged "Oui" in the reference data |
| **Titulaire** | Current holder of a position |
| **Successeur identifié** | Successor chosen by HR for a position (an HR input, not a calculation) |
| **Matching** | Fit score (/100) between a candidate and a target position |
| **Readiness** | How soon a candidate is ready: Ready Now, < 1 an, 1–2 ans, > 2 ans |
| **Couverture** | Coverage of a critical position by its successors; "ALERTE" when too few |
| **Taux de couverture** | Share of critical positions not in alert |
| **Vigilance / indice de vigilance** | Risk-of-departure index (/100) built from transparent signals, used to prioritise HR follow-up; not a prediction |
| **Signal de vigilance** | One risk factor (low engagement, no move in 4 years…) |
| **Engagement** | Score from the engagement questionnaire (/100) |
| **Gap de compétence** | Target level minus current level for a skill |
| **Maîtrise / À développer / Prioritaire** | Skill gap statuses |
| **Direction** | Business division the employee belongs to (Réseau Retail, Risques…) |
| **Référentiel** | Reference list (e.g. the 25 skills) |
| **Monoposte** | Installed and used on a single workstation |
