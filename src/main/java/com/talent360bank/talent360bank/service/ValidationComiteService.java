package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.service.enums.StatutValidationComite;
import com.talent360bank.talent360bank.service.resultat.DecisionComite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validation des talents par le Comite Talent (10_TALENTS!H) : un talent
 * valide est un talent propose par {@link TalentService} ET retenu par le
 * comite ({@link ValidationComiteSource}).
 *
 * <p>Le comite ne statue que sur les talents : les hauts potentiels ne sont
 * pas valides, et le vivier de releve (talents OU hauts potentiels) ne depend
 * pas de sa decision. Lecture seule.
 */
@Service
public class ValidationComiteService {

    private static final Logger log = LoggerFactory.getLogger(ValidationComiteService.class);

    private final TalentService talentService;
    private final ValidationComiteSource validationComiteSource;

    /**
     * Constructeur de Spring. La source des decisions est optionnelle : tant
     * que l'import ne la fournit pas, toutes les decisions sont en attente.
     */
    @Autowired
    public ValidationComiteService(TalentService talentService,
                                   ObjectProvider<ValidationComiteSource> validationComiteSource) {
        this(talentService, validationComiteSource.getIfAvailable(() -> {
            log.warn("Aucune source de decisions du Comite Talent : aucun talent ne sera valide "
                    + "tant que l'import ne les fournit pas");
            return (idCollaborateur, trimestre) -> StatutValidationComite.EN_ATTENTE;
        }));
    }

    public ValidationComiteService(TalentService talentService, ValidationComiteSource validationComiteSource) {
        this.talentService = talentService;
        this.validationComiteSource = Objects.requireNonNull(validationComiteSource, "validationComiteSource");
    }

    /** Regle de 10_TALENTS!H : talent propose ET decision Oui. */
    public boolean estTalentValide(boolean talentPropose, StatutValidationComite statut) {
        Objects.requireNonNull(statut, "statut");
        return talentPropose && statut == StatutValidationComite.OUI;
    }

    /**
     * Decision du comite sur chaque talent propose du trimestre, en attente
     * compris, du meilleur au moins bon en performance.
     */
    @Transactional(readOnly = true)
    public List<DecisionComite> getDecisionsComite(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");
        return deciderPour(talentService.detecterTalents(trimestre), trimestre);
    }

    /**
     * Decisions pour des talents deja detectes, sans nouvelle lecture des
     * scores : le tableau de bord les a deja en main. L'ordre est conserve.
     */
    public List<DecisionComite> deciderPour(List<Score> talentsProposes, Trimestre trimestre) {
        Objects.requireNonNull(talentsProposes, "talentsProposes");
        Objects.requireNonNull(trimestre, "trimestre");

        List<DecisionComite> decisions = new ArrayList<>(talentsProposes.size());
        for (Score score : talentsProposes) {
            StatutValidationComite statut = validationComiteSource.statut(
                    score.getCollaborateur().getIdCollaborateur(), trimestre);
            decisions.add(new DecisionComite(score,
                    Objects.requireNonNullElse(statut, StatutValidationComite.EN_ATTENTE)));
        }
        return decisions;
    }

    /** Talents valides du trimestre, du meilleur au moins bon en performance. */
    @Transactional(readOnly = true)
    public List<Score> getTalentsValides(Trimestre trimestre) {
        List<Score> valides = getDecisionsComite(trimestre).stream()
                .filter(DecisionComite::talentValide)
                .map(DecisionComite::score)
                .toList();
        log.info("Talents valides T{} {} : {}", trimestre.getNumero(), trimestre.getAnnee(), valides.size());
        return valides;
    }
}
