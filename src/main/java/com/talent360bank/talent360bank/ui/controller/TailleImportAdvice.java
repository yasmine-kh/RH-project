package com.talent360bank.talent360bank.ui.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Fichier au-dela de spring.servlet.multipart.max-file-size envoye par la page
 * d'import : retour sur /import avec un message clair, au lieu de la page
 * d'erreur 413 de Spring.
 *
 * <p>Sans selecteur de controleur, volontairement : le depassement est detecte
 * en lisant la requete, avant que Spring ne sache quel controleur l'aurait
 * traitee, et un conseil limite a un controleur ne serait jamais appele.
 * Toute autre URL (l'API notamment) relance l'exception, qui garde son
 * traitement habituel (413).
 *
 * <p>Sur Tomcat, le corps trop gros n'est pas lu : Spring Security ne trouve
 * pas le jeton CSRF du formulaire et renvoie vers la page 403, ou le
 * depassement est detecte a son tour. L'URL d'origine est alors celle du
 * renvoi (attribut jakarta.servlet.forward.request_uri). Rien n'est importe
 * dans les deux cas : la redirection ne fait qu'afficher le message
 * (ImportPageHttpTest).
 */
@ControllerAdvice
public class TailleImportAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fichierTropVolumineux(MaxUploadSizeExceededException exception, HttpServletRequest requete) {
        Object origine = requete.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        String uri = origine instanceof String renvoi ? renvoi : requete.getRequestURI();
        String chemin = uri.substring(Math.min(uri.length(), requete.getContextPath().length()));
        if (!"/import".equals(chemin)) {
            throw exception;
        }
        return "redirect:/import?" + ImportPageController.PARAMETRE_ERREUR + "=" + ImportPageController.ERREUR_TAILLE;
    }
}
