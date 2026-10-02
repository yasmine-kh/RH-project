# Charger les données du jeu de test

Ce guide explique comment remplir la base avec le classeur `TALENT_360_BANK_Dataset_V1.xlsx` : **un seul envoi importe le classeur et lance le calcul** (scores, 9-Box, vivier de relève), depuis la page **Import** de l'application ([section 3](#3-charger-les-données)) ou par l'API. Après ça, tous les écrans affichent de vraies données.

Le client envoie **un nouveau classeur à chaque campagne** (nouveau trimestre), et peut renvoyer un **fichier corrigé** pour le même trimestre : voir la [section 6](#6-nouvelle-campagne-ou-fichier-corrigé).

Aucun profil particulier n'est nécessaire (le profil `demo` n'existe que sur un poste et n'est plus utile pour avoir des données).

## 1. Récupérer le classeur

Le fichier n'est **pas dans Git** (`docs/data/` est dans `.gitignore`) : il contient des données de collaborateurs. Jasmine l'envoie à part.

Le copier ici :

```
docs/data/TALENT_360_BANK_Dataset_V1.xlsx
```

L'import accepte un fichier placé n'importe où, mais cet emplacement est celui qu'utilisent les exemples ci-dessous et les deux tests qui comparent le moteur au classeur (ignorés si le fichier est absent).

## 2. Préparer la base et lancer l'application

Une seule fois :

```
mysql -u root -p -e "CREATE DATABASE talent360bank CHARACTER SET utf8mb4;"
```

```powershell
setx SPRING_DATASOURCE_PASSWORD "mot-de-passe-mysql"
```

Ouvrir un **nouveau** terminal (pour que la variable soit prise en compte), puis :

```powershell
.\mvnw spring-boot:run
```

Les tables sont créées au premier démarrage. L'application écoute sur http://localhost:8080 (utiliser `localhost` ou `127.0.0.1`, tout autre nom est refusé).

## 3. Charger les données

Deux façons, qui font exactement le même traitement (import, puis calcul) : la page **Import** (pour le RH, ci-dessous) ou l'API ([plus bas](#par-lapi-curl)).

### Importer depuis l'application

1. Se connecter avec le compte RH, puis ouvrir **http://localhost:8080/import**. (La page n'a pas encore d'entrée dans le menu de gauche : taper l'adresse.)
2. **Classeur (.xlsx)** : choisir `TALENT_360_BANK_Dataset_V1.xlsx`. Seuls les `.xlsx` sont acceptés, 10 Mo au maximum (un fichier plus gros est refusé avant l'envoi, avec un message).
3. **Année** et **Trimestre** : le formulaire propose le trimestre qui suit le dernier existant (sans aucun trimestre, celui d'aujourd'hui). Pour le jeu de données : **2026** et **T3**. Un classeur daté d'un autre trimestre est refusé.
4. **Date de référence** (facultative) : la date à laquelle le trimestre est évalué (ancienneté). Vide = dernier jour du trimestre. Pour le jeu de données : **15/09/2026**, la date du classeur.
5. **Simulation** (conseillé la première fois) : cocher pour contrôler le fichier **sans rien enregistrer**. Le bilan montre ce que ferait l'import : lignes par feuille, lignes refusées, collaborateurs qui passeraient INACTIF.
6. **Calculer après l'import** : coché par défaut. Le décocher n'importe que les données ; le calcul se relance ensuite par l'API (`POST /api/trimestres/2026/3/calcul`).
7. Cliquer sur **Importer**. Le bouton se désactive pendant l'import (quelques secondes pour le jeu de données).
8. Lire le **bilan** affiché au-dessus du formulaire :
   - le **statut** : `SUCCES` (tout est importé), `PARTIEL` (des lignes ont été écartées, le reste est en base), `ECHEC` (rien n'est importé, la cause est affichée) ;
   - les **lignes importées par feuille** (et celles retirées d'un fichier corrigé) ;
   - les **lignes écartées** : la feuille, le **numéro de ligne Excel** et le motif. Corriger le classeur à ces lignes, puis réimporter ;
   - les collaborateurs **passés INACTIF** (absents de `01_COLLABORATEURS`) ou remis ACTIF ;
   - le **calcul** : nombre de scores, de placements 9-Box et de membres du vivier de relève ; ou la cause si le calcul a échoué (l'import reste enregistré).
9. Après un import calculé, cliquer sur **Voir le tableau de bord T3 2026** (`/?trimestre=2026-3`).

Sous le formulaire, **Derniers imports** liste les 20 derniers envois (date, fichier, trimestre, statut, lignes, erreurs), le plus récent en premier. Les simulations n'y figurent pas. La colonne Utilisateur reste vide tant que l'import n'enregistre pas le compte qui l'a lancé.

Un seul import ou calcul à la fois par trimestre : si un calcul du même trimestre tourne déjà, la page affiche « Un calcul est déjà en cours » et n'importe rien. Réessayer quand il est terminé.

### Par l'API (curl)

Toute écriture (POST, PUT, DELETE) doit porter l'en-tête **`X-Talent360`** (n'importe quelle valeur non vide, on envoie `1`). Sans lui : `403 en_tete_manquant`. Voir [`requetes-ecriture.md`](requetes-ecriture.md).

Le jeu de données porte le trimestre **T3 2026** : `annee=2026`, `numero=3`.

#### Windows (PowerShell)

Dans PowerShell 5.1, `curl` est un alias d'`Invoke-WebRequest` : écrire **`curl.exe`** (livré avec Windows 10 et 11). Lancer les commandes depuis la racine du projet.

**Étape 1 (facultative) : ouvrir le trimestre** (crée le trimestre et ses réglages, repris du trimestre précédent ou par défaut ; rejouable)

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/trimestres `
  -Headers @{ 'X-Talent360' = '1' } -ContentType 'application/json' `
  -Body '{"annee":2026,"numero":3}'
```

**Étape 2 : simuler** (facultatif mais conseillé : contrôle le fichier et rend les compteurs, **sans rien enregistrer**)

```powershell
curl.exe -X POST "http://localhost:8080/api/imports?simulation=true" `
  -H "X-Talent360: 1" `
  -F "fichier=@docs/data/TALENT_360_BANK_Dataset_V1.xlsx" `
  -F "annee=2026" -F "numero=3"
```

**Étape 3 : importer le classeur** (le calcul complet suit automatiquement)

```powershell
curl.exe -X POST http://localhost:8080/api/imports `
  -H "X-Talent360: 1" `
  -F "fichier=@docs/data/TALENT_360_BANK_Dataset_V1.xlsx" `
  -F "annee=2026" -F "numero=3"
```

Pour importer sans calculer, ajouter `?calcul=false` à l'URL ; le calcul se relance ensuite seul :

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/trimestres/2026/3/calcul `
  -Headers @{ 'X-Talent360' = '1' }
```

**Vérifier**

```powershell
Invoke-RestMethod http://localhost:8080/api/imports
Invoke-RestMethod "http://localhost:8080/api/dashboard/synthese?annee=2026&numero=3"
```

#### Git Bash, macOS, Linux

```bash
# 1. Ouvrir le trimestre
curl -X POST http://localhost:8080/api/trimestres \
  -H "X-Talent360: 1" -H "Content-Type: application/json" \
  -d '{"annee":2026,"numero":3}'

# 2. Simuler (rien n'est enregistre)
curl -X POST "http://localhost:8080/api/imports?simulation=true" \
  -H "X-Talent360: 1" \
  -F "fichier=@docs/data/TALENT_360_BANK_Dataset_V1.xlsx" \
  -F "annee=2026" -F "numero=3"

# 3. Importer : le calcul complet suit (ajouter ?calcul=false pour l'eviter)
curl -X POST http://localhost:8080/api/imports \
  -H "X-Talent360: 1" \
  -F "fichier=@docs/data/TALENT_360_BANK_Dataset_V1.xlsx" \
  -F "annee=2026" -F "numero=3"

# Relancer le calcul seul, si besoin
curl -X POST http://localhost:8080/api/trimestres/2026/3/calcul -H "X-Talent360: 1"

# Vérifier
curl http://localhost:8080/api/imports
curl "http://localhost:8080/api/dashboard/synthese?annee=2026&numero=3"
```

Puis ouvrir http://localhost:8080/ : le tableau de bord, la 9-Box, les viviers et le Comité Talent affichent les données.

> L'étape 1 est facultative : l'import ouvre lui-même le trimestre s'il n'existe pas. Elle reste utile pour ouvrir un trimestre et ajuster ses réglages avant d'avoir son fichier. Attention : un trimestre ouvert devient le « dernier trimestre » des écrans, même vide.

## 4. Ce que l'import lit

| Feuille | Colonnes | Écrit dans |
|---|---|---|
| `01_COLLABORATEURS` | A–F, L–M, O (dont sexe, naissance, statut) | `Collaborateur` |
| `01_COLLABORATEURS` | H–K (direction, département, région, agence) | `Entite` : l'organigramme, sans doublon ; le collaborateur est rattaché à l'entité la plus fine |
| `01_COLLABORATEURS` | N (Manager_ID) | `Manager` : un par Manager_ID distinct, lié au collaborateur qui tient le rôle |
| `05_REFERENTIEL_COMPETENCES` | A–C (la légende 1–5 en dessous est ignorée) | `Competence` |
| `06_EMPLOYEE_SKILLS` | A–C, E–H | `CompetenceCollaborateur` |
| `07_POSTES` | A–P (C : direction → `Entite` de type DIRECTION) | `Poste` |
| `08_POSTES_CRITIQUES` | A, E–F (titulaire) | `Poste` |
| `02_PERFORMANCE` | A, D–H (notes sur 100) | `Performance` du trimestre ; l'évaluateur est le manager du collaborateur (col. N de 01), la feuille ne le nomme pas |
| `03_POTENTIEL` | A, D–J (notes sur 100) | `Potentiel` du trimestre |
| `09_SUCCESSION` | A–B | `SuccesseurIdentifie` |
| `10_TALENTS` | A, C, G, I | `ValidationComite` (décision du comité), `RattachementVivier` (direction → vivier) |
| `12_VIGILANCE` | A, D, F–K | `QuestionnaireEngagement` (D), `DeclarationVigilance` (F–K) |

Les colonnes calculées du classeur (scores, catégories, case 9-Box, indices…) **ne sont pas importées** : c'est le moteur qui les recalcule juste après l'import.

`00_PARAMETRES` (poids et seuils) **n'est pas lue** : les réglages sont ceux de l'application, propres à chaque trimestre (`/api/trimestres/{année}/{numéro}/parametre`). Un nouveau trimestre reprend ceux du trimestre précédent le plus récent, sinon les valeurs par défaut, identiques à celles de la feuille 00 du jeu de données actuel.

`11_DEVELOPMENT_PLAN` n'est pas encore importée (voir la TODO dans `ImportClasseurService`).

**Organigramme.** Chaque ligne de `01_COLLABORATEURS` donne un chemin Direction › Département › Région › Agence. Une entité est identifiée par ce chemin (son `code`, par exemple `DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_NORD/REG:TANGER_TETOUAN`) : une même région sous deux départements donne deux entités, ce qui est le cas dans le jeu de données (Casablanca-Settat existe sous cinq départements, l'agence « Siege » sous presque tous). Une cellule vide saute son niveau. Le jeu de données donne 132 entités : 10 directions, 25 départements, 41 régions, 56 agences.

**Managers.** 38 managers pour 81 collaborateurs qui en ont un. Le classeur ne dit pas quelle entité dirige chaque manager : `Manager.entiteGeree` reste vide.

**Contrôles du fichier entier, avant toute écriture.** Si l'un échoue, rien n'est importé (statut `ECHEC`, HTTP 422) :

- **Format** : les dix feuilles du tableau sont présentes, et chaque colonne lue porte son en-tête (casse, accents et espaces ignorés). Chaque feuille manquante (« renommée en … ? » si une feuille au même préfixe existe) et chaque colonne renommée ou déplacée est listée dans `erreurs`.
- **Période** : si le classeur annonce sa période, elle doit être le trimestre choisi. Sont lus le nom du fichier (`T3_2026`, `2026-Q3`, `T3 2026`…) et les lignes de titre (1 à 3) de chaque feuille (mêmes formes, « 3e trimestre 2026 », ou une date jj/mm/aaaa, rattachée à son trimestre). Le jeu de données actuel porte « au 15/09/2026 » en `00_DASHBOARD` A2 : il ne s'importe que sur T3 2026.

**Contrôles ligne à ligne.** Notes de 0 à 100, niveaux de compétence de 1 à 5, engagement de 0 à 100, Oui/Non pour les faits de vigilance, décision du comité parmi Oui / Non / En attente, statut parmi Actif / Inactif / Archive. Une ligne en erreur est écartée, les autres sont importées.

## 5. Lire la réponse de l'import

La page Import affiche ces mêmes informations en clair (section 3). Réponse de l'API :

```json
{
  "idImport": 1,
  "nomFichier": "TALENT_360_BANK_Dataset_V1.xlsx",
  "annee": 2026, "numero": 3,
  "simulation": false,
  "statut": "SUCCES",
  "nbLignes": 3102, "nbErreurs": 0,
  "feuilles": [ { "feuille": "01_COLLABORATEURS", "presente": true, "nbLignes": 100, "nbErreurs": 0, "nbRetirees": 0 }, ... ],
  "erreurs": [],
  "nbDesactives": 0, "desactives": [], "reactives": [],
  "message": null,
  "calcul": { "scores": { "nombreCalcules": 100, ... }, "placement9Box": { ... }, "vivierReleve": { ... } },
  "erreurCalcul": null
}
```

| Champ | Sens |
|---|---|
| `simulation` | `true` : rien n'a été enregistré (ni données, ni trimestre, ni journal), `idImport` est nul, pas de calcul. |
| `desactives` | Collaborateurs absents de `01_COLLABORATEURS`, passés **INACTIF** (voir section 6). En simulation : ceux qui le seraient. |
| `reactives` | Collaborateurs qui n'étaient pas actifs et que le fichier remet ACTIF. |
| `feuilles[].nbRetirees` | Lignes du trimestre supprimées parce que leur collaborateur n'est plus dans la feuille (fichier corrigé). |
| `calcul` | Bilan du calcul lancé après l'import : mêmes corps que `POST /api/trimestres/{année}/{numéro}/calcul`. Nul si `calcul=false`, en simulation ou en échec. |
| `erreurCalcul` | Cause si le calcul a échoué ; l'import, lui, est enregistré. Corriger puis relancer le calcul seul. |

| Statut | HTTP | Sens |
|---|---|---|
| `SUCCES` | 200 | Tout est importé. |
| `PARTIEL` | 200 | Des lignes ont été écartées ; le reste est en base. Le détail est dans `erreurs`. |
| `ECHEC` | 422 | Rien n'a été importé : fichier illisible, format non conforme, autre période, aucune ligne lisible, erreur inattendue. La cause est dans `message`, le détail des écarts de format dans `erreurs`. |

Chaque erreur donne la feuille, le **numéro de ligne Excel** et le motif :

```json
{ "feuille": "02_PERFORMANCE", "ligne": 12, "message": "colonne D (noteObjectifs) : doit être inférieur ou égal à 100" }
```

`ligne` vaut `null` quand l'erreur porte sur toute la feuille (feuille absente, en-tête introuvable) ; pour une colonne renommée, c'est la ligne de l'en-tête. Une ligne en erreur n'écrit rien ; les autres lignes sont importées.

Chaque import est enregistré dans le journal, y compris les échecs (pas les simulations) : `GET /api/imports` (le plus récent en premier).

## 6. Nouvelle campagne ou fichier corrigé

Chaque classeur **décrit toute la population et tout le trimestre** : l'application se régénère à partir de lui. Rien n'est jamais supprimé côté collaborateurs, et l'historique des trimestres passés est conservé.

### Nouvelle campagne (nouveau trimestre)

1. Vérifier que le fichier porte bien la période de la campagne (nom de fichier, date du tableau de bord) : un classeur daté d'un autre trimestre est refusé.
2. **Simuler** avec le nouveau trimestre (`annee`, `numero`) et `simulation=true`. Lire `statut`, `erreurs` (format, lignes) et surtout **`desactives`** : les collaborateurs qui ne sont plus dans le fichier. Si la liste surprend, vérifier le fichier avant d'aller plus loin.
3. **Importer** sans `simulation` : le trimestre est ouvert, ses **réglages sont repris du trimestre précédent** (ceux que le RH a ajustés dans l'application), puis le calcul complet tourne. Vérifier `calcul` et `erreurCalcul` dans la réponse.
4. Si les réglages de la nouvelle campagne doivent changer : `PUT /api/trimestres/{année}/{numéro}/parametre`, puis relancer le calcul seul (`POST /api/trimestres/{année}/{numéro}/calcul`).

Ce que fait l'import d'une campagne :

- un collaborateur **absent** de `01_COLLABORATEURS` passe **INACTIF** (jamais supprimé) : il sort des calculs, son historique reste. S'il **revient** dans un fichier suivant (statut Actif en colonne O), il repasse ACTIF (`reactives`). Un identifiant présent sur une ligne rejetée compte comme présent ; une feuille 01 sans aucune ligne ne désactive personne ;
- les écrans « dernier trimestre » (tableau de bord, 9-Box, viviers, Comité Talent) montrent le **plus récent par (année, numéro)**, pas le dernier importé.

### Fichier corrigé (même trimestre)

1. **Simuler** avec le même trimestre : contrôler `erreurs`, `desactives` et `nbRetirees` par feuille.
2. **Importer** : tout est mis à jour **sans doublon** et recalculé. Les scores, cases 9-Box et membres du vivier de relève du trimestre sont remplacés : un collaborateur qui ne remplit plus les conditions (inactif, notes retirées) perd son score et sa place.
3. Dans `02_PERFORMANCE`, `03_POTENTIEL`, `10_TALENTS`, `12_VIGILANCE` et `09_SUCCESSION`, une ligne du trimestre dont le collaborateur **a disparu de la feuille est supprimée** (`nbRetirees`), pour que le trimestre reflète le dernier fichier et non l'union des envois. Par prudence, rien n'est retiré d'une feuille qui a une ligne en erreur : corriger puis réimporter.

### Ce qui est global et ce qui est propre au trimestre

| Global : le dernier fichier importé fait foi | Par trimestre : conservé pour chaque trimestre |
|---|---|
| `Collaborateur` (identité, fonction, grade, statut, entité, manager), `Entite`, `Manager`, `Competence`, `CompetenceCollaborateur` (niveaux de compétence), `Poste` (et titulaire), `SuccesseurIdentifie`, `RattachementVivier` | `Performance`, `Potentiel`, `QuestionnaireEngagement`, `DeclarationVigilance`, `ValidationComite`, `Score`, `AppartenanceVivier`, `Parametre`, journal des imports |

Pour que les trimestres passés gardent leur contexte, chaque `Score` **fige au calcul** l'entité (donc la direction) et le manager du collaborateur. Les viviers thématiques, le Comité Talent (API et page) et la page des viviers lisent ces valeurs figées : un collaborateur muté en T4 reste dans la direction qu'il avait en T3 sur les écrans du T3.

Limite : les données globales suivent le **dernier fichier importé**. Réimporter un fichier corrigé d'un trimestre ancien après la campagne suivante remet la population (statuts, directions, compétences) dans l'état de ce fichier ancien : réimporter ensuite le fichier de la campagne la plus récente.

## 7. Repartir d'une base vide

À faire **une fois après la refonte du modèle** (Employe → Collaborateur, ajout de `Entite` et `Manager`), et chaque fois qu'une base de développement est dans un état douteux.

**Pourquoi.** L'application tourne avec `spring.jpa.hibernate.ddl-auto=update` : Hibernate **ajoute** tables et colonnes, mais ne renomme, ne modifie ni ne supprime rien. Sur une base créée avant la refonte :

- les nouvelles tables `collaborateur`, `competence_collaborateur`, `entite` et `manager` sont créées **vides** ; les anciennes `employe` et `employee_skill` restent là avec leurs données, mais plus aucun code ne les lit ;
- dans `performance`, `potentiel`, `score`, `appartenance_vivier`, `questionnaire_engagement`, `declaration_vigilance`, `validation_comite`, `successeur_identifie` et `alerte`, une colonne `id_collaborateur` est ajoutée **à côté** de l'ancienne `id_employe`. Celle-ci est `NOT NULL` avec une clé étrangère vers `employe` : Hibernate ne la remplit plus, donc **toute insertion échoue** (MySQL en mode strict) ;
- les anciennes contraintes d'unicité (`uk_*_employe_*`) restent actives sur `id_employe` ; les nouvelles (`uk_*_collaborateur_*`) sont ajoutées en plus ;
- `poste.direction` (texte) reste, à côté de la nouvelle `poste.id_entite`, et n'est plus lue.

Migrer ces données à la main (SQL) serait possible, mais tout se recharge depuis le classeur : le plus simple est de repartir de zéro.

**Procédure** (supprime **toutes** les données de `talent360bank`, y compris les réglages modifiés à la main et les saisies faites dans l'application) :

1. Arrêter l'application (Ctrl+C dans son terminal).
2. Supprimer et recréer la base :

   ```powershell
   & "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p -e "DROP DATABASE IF EXISTS talent360bank; CREATE DATABASE talent360bank CHARACTER SET utf8mb4;"
   ```

   (ou `mysql -u root -p -e "..."` si `mysql` est dans le `PATH` ; dans MySQL Workbench, les deux instructions dans un onglet SQL.)
3. Relancer l'application : `.\mvnw spring-boot:run`. Hibernate crée toutes les tables du nouveau modèle ; la matrice 9-Box et le vivier de relève sont posés au démarrage.
4. Recharger les données avec la [section 3](#3-charger-les-données) : l'import lance le calcul.
5. Vérifier :

   ```sql
   USE talent360bank;
   SHOW TABLES;                                          -- ni employe ni employee_skill
   SELECT type, COUNT(*) FROM entite GROUP BY type;      -- 10 / 25 / 41 / 56
   SELECT COUNT(*) FROM manager;                         -- 38
   SELECT COUNT(*) FROM collaborateur WHERE id_manager IS NOT NULL;  -- 81
   ```

## 8. En cas de problème

| Symptôme | Cause probable |
|---|---|
| `403` avec `en_tete_manquant` | En-tête `X-Talent360` oublié. |
| `403` avec `hote_non_autorise` | URL avec un autre nom que `localhost` / `127.0.0.1`. |
| `400 requete_mal_formee` | Champ `fichier`, `annee` ou `numero` manquant. |
| `413` | Fichier de plus de 10 Mo. |
| `422` avec `Fichier illisible` | Ce n'est pas un `.xlsx` (ou le chemin après `@` est faux : curl envoie alors une erreur avant même l'appel). |
| `422` avec `Format du classeur non conforme` | Une feuille manque ou une colonne a été renommée ou déplacée : la liste est dans `erreurs`. Corriger le classeur (pas l'application). |
| `422` avec `autre periode` | Le classeur est daté d'un autre trimestre (nom du fichier ou titre d'une feuille) : vérifier `annee` / `numero` ou le fichier. |
| `erreurCalcul` renseigné | L'import est enregistré mais le calcul a échoué (réglages invalides, matrice 9-Box vide…) : corriger puis `POST /api/trimestres/{année}/{numéro}/calcul`. |
| Des collaborateurs dans `desactives` | Ils ne sont plus dans `01_COLLABORATEURS`. Voulu (départs) : rien à faire. Sinon, corriger le fichier et réimporter : ils redeviennent actifs. |
| `404` sur `/calcul` | Le trimestre n'existe pas : faire l'étape 1 ou 2 d'abord. |
| Calcul avec beaucoup d'`ignores` | Des notes manquent pour ces collaborateurs : voir les erreurs de l'import. |
| Page Import : « Seuls les classeurs Excel .xlsx sont acceptés » | Le fichier choisi n'est pas un `.xlsx` (un `.xls` ou `.csv` doit être réenregistré en `.xlsx` depuis Excel). |
| Page Import : « Fichier trop volumineux » | Plus de 10 Mo. Le classeur du jeu de données fait moins de 300 Ko : vérifier le fichier choisi. |
| Page Import : « Un calcul est déjà en cours » | Un import ou un calcul du même trimestre tourne déjà (double clic, autre onglet, appel API). Attendre, puis réessayer. |
| Page Import : « … ouvert par cet import mais ne contient aucune donnée » | L'import a échoué après avoir créé le trimestre (aucune ligne lisible) : le trimestre existe, vide. Les écrans continuent d'afficher le dernier trimestre calculé. Corriger le fichier et réimporter sur le même trimestre. |
| Page Import : page « Accès refusé » | La page est restée ouverte trop longtemps (session expirée) : se reconnecter et recharger /import. |
