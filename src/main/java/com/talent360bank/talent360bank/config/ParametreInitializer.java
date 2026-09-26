package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.repository.ParametreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete au demarrage les reglages crees avant l'ajout d'un bloc.
 *
 * <p>Les colonnes des blocs ajoutes portent leur valeur par defaut en base
 * (@ColumnDefault), ce qui suffit quand ddl-auto=update les ajoute. Ce passage
 * couvre le cas restant : une colonne creee nullable, dont les lignes
 * existantes sont a NULL et que le moteur refuserait de calculer. Chaque
 * Parametre incomplet recoit les valeurs par defaut des champs manquants ; les
 * champs deja renseignes ne sont pas touches.
 *
 * <p>Une colonne ajoutee NOT NULL sans valeur par defaut (avant @ColumnDefault)
 * a pu recevoir le 0 implicite de MySQL. Un 0 ne se distingue pas d'un reglage
 * voulu : il est signale, pas corrige.
 */
@Component
public class ParametreInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ParametreInitializer.class);

    private final ParametreRepository parametreRepository;

    public ParametreInitializer(ParametreRepository parametreRepository) {
        this.parametreRepository = parametreRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int completes = 0;

        for (Parametre parametre : parametreRepository.findAll()) {
            if (parametre.completerBlocsAjoutes()) {
                parametreRepository.save(parametre);
                completes++;
            }
            signalerZeros(parametre);
        }

        if (completes > 0) {
            log.info("Parametres : {} jeu(x) de reglages complete(s) avec les valeurs par defaut", completes);
        }
    }

    private void signalerZeros(Parametre parametre) {
        List<String> aZero = new ArrayList<>();
        if (parametre.getBaremeExperience().getPointsParAnnee().signum() == 0) {
            aZero.add("exp_points_par_annee");
        }
        if (parametre.getBaremeExperience().getPlafond().signum() == 0) {
            aZero.add("exp_plafond");
        }
        if (parametre.getBaremeCompetences().getPointsParNiveauManquant().signum() == 0) {
            aZero.add("comp_points_par_niveau_manquant");
        }
        if (parametre.getBaremeCompetences().getNiveauParDefaut() == 0) {
            aZero.add("comp_niveau_par_defaut");
        }
        if (parametre.getSeuilsTalent() != null) {
            if (parametre.getSeuilsTalent().getSeuilHautPotentielPotentiel().signum() == 0) {
                aZero.add("seuil_hp_pot");
            }
            if (parametre.getSeuilsTalent().getSeuilHautPotentielPerformance().signum() == 0) {
                aZero.add("seuil_hp_perf");
            }
        }
        if (!aZero.isEmpty()) {
            log.warn("Parametre {} : {} a 0, peut-etre pose par l'ajout de la colonne ; "
                    + "a verifier dans les reglages", parametre.getIdParametre(), aZero);
        }
    }
}
