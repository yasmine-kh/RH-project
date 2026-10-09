package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Collaborateur;
import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.Matrice9Box;
import com.talent360bank.talent360bank.entity.Score;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.repository.CollaborateurRepository;
import com.talent360bank.talent360bank.repository.JournalEvenementRepository;
import com.talent360bank.talent360bank.repository.Matrice9BoxRepository;
import com.talent360bank.talent360bank.repository.ScoreRepository;
import com.talent360bank.talent360bank.service.NeufBoxService;
import com.talent360bank.talent360bank.service.VigilanceService;
import com.talent360bank.talent360bank.service.resultat.ResultatVigilance;
import com.talent360bank.talent360bank.ui.model.Historique;
import com.talent360bank.talent360bank.ui.model.HistoriqueCollaborateur;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * La section "Historique" du Talent Passport, a partir de ce qui est deja en base : un score par
 * trimestre calcule (performance, potentiel, categories, case 9-Box enregistree), la vigilance de
 * chaque trimestre par la regle de l'ecran Vigilance (VigilanceService.evaluerTrimestre : seuls les
 * collaborateurs qui ont une donnee de vigilance ont un niveau), et les evenements du journal.
 */
@Service
public class HistoriqueCollaborateurViewService {

    private final CollaborateurRepository collaborateurRepository;
    private final ScoreRepository scoreRepository;
    private final Matrice9BoxRepository matrice9BoxRepository;
    private final VigilanceService vigilanceService;
    private final JournalEvenementRepository journalRepository;

    public HistoriqueCollaborateurViewService(CollaborateurRepository collaborateurRepository,
                                              ScoreRepository scoreRepository,
                                              Matrice9BoxRepository matrice9BoxRepository,
                                              VigilanceService vigilanceService,
                                              JournalEvenementRepository journalRepository) {
        this.collaborateurRepository = collaborateurRepository;
        this.scoreRepository = scoreRepository;
        this.matrice9BoxRepository = matrice9BoxRepository;
        this.vigilanceService = vigilanceService;
        this.journalRepository = journalRepository;
    }

    /**
     * Sans transaction propre : la vigilance de chaque trimestre a la sienne, et un trimestre sans
     * reglages de vigilance (exception attrapee ici) ne doit pas annuler la lecture des autres.
     *
     * @param courant trimestre affiche par la fiche (marque dans le tableau), peut etre null
     */
    public HistoriqueCollaborateur construire(String matricule, Trimestre courant) {
        Collaborateur collaborateur = collaborateurRepository.findById(matricule).orElse(null);
        List<HistoriqueCollaborateur.Trimestre> trimestres = List.of();
        if (collaborateur != null) {
            Map<String, Integer> numeros = new HashMap<>();
            for (Matrice9Box case9Box : matrice9BoxRepository.findAll()) {
                numeros.put(case9Box.getCategorie(), NeufBoxService.numeroCase(case9Box));
            }
            trimestres = scoreRepository.findHistorique(collaborateur).stream()
                    .map(score -> ligne(score, numeros, courant))
                    .toList();
        }
        List<Historique.Ligne> evenements = journalRepository.findByMatricule(matricule).stream()
                .map(HistoriqueCollaborateurViewService::evenement).toList();
        return new HistoriqueCollaborateur(trimestres, evenements);
    }

    private HistoriqueCollaborateur.Trimestre ligne(Score score, Map<String, Integer> numeros, Trimestre courant) {
        Trimestre t = score.getTrimestre();
        ResultatVigilance vigilance = vigilance(score.getCollaborateur().getIdCollaborateur(), t);
        return new HistoriqueCollaborateur.Trimestre(TrimestreCourantService.valeur(t),
                TrimestreCourantService.libelle(t), score.getScorePerformance(),
                score.getCategoriePerformance() == null ? null : score.getCategoriePerformance().getLibelle(),
                score.getScorePotentiel(),
                score.getCategoriePotentiel() == null ? null : score.getCategoriePotentiel().getLibelle(),
                score.getPositionBox() == null ? null : numeros.get(score.getPositionBox()), score.getPositionBox(),
                vigilance == null ? null : vigilance.niveau().name(),
                vigilance == null ? null : vigilance.niveau().getLibelle(),
                courant != null && Objects.equals(courant.getIdTrimestre(), t.getIdTrimestre()));
    }

    /** Niveau du trimestre ; null sans donnee de vigilance ou sans reglages pour ce trimestre. */
    private ResultatVigilance vigilance(String matricule, Trimestre trimestre) {
        try {
            return vigilanceService.evaluerTrimestre(trimestre).stream()
                    .filter(r -> r.collaborateur().getIdCollaborateur().equals(matricule))
                    .findFirst().orElse(null);
        } catch (RessourceIntrouvableException | DonneesIncompletesException e) {
            return null;
        }
    }

    private static Historique.Ligne evenement(JournalEvenement e) {
        return new Historique.Ligne(e.getDateEvenement(), e.getType().name(), e.getType().getLibelle(),
                e.getTrimestre() == null ? null : TrimestreCourantService.libelle(e.getTrimestre()),
                e.getDescription(), e.getUtilisateur() == null ? null : e.getUtilisateur().getLogin(),
                e.getMatricule(), e.getLien());
    }
}
