package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.service.resultat.ResultatCampagne;
import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.BilanFeuille;
import com.talent360bank.talent360bank.service.resultat.ResultatImport.ErreurImport;

import java.util.List;

/**
 * Bilan d'un import : lignes ecrites par feuille, et chaque ligne ou feuille
 * ecartee avec son numero de ligne Excel et son motif.
 */
public record ImportResponse(Integer idImport, String nomFichier, int annee, int numero, boolean simulation,
                             String statut, int nbLignes, int nbErreurs, List<BilanFeuille> feuilles,
                             List<ErreurImport> erreurs, int nbDesactives, List<String> desactives,
                             List<String> reactives, String message, CalculTrimestreResponse calcul,
                             String erreurCalcul, List<ErreurImport> decisionsConservees) {

    /** Import suivi du calcul : les deux bilans dans la meme reponse. */
    public static ImportResponse de(ResultatCampagne campagne) {
        ImportResponse importation = de(campagne.importation());
        return new ImportResponse(importation.idImport(), importation.nomFichier(), importation.annee(),
                importation.numero(), importation.simulation(), importation.statut(), importation.nbLignes(),
                importation.nbErreurs(), importation.feuilles(), importation.erreurs(), importation.nbDesactives(),
                importation.desactives(), importation.reactives(), importation.message(),
                campagne.calcul() == null ? null : CalculTrimestreResponse.de(campagne.calcul()),
                campagne.erreurCalcul(), importation.decisionsConservees());
    }

    public static ImportResponse de(ResultatImport resultat) {
        return new ImportResponse(resultat.idImport(), resultat.nomFichier(), resultat.annee(), resultat.numero(),
                resultat.simulation(), resultat.statut().name(), resultat.nbLignes(), resultat.nbErreurs(),
                resultat.feuilles(), resultat.erreurs(), resultat.desactives().size(), resultat.desactives(),
                resultat.reactives(), resultat.message(), null, null, resultat.decisionsConservees());
    }
}
