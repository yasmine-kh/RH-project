package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.Entite;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.TypeEntite;
import com.talent360bank.talent360bank.ui.model.FicheCollaborateur.CaseNeufBox;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Criteres;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.LigneCollaborateur;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.PosteCibleLigne;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.Tri;
import com.talent360bank.talent360bank.ui.model.ListeCollaborateurs.VivierRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Filtres, tri et pagination de la liste des collaborateurs, sur des lignes deja construites (sans base). */
class ListeCollaborateursViewServiceTest {

    private final Entite direction = entite(1, "Reseau Retail", TypeEntite.DIRECTION, null);
    private final Entite region = entite(2, "Nord", TypeEntite.REGION, direction);
    private final Entite agence = entite(3, "Tanger", TypeEntite.AGENCE, region);
    private final Entite autreDirection = entite(4, "Corporate", TypeEntite.DIRECTION, null);

    private List<LigneCollaborateur> lignes;
    private Map<String, Collaborateur> collaborateurs;

    private static Entite entite(int id, String libelle, TypeEntite type, Entite parent) {
        Entite entite = new Entite(libelle, type, parent);
        entite.setIdEntite(id);
        return entite;
    }

    private LigneCollaborateur ligne(String matricule, String nom, Entite entite, String perf, Integer case9,
                                     Boolean talent, String vivier, String readiness, String vigilance) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(matricule);
        collaborateur.setNom(nom);
        collaborateur.setPrenom("Prénom");
        collaborateur.setEntite(entite);
        collaborateurs.put(matricule, collaborateur);
        return new LigneCollaborateur(matricule, nom, "Prénom", "Prénom " + nom, null, null, null, null, null,
                perf == null ? null : new BigDecimal(perf), null, case9 == null ? null : new CaseNeufBox(case9, "c"),
                talent, false, false, null, vivier == null ? List.of() : List.of(new VivierRef(vivier, vivier)),
                readiness == null ? null : new PosteCibleLigne("PST01", "P", new BigDecimal(perf == null ? "0" : perf),
                        readiness, readiness, false, null, 0),
                null, vigilance, vigilance);
    }

    @BeforeEach
    void init() {
        collaborateurs = new HashMap<>();
        lignes = List.of(
                ligne("BP003", "Élbaz", agence, "89.30", 7, false, "COMMERCIAL", "ENTRE_1_ET_2_ANS", "MODEREE"),
                ligne("BP001", "Bouzid", agence, "61.60", 2, false, "COMMERCIAL", "PLUS_2_ANS", "MODEREE"),
                ligne("BP019", "El Khatib", region, "93.20", 9, true, "RELEVE", "READY_NOW", "FAIBLE"),
                ligne("BP050", "Alaoui", autreDirection, null, null, null, null, null, null));
    }

    private ListeCollaborateurs appliquer(Criteres criteres) {
        ListeCollaborateursViewService.verifier(criteres);
        return ListeCollaborateursViewService.paginer(null, lignes, collaborateurs, criteres, List.of());
    }

    private static Criteres criteres(String entite, Integer case9, Boolean talent, String vivier, String readiness,
                                     String vigilance, String recherche) {
        return new Criteres(entite, case9, talent, vivier, readiness, vigilance, recherche, Tri.NOM, false, 1, 20);
    }

    private static List<String> matricules(ListeCollaborateurs liste) {
        return liste.lignes().stream().map(LigneCollaborateur::matricule).toList();
    }

    @Test
    void le_filtre_par_entite_prend_tout_le_sous_arbre() {
        assertThat(matricules(appliquer(criteres(direction.getCode(), null, null, null, null, null, null))))
                .containsExactlyInAnyOrder("BP001", "BP003", "BP019");
        assertThat(matricules(appliquer(criteres(agence.getCode(), null, null, null, null, null, null))))
                .containsExactlyInAnyOrder("BP001", "BP003");
        assertThat(appliquer(criteres("INCONNU", null, null, null, null, null, null)).lignes()).isEmpty();
    }

    @Test
    void chaque_filtre_se_combine_aux_autres() {
        assertThat(matricules(appliquer(criteres(null, 9, null, null, null, null, null)))).containsExactly("BP019");
        assertThat(matricules(appliquer(criteres(null, null, true, null, null, null, null)))).containsExactly("BP019");
        // talent=false : non-talents ET collaborateurs sans statut (score incomplet).
        assertThat(matricules(appliquer(criteres(null, null, false, null, null, null, null))))
                .containsExactlyInAnyOrder("BP001", "BP003", "BP050");
        assertThat(matricules(appliquer(criteres(null, null, null, "COMMERCIAL", null, "MODEREE", null))))
                .containsExactlyInAnyOrder("BP001", "BP003");
        assertThat(matricules(appliquer(criteres(null, null, null, null, "READY_NOW", null, null))))
                .containsExactly("BP019");
        ListeCollaborateurs combine = appliquer(criteres(direction.getCode(), 2, false, "COMMERCIAL", "PLUS_2_ANS",
                "MODEREE", null));
        assertThat(matricules(combine)).containsExactly("BP001");
        assertThat(combine.nbTotal()).isEqualTo(4);
        assertThat(combine.nbFiltres()).isEqualTo(1);
    }

    @Test
    void la_recherche_ignore_accents_et_casse_sur_nom_et_matricule() {
        assertThat(matricules(appliquer(criteres(null, null, null, null, null, null, "elbaz")))).containsExactly("BP003");
        assertThat(matricules(appliquer(criteres(null, null, null, null, null, null, "bp00"))))
                .containsExactlyInAnyOrder("BP001", "BP003");
        assertThat(matricules(appliquer(criteres(null, null, null, null, null, null, "prenom el khatib"))))
                .containsExactly("BP019");
    }

    @Test
    void le_tri_par_score_met_les_valeurs_inconnues_en_dernier_dans_les_deux_sens() {
        assertThat(matricules(appliquer(new Criteres(null, null, null, null, null, null, null, Tri.PERFORMANCE, true,
                1, 20)))).containsExactly("BP019", "BP003", "BP001", "BP050");
        assertThat(matricules(appliquer(new Criteres(null, null, null, null, null, null, null, Tri.PERFORMANCE, false,
                1, 20)))).containsExactly("BP001", "BP003", "BP019", "BP050");
        assertThat(matricules(appliquer(new Criteres(null, null, null, null, null, null, null, Tri.MATCHING, true,
                1, 20)))).containsExactly("BP019", "BP003", "BP001", "BP050");
        // Par nom, sans accents : Alaoui, Bouzid, El Khatib, Elbaz.
        assertThat(matricules(appliquer(criteres(null, null, null, null, null, null, null))))
                .containsExactly("BP050", "BP001", "BP019", "BP003");
    }

    @Test
    void la_pagination_decoupe_la_liste_filtree() {
        ListeCollaborateurs page1 = appliquer(new Criteres(null, null, null, null, null, null, null, Tri.MATRICULE,
                false, 1, 3));
        assertThat(matricules(page1)).containsExactly("BP001", "BP003", "BP019");
        assertThat(page1.nbPages()).isEqualTo(2);
        ListeCollaborateurs page2 = appliquer(new Criteres(null, null, null, null, null, null, null, Tri.MATRICULE,
                false, 2, 3));
        assertThat(matricules(page2)).containsExactly("BP050");
        ListeCollaborateurs auDela = appliquer(new Criteres(null, null, null, null, null, null, null, Tri.MATRICULE,
                false, 5, 3));
        assertThat(auDela.lignes()).isEmpty();
        assertThat(auDela.nbFiltres()).isEqualTo(4);
        assertThat(appliquer(criteres(null, 5, null, null, null, null, null)).nbPages()).isEqualTo(1);
    }

    @Test
    void les_criteres_hors_bornes_ou_inconnus_sont_refuses() {
        assertThatThrownBy(() -> appliquer(new Criteres(null, null, null, null, null, null, null, Tri.NOM, false,
                0, 20))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appliquer(new Criteres(null, null, null, null, null, null, null, Tri.NOM, false,
                1, 101))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appliquer(criteres(null, 10, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appliquer(criteres(null, null, null, "AUTRE", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RELEVE");
        assertThatThrownBy(() -> appliquer(criteres(null, null, null, null, "BIENTOT", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appliquer(criteres(null, null, null, null, null, "HAUTE", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void les_talents_sont_classes_par_performance_plus_potentiel_puis_performance_puis_matricule() {
        Score a = score("BP010", "90.00", "86.00");   // 176
        Score b = score("BP011", "86.00", "90.00");   // 176, performance plus faible
        Score c = score("BP009", "88.00", "88.00");   // 176, performance au milieu
        Score d = score("BP012", "95.00", "95.00");   // 190
        Score e = score("BP008", "88.00", "88.00");   // egalite parfaite avec c : matricule

        assertThat(List.of(a, b, c, d, e).stream().sorted(TableauDeBordDgViewService.ORDRE_TOP_TALENTS)
                .map(s -> s.getCollaborateur().getIdCollaborateur()))
                .containsExactly("BP012", "BP010", "BP008", "BP009", "BP011");
    }

    private static Score score(String id, String performance, String potentiel) {
        Collaborateur collaborateur = new Collaborateur();
        collaborateur.setIdCollaborateur(id);
        Score score = new Score();
        score.setCollaborateur(collaborateur);
        score.setScorePerformance(new BigDecimal(performance));
        score.setScorePotentiel(new BigDecimal(potentiel));
        return score;
    }
}
