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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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

    private static final Logger log = LoggerFactory.getLogger(ProfilsAdvice.class);

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
    /** La page courante (chemin et parametres), pour que le selecteur de profil y revienne. */
    @ModelAttribute("pageCourante")
    public String pageCourante(HttpServletRequest requete) {
        String requeteTexte = requete.getQueryString();
        return requete.getRequestURI() + (requeteTexte == null ? "" : "?" + requeteTexte);
    }

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

    /**
     * Erreur inattendue d'un ecran : page "Une erreur est survenue" (erreur/500), statut 500, sans
     * aucun detail technique ; l'exception complete va au journal avec une reference courte, affichee
     * a l'utilisateur pour retrouver la ligne du journal. Les erreurs qui portent deja leur statut
     * (parametre manquant 400, methode 405, ressource 404...) et les refus d'acces suivent leur
     * chemin habituel.
     */
    @ExceptionHandler(Exception.class)
    public String erreurInattendue(Exception e, HttpServletRequest requete, Model model,
                                   HttpServletResponse reponse) throws Exception {
        if (e instanceof ErrorResponse || e instanceof AccessDeniedException) {
            throw e;
        }
        String reference = reference();
        log.error("Erreur inattendue [ref {}] sur {} {}", reference, requete.getMethod(), requete.getRequestURI(), e);
        reponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("reference", reference);
        return "erreur/500";
    }

    /** 8 caracteres, assez pour retrouver l'erreur dans le journal. */
    static String reference() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String pageIntrouvable(String message, Model model, HttpServletResponse reponse) {
        reponse.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("message", message);
        return "erreur/404";
    }
}
