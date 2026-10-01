package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trimestre affiche par les ecrans (tableau de bord, 9-box, viviers, comite...).
 *
 * <p>Regle : le trimestre demande dans l'URL ({@code ?trimestre=AAAA-N}), sinon
 * le plus recent qui a des scores. Le plus recent cree ne suffit pas : un
 * trimestre ouvert avant son import (ou par un import en echec, AUDIT_REPORT
 * B5) n'a encore rien a montrer, et les ecrans resteraient vides alors que le
 * precedent a toutes ses donnees. Sans aucun trimestre calcule, le plus recent
 * cree ; sans aucun trimestre, rien.
 *
 * <p>Deux requetes quel que soit le nombre de trimestres : la liste des
 * trimestres et ceux qui ont des scores.
 */
@Service
public class TrimestreCourantService {

    /** Nom du parametre d'URL des ecrans. */
    public static final String PARAMETRE = "trimestre";

    /** Attribut de modele : le trimestre affiche ({@link OptionTrimestre}), null s'il n'y en a aucun. */
    public static final String ATTRIBUT_TRIMESTRE = "trimestre";

    /** Attribut de modele : tous les trimestres, du plus recent au plus ancien ({@link OptionTrimestre}). */
    public static final String ATTRIBUT_TRIMESTRES = "trimestres";

    private static final Pattern FORMAT = Pattern.compile("(\\d{4})-(\\d)");

    private final TrimestreRepository trimestreRepository;
    private final ScoreRepository scoreRepository;

    public TrimestreCourantService(TrimestreRepository trimestreRepository, ScoreRepository scoreRepository) {
        this.trimestreRepository = trimestreRepository;
        this.scoreRepository = scoreRepository;
    }

    /**
     * Le trimestre a afficher.
     *
     * @param parametre valeur de {@code ?trimestre=} ("AAAA-N"), null ou vide pour le choix par defaut
     * @return vide s'il n'existe aucun trimestre
     * @throws ResponseStatusException 404 si le trimestre demande est mal ecrit ou inconnu
     */
    @Transactional(readOnly = true)
    public Optional<Trimestre> resoudre(String parametre) {
        return Optional.ofNullable(selectionner(parametre).trimestre());
    }

    /** Tous les trimestres, du plus recent au plus ancien, chacun avec {@code hasData}. */
    @Transactional(readOnly = true)
    public List<OptionTrimestre> lister() {
        return selectionner(null).trimestres();
    }

    /**
     * Le trimestre a afficher et la liste du selecteur, en une fois (deux
     * requetes) : ce qu'appellent les controleurs des ecrans.
     *
     * @throws ResponseStatusException 404 si le trimestre demande est mal ecrit ou inconnu
     */
    @Transactional(readOnly = true)
    public Selection selectionner(String parametre) {
        List<Trimestre> trimestres = trimestreRepository.findAllByOrderByAnneeDescNumeroDesc();
        Set<Integer> avecScores = new HashSet<>(scoreRepository.findIdsTrimestresAvecScores());
        List<OptionTrimestre> options = trimestres.stream()
                .map(trimestre -> option(trimestre, avecScores.contains(trimestre.getIdTrimestre())))
                .toList();

        Trimestre choisi;
        if (parametre != null && !parametre.isBlank()) {
            choisi = demande(trimestres, parametre.trim());
        } else {
            choisi = trimestres.stream()
                    .filter(trimestre -> avecScores.contains(trimestre.getIdTrimestre()))
                    .findFirst()
                    .orElse(trimestres.isEmpty() ? null : trimestres.get(0));
        }
        return new Selection(choisi, options);
    }

    /** "AAAA-N", la valeur du parametre {@code ?trimestre=} pour ce trimestre. */
    public static String valeur(Trimestre trimestre) {
        return trimestre.getAnnee() + "-" + trimestre.getNumero();
    }

    /** "TN AAAA", le libelle affiche. */
    public static String libelle(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }

    private static OptionTrimestre option(Trimestre trimestre, boolean hasData) {
        return new OptionTrimestre(valeur(trimestre), libelle(trimestre), trimestre.getAnnee(),
                trimestre.getNumero(), hasData);
    }

    private static Trimestre demande(List<Trimestre> trimestres, String parametre) {
        Matcher format = FORMAT.matcher(parametre);
        if (format.matches()) {
            int annee = Integer.parseInt(format.group(1));
            int numero = Integer.parseInt(format.group(2));
            for (Trimestre trimestre : trimestres) {
                if (trimestre.getAnnee() == annee && trimestre.getNumero() == numero) {
                    return trimestre;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Trimestre inconnu : " + parametre + " (format attendu AAAA-N, ex. 2026-3)");
    }

    /**
     * Le trimestre affiche et la liste du selecteur.
     *
     * @param trimestre null s'il n'existe aucun trimestre
     */
    public record Selection(Trimestre trimestre, List<OptionTrimestre> trimestres) {

        /** Le trimestre affiche, tel que le voit le selecteur ; null s'il n'y en a aucun. */
        public OptionTrimestre courant() {
            if (trimestre == null) {
                return null;
            }
            String valeur = valeur(trimestre);
            return trimestres.stream().filter(option -> option.valeur().equals(valeur)).findFirst().orElse(null);
        }

        /**
         * Pose les attributs "trimestre" ({@link OptionTrimestre} affiche, null
         * s'il n'y en a aucun) et "trimestres" (la liste) pour les templates.
         */
        public void exposer(Model model) {
            model.addAttribute(ATTRIBUT_TRIMESTRE, courant());
            model.addAttribute(ATTRIBUT_TRIMESTRES, trimestres);
        }
    }
}
