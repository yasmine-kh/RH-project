package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.NiveauGrille;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pose les neuf cases de reference de la matrice au premier demarrage.
 *
 * <p>Seules les combinaisons absentes sont inserees : un libelle renomme par
 * le RH n'est jamais ecrase. Ces valeurs sont un point de depart, pas la
 * source de verite du moteur, qui lit toujours la table.
 */
@Component
public class Matrice9BoxInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Matrice9BoxInitializer.class);

    private final Matrice9BoxRepository matriceRepository;

    public Matrice9BoxInitializer(Matrice9BoxRepository matriceRepository) {
        this.matriceRepository = matriceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int inserees = 0;

        inserees += creerSiAbsente(NiveauGrille.FAIBLE, NiveauGrille.FAIBLE, "À surveiller");
        inserees += creerSiAbsente(NiveauGrille.FAIBLE, NiveauGrille.MOYEN, "À développer");
        inserees += creerSiAbsente(NiveauGrille.FAIBLE, NiveauGrille.ELEVE, "Potentiel à confirmer");

        inserees += creerSiAbsente(NiveauGrille.MOYEN, NiveauGrille.FAIBLE, "À accompagner");
        inserees += creerSiAbsente(NiveauGrille.MOYEN, NiveauGrille.MOYEN, "Confirmé");
        inserees += creerSiAbsente(NiveauGrille.MOYEN, NiveauGrille.ELEVE, "Potentiel / Haut potentiel");

        inserees += creerSiAbsente(NiveauGrille.ELEVE, NiveauGrille.FAIBLE, "Expert");
        inserees += creerSiAbsente(NiveauGrille.ELEVE, NiveauGrille.MOYEN, "Performant");
        inserees += creerSiAbsente(NiveauGrille.ELEVE, NiveauGrille.ELEVE, "Talent clé");

        if (inserees > 0) {
            log.info("Matrice 9-box : {} case(s) de reference inseree(s)", inserees);
        }
    }

    private int creerSiAbsente(NiveauGrille performance, NiveauGrille potentiel, String categorie) {
        if (matriceRepository.findByNiveauPerformanceAndNiveauPotentiel(
                performance.getRang(), potentiel.getRang()).isPresent()) {
            return 0;
        }
        Matrice9Box case9Box = new Matrice9Box();
        case9Box.setNiveauPerformance(performance.getRang());
        case9Box.setNiveauPotentiel(potentiel.getRang());
        case9Box.setCategorie(categorie);
        matriceRepository.save(case9Box);
        return 1;
    }
}
