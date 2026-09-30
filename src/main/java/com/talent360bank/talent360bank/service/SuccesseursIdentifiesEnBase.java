package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.repository.SuccesseurIdentifieRepository;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
