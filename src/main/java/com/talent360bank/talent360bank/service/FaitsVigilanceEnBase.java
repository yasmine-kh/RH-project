package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.DeclarationVigilance;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.DeclarationVigilanceRepository;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Faits de vigilance lus en base (DeclarationVigilance, rempli par l'import
 * de 12_VIGILANCE colonnes F a K).
 *
 * <p>{@link Fallback} : une autre implementation declaree passe devant.
 */
@Component
@Fallback
public class FaitsVigilanceEnBase implements FaitsVigilanceSource {

    private final DeclarationVigilanceRepository repository;

    public FaitsVigilanceEnBase(DeclarationVigilanceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, FaitsVigilance> faitsDuTrimestre(Trimestre trimestre) {
        Map<String, FaitsVigilance> faits = new HashMap<>();
        for (DeclarationVigilance declaration : repository.findByTrimestreAvecCollaborateur(trimestre)) {
            faits.put(declaration.getCollaborateur().getIdCollaborateur(), declaration.versFaits());
        }
        return faits;
    }

    /** Lit les seuls faits du lot, sans charger les collaborateurs. */
    @Override
    @Transactional(readOnly = true)
    public Map<String, FaitsVigilance> faitsDe(Collection<String> idsCollaborateurs, Trimestre trimestre) {
        Map<String, FaitsVigilance> faits = new HashMap<>();
        if (idsCollaborateurs.isEmpty()) {
            return faits;
        }
        for (DeclarationVigilance declaration
                : repository.findByTrimestreEtCollaborateurs(trimestre, idsCollaborateurs)) {
            faits.put(declaration.getCollaborateur().getIdCollaborateur(), declaration.versFaits());
        }
        return faits;
    }
}
