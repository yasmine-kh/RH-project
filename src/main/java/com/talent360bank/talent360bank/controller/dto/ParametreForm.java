package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.BaremeCompetences;
import com.talent360bank.talent360bank.entity.BaremeExperience;
import com.talent360bank.talent360bank.entity.Parametre;
import com.talent360bank.talent360bank.entity.PoidsPerformance;
import com.talent360bank.talent360bank.entity.PoidsPotentiel;
import com.talent360bank.talent360bank.entity.PoidsSuccession;
import com.talent360bank.talent360bank.entity.PointsVigilance;
import com.talent360bank.talent360bank.entity.PonderationSources;
import com.talent360bank.talent360bank.entity.SeuilsAutoEvaluation;
import com.talent360bank.talent360bank.entity.SeuilsCategoriePerformance;
import com.talent360bank.talent360bank.entity.SeuilsCouverture;
import com.talent360bank.talent360bank.entity.SeuilsGapCompetence;
import com.talent360bank.talent360bank.entity.SeuilsNeufBox;
import com.talent360bank.talent360bank.entity.SeuilsReadiness;
import com.talent360bank.talent360bank.entity.SeuilsTalent;
import com.talent360bank.talent360bank.entity.SeuilsVigilance;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de mise a jour des reglages : les blocs de reglages, et rien d'autre.
 *
 * <p>Ni l'identifiant ni le trimestre ne sont acceptes du client. Un jeu de
 * reglages appartient a son trimestre et n'en change pas : le trimestre vient
 * de l'URL, l'identifiant de la ligne existante.
 *
 * <p>Les blocs ajoutes apres les autres sont facultatifs, pour ne pas casser
 * un client qui ne les connait pas encore : {@code seuilsCouverture},
 * {@code seuilsNeufBoxPotentiel}, {@code seuilsCategoriePerformance},
 * {@code seuilsGapCompetence}, {@code ponderationSources} et
 * {@code seuilsAutoEvaluation}. Absents, la valeur en place est conservee. En
 * particulier, un client qui n'envoie que {@code seuilsNeufBox} ne change que
 * l'axe performance de la 9-box : l'axe potentiel garde ses seuils.
 *
 * <p>{@code poidsSources} a ete retire (aucune formule du classeur ne l'utilise) :
 * un client qui l'envoie encore voit le bloc accepte et ignore. Ne pas le
 * confondre avec {@code ponderationSources} (manager / auto-evaluation), qui
 * compte dans les scores officiels.
 */
@JsonIgnoreProperties({"poidsSources"})
public record ParametreForm(@Size(max = 100) String libelle,
                            @Valid @NotNull PoidsPerformance poidsPerformance,
                            @Valid @NotNull PoidsPotentiel poidsPotentiel,
                            @Valid @NotNull PoidsSuccession poidsSuccession,
                            @Valid PonderationSources ponderationSources,
                            @Valid SeuilsAutoEvaluation seuilsAutoEvaluation,
                            @Valid @NotNull BaremeExperience baremeExperience,
                            @Valid @NotNull BaremeCompetences baremeCompetences,
                            @Valid @NotNull SeuilsNeufBox seuilsNeufBox,
                            @Valid SeuilsNeufBox seuilsNeufBoxPotentiel,
                            @Valid SeuilsCategoriePerformance seuilsCategoriePerformance,
                            @Valid SeuilsGapCompetence seuilsGapCompetence,
                            @Valid @NotNull SeuilsReadiness seuilsReadiness,
                            @Valid SeuilsCouverture seuilsCouverture,
                            @Valid @NotNull SeuilsTalent seuilsTalent,
                            @Valid @NotNull PointsVigilance pointsVigilance,
                            @Valid @NotNull SeuilsVigilance seuilsVigilance) {

    /** Recopie les blocs sur les reglages existants ; un bloc facultatif absent garde sa valeur. */
    public void appliquerA(Parametre parametre) {
        parametre.setLibelle(libelle);
        parametre.setPoidsPerformance(poidsPerformance);
        parametre.setPoidsPotentiel(poidsPotentiel);
        parametre.setPoidsSuccession(poidsSuccession);
        if (ponderationSources != null) {
            parametre.setPonderationSources(ponderationSources);
        }
        if (seuilsAutoEvaluation != null) {
            parametre.setSeuilsAutoEvaluation(seuilsAutoEvaluation);
        }
        parametre.setBaremeExperience(baremeExperience);
        parametre.setBaremeCompetences(baremeCompetences);
        parametre.setSeuilsNeufBox(seuilsNeufBox);
        if (seuilsNeufBoxPotentiel != null) {
            parametre.setSeuilsNeufBoxPotentiel(seuilsNeufBoxPotentiel);
        }
        if (seuilsCategoriePerformance != null) {
            parametre.setSeuilsCategoriePerformance(seuilsCategoriePerformance);
        }
        if (seuilsGapCompetence != null) {
            parametre.setSeuilsGapCompetence(seuilsGapCompetence);
        }
        parametre.setSeuilsReadiness(seuilsReadiness);
        if (seuilsCouverture != null) {
            parametre.setSeuilsCouverture(seuilsCouverture);
        }
        parametre.setSeuilsTalent(seuilsTalent);
        parametre.setPointsVigilance(pointsVigilance);
        parametre.setSeuilsVigilance(seuilsVigilance);
    }
}
