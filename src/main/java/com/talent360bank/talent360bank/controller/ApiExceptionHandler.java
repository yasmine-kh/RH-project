package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.controller.dto.ErreurApi;
import com.talent360bank.talent360bank.exception.DonneesIncompletesException;
import com.talent360bank.talent360bank.exception.RessourceIntrouvableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Traduit les exceptions du moteur en codes HTTP.
 *
 * <p>Porte volontairement sur le seul package des controleurs d'API : les
 * pages Thymeleaf doivent continuer a rendre des pages d'erreur HTML, pas du
 * JSON. Contrepartie de ce cloisonnement : les erreurs levees avant que Spring
 * n'ait resolu le controleur (verbe non supporte, URL inconnue) ne passent pas
 * par ici, faute de controleur a rattacher. Elles gardent le traitement par
 * defaut de Spring, qui rend le bon statut sans corps JSON.
 *
 * <p>La distinction 404 / 422 est celle que fait deja le domaine :
 * RessourceIntrouvableException dit qu'une donnee attendue est absente de la
 * base, DonneesIncompletesException dit qu'elle est presente mais ne permet
 * pas de mener le calcul a son terme. La seconde n'est pas une erreur de
 * requete, le client ne peut rien y changer : c'est la configuration ou la
 * saisie qu'il faut completer.
 */
@RestControllerAdvice(basePackages = "com.talent360bank.talent360bank.controller")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ErreurApi> introuvable(RessourceIntrouvableException exception) {
        return reponse(HttpStatus.NOT_FOUND, "ressource_introuvable", exception.getMessage());
    }

    @ExceptionHandler(DonneesIncompletesException.class)
    public ResponseEntity<ErreurApi> incompletes(DonneesIncompletesException exception) {
        return reponse(HttpStatus.UNPROCESSABLE_ENTITY, "donnees_incompletes", exception.getMessage());
    }

    /**
     * Les services levent IllegalArgumentException avec un message destine au
     * client (trimestre hors bornes, fichier vide...). NumberFormatException en
     * herite mais porte un message de la JVM : il n'est pas renvoye.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErreurApi> argumentInvalide(IllegalArgumentException exception) {
        if (exception instanceof NumberFormatException) {
            log.debug("Nombre illisible", exception);
            return reponse(HttpStatus.BAD_REQUEST, "argument_invalide", "Valeur numerique invalide");
        }
        return reponse(HttpStatus.BAD_REQUEST, "argument_invalide", exception.getMessage());
    }

    /** Violations inter-blocs des reglages, detectees une fois les douze blocs reunis. */
    @ExceptionHandler(ParametreInvalideException.class)
    public ResponseEntity<ErreurApi> parametreInvalide(ParametreInvalideException exception) {
        return ResponseEntity.badRequest().body(new ErreurApi(
                HttpStatus.BAD_REQUEST.value(), "reglages_invalides",
                exception.getMessage(), exception.getViolations()));
    }

    /** Violations de @Valid sur un corps de requete, champ par champ. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApi> corpsInvalide(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getAllErrors().stream()
                .map(erreur -> erreur instanceof FieldError champ
                        ? champ.getField() + " : " + champ.getDefaultMessage()
                        : erreur.getDefaultMessage())
                .sorted()
                .toList();

        return ResponseEntity.badRequest().body(new ErreurApi(
                HttpStatus.BAD_REQUEST.value(), "corps_invalide",
                "Le corps de la requete est invalide", details));
    }

    /**
     * Erreurs de requete que Spring detecte avant d'atteindre le controleur :
     * parametre absent, valeur d'enum inconnue, corps JSON illisible.
     *
     * <p>Elles sont traitees explicitement car le filet de securite plus bas
     * les capterait sinon et rendrait 500 la ou le client a simplement mal
     * forme sa requete.
     *
     * <p>Le message de Spring n'est pas renvoye : il peut citer des classes
     * internes (erreur Jackson, par exemple). Il reste dans le journal ; la
     * reponse ne nomme que le parametre en cause.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class,
            MultipartException.class})
    public ResponseEntity<ErreurApi> requeteMalFormee(Exception exception) {
        log.debug("Requete mal formee", exception);
        return reponse(HttpStatus.BAD_REQUEST, "requete_mal_formee", messageRequeteMalFormee(exception));
    }

    private static String messageRequeteMalFormee(Exception exception) {
        if (exception instanceof MissingServletRequestParameterException absent) {
            return "Parametre obligatoire absent : " + absent.getParameterName();
        }
        if (exception instanceof MethodArgumentTypeMismatchException invalide) {
            return "Valeur invalide pour le parametre " + invalide.getName();
        }
        if (exception instanceof MissingServletRequestPartException absente) {
            return "Partie obligatoire absente : " + absente.getRequestPartName();
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "Corps de la requete illisible (JSON mal forme ou valeur invalide)";
        }
        return "Requete multipart invalide";
    }

    /**
     * Fichier au-dela de spring.servlet.multipart.max-file-size. Ne passe par
     * ici que si le depassement est detecte apres la resolution du controleur ;
     * sinon Spring rend 413 par defaut, sans corps JSON (voir plus haut).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErreurApi> fichierTropVolumineux(MaxUploadSizeExceededException exception) {
        log.debug("Fichier refuse", exception);
        return reponse(HttpStatus.PAYLOAD_TOO_LARGE, "fichier_trop_volumineux", "Fichier trop volumineux");
    }

    /**
     * Refus leve dans un controleur ou un service (controle de perimetre),
     * apres le filtre de Spring Security. Meme corps que le refus du filtre
     * (SecurityConfig) ; sans ce handler, le filet de securite rendrait 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApi> accesRefuse(AccessDeniedException exception) {
        return reponse(HttpStatus.FORBIDDEN, "acces_refuse", "Votre profil ne permet pas cette action");
    }

    /** Statut deja porte par l'exception : on le respecte au lieu de le reecrire. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErreurApi> statutExplicite(ResponseStatusException exception) {
        HttpStatus statut = HttpStatus.valueOf(exception.getStatusCode().value());
        return reponse(statut, statut.name().toLowerCase(), exception.getReason());
    }

    /**
     * Filet de securite : sans lui une exception non prevue remonterait en
     * page d'erreur HTML au milieu d'une API JSON. Seul cas ou l'on logue en
     * erreur, les autres sont des reponses metier normales.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApi> imprevue(Exception exception) {
        log.error("Erreur non geree dans l'API", exception);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "erreur_interne",
                "Une erreur inattendue est survenue");
    }

    private ResponseEntity<ErreurApi> reponse(HttpStatus statut, String code, String message) {
        return ResponseEntity.status(statut).body(ErreurApi.de(statut.value(), code, message));
    }
}
