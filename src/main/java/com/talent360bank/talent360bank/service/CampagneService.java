package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.StatutImport;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.TrimestreRepository;
import com.talent360bank.talent360bank.service.resultat.ResultatCalculTrimestre;
import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Une campagne d'evaluation : le classeur du trimestre est importe, puis tout
 * ce qui en decoule est recalcule (scores, 9-box, vivier de releve), pour que
 * les ecrans ne montrent jamais les resultats d'un fichier precedent.
 *
 * <p>Le calcul n'est lance que si l'import a ecrit quelque chose (SUCCES ou
 * PARTIEL), jamais en simulation. S'il echoue, l'import reste enregistre et
 * la cause est rendue : le calcul peut etre relance seul par
 * POST /api/trimestres/{annee}/{numero}/calcul.
 */
@Service
public class CampagneService {

    private static final Logger log = LoggerFactory.getLogger(CampagneService.class);

    private final ImportService importService;
    private final CalculTrimestreService calculTrimestreService;
    private final TrimestreRepository trimestreRepository;

    public CampagneService(ImportService importService, CalculTrimestreService calculTrimestreService,
                           TrimestreRepository trimestreRepository) {
        this.importService = importService;
        this.calculTrimestreService = calculTrimestreService;
        this.trimestreRepository = trimestreRepository;
    }

    /**
     * @param simulation valider sans rien enregistrer (pas de calcul)
     * @param calcul     lancer le calcul complet apres l'import
     */
    public ResultatCampagne importer(MultipartFile fichier, int annee, int numero, boolean simulation,
                                     boolean calcul) {
        ResultatImport importation = importService.importer(fichier, annee, numero, simulation);
        if (simulation || !calcul || importation.statut() == StatutImport.ECHEC) {
            return new ResultatCampagne(importation, null, null);
        }
        Trimestre trimestre = trimestreRepository.findByNumeroAndAnnee(numero, annee).orElseThrow();
        try {
            ResultatCalculTrimestre resultat = calculTrimestreService.calculer(trimestre);
            return new ResultatCampagne(importation, resultat, null);
        } catch (DonneesIncompletesException | RessourceIntrouvableException e) {
            // Cause metier (reglages incomplets...) : son message est fait pour le RH.
            log.warn("Calcul T{} {} apres import : echec ({})", numero, annee, e.getMessage());
            return new ResultatCampagne(importation, null, "Import enregistré, mais le calcul a échoué : "
                    + e.getMessage() + ". Corrigez les réglages du trimestre (page Paramètres) : leur "
                    + "enregistrement relance le calcul.");
        } catch (RuntimeException e) {
            // Erreur technique : le detail va au journal seulement.
            log.error("Calcul T{} {} apres import : echec", numero, annee, e);
            return new ResultatCampagne(importation, null, "Import enregistré, mais le calcul a échoué. "
                    + "Vérifiez les réglages du trimestre (page Paramètres) et réessayez.");
        }
    }
}
