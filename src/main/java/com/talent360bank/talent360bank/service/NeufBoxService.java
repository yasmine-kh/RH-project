package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.CategoriePotentiel;
import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.NiveauGrille;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.SeuilsNeufBox;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatRecalcul;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Place les collaborateurs sur la matrice 9-box a partir de leurs scores et des
 * seuils du {@link Parametre} du trimestre, propres a chaque axe
 * (seuilsNeufBox pour la performance, seuilsNeufBoxPotentiel pour le
 * potentiel). Le niveau de l'axe potentiel est aussi la categorie de
 * potentiel de le collaborateur, enregistree avec sa case.
 *
 * <p>Les neuf libelles ne sont pas ecrits ici : ils viennent de la table de
 * reference {@link Matrice9Box}, que le RH peut renommer sans toucher au code.
 */
@Service
public class NeufBoxService {

    private static final Logger log = LoggerFactory.getLogger(NeufBoxService.class);

    private final Matrice9BoxRepository matriceRepository;
    private final ScoreRepository scoreRepository;
    private final CalculService calculService;

    public NeufBoxService(Matrice9BoxRepository matriceRepository,
                          ScoreRepository scoreRepository,
                          CalculService calculService) {
        this.matriceRepository = matriceRepository;
        this.scoreRepository = scoreRepository;
        this.calculService = calculService;
    }

    /**
     * Niveau d'un axe pour un score donne. Les bornes sont inclusives :
     * un score egal au seuil eleve compte comme eleve.
     *
     * <p>Le score est compare tel que CalculService l'a arrondi : c'est cet
     * arrondi a deux decimales qui tranche les cas limites.
     */
    public NiveauGrille niveauPour(BigDecimal score, SeuilsNeufBox seuils) {
        Objects.requireNonNull(seuils, "seuils");
        if (score == null) {
            throw new DonneesIncompletesException("Score absent, niveau indeterminable");
        }
        if (seuils.getSeuilEleve() == null || seuils.getSeuilMoyen() == null) {
            throw new DonneesIncompletesException("Les seuils de la matrice 9-box ne sont pas configures");
        }
        if (score.compareTo(seuils.getSeuilEleve()) >= 0) {
            return NiveauGrille.ELEVE;
        }
        if (score.compareTo(seuils.getSeuilMoyen()) >= 0) {
            return NiveauGrille.MOYEN;
        }
        return NiveauGrille.FAIBLE;
    }

    /**
     * Numero de la case, de 1 a 9, comme la colonne H de 04_9BOX :
     * (niveau de performance - 1) x 3 + niveau de potentiel, niveaux de 1
     * (faible) a 3 (eleve). 9 = performance et potentiel eleves, 1 = les deux faibles.
     */
    public static int numeroCase(Matrice9Box case9Box) {
        Objects.requireNonNull(case9Box, "case9Box");
        return numeroCase(NiveauGrille.depuisRang(case9Box.getNiveauPerformance()),
                NiveauGrille.depuisRang(case9Box.getNiveauPotentiel()));
    }

    /** Numero de la case (1 a 9) pour une ligne et une colonne de la matrice, comme 04_9BOX!H. */
    public static int numeroCase(NiveauGrille niveauPerformance, NiveauGrille niveauPotentiel) {
        Objects.requireNonNull(niveauPerformance, "niveauPerformance");
        Objects.requireNonNull(niveauPotentiel, "niveauPotentiel");
        return (niveauPerformance.getRang() - 1) * 3 + niveauPotentiel.getRang();
    }

    /**
     * Ligne et colonne de la case d'un score enregistre, par la meme regle que le
     * placement ({@link #placerEtEnregistrer}) : colonne = categorie de potentiel
     * enregistree (le niveau de l'axe potentiel), ligne = niveau du score de
     * performance sur l'axe performance des reglages du trimestre. La
     * categorie de performance (5 niveaux, ses propres seuils) ne donne pas la
     * ligne. Ne depend pas du libelle de la case : renommer une case ne change
     * rien.
     *
     * @return {niveau performance, niveau potentiel}
     * @throws DonneesIncompletesException si un score ou les seuils manquent
     */
    public NiveauGrille[] niveauxDe(Score score, Parametre parametre) {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(parametre, "parametre");
        NiveauGrille performance = niveauPour(score.getScorePerformance(), seuilsPerformance(parametre));
        NiveauGrille potentiel = score.getCategoriePotentiel() != null
                ? NiveauGrille.valueOf(score.getCategoriePotentiel().name())
                : niveauPour(score.getScorePotentiel(), seuilsPotentiel(parametre));
        return new NiveauGrille[]{performance, potentiel};
    }

    /**
     * Case de la matrice correspondant a un couple de scores.
     *
     * @throws RessourceIntrouvableException si la table de reference ne couvre
     *                                       pas cette combinaison
     */
    @Transactional(readOnly = true)
    public Matrice9Box placer(BigDecimal scorePerformance, BigDecimal scorePotentiel, Parametre parametre) {
        Objects.requireNonNull(parametre, "parametre");

        NiveauGrille niveauPerformance = niveauPour(scorePerformance, seuilsPerformance(parametre));
        NiveauGrille niveauPotentiel = niveauPour(scorePotentiel, seuilsPotentiel(parametre));

        return matriceRepository
                .findByNiveauPerformanceAndNiveauPotentiel(niveauPerformance.getRang(), niveauPotentiel.getRang())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucune case de matrice 9-box pour performance " + niveauPerformance
                                + " et potentiel " + niveauPotentiel
                                + " : la table de reference est incomplete"));
    }

    /**
     * Categorie de potentiel (Eleve / Moyen / Faible), comme la colonne L de
     * 03_POTENTIEL : le niveau de l'axe potentiel de la matrice.
     */
    public CategoriePotentiel categoriePotentiel(BigDecimal scorePotentiel, Parametre parametre) {
        Objects.requireNonNull(parametre, "parametre");
        return CategoriePotentiel.depuis(niveauPour(scorePotentiel, seuilsPotentiel(parametre)));
    }

    /**
     * Place un collaborateur et enregistre la categorie dans son Score, avec sa
     * categorie de potentiel.
     *
     * @throws RessourceIntrouvableException si le collaborateur n'a pas de score sur ce trimestre
     */
    @Transactional
    public Score placerEtEnregistrer(Collaborateur collaborateur, Trimestre trimestre) {
        Objects.requireNonNull(collaborateur, "collaborateur");
        Objects.requireNonNull(trimestre, "trimestre");

        Score score = scoreRepository.findByCollaborateurAndTrimestre(collaborateur, trimestre)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Aucun score calcule pour " + collaborateur.getIdCollaborateur()
                                + " sur " + decrire(trimestre)));

        Parametre parametre = calculService.chargerParametre(trimestre);
        Matrice9Box case9Box = placer(score.getScorePerformance(), score.getScorePotentiel(), parametre);

        score.setPositionBox(case9Box.getCategorie());
        score.setCategoriePotentiel(categoriePotentiel(score.getScorePotentiel(), parametre));
        return scoreRepository.save(score);
    }

    /**
     * Place tous les collaborateurs scores du trimestre. La table de reference est
     * chargee une seule fois, les seuils aussi.
     *
     * <p>Un score incomplet ou un collaborateur hors perimetre est remonte dans le
     * bilan avec son motif plutot qu'ecarte en silence.
     */
    @Transactional
    public ResultatRecalcul placerTrimestre(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        Parametre parametre = calculService.chargerParametre(trimestre);
        SeuilsNeufBox seuilsPerformance = seuilsPerformance(parametre);
        SeuilsNeufBox seuilsPotentiel = seuilsPotentiel(parametre);
        Map<String, Matrice9Box> matriceParCase = indexerMatrice();

        List<Score> places = new ArrayList<>();
        List<ResultatRecalcul.CollaborateurIgnore> ignores = new ArrayList<>();

        for (Score score : scoreRepository.findByTrimestreAvecCollaborateur(trimestre)) {
            Collaborateur collaborateur = score.getCollaborateur();
            String matricule = collaborateur.getIdCollaborateur();

            // Un score non place perd sa case : elle viendrait d'un placement precedent.
            if (!collaborateur.estCalculable()) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Statut " + collaborateur.getStatut() + ", hors perimetre de calcul"));
                effacerCase(score);
                continue;
            }
            if (score.getScorePerformance() == null || score.getScorePotentiel() == null) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Score incomplet, les deux axes sont necessaires au placement"));
                effacerCase(score);
                continue;
            }

            NiveauGrille niveauPerformance = niveauPour(score.getScorePerformance(), seuilsPerformance);
            NiveauGrille niveauPotentiel = niveauPour(score.getScorePotentiel(), seuilsPotentiel);
            Matrice9Box case9Box = matriceParCase.get(cle(niveauPerformance.getRang(), niveauPotentiel.getRang()));

            if (case9Box == null) {
                ignores.add(new ResultatRecalcul.CollaborateurIgnore(matricule,
                        "Aucune case de reference pour performance " + niveauPerformance
                                + " et potentiel " + niveauPotentiel));
                effacerCase(score);
                continue;
            }

            score.setPositionBox(case9Box.getCategorie());
            score.setCategoriePotentiel(CategoriePotentiel.depuis(niveauPotentiel));
            places.add(scoreRepository.save(score));
        }

        log.info("Placement 9-box {} : {} collaborateur(s) place(s), {} ignore(s)",
                decrire(trimestre), places.size(), ignores.size());

        return new ResultatRecalcul(places, ignores);
    }

    private void effacerCase(Score score) {
        if (score.getPositionBox() != null || score.getCategoriePotentiel() != null) {
            score.setPositionBox(null);
            score.setCategoriePotentiel(null);
            scoreRepository.save(score);
        }
    }

    private static SeuilsNeufBox seuilsPerformance(Parametre parametre) {
        return exigerSeuils(parametre.getSeuilsNeufBox(), "performance");
    }

    private static SeuilsNeufBox seuilsPotentiel(Parametre parametre) {
        return exigerSeuils(parametre.getSeuilsNeufBoxPotentiel(), "potentiel");
    }

    private static SeuilsNeufBox exigerSeuils(SeuilsNeufBox seuils, String axe) {
        if (seuils == null) {
            throw new DonneesIncompletesException(
                    "Les seuils de l'axe " + axe + " de la matrice 9-box ne sont pas configures");
        }
        return seuils;
    }

    private Map<String, Matrice9Box> indexerMatrice() {
        List<Matrice9Box> cases = matriceRepository.findAll();
        if (cases.isEmpty()) {
            throw new RessourceIntrouvableException(
                    "La table de reference Matrice9Box est vide : aucun placement possible");
        }
        Map<String, Matrice9Box> index = new HashMap<>();
        for (Matrice9Box case9Box : cases) {
            index.put(cle(case9Box.getNiveauPerformance(), case9Box.getNiveauPotentiel()), case9Box);
        }
        return index;
    }

    private String cle(Integer rangPerformance, Integer rangPotentiel) {
        return rangPerformance + "-" + rangPotentiel;
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
