package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.KpiCard;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.LigneCollaborateur;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.VivierRef;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.Kpi;
import com.talent360bank.talent360bank.ui.model.TableauDeBordInteractif.Personne;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView;
import com.talent360bank.talent360bank.ui.model.TableauDeBordView.CaseTableau;
import com.talent360bank.talent360bank.ui.model.VueEntite.EntiteRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Donnees absentes ou incompletes : le tableau de bord les affiche "—" au lieu d'echouer pendant le
 * rendu. Cas couverts : collaborateur sans entite, sans nom, sans case 9-Box, sans vivier (liste nulle
 * ou element nul), sans vigilance ni resultat du moteur ; case 9-Box sans libelle ; carte du moteur
 * sans valeur ou absente.
 */
class TableauDeBordInteractifDonneesManquantesTest {

    private static final Map<String, String> VIVIERS = Map.of("COMMERCIAL", "Vivier Commercial",
            "RELEVE", "Vivier de relève");

    private static LigneCollaborateur ligne(String matricule, String nomComplet, EntiteRef entite, String direction,
                                            CaseNeufBox neufBox, List<VivierRef> viviers, String vigilance) {
        return new LigneCollaborateur(matricule, null, null, nomComplet, entite, direction, null, null, null, null,
                null, neufBox, null, null, false, null, viviers, null, null, vigilance, null);
    }

    @Test
    void un_collaborateur_sans_aucune_donnee_s_affiche_avec_des_tirets() {
        Personne p = TableauDeBordInteractifService.personne(ligne("X01", null, null, null, null, null, null), null,
                "2026-3", VIVIERS);
        assertThat(p.nomComplet()).isEqualTo("—");
        assertThat(p.directionCode()).isEqualTo("SANS");
        assertThat(p.direction()).isEqualTo("Sans direction");
        assertThat(p.caseNumero()).isNull();
        assertThat(p.caseEtiquette()).isEqualTo("—");
        assertThat(p.viviers()).isEmpty();
        assertThat(p.viviersTexte()).isEqualTo("—");
        assertThat(p.vigilance()).isNull();
        assertThat(p.vigilanceLibelle()).isNull();
        assertThat(p.engagement()).isNull();
        assertThat(p.lien()).isEqualTo("/fiche-collaborateur?matricule=X01&trimestre=2026-3");
    }

    @Test
    void entite_sans_code_case_sans_libelle_et_vivier_nul_dans_la_liste() {
        List<VivierRef> viviers = new ArrayList<>(Arrays.asList(new VivierRef("COMMERCIAL", "Vivier Commercial"), null,
                new VivierRef(null, null), new VivierRef("INCONNU", null)));
        Personne p = TableauDeBordInteractifService.personne(ligne("X02", "Ana Ben", new EntiteRef(" ", null, null),
                " ", new CaseNeufBox(9, null), viviers, "INCONNUE"), null, "2026-3", VIVIERS);
        assertThat(p.directionCode()).isEqualTo("SANS");
        assertThat(p.direction()).isEqualTo("Sans direction");
        assertThat(p.caseEtiquette()).isEqualTo("⭐ —");
        assertThat(p.viviers()).containsExactly("COMMERCIAL", "INCONNU");
        assertThat(p.viviersTexte()).isEqualTo("Vivier Commercial, INCONNU");
        // Un niveau de vigilance inconnu s'affiche tel quel, sans exception.
        assertThat(p.vigilanceLibelle()).isEqualTo("INCONNUE");
    }

    @Test
    void une_carte_du_moteur_sans_valeur_ou_absente_et_une_case_sans_libelle_donnent_un_tiret() {
        TableauDeBordView tableau = new TableauDeBordView("T3 2026",
                List.of(new KpiCard("Postes critiques", null, null, null), new KpiCard(null, "3", null, null),
                        new KpiCard("Couverture succession", "93.33 %", null, null)),
                List.of(new CaseTableau(null, 3, 3, 4)), 4, 0, List.of(), 0, List.of(), 0, List.of(), List.of(), null);
        Personne p = TableauDeBordInteractifService.personne(ligne("X03", "Ana Ben", null, null, null, List.of(), null),
                null, "2026-3", VIVIERS);
        List<Kpi> kpis = TableauDeBordInteractifService.kpis(List.of(p), tableau);
        assertThat(kpis).hasSize(10);
        assertThat(kpis).filteredOn(k -> k.code().equals("postesCritiques")).singleElement()
                .satisfies(k -> assertThat(k.valeur()).isEqualTo("—"));
        assertThat(kpis).filteredOn(k -> k.code().equals("couverture")).singleElement()
                .satisfies(k -> assertThat(k.valeur()).isEqualTo("93.33 %"));
        // Carte absente du moteur (Ready Now...) : "—" ; pas de reponse d'engagement : "—".
        assertThat(kpis).filteredOn(k -> k.code().equals("readyNow")).singleElement()
                .satisfies(k -> assertThat(k.valeur()).isEqualTo("—"));
        assertThat(kpis).filteredOn(k -> k.code().equals("engagement")).singleElement()
                .satisfies(k -> assertThat(k.valeur()).isEqualTo("—"));
        assertThat(kpis).allSatisfy(k -> assertThat(k.valeur()).isNotNull());

        Map<String, Object> dimensions = TableauDeBordInteractifService.dimensions(tableau, List.of(p));
        assertThat(dimensions.get("case").toString()).contains("libelle=—");
        assertThat(dimensions.get("entite").toString()).contains("code=SANS", "libelle=Sans direction");
    }

    @Test
    void une_moyenne_d_engagement_est_calculee_sur_les_seules_reponses() {
        TableauDeBordView tableau = new TableauDeBordView("T3 2026", List.of(), List.of(), 0, 0, List.of(), 0,
                List.of(), 0, List.of(), List.of(), null);
        Personne sans = TableauDeBordInteractifService.personne(ligne("X04", "A", null, null, null, null, null), null,
                "2026-3", VIVIERS);
        Kpi engagement = TableauDeBordInteractifService.kpis(List.of(sans), tableau).get(7);
        assertThat(engagement.libelle()).isEqualTo("❤️ Engagement (0 réponse)");
        assertThat(engagement.valeur()).isEqualTo("—");
        assertThat(TableauDeBordInteractifService.classeEngagement(new BigDecimal("75.00"))).isEqualTo("teal");
        assertThat(TableauDeBordInteractifService.classeEngagement(null)).isEqualTo("gold");
    }
}
