package com.talent360bank.talent360bank.ui.controller;

import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import com.talent360bank.talent360bank.ui.model.ElementMenu;
import com.talent360bank.talent360bank.ui.model.ProfilActif;
import com.talent360bank.talent360bank.ui.model.Profils;
import com.talent360bank.talent360bank.ui.service.MenuProfilService;
import com.talent360bank.talent360bank.ui.service.ProfilsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Pour toutes les pages (controleurs de ui.controller, jamais l'API) :
 * <ul>
 *   <li>les options du selecteur de profil de la sidebar, lues une fois par
 *   requete ({@link ProfilsService}, trois requetes) ;</li>
 *   <li>le profil choisi (session HTTP) et le menu de ce profil, sans requete ;</li>
 *   <li>une ressource introuvable (matricule, code d'entite, trimestre) donne
 *   la page 404 en francais, jamais une trace d'erreur.</li>
 * </ul>
 */
@ControllerAdvice(basePackages = "com.talent360bank.talent360bank.ui.controller")
public class ProfilsAdvice {

    private final ObjectProvider<ProfilsService> profilsService;

    /**
     * Service optionnel : les tranches de test {@code @WebMvcTest} chargent tous les
     * ControllerAdvice sans les services ; la sidebar masque alors le selecteur.
     */
    public ProfilsAdvice(ObjectProvider<ProfilsService> profilsService) {
        this.profilsService = profilsService;
    }

    @ModelAttribute("profils")
    public Profils profils() {
        ProfilsService service = profilsService.getIfAvailable();
        return service == null ? null : service.options();
    }

    /** Le profil choisi, garde en session (RH par defaut) : aucune requete. */
    @ModelAttribute("profilActif")
    public ProfilActif profilActif(HttpSession session) {
        Object profil = session.getAttribute(ProfilActif.SESSION);
        return profil instanceof ProfilActif actif ? actif : ProfilActif.RH;
    }

    /** Le menu du profil choisi ; liens pour le trimestre demande ({@code ?trimestre=}). */
    @ModelAttribute("menu")
    public List<ElementMenu> menu(HttpSession session, HttpServletRequest requete) {
        String trimestre = requete.getParameter("trimestre");
        return MenuProfilService.menu(profilActif(session),
                trimestre == null || trimestre.isBlank() ? null : trimestre.trim());
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public String introuvable(RessourceIntrouvableException e, Model model, HttpServletResponse reponse) {
        return pageIntrouvable(e.getMessage(), model, reponse);
    }

    /** 404 d'un trimestre demande inconnu (TrimestreCourantService) ; les autres statuts restent ceux de Spring. */
    @ExceptionHandler(ResponseStatusException.class)
    public String statut(ResponseStatusException e, Model model, HttpServletResponse reponse) {
        if (e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
            throw e;
        }
        return pageIntrouvable(e.getReason(), model, reponse);
    }

    private static String pageIntrouvable(String message, Model model, HttpServletResponse reponse) {
        reponse.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("message", message);
        return "erreur/404";
    }
}
