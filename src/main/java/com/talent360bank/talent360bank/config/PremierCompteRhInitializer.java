package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.entity.Role;
import com.talent360bank.talent360bank.entity.Utilisateur;
import com.talent360bank.talent360bank.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cree le premier compte RH au demarrage, s'il n'existe encore aucun compte
 * RH, a partir de deux variables d'environnement : {@link #VARIABLE_LOGIN} et
 * {@link #VARIABLE_MOT_DE_PASSE}. Aucun mot de passe par defaut n'existe, ni
 * dans le code ni dans application.properties.
 *
 * <p>Si les variables manquent, rien n'est cree et un avertissement explique
 * quoi faire : personne ne peut se connecter tant qu'elles ne sont pas
 * posees. Une fois le compte cree, les variables ne servent plus (un compte RH
 * existe) : on peut les retirer.
 */
@Component
public class PremierCompteRhInitializer implements ApplicationRunner {

    public static final String VARIABLE_LOGIN = "TALENT360_ADMIN_LOGIN";
    public static final String VARIABLE_MOT_DE_PASSE = "TALENT360_ADMIN_PASSWORD";

    /** Longueur minimale du mot de passe initial. */
    public static final int LONGUEUR_MIN_MOT_DE_PASSE = 12;

    private static final Logger log = LoggerFactory.getLogger(PremierCompteRhInitializer.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environnement;

    public PremierCompteRhInitializer(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder,
                                      Environment environnement) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.environnement = environnement;
    }

    @Override
    public void run(ApplicationArguments args) {
        creerSiAucunRh();
    }

    /** @return true si le compte a ete cree */
    public boolean creerSiAucunRh() {
        if (utilisateurRepository.existsByRole(Role.RH)) {
            return false;
        }
        String login = valeur(VARIABLE_LOGIN);
        if (login != null) {
            login = login.trim();
        }
        // Pas de trim : des espaces en bout de mot de passe en font partie.
        String motDePasse = valeur(VARIABLE_MOT_DE_PASSE);
        if (login == null || motDePasse == null) {
            log.warn("Aucun compte RH en base et variables {} / {} absentes : aucun compte cree, personne ne peut "
                            + "se connecter. Definir les deux variables d'environnement puis redemarrer "
                            + "(voir docs/guide-developpeur.md).", VARIABLE_LOGIN, VARIABLE_MOT_DE_PASSE);
            return false;
        }
        if (login.length() > Utilisateur.LONGUEUR_LOGIN) {
            log.warn("{} depasse {} caracteres : aucun compte RH cree.", VARIABLE_LOGIN, Utilisateur.LONGUEUR_LOGIN);
            return false;
        }
        if (motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            log.warn("{} fait moins de {} caracteres : aucun compte RH cree.", VARIABLE_MOT_DE_PASSE,
                    LONGUEUR_MIN_MOT_DE_PASSE);
            return false;
        }
        if (utilisateurRepository.findByLogin(login).isPresent()) {
            log.warn("Le login {} est deja pris par un compte non RH : aucun compte RH cree. Choisir un autre login.",
                    login);
            return false;
        }

        utilisateurRepository.save(new Utilisateur(login, passwordEncoder.encode(motDePasse), Role.RH));
        // Le mot de passe n'est jamais logue.
        log.info("Premier compte RH cree : {}", login);
        return true;
    }

    private String valeur(String nom) {
        String valeur = environnement.getProperty(nom);
        return valeur == null || valeur.isBlank() ? null : valeur;
    }
}
