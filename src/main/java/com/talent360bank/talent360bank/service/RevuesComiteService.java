package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.repository.ValidationComiteRepository;
import com.talent360bank.talent360bank.repository.ValidationSuccessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Derniere revue" des ecrans Viviers et Postes critiques : la date de la derniere decision du
 * Comite Talent saisie dans l'application pour le trimestre.
 *
 * <ul>
 *   <li>Poste critique : la derniere decision sur l'un de ses successeurs (validation_succession).</li>
 *   <li>Vivier : la derniere decision sur l'un de ses membres, comme talent (validation_comite) ou
 *       comme successeur d'un poste critique (validation_succession).</li>
 * </ul>
 * Pas de date (aucune decision, ou seulement des decisions importees du classeur, qui n'en portent
 * pas) : absent de la map, l'ecran affiche "—". Deux requetes.
 */
@Service
public class RevuesComiteService {

    private final ValidationComiteRepository validationComiteRepository;
    private final ValidationSuccessionRepository validationSuccessionRepository;

    public RevuesComiteService(ValidationComiteRepository validationComiteRepository,
                               ValidationSuccessionRepository validationSuccessionRepository) {
        this.validationComiteRepository = validationComiteRepository;
        this.validationSuccessionRepository = validationSuccessionRepository;
    }

    /** Poste_ID → date de la derniere decision sur ses successeurs. */
    @Transactional(readOnly = true)
    public Map<String, LocalDate> parPoste(Trimestre trimestre) {
        Map<String, LocalDate> dates = new HashMap<>();
        for (Object[] ligne : validationSuccessionRepository.findDates(trimestre)) {
            garderLaPlusRecente(dates, (String) ligne[0], (LocalDateTime) ligne[2]);
        }
        return dates;
    }

    /**
     * @param membres code du vivier → matricules de ses membres (VivierSyntheseService)
     * @return code du vivier → date de la derniere decision sur l'un de ses membres
     */
    @Transactional(readOnly = true)
    public Map<String, LocalDate> parVivier(Trimestre trimestre, Map<String, Set<String>> membres) {
        Map<String, LocalDate> parMatricule = new HashMap<>();
        for (Object[] ligne : validationComiteRepository.findDates(trimestre)) {
            garderLaPlusRecente(parMatricule, (String) ligne[0], (LocalDateTime) ligne[1]);
        }
        List<Object[]> successions = validationSuccessionRepository.findDates(trimestre);
        for (Object[] ligne : successions) {
            garderLaPlusRecente(parMatricule, (String) ligne[1], (LocalDateTime) ligne[2]);
        }
        Map<String, LocalDate> dates = new HashMap<>();
        membres.forEach((code, matricules) -> matricules.stream()
                .map(parMatricule::get)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .ifPresent(date -> dates.put(code, date)));
        return dates;
    }

    private static void garderLaPlusRecente(Map<String, LocalDate> dates, String cle, LocalDateTime date) {
        if (date != null) {
            dates.merge(cle, date.toLocalDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
    }
}
