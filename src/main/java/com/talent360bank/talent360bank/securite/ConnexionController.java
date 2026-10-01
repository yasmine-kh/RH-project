package com.talent360bank.talent360bank.securite;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Pages de connexion et d'acces refuse. Le traitement du formulaire
 * (POST /login) et la deconnexion (POST /logout) sont faits par Spring
 * Security, pas ici.
 */
@Controller
public class ConnexionController {

    @GetMapping("/login")
    public String connexion() {
        return "login";
    }

    /**
     * Cible du renvoi (forward) fait par SecurityConfig quand une page est
     * refusee : toutes les methodes, car la requete refusee peut etre un POST.
     */
    @RequestMapping("/erreur/403")
    public String accesRefuse() {
        return "erreur/403";
    }
}
