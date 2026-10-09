package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Competence;
import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.CompetenceCollaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Manager;
import com.talent360bank.talent360bank.entity.Performance;
import com.talent360bank.talent360bank.entity.Poste;
import com.talent360bank.talent360bank.entity.Potentiel;
import com.talent360bank.talent360bank.entity.QuestionnaireEngagement;
import com.talent360bank.talent360bank.entity.RattachementVivier;
import com.talent360bank.talent360bank.entity.Sexe;
import com.talent360bank.talent360bank.entity.SourceEvaluation;
import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import com.talent360bank.talent360bank.entity.SuccesseurIdentifie;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.excel.Cellules;
import com.talent360bank.talent360bank.excel.Cellules.ValeurIllisibleException;
import com.talent360bank.talent360bank.repository.CompetenceRepository;
import com.talent360bank.talent360bank.repository.DeclarationVigilanceRepository;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.CompetenceCollaborateurRepository;
import com.talent360bank.talent360bank.repository.EntiteRepository;
import com.talent360bank.talent360bank.repository.ManagerRepository;
import com.talent360bank.talent360bank.repository.PerformanceRepository;
import com.talent360bank.talent360bank.repository.PosteRepository;
import com.talent360bank.talent360bank.repository.PotentielRepository;
import com.talent360bank.talent360bank.repository.QuestionnaireEngagementRepository;
import com.talent360bank.talent360bank.repository.RattachementVivierRepository;
import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.service.enums.SignalVigilance;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.enums.VivierThematique;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * Ecrit en base le contenu du classeur TALENT_360_BANK_Dataset, feuille par
 * feuille, dans une seule transaction.
 *
 * <p><strong>Erreurs.</strong> Une feuille absente, un en-tete introuvable ou
 * une ligne invalide sont rapportes dans {@link RapportImport} et ecartes ;
 * le reste est importe. Chaque ligne est entierement lue et validee (memes
 * contraintes Bean Validation que les entites) avant toute ecriture : une
 * ligne rejetee n'ecrit rien, et aucune violation ne peut surgir au flush.
 *
 * <p><strong>Campagne.</strong> Un import complet ({@link #importer}) decrit
 * toute la population : un collaborateur absent de 01_COLLABORATEURS passe
 * INACTIF (jamais supprime), et revient ACTIF s'il reparait. Il decrit aussi
 * tout le trimestre : dans 02, 03, 10 et 12, une ligne du trimestre dont le
 * collaborateur a quitte la feuille est supprimee, comme dans 09 (voir
 * {@link #retirerAbsents}).
 *
 * <p><strong>Re-import.</strong> Chaque ligne met a jour l'existant au lieu de
 * le doubler : collaborateurs, competences et postes par identifiant ; entites
 * de l'organigramme par code (leur chemin) ; managers par collaborateur ; competences
 * d'un collaborateur par (collaborateur, competence) ; notes, engagement, decision du
 * comite et faits de vigilance par (collaborateur, trimestre) ; rattachements par
 * direction. Rien n'est supprime, sauf les successeurs identifies retires de
 * 09_SUCCESSION (voir {@link #importerSuccession}). Les tables existantes
 * competence_collaborateur et questionnaire_engagement n'ont pas de contrainte d'unicite
 * en base : l'unicite y est garantie par ce code seul.
 *
 * <p><strong>Donnees de reference.</strong> Les feuilles suivantes retrouvent
 * collaborateurs, competences et postes en base, pas seulement dans le fichier : un
 * classeur partiel (notes seules, par exemple) s'importe sur une base deja
 * chargee.
 *
 * <p>Ne lance aucun calcul : c'est le role de CalculTrimestreService.
 */
@Service
public class ImportClasseurService {
    public static final String FEUILLE_COLLABORATEURS = "01_COLLABORATEURS";
    public static final String FEUILLE_PERFORMANCE = "02_PERFORMANCE";
    public static final String FEUILLE_POTENTIEL = "03_POTENTIEL";
    public static final String FEUILLE_COMPETENCES = "05_REFERENTIEL_COMPETENCES";
    public static final String FEUILLE_SKILLS = "06_EMPLOYEE_SKILLS";
    public static final String FEUILLE_POSTES = "07_POSTES";
    public static final String FEUILLE_POSTES_CRITIQUES = "08_POSTES_CRITIQUES";
    public static final String FEUILLE_SUCCESSION = "09_SUCCESSION";
    public static final String FEUILLE_TALENTS = "10_TALENTS";
    public static final String FEUILLE_VIGILANCE = "12_VIGILANCE";
    /** Date d'une decision du Comite saisie dans l'application, dans le bilan de l'import. */
    private static final java.time.format.DateTimeFormatter DATE_DECISION =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Libelle de la colonne A de la ligne d'en-tete, qui la situe dans la feuille. */
    private static final String ENTETE_COLLABORATEUR = "Employee_ID";
    private static final String ENTETE_COMPETENCE = "Competence_ID";
    private static final String ENTETE_POSTE = "Poste_ID";
    private static final String ENTETE_SKILL = "Cle (lookup)";
    /** Echelle des niveaux de competence (05_REFERENTIEL_COMPETENCES : 1 Debutant a 5 Referent). */
    public static final int NIVEAU_MIN = 1;
    public static final int NIVEAU_MAX = 5;
    /** Score d'engagement (12_VIGILANCE D, "Engagement (/100)"). */
    public static final BigDecimal ENGAGEMENT_MIN = BigDecimal.ZERO;
    public static final BigDecimal ENGAGEMENT_MAX = new BigDecimal("100");
    /** Colonnes F a K de 12_VIGILANCE, dans l'ordre. */
    private static final SignalVigilance[] COLONNES_VIGILANCE = {
            SignalVigilance.SANS_MOBILITE_4_ANS,
            SignalVigilance.MOBILITE_NON_TRAITEE,
            SignalVigilance.SANS_DEVELOPPEMENT_RECENT,
            SignalVigilance.BAISSE_PERFORMANCE,
            SignalVigilance.FAIBLE_RECONNAISSANCE,
            SignalVigilance.FORMATION_NON_FAITE};
    private static final int PREMIERE_COLONNE_VIGILANCE = 5;

    /** Colonnes des notes, pour nommer la cellule fautive dans les messages. */
    private static final Map<String, String> COLONNES_PERFORMANCE = Map.of(
            "noteObjectifs", "D", "noteCompetences", "E", "noteComportement", "F",
            "noteContribution", "G", "noteDeveloppement", "H");
    private static final Map<String, String> COLONNES_POTENTIEL = Map.of(
            "noteLearning", "D", "noteLeadership", "E", "noteAdaptabilite", "F",
            "noteComplexite", "G", "noteMobilite", "H", "noteStrategie", "I", "noteAutonomie", "J");
    private static final Map<String, String> COLONNES_COLLABORATEUR = Map.of(
            "idCollaborateur", "A", "nom", "B", "prenom", "C", "dateNaissance", "E", "dateEntree", "F",
            "fonction", "L");

    /** Colonnes H a K de 01_COLLABORATEURS, du niveau le plus large au plus fin. */
    private static final TypeEntite[] NIVEAUX_ORGANIGRAMME = TypeEntite.values();
    private static final int PREMIERE_COLONNE_ORGANIGRAMME = 7;

    /** Ligne ecartee, avec le motif rendu au RH. */
    static class LigneRejeteeException extends RuntimeException {
        LigneRejeteeException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    private interface TraitementLigne {
        void traiter(Row ligne);
    }

    private final Validator validator;
    private final CollaborateurRepository collaborateurRepository;
    private final CompetenceRepository competenceRepository;
    private final CompetenceCollaborateurRepository competenceCollaborateurRepository;
    private final PosteRepository posteRepository;
    private final PerformanceRepository performanceRepository;
    private final PotentielRepository potentielRepository;
    private final SuccesseurIdentifieRepository successeurRepository;
    private final ValidationComiteRepository validationComiteRepository;
    private final RattachementVivierRepository rattachementRepository;
    private final QuestionnaireEngagementRepository questionnaireRepository;
    private final DeclarationVigilanceRepository declarationRepository;
    private final EntiteRepository entiteRepository;
    private final ManagerRepository managerRepository;

    public ImportClasseurService(Validator validator,
                                 CollaborateurRepository collaborateurRepository,
                                 CompetenceRepository competenceRepository,
                                 CompetenceCollaborateurRepository competenceCollaborateurRepository,
                                 PosteRepository posteRepository,
                                 PerformanceRepository performanceRepository,
                                 PotentielRepository potentielRepository,
                                 SuccesseurIdentifieRepository successeurRepository,
                                 ValidationComiteRepository validationComiteRepository,
                                 RattachementVivierRepository rattachementRepository,
                                 QuestionnaireEngagementRepository questionnaireRepository,
                                 DeclarationVigilanceRepository declarationRepository,
                                 EntiteRepository entiteRepository,
                                 ManagerRepository managerRepository) {
        this.validator = validator;
        this.collaborateurRepository = collaborateurRepository;
        this.competenceRepository = competenceRepository;
        this.competenceCollaborateurRepository = competenceCollaborateurRepository;
        this.posteRepository = posteRepository;
        this.performanceRepository = performanceRepository;
        this.potentielRepository = potentielRepository;
        this.successeurRepository = successeurRepository;
        this.validationComiteRepository = validationComiteRepository;
        this.rattachementRepository = rattachementRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.declarationRepository = declarationRepository;
        this.entiteRepository = entiteRepository;
        this.managerRepository = managerRepository;
    }

    /**
     * Importe tout le classeur pour le trimestre, dans l'ordre des dependances :
     * collaborateurs, referentiels, postes, notes, puis les saisies RH qui
     * s'y rattachent.
     */
    @Transactional
    public RapportImport importer(Workbook classeur, Trimestre trimestre) {
        RapportImport rapport = new RapportImport();
        Map<String, Entite> entites = indexer(entiteRepository.findAll(), Entite::getCode);
        importerCollaborateurs(classeur, rapport, entites, true);
        Map<String, Collaborateur> collaborateurs = indexer(collaborateurRepository.findAll(),
                Collaborateur::getIdCollaborateur);
        importerCompetences(classeur, rapport);
        Map<String, Competence> competencesParNom = new HashMap<>();
        for (Competence competence : competenceRepository.findAll()) {
            competencesParNom.putIfAbsent(competence.getNom(), competence);
        }
        importerSkills(classeur, rapport, collaborateurs, competencesParNom);
        importerPostes(classeur, rapport, competencesParNom, entites);
        Map<String, Poste> postes = indexer(posteRepository.findAll(), Poste::getPosteId);
        completerPostesCritiques(classeur, rapport, postes);
        importerPerformances(classeur, rapport, collaborateurs, trimestre);
        importerPotentiels(classeur, rapport, collaborateurs, trimestre);
        importerSuccession(classeur, rapport, collaborateurs, postes);
        importerTalents(classeur, rapport, collaborateurs, trimestre);
        importerVigilance(classeur, rapport, collaborateurs, trimestre);
        // TODO(PlanDeveloppement) : 11_DEVELOPMENT_PLAN (Plan_ID, Employee_ID, gap,
        // poste vise, action, echeance, statut) n'est pas importe. A ajouter ici
        // avec l'entite PlanDeveloppement, apres les collaborateurs.
        return rapport;
    }

    // ------------------------------------------------------------------ 01

    /**
     * 01_COLLABORATEURS, en deux passes : les collaborateurs et leur entite
     * (colonnes H a K), puis leur manager (colonne N), qui peut apparaitre plus
     * bas dans la feuille.
     *
     * @param complet import d'une campagne : les collaborateurs absents de la
     *                feuille passent INACTIF
     */
    private void importerCollaborateurs(Workbook classeur, RapportImport rapport, Map<String, Entite> entites,
                                        boolean complet) {
        String feuille = FEUILLE_COLLABORATEURS;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_COLLABORATEUR, rapport);
        Map<String, String> managerParCollaborateur = new LinkedHashMap<>();
        Map<String, Row> ligneParCollaborateur = new HashMap<>();
        Map<String, StatutCollaborateur> statutsAvant = new HashMap<>();
        for (Collaborateur existant : collaborateurRepository.findAll()) {
            statutsAvant.put(existant.getIdCollaborateur(), existant.getStatut());
        }
        // Identifiants lus en colonne A, lignes rejetees comprises : une faute de
        // saisie ailleurs sur la ligne ne doit pas desactiver le collaborateur.
        Set<String> presents = new HashSet<>();
        for (Row ligne : lignes) {
            try {
                String id = Cellules.matricule(ligne, 0);
                if (id != null) {
                    presents.add(id);
                }
            } catch (ValeurIllisibleException e) {
                // rapportee par la ligne elle-meme
            }}
        pourChaqueLigne(feuille, lignes, rapport, ligne -> {
            Collaborateur collaborateur = new Collaborateur();
            collaborateur.setIdCollaborateur(Cellules.matricule(ligne, 0));
            collaborateur.setNom(Cellules.matricule(ligne, 1));
            collaborateur.setPrenom(Cellules.matricule(ligne, 2));
            collaborateur.setSexe(sexe(ligne));
            collaborateur.setDateNaissance(Cellules.date(ligne, 4));
            collaborateur.setDateEntree(Cellules.date(ligne, 5));
            collaborateur.setFonction(Cellules.matricule(ligne, 11));
            collaborateur.setGrade(Cellules.matricule(ligne, 12));
            collaborateur.setStatut(statutCollaborateur(ligne));
            valider(collaborateur, COLONNES_COLLABORATEUR);
            // Derniere etape : elle cree les entites manquantes, la ligne doit etre valide.
            collaborateur.setEntite(entite(entites, ligne, PREMIERE_COLONNE_ORGANIGRAMME, NIVEAUX_ORGANIGRAMME));

            // Le manager est pose a la seconde passe ; merge le remet a nul d'ici la.
            collaborateurRepository.save(collaborateur);
            StatutCollaborateur avant = statutsAvant.get(collaborateur.getIdCollaborateur());
            if (avant != null && avant != StatutCollaborateur.ACTIF
                    && collaborateur.getStatut() == StatutCollaborateur.ACTIF) {
                rapport.reactive(collaborateur.getIdCollaborateur());
            }
            managerParCollaborateur.put(collaborateur.getIdCollaborateur(), Cellules.matricule(ligne, 13));
            ligneParCollaborateur.put(collaborateur.getIdCollaborateur(), ligne);
        });
        Map<String, Collaborateur> collaborateurs = indexer(collaborateurRepository.findAll(),
                Collaborateur::getIdCollaborateur);
        // Une feuille sans aucune ligne ne vide pas la population : c'est un
        // fichier incomplet, pas le depart de tout le monde.
        if (complet && !presents.isEmpty()) {
            collaborateurs.values().stream()
                    .filter(collaborateur -> !presents.contains(collaborateur.getIdCollaborateur()))
                    .filter(collaborateur -> collaborateur.getStatut() == StatutCollaborateur.ACTIF)
                    .sorted(java.util.Comparator.comparing(Collaborateur::getIdCollaborateur))
                    .forEach(collaborateur -> {
                        collaborateur.setStatut(StatutCollaborateur.INACTIF);
                        rapport.desactive(collaborateur.getIdCollaborateur());
                    });}
        if (managerParCollaborateur.isEmpty()) {
            return;
        }
        Map<String, Manager> managers = indexer(managerRepository.findAll(), Manager::getIdCollaborateur);
        managerParCollaborateur.forEach((idCollaborateur, managerId) -> {
            if (managerId == null) {
                return;
            }
            Row ligne = ligneParCollaborateur.get(idCollaborateur);
            if (!collaborateurs.containsKey(managerId)) {
                rapport.erreur(feuille, ligne, "colonne N : manager " + managerId
                        + " inconnu, collaborateur " + idCollaborateur + " importé sans manager");
            } else if (managerId.equals(idCollaborateur)) {
                rapport.erreur(feuille, ligne, "colonne N : le collaborateur " + idCollaborateur
                        + " ne peut pas être son propre manager, importé sans manager");
            } else {
                // Un Manager par Manager_ID distinct, reutilise d'un import a l'autre.
                Manager manager = managers.computeIfAbsent(managerId,
                        id -> managerRepository.save(new Manager(collaborateurs.get(id))));
                collaborateurs.get(idCollaborateur).setManager(manager);
            }});}
    private static Sexe sexe(Row ligne) {
        String libelle = Cellules.matricule(ligne, 3);
        Sexe sexe = Sexe.depuisLibelle(libelle);
        if (libelle != null && sexe == null) {
            throw new LigneRejeteeException("colonne D : sexe \"" + libelle + "\" inconnu (M ou F attendu)");
        }
        return sexe;}
    /** Actif, Inactif ou Archive ; vide vaut Actif. */
    private static StatutCollaborateur statutCollaborateur(Row ligne) {
        String libelle = Cellules.matricule(ligne, 14);
        if (libelle == null) {
            return StatutCollaborateur.ACTIF;}
        String valeur = sansAccents(libelle);
        for (StatutCollaborateur statut : StatutCollaborateur.values()) {
            if (statut.name().equalsIgnoreCase(valeur)) {
                return statut;}}
        throw new LigneRejeteeException("colonne O : statut \"" + libelle
                + "\" inconnu (Actif, Inactif ou Archive attendu)");}
    // ------------------------------------------------------------------ 05
    private void importerCompetences(Workbook classeur, RapportImport rapport) {
        String feuille = FEUILLE_COMPETENCES;
        pourChaqueLigne(feuille, lignes(classeur, feuille, ENTETE_COMPETENCE, rapport), rapport, ligne -> {
            Competence competence = new Competence();
            competence.setCompetenceId(Cellules.matricule(ligne, 0));
            competence.setNom(Cellules.matricule(ligne, 1));
            competence.setCategorie(Cellules.matricule(ligne, 2));
            valider(competence, Map.of("competenceId", "A", "nom", "B"));
            competenceRepository.save(competence);
        });}
    // ------------------------------------------------------------------ 06
    private void importerSkills(Workbook classeur, RapportImport rapport, Map<String, Collaborateur> collaborateurs,
                                Map<String, Competence> competencesParNom) {
        String feuille = FEUILLE_SKILLS;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_SKILL, rapport);
        if (lignes.isEmpty()) {
            return;}
        Map<String, CompetenceCollaborateur> existants = new HashMap<>();
        for (CompetenceCollaborateur skill
                : competenceCollaborateurRepository.findByCollaborateurIdsAvecCompetence(collaborateurs.keySet())) {
            existants.put(cle(skill.getCollaborateur().getIdCollaborateur(), skill.getCompetence().getCompetenceId()),
                    skill);}
        pourChaqueLigne(feuille, lignes, rapport, ligne -> {
            Collaborateur collaborateur = collaborateur(collaborateurs, ligne, 1, "B");
            String nomCompetence = Cellules.matricule(ligne, 2);
            Competence competence = nomCompetence == null ? null : competencesParNom.get(nomCompetence);
            if (competence == null) {
                throw new LigneRejeteeException(nomCompetence == null
                        ? "colonne C : compétence absente"
                        : "colonne C : compétence \"" + nomCompetence + "\" absente du référentiel");
            }
            Integer actuel = niveauObligatoire(ligne, 4);
            Integer cible = niveauObligatoire(ligne, 5);
            Integer gap = Cellules.entier(ligne, 6);
            String cle = cle(collaborateur.getIdCollaborateur(), competence.getCompetenceId());
            CompetenceCollaborateur skill = existants.get(cle);
            if (skill == null) {
                skill = new CompetenceCollaborateur();
                skill.setCollaborateur(collaborateur);
                skill.setCompetence(competence);}
            skill.setCleLookup(Cellules.matricule(ligne, 0));
            skill.setNiveauActuel(actuel);
            skill.setNiveauCible(cible);
            skill.setGap(gap != null ? gap : cible - actuel);
            skill.setStatutGap(Cellules.matricule(ligne, 7));
            existants.put(cle, competenceCollaborateurRepository.save(skill));
        });}
    // ------------------------------------------------------------------ 07 / 08
    private void importerPostes(Workbook classeur, RapportImport rapport,
                                Map<String, Competence> competencesParNom, Map<String, Entite> entites) {
        String feuille = FEUILLE_POSTES;
        pourChaqueLigne(feuille, lignes(classeur, feuille, ENTETE_POSTE, rapport), rapport, ligne -> {
            String posteId = Cellules.matricule(ligne, 0);
            String nomPoste = Cellules.matricule(ligne, 1);
            if (nomPoste == null) {
                throw new LigneRejeteeException("colonne B : nom du poste absent");
            }
            // Cinq couples competence / niveau, colonnes F-G a N-O.
            Competence[] competences = new Competence[5];
            Integer[] niveaux = new Integer[5];
            for (int i = 0; i < 5; i++) {
                int colonne = 5 + 2 * i;
                String nom = Cellules.matricule(ligne, colonne);
                if (nom != null) {
                    competences[i] = competencesParNom.get(nom);
                    if (competences[i] == null) {
                        throw new LigneRejeteeException("colonne " + Cellules.lettre(colonne)
                                + " : compétence \"" + nom + "\" absente du référentiel");
                    }}
                niveaux[i] = niveau(ligne, colonne + 1);}
            Entite direction = entite(entites, ligne, 2, TypeEntite.DIRECTION);
            // Mise a jour en place : titulaire (08) et champs hors classeur conserves.
            Poste poste = posteRepository.findById(posteId).orElseGet(() -> {
                Poste nouveau = new Poste();
                nouveau.setPosteId(posteId);
                return nouveau;
            });
            poste.setNomPoste(nomPoste);
            poste.setEntite(direction);
            poste.setGradeCible(Cellules.matricule(ligne, 3));
            poste.setCriticite(Cellules.matricule(ligne, 4));
            poste.setCompetenceRequise1(competences[0]);
            poste.setNiveau1(niveaux[0]);
            poste.setCompetenceRequise2(competences[1]);
            poste.setNiveau2(niveaux[1]);
            poste.setCompetenceRequise3(competences[2]);
            poste.setNiveau3(niveaux[2]);
            poste.setCompetenceRequise4(competences[3]);
            poste.setNiveau4(niveaux[3]);
            poste.setCompetenceRequise5(competences[4]);
            poste.setNiveau5(niveaux[4]);
            poste.setPosteCritique(Cellules.matricule(ligne, 15));
            posteRepository.save(poste);});}
    /** 08_POSTES_CRITIQUES : titulaire des postes deja connus (colonnes E et F). */
    private void completerPostesCritiques(Workbook classeur, RapportImport rapport, Map<String, Poste> postes) {
        String feuille = FEUILLE_POSTES_CRITIQUES;
        pourChaqueLigne(feuille, lignes(classeur, feuille, ENTETE_POSTE, rapport), rapport, ligne -> {
            Poste poste = poste(postes, ligne, 0, "A");
            poste.setTitulaireId(Cellules.matricule(ligne, 4));
            poste.setTitulaireNom(Cellules.matricule(ligne, 5));
        });}
    // ------------------------------------------------------------------ 02 / 03
    private void importerPerformances(Workbook classeur, RapportImport rapport,
                                      Map<String, Collaborateur> collaborateurs, Trimestre trimestre) {
        String feuille = FEUILLE_PERFORMANCE;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_COLLABORATEUR, rapport);
        if (lignes.isEmpty()) {
            return;}
        Map<String, Performance> existantes = indexer(
                performanceRepository.findByTrimestreAvecCollaborateur(trimestre, SourceEvaluation.MANAGER),
                performance -> performance.getCollaborateur().getIdCollaborateur());
        Set<String> presents = new HashSet<>();
        int rejetees = pourChaqueLigne(feuille, lignes, rapport, ligne -> {
            Collaborateur collaborateur = collaborateur(collaborateurs, ligne, 0, "A");
            presents.add(collaborateur.getIdCollaborateur());
            Performance notes = new Performance(collaborateur, trimestre,
                    Cellules.nombre(ligne, 3), Cellules.nombre(ligne, 4), Cellules.nombre(ligne, 5),
                    Cellules.nombre(ligne, 6), Cellules.nombre(ligne, 7));
            notes.setSource(SourceEvaluation.MANAGER);
            valider(notes, COLONNES_PERFORMANCE);
            // 02_PERFORMANCE ne nomme pas l'evaluateur : c'est le manager du collaborateur.
            notes.setEvaluateur(collaborateur.getManager());
            Performance performance = existantes.get(collaborateur.getIdCollaborateur());
            if (performance == null) {
                existantes.put(collaborateur.getIdCollaborateur(), performanceRepository.save(notes));
            } else {
                performance.setEvaluateur(notes.getEvaluateur());
                performance.setNoteObjectifs(notes.getNoteObjectifs());
                performance.setNoteCompetences(notes.getNoteCompetences());
                performance.setNoteComportement(notes.getNoteComportement());
                performance.setNoteContribution(notes.getNoteContribution());
                performance.setNoteDeveloppement(notes.getNoteDeveloppement());
            }});
        retirerAbsents(feuille, rapport, rejetees, existantes, presents, performanceRepository);}
    private void importerPotentiels(Workbook classeur, RapportImport rapport, Map<String, Collaborateur> collaborateurs,
                                    Trimestre trimestre) {
        String feuille = FEUILLE_POTENTIEL;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_COLLABORATEUR, rapport);
        if (lignes.isEmpty()) {
            return;}
        Map<String, Potentiel> existants = indexer(
                potentielRepository.findByTrimestreAvecCollaborateur(trimestre, SourceEvaluation.MANAGER),
                potentiel -> potentiel.getCollaborateur().getIdCollaborateur());
        Set<String> presents = new HashSet<>();
        int rejetees = pourChaqueLigne(feuille, lignes, rapport, ligne -> {
            Collaborateur collaborateur = collaborateur(collaborateurs, ligne, 0, "A");
            presents.add(collaborateur.getIdCollaborateur());
            Potentiel notes = new Potentiel(collaborateur, trimestre,
                    Cellules.nombre(ligne, 3), Cellules.nombre(ligne, 4), Cellules.nombre(ligne, 5),
                    Cellules.nombre(ligne, 6), Cellules.nombre(ligne, 7), Cellules.nombre(ligne, 8),
                    Cellules.nombre(ligne, 9));
            notes.setSource(SourceEvaluation.MANAGER);
            valider(notes, COLONNES_POTENTIEL);
            Potentiel potentiel = existants.get(collaborateur.getIdCollaborateur());
            if (potentiel == null) {
                existants.put(collaborateur.getIdCollaborateur(), potentielRepository.save(notes));
            } else {
                potentiel.setNoteLearning(notes.getNoteLearning());
                potentiel.setNoteLeadership(notes.getNoteLeadership());
                potentiel.setNoteAdaptabilite(notes.getNoteAdaptabilite());
                potentiel.setNoteComplexite(notes.getNoteComplexite());
                potentiel.setNoteMobilite(notes.getNoteMobilite());
                potentiel.setNoteStrategie(notes.getNoteStrategie());
                potentiel.setNoteAutonomie(notes.getNoteAutonomie());
            }});
        retirerAbsents(feuille, rapport, rejetees, existants, presents, potentielRepository);}
    // ------------------------------------------------------------------ 09
    /**
     * 09_SUCCESSION est le plan de succession complet : un successeur deja en
     * base mais absent de la feuille est retire (un poste sans successeur,
     * comme PST13, doit repasser en alerte). Par prudence, ce retrait n'a lieu
     * que si toutes les lignes de la feuille sont valides : une ligne ecartee
     * ne doit pas effacer le successeur qu'elle designait.
     */
    private void importerSuccession(Workbook classeur, RapportImport rapport, Map<String, Collaborateur> collaborateurs,
                                    Map<String, Poste> postes) {
        String feuille = FEUILLE_SUCCESSION;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_POSTE, rapport);
        // Feuille absente, sans en-tete ou vide : rien a retirer, rien a ajouter.
        if (lignes.isEmpty()) {
            return;
        }
        Map<String, SuccesseurIdentifie> existants = new HashMap<>();
        for (SuccesseurIdentifie successeur : successeurRepository.findAllAvecPosteEtCollaborateur()) {
            existants.put(cle(successeur.getPoste().getPosteId(), successeur.getCollaborateur().getIdCollaborateur()),
                    successeur);
        }
        Set<String> retenus = new LinkedHashSet<>();
        int rejetees = 0;
        for (Row ligne : lignes) {
            try {
                Poste poste = poste(postes, ligne, 0, "A");
                Collaborateur collaborateur = collaborateur(collaborateurs, ligne, 1, "B");
                String cle = cle(poste.getPosteId(), collaborateur.getIdCollaborateur());
                if (retenus.add(cle) && !existants.containsKey(cle)) {
                    successeurRepository.save(new SuccesseurIdentifie(poste, collaborateur));
                 }
                rapport.ligneImportee(feuille);
            } catch (LigneRejeteeException | ValeurIllisibleException e) {
                rapport.erreur(feuille, ligne, e.getMessage());
                rejetees++;}}
        List<SuccesseurIdentifie> retires = existants.entrySet().stream()
                .filter(entree -> !retenus.contains(entree.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (retires.isEmpty()) {
            return;
        }
        if (rejetees > 0) {
            rapport.erreurFeuille(feuille, retires.size() + " successeur(s) absent(s) de la feuille conservé(s) "
                    + "en base : corriger les lignes écartées puis réimporter pour les retirer");
        } else {
            successeurRepository.deleteAll(retires);
            rapport.lignesRetirees(feuille, retires.size());}}
    // ------------------------------------------------------------------ 10
    /**
     * 10_TALENTS : decision du Comite Talent (G) et vivier thematique de la
     * direction (C, I). Une cellule vide ne change rien ; une direction
     * rattachee a deux viviers differents dans la feuille est signalee, le
     * premier rattachement est garde.
     */
    private void importerTalents(Workbook classeur, RapportImport rapport, Map<String, Collaborateur> collaborateurs,
                                 Trimestre trimestre) {
        String feuille = FEUILLE_TALENTS;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_COLLABORATEUR, rapport);
        if (lignes.isEmpty()) {
            return;
        }
        Map<String, ValidationComite> validations = indexer(
                validationComiteRepository.findByTrimestreAvecCollaborateur(trimestre),
                validation -> validation.getCollaborateur().getIdCollaborateur());
        // Seuls les rattachements dont la direction existe : une ligne cassee (entite_id 0) est ignoree.
        Map<String, RattachementVivier> rattachements = indexer(rattachementRepository.findAllAvecDirection(),
                rattachement -> rattachement.getDirection().getLibelle());
        Map<String, VivierThematique> vusDansLaFeuille = new HashMap<>();
        Set<String> presents = new HashSet<>();
        int rejetees = pourChaqueLigne(feuille, lignes, rapport, ligne -> {
            Collaborateur collaborateur = collaborateur(collaborateurs, ligne, 0, "A");
            presents.add(collaborateur.getIdCollaborateur());
            StatutValidationComite statut = statutComite(ligne);
            String direction = Cellules.matricule(ligne, 2);
            VivierThematique vivier = vivierThematique(ligne);
            if (vivier != null && direction == null) {
                throw new LigneRejeteeException("colonne C : direction absente, vivier "
                        + vivier.getLibelle() + " non rattaché");
            }
            VivierThematique dejaVu = vivier == null ? null : vusDansLaFeuille.putIfAbsent(direction, vivier);
            if (dejaVu != null && dejaVu != vivier) {
                throw new LigneRejeteeException("colonne I : la direction " + direction + " est déjà rattachée au "
                        + dejaVu.getLibelle() + " plus haut dans la feuille, " + vivier.getLibelle() + " ignore");}
            if (statut != null) {
                ValidationComite validation = validations.get(collaborateur.getIdCollaborateur());
                if (validation == null) {
                    validations.put(collaborateur.getIdCollaborateur(),
                            validationComiteRepository.save(new ValidationComite(collaborateur, trimestre, statut)));
                } else if (validation.estSaisieApplication()) {
                    // Decision prise dans l'application : le classeur ne la remplace jamais.
                    if (validation.getStatut() != statut) {
                        rapport.decisionConservee(feuille, ligne, collaborateur.getIdCollaborateur()
                                + " : le classeur indique « " + statut.getLibelle() + " », la décision saisie dans "
                                + "l'application est conservée (« " + validation.getStatut().getLibelle() + " », le "
                                + DATE_DECISION.format(validation.getDateDecision()) + ").");
                    }
                } else {
                    validation.setStatut(statut);}}
            if (vivier != null) {
                RattachementVivier rattachement = rattachements.get(direction);
                if (rattachement == null) {
                    // 1. Generation du code normalise pour la direction
                    String codeDirection = Entite.code(null, TypeEntite.DIRECTION, direction);

                    // 2. Recherche par code ou creation si absente
                    Entite entiteObjet = entiteRepository.findByCode(codeDirection)
                            .orElseGet(() -> entiteRepository.save(new Entite(direction, TypeEntite.DIRECTION, null)));

                    // 3. Sauvegarde du rattachement avec l'objet Entite
                    rattachements.put(direction, rattachementRepository.save(new RattachementVivier(entiteObjet, vivier)));
                } else {
                    rattachement.setVivier(vivier);
                }}
        });
        // Une decision saisie dans l'application n'est jamais retiree, meme si la feuille ne la porte plus.
        Map<String, ValidationComite> importees = new HashMap<>();
        validations.forEach((matricule, validation) -> {
            if (!validation.estSaisieApplication()) {
                importees.put(matricule, validation);
            } else if (!presents.contains(matricule)) {
                rapport.decisionConservee(feuille, null, matricule + " : absent de la feuille, la décision saisie "
                        + "dans l'application est conservée (« " + validation.getStatut().getLibelle() + " », le "
                        + DATE_DECISION.format(validation.getDateDecision()) + ").");
            }
        });
        retirerAbsents(feuille, rapport, rejetees, importees, presents, validationComiteRepository);}
    private static StatutValidationComite statutComite(Row ligne) {
        String libelle = Cellules.matricule(ligne, 6);
        if (libelle == null) {
            return null;
        }
        String valeur = sansAccents(libelle);
        for (StatutValidationComite statut : StatutValidationComite.values()) {
            if (statut.getLibelle().equalsIgnoreCase(valeur) || statut.name().equalsIgnoreCase(valeur)) {
                return statut;
            }}
        throw new LigneRejeteeException("colonne G : decision \"" + libelle
                + "\" inconnue (Oui, Non ou En attente attendu)");}
    private static VivierThematique vivierThematique(Row ligne) {
        String libelle = Cellules.matricule(ligne, 8);
        if (libelle == null) {
            return null;
        }
        for (VivierThematique vivier : VivierThematique.values()) {
            if (vivier.getLibelle().equalsIgnoreCase(libelle) || vivier.getCode().equalsIgnoreCase(libelle)) {
                return vivier;}}
        throw new LigneRejeteeException("colonne I : vivier \"" + libelle + "\" inconnu");}
    // ------------------------------------------------------------------ 12
    /**
     * 12_VIGILANCE : score d'engagement (D) en QuestionnaireEngagement, faits
     * F a K en DeclarationVigilance. Les colonnes E, L et M sont calculees par
     * le moteur, pas importees.
     */
    private void importerVigilance(Workbook classeur, RapportImport rapport, Map<String, Collaborateur> collaborateurs,
                                   Trimestre trimestre) {
        String feuille = FEUILLE_VIGILANCE;
        List<Row> lignes = lignes(classeur, feuille, ENTETE_COLLABORATEUR, rapport);
        if (lignes.isEmpty()) {
            return;}
        Map<String, QuestionnaireEngagement> questionnaires = indexer(
                questionnaireRepository.findByTrimestreAvecCollaborateur(trimestre),
                questionnaire -> questionnaire.getCollaborateur().getIdCollaborateur());
        Map<String, DeclarationVigilance> declarations = indexer(
                declarationRepository.findByTrimestreAvecCollaborateur(trimestre),
                declaration -> declaration.getCollaborateur().getIdCollaborateur());
        Set<String> presents = new HashSet<>();
        int rejetees = pourChaqueLigne(feuille, lignes, rapport, ligne -> {
            Collaborateur collaborateur = collaborateur(collaborateurs, ligne, 0, "A");
            presents.add(collaborateur.getIdCollaborateur());
            BigDecimal engagement = Cellules.nombre(ligne, 3);
            if (engagement != null
                    && (engagement.compareTo(ENGAGEMENT_MIN) < 0 || engagement.compareTo(ENGAGEMENT_MAX) > 0)) {
                throw new LigneRejeteeException("colonne D : engagement " + engagement.toPlainString()
                        + " hors de l'echelle 0 a 100");
            }
            Boolean[] drapeaux = new Boolean[COLONNES_VIGILANCE.length];
            for (int i = 0; i < drapeaux.length; i++) {
                drapeaux[i] = Cellules.ouiNon(ligne, PREMIERE_COLONNE_VIGILANCE + i);}
            String idCollaborateur = collaborateur.getIdCollaborateur();
            if (engagement != null) {
                QuestionnaireEngagement questionnaire = questionnaires.get(idCollaborateur);
                if (questionnaire == null) {
                    questionnaire = new QuestionnaireEngagement();
                    questionnaire.setCollaborateur(collaborateur);
                    questionnaire.setTrimestre(trimestre);
                }
                questionnaire.setScoreEngagement(engagement);
                questionnaires.put(idCollaborateur, questionnaireRepository.save(questionnaire));}
            DeclarationVigilance declaration = declarations.get(idCollaborateur);
            if (declaration == null) {
                declaration = new DeclarationVigilance(collaborateur, trimestre);
            }
            declaration.setSansMobilite4Ans(drapeaux[0]);
            declaration.setMobiliteNonTraitee(drapeaux[1]);
            declaration.setSansDeveloppementRecent(drapeaux[2]);
            declaration.setBaissePerformance(drapeaux[3]);
            declaration.setFaibleReconnaissance(drapeaux[4]);
            declaration.setFormationNonFaite(drapeaux[5]);
            declarations.put(idCollaborateur, declarationRepository.save(declaration));
        });
        retirerAbsents(feuille, rapport, rejetees, questionnaires, presents, questionnaireRepository);
        retirerAbsents(feuille, rapport, rejetees, declarations, presents, declarationRepository);}
    // ------------------------------------------------------------------ outils

    /**
     * Lignes de donnees de la feuille, ou liste vide si la feuille ou son
     * en-tete manque (signale dans le rapport).
     */
    private static List<Row> lignes(Workbook classeur, String nomFeuille, String enteteColonneA,
                                    RapportImport rapport) {
        Sheet feuille = classeur.getSheet(nomFeuille);
        if (feuille == null) {
            rapport.feuilleAbsente(nomFeuille, "Feuille absente du classeur");
            return List.of();
        }
        int entete = Cellules.ligneEntete(feuille, enteteColonneA);
        if (entete < 0) {
            rapport.feuilleAbsente(nomFeuille, "En-tête introuvable : la colonne A de la ligne d'en-tête doit "
                    + "porter \"" + enteteColonneA + "\"");
            return List.of();
        }
        rapport.feuilleLue(nomFeuille);
        return Cellules.lignesDonnees(feuille, entete);}
    /**
     * Traite chaque ligne ; une ligne rejetee est rapportee et n'interrompt pas
     * les suivantes.
     *
     * @return nombre de lignes rejetees
     */
    private static int pourChaqueLigne(String feuille, List<Row> lignes, RapportImport rapport,
                                       TraitementLigne traitement) {
        int rejetees = 0;
        for (Row ligne : lignes) {
            try {
                traitement.traiter(ligne);
                rapport.ligneImportee(feuille);
            } catch (LigneRejeteeException | ValeurIllisibleException e) {
                rapport.erreur(feuille, ligne, e.getMessage());
                rejetees++;}}
        return rejetees;}
    /**
     * Fichier corrige : supprime les lignes du trimestre dont le collaborateur
     * n'est plus dans la feuille, pour que le trimestre reflete le dernier
     * fichier et non l'union des fichiers successifs. Comme pour 09, rien
     * n'est retire si une ligne de la feuille a ete ecartee : elle designait
     * peut-etre justement un de ces collaborateurs.
     *
     * @param existants lignes du trimestre (avant import et creees), par collaborateur
     */
    private static <T> void retirerAbsents(String feuille, RapportImport rapport, int rejetees,
                                           Map<String, T> existants, Set<String> presents,
                                           JpaRepository<T, ?> repository) {
        List<T> retires = existants.entrySet().stream()
                .filter(entree -> !presents.contains(entree.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (retires.isEmpty()) {
            return;
        }
        if (rejetees > 0) {
            rapport.erreurFeuille(feuille, retires.size() + " ligne(s) du trimestre absente(s) de la feuille "
                    + "conservée(s) : corriger les lignes écartées puis réimporter pour les retirer");
            return;
        }
        repository.deleteAll(retires);
        rapport.lignesRetirees(feuille, retires.size());
    }
    /** Contraintes Bean Validation de l'entite ; les violations rejettent la ligne. */
    private void valider(Object entite, Map<String, String> colonnes) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(entite);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .map(violation -> {
                    String champ = violation.getPropertyPath().toString();
                    String colonne = colonnes.get(champ);
                    return (colonne == null ? champ : "colonne " + colonne + " (" + champ + ")")
                            + " : " + violation.getMessage();
                })
                .sorted()
                .collect(Collectors.joining(" ; "));
        throw new LigneRejeteeException(message);}
    private static Collaborateur collaborateur(Map<String, Collaborateur> collaborateurs, Row ligne, int colonne, String lettre) {
        String idCollaborateur = Cellules.matricule(ligne, colonne); // ✅ Nouveau
        if (idCollaborateur == null) {
            throw new LigneRejeteeException("colonne " + lettre + " : Employee_ID absent");
        }
        Collaborateur collaborateur = collaborateurs.get(idCollaborateur);
        if (collaborateur == null) {
            throw new LigneRejeteeException("colonne " + lettre + " : collaborateur " + idCollaborateur
                    + " inconnu (absent de 01_COLLABORATEURS et de la base)");
        }
        return collaborateur;
    }
    /**
     * Entite la plus fine designee par des cellules consecutives, une par
     * niveau, creee avec ses ancetres si elle n'existe pas encore. Une cellule
     * vide saute son niveau : l'entite suivante se rattache au dernier niveau
     * renseigne. Null si toutes les cellules sont vides.
     *
     * <p>Les libelles sont tous controles avant la premiere creation : une
     * ligne rejetee ne laisse aucune entite derriere elle.
     */
    private Entite entite(Map<String, Entite> entites, Row ligne, int premiereColonne, TypeEntite... niveaux) {
        String[] libelles = new String[niveaux.length];
        for (int i = 0; i < niveaux.length; i++) {
            libelles[i] = Cellules.matricule(ligne, premiereColonne + i);
            if (libelles[i] != null && libelles[i].length() > Entite.LONGUEUR_LIBELLE) {
                throw new LigneRejeteeException("colonne " + Cellules.lettre(premiereColonne + i) + " : "
                        + niveaux[i].name().toLowerCase(Locale.ROOT) + " de plus de " + Entite.LONGUEUR_LIBELLE
                        + " caracteres");
            }}
        Entite courante = null;
        for (int i = 0; i < niveaux.length; i++) {
            if (libelles[i] == null) {
                continue;
            }
            Entite parent = courante;
            TypeEntite niveau = niveaux[i];
            String libelle = libelles[i];
            courante = entites.computeIfAbsent(Entite.code(parent, niveau, libelle),
                    code -> entiteRepository.save(new Entite(libelle, niveau, parent)));
        }
        return courante;
    }
    private static Poste poste(Map<String, Poste> postes, Row ligne, int colonne, String lettre) {
        String posteId = Cellules.matricule(ligne, colonne);
        Poste poste = posteId == null ? null : postes.get(posteId);
        if (poste == null) {
            throw new LigneRejeteeException(posteId == null
                    ? "colonne " + lettre + " : Poste_ID absent"
                    : "colonne " + lettre + " : poste " + posteId + " inconnu (absent de 07_POSTES et de la base)");
        }
        return poste;
    }
    /** Niveau de competence facultatif, dans l'echelle 1 a 5. */
    private static Integer niveau(Row ligne, int colonne) {
        Integer niveau = Cellules.entier(ligne, colonne);
        if (niveau != null && (niveau < NIVEAU_MIN || niveau > NIVEAU_MAX)) {
            throw new LigneRejeteeException("colonne " + Cellules.lettre(colonne) + " : niveau " + niveau
                    + " hors de l'echelle " + NIVEAU_MIN + " a " + NIVEAU_MAX);
        }
        return niveau;
    }
    private static Integer niveauObligatoire(Row ligne, int colonne) {
        Integer niveau = niveau(ligne, colonne);
        if (niveau == null) {
            throw new LigneRejeteeException("colonne " + Cellules.lettre(colonne) + " : niveau absent");
        }
        return niveau;
    }
    private static String cle(String premier, String second) {
        return premier + "|" + second;
    }
    private static <T> Map<String, T> indexer(List<T> elements, Function<T, String> cle) {
        Map<String, T> index = new HashMap<>();
        for (T element : elements) {
            index.putIfAbsent(cle.apply(element), element);
        }
        return index;
    }
    /** "Archivé" -> "ARCHIVE", "En attente" -> "EN ATTENTE" : comparaison tolerante aux accents. */
    private static String sansAccents(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }
}
