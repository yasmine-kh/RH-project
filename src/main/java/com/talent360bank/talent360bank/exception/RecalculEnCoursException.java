package com.talent360bank.talent360bank.exception;

/**
 * Un recalcul du meme trimestre est deja en cours (double clic sur
 * "Enregistrer", calcul lance pendant un import...). Rendu en 409 par
 * l'API : rien n'a ete fait, il suffit de reessayer une fois le premier fini.
 */
public class RecalculEnCoursException extends RuntimeException {

    public RecalculEnCoursException(String message) {
        super(message);
    }
}
