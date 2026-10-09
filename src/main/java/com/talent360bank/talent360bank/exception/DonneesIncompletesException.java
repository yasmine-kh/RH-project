package com.talent360bank.talent360bank.exception;

/** Les donnees existent mais ne permettent pas de mener le calcul a son terme. */
public class DonneesIncompletesException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    public DonneesIncompletesException(String message) {
        super(message);
    }
}
