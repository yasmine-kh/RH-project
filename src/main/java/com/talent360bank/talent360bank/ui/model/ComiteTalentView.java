package com.talent360bank.talent360bank.ui.model;

import java.util.List;

/**
 * Tout ce qu'affiche l'ecran Comite Talent, pret pour le template : les
 * menus de filtre, la carte KPI et les lignes deja filtrees.
 * Aucun calcul ici.
 */
public class ComiteTalentView {

    private final List<OptionFiltre> trimestres;
    private final String trimestreLibelle;   // null s'il n'existe aucun trimestre
    private final List<OptionFiltre> statuts;
    private final List<KpiCard> kpis;
    private final int nbProposes;
    private final List<ComiteTalentRow> rows;
    private final String erreur;             // reglages absents ou incomplets, sinon null

    public ComiteTalentView(List<OptionFiltre> trimestres, String trimestreLibelle,
                            List<OptionFiltre> statuts, List<KpiCard> kpis, int nbProposes,
                            List<ComiteTalentRow> rows, String erreur) {
        this.trimestres = trimestres;
        this.trimestreLibelle = trimestreLibelle;
        this.statuts = statuts;
        this.kpis = kpis;
        this.nbProposes = nbProposes;
        this.rows = rows;
        this.erreur = erreur;
    }

    public List<OptionFiltre> getTrimestres() {
        return trimestres;
    }

    public String getTrimestreLibelle() {
        return trimestreLibelle;
    }

    public List<OptionFiltre> getStatuts() {
        return statuts;
    }

    public List<KpiCard> getKpis() {
        return kpis;
    }

    public int getNbProposes() {
        return nbProposes;
    }

    public List<ComiteTalentRow> getRows() {
        return rows;
    }

    public String getErreur() {
        return erreur;
    }
}
