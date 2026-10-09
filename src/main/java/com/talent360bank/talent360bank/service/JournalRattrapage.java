package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.ImportExcel;
import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.ValidationComite;
import com.talent360bank.talent360bank.entity.ValidationSuccession;
import com.talent360bank.talent360bank.repository.ImportExcelRepository;
import com.talent360bank.talent360bank.repository.JournalEvenementRepository;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.repository.ValidationSuccessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remplit l'historique a partir de ce que la base sait deja, pour qu'il ne soit pas vide le premier
 * jour : chaque ligne de import_excel, et chaque decision du Comite datee (validation_comite,
 * validation_succession). Au demarrage, et sans doublon : une ligne deja journalisee (meme
 * reference, ecrite au moment de l'action ou par un rattrapage precedent) est ignoree.
 *
 * <p>Limite : une decision modifiee plusieurs fois avant ce rattrapage n'a laisse que son dernier
 * etat ; un seul evenement est donc repris, a sa date. Un import n'a qu'une date (sans heure).
 */
@Component
public class JournalRattrapage implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JournalRattrapage.class);

    private final JournalService journalService;
    private final JournalEvenementRepository journalRepository;
    private final ImportExcelRepository importExcelRepository;
    private final ValidationComiteRepository validationComiteRepository;
    private final ValidationSuccessionRepository validationSuccessionRepository;

    public JournalRattrapage(JournalService journalService, JournalEvenementRepository journalRepository,
                             ImportExcelRepository importExcelRepository,
                             ValidationComiteRepository validationComiteRepository,
                             ValidationSuccessionRepository validationSuccessionRepository) {
        this.journalService = journalService;
        this.journalRepository = journalRepository;
        this.importExcelRepository = importExcelRepository;
        this.validationComiteRepository = validationComiteRepository;
        this.validationSuccessionRepository = validationSuccessionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int repris = rattraper();
        if (repris > 0) {
            log.info("Historique : {} evenement(s) repris des imports et des decisions deja enregistres", repris);
        }
    }

    /** @return nombre d'evenements ajoutes */
    @Transactional
    public int rattraper() {
        List<JournalEvenement> candidats = new ArrayList<>();
        for (ImportExcel journal : importExcelRepository.findAllRecentsDabord()) {
            if (journal.getDateImport() != null) {
                candidats.add(journalService.evenementImport(journal, journal.getDateImport().atStartOfDay(),
                        journal.getUtilisateur()));
            }
        }
        for (ValidationComite v : validationComiteRepository.findSaisiesAvecDetails()) {
            candidats.add(journalService.evenementTalent(v, v.getCollaborateur().getNomComplet(), null,
                    v.getDateDecision(), v.getUtilisateur()));
        }
        for (ValidationSuccession v : validationSuccessionRepository.findDateesAvecDetails()) {
            candidats.add(journalService.evenementSuccession(v, null, v.getDateDecision(), v.getUtilisateur()));
        }
        if (candidats.isEmpty()) {
            return 0;
        }
        Set<String> existantes = new HashSet<>();
        List<String> references = candidats.stream().map(JournalEvenement::getReference).toList();
        for (int i = 0; i < references.size(); i += 500) {
            existantes.addAll(journalRepository.findReferencesExistantes(
                    references.subList(i, Math.min(references.size(), i + 500))));
        }
        List<JournalEvenement> nouveaux = candidats.stream()
                .filter(e -> !existantes.contains(e.getReference())).toList();
        journalRepository.saveAll(nouveaux);
        return nouveaux.size();
    }
}
