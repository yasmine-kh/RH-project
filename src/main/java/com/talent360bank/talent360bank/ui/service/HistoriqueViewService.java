package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.JournalEvenement;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.TypeEvenement;
import com.talent360bank.talent360bank.repository.JournalEvenementRepository;
import com.talent360bank.talent360bank.ui.model.Historique;
import com.talent360bank.talent360bank.ui.model.Historique.Filtres;
import com.talent360bank.talent360bank.ui.model.Historique.Ligne;
import com.talent360bank.talent360bank.ui.model.OptionFiltre;
import com.talent360bank.talent360bank.ui.model.OptionTrimestre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * L'ecran Historique (journal_evenement) : filtres par type, trimestre, periode et texte, le plus
 * recent d'abord, {@value #TAILLE_PAGE} evenements par page. Deux requetes (page et total), plus la
 * liste des trimestres.
 */
@Service
public class HistoriqueViewService {

    public static final int TAILLE_PAGE = 50;

    private final JournalEvenementRepository repository;
    private final TrimestreCourantService trimestreCourant;

    public HistoriqueViewService(JournalEvenementRepository repository, TrimestreCourantService trimestreCourant) {
        this.repository = repository;
        this.trimestreCourant = trimestreCourant;
    }

    /**
     * @param trimestre "AAAA-N" ou vide (tous) ; inconnu : 404 (TrimestreCourantService)
     * @param du        premier jour inclus (AAAA-MM-JJ), vide sans borne
     * @param au        dernier jour inclus, vide sans borne
     * @param page      a partir de 1
     */
    @Transactional(readOnly = true)
    public Historique construire(String type, String trimestre, String du, String au, String q, Integer page) {
        TypeEvenement typeChoisi = type(type);
        String erreur = null;
        LocalDate debut = null;
        LocalDate fin = null;
        try {
            debut = date(du);
            fin = date(au);
        } catch (DateTimeParseException e) {
            erreur = "Date illisible : utilisez le format jj/mm/aaaa du sélecteur de date.";
        }
        if (debut != null && fin != null && fin.isBefore(debut)) {
            erreur = "La période est inversée : la date de fin précède la date de début.";
        }
        Trimestre trimestreChoisi = vide(trimestre) ? null : trimestreCourant.resoudre(trimestre.trim()).orElse(null);
        String recherche = vide(q) ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Filtres filtres = new Filtres(typeChoisi == null ? null : typeChoisi.name(),
                trimestreChoisi == null ? null : TrimestreCourantService.valeur(trimestreChoisi), debut, fin,
                vide(q) ? null : q.trim());

        int numero = page == null || page < 1 ? 1 : page;
        List<Ligne> lignes = List.of();
        long total = 0;
        int nbPages = 1;
        if (erreur == null) {
            Page<JournalEvenement> resultat = repository.rechercher(typeChoisi, trimestreChoisi,
                    debut == null ? null : debut.atStartOfDay(), fin == null ? null : fin.plusDays(1).atStartOfDay(),
                    recherche, PageRequest.of(numero - 1, TAILLE_PAGE));
            lignes = resultat.getContent().stream().map(HistoriqueViewService::ligne).toList();
            total = resultat.getTotalElements();
            nbPages = Math.max(1, resultat.getTotalPages());
        }
        return new Historique(filtres, types(typeChoisi), trimestres(filtres.trimestre()), lignes, total,
                Math.min(numero, nbPages), nbPages, erreur);
    }

    private static Ligne ligne(JournalEvenement e) {
        return new Ligne(e.getDateEvenement(), e.getType().name(), e.getType().getLibelle(),
                e.getTrimestre() == null ? null : TrimestreCourantService.libelle(e.getTrimestre()),
                e.getDescription(), e.getUtilisateur() == null ? null : e.getUtilisateur().getLogin(),
                e.getMatricule(), e.getLien());
    }

    private static List<OptionFiltre> types(TypeEvenement choisi) {
        List<OptionFiltre> options = new ArrayList<>();
        options.add(new OptionFiltre("", "Tous les événements", choisi == null));
        for (TypeEvenement type : TypeEvenement.values()) {
            options.add(new OptionFiltre(type.name(), type.getLibelle(), type == choisi));
        }
        return options;
    }

    private List<OptionFiltre> trimestres(String choisi) {
        List<OptionFiltre> options = new ArrayList<>();
        options.add(new OptionFiltre("", "Tous les trimestres", choisi == null));
        for (OptionTrimestre option : trimestreCourant.lister()) {
            options.add(new OptionFiltre(option.valeur(), option.libelle(), option.valeur().equals(choisi)));
        }
        return options;
    }

    /** Type inconnu : ignore (tous les types), comme les autres filtres de l'application. */
    private static TypeEvenement type(String code) {
        if (vide(code)) {
            return null;
        }
        for (TypeEvenement type : TypeEvenement.values()) {
            if (type.name().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }

    private static LocalDate date(String valeur) {
        return vide(valeur) ? null : LocalDate.parse(valeur.trim());
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
