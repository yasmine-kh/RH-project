package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Successeurs identifies lus en base (SuccesseurIdentifie, rempli par l'import
 * de 09_SUCCESSION).
 *
 * <p>{@link Fallback} : une autre implementation declaree passe devant.
 */
@Component
@Fallback
public class SuccesseursIdentifiesEnBase implements SuccesseurIdentifieSource {

    private final SuccesseurIdentifieRepository repository;

    public SuccesseursIdentifiesEnBase(SuccesseurIdentifieRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> successeursIdentifies(String posteId) {
        return repository.findIdsCollaborateursParPoste(posteId);
    }

    /** Une requete pour tous les postes. */
    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> successeursParPoste(Collection<String> posteIds) {
        Map<String, List<String>> successeurs = new HashMap<>();
        if (posteIds.isEmpty()) {
            return successeurs;
        }
        for (String posteId : posteIds) {
            successeurs.put(posteId, new ArrayList<>());
        }
        for (Object[] paire : repository.findPairesParPostes(posteIds)) {
            successeurs.get((String) paire[0]).add((String) paire[1]);
        }
        return successeurs;
    }
}
