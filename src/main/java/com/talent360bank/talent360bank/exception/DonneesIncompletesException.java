package com.talent360bank.talent360bank.exception;

/** Les donnees existent mais ne permettent pas de mener le calcul a son terme. */
public class DonneesIncompletesException extends RuntimeException {

    public DonneesIncompletesException(String message) {
        super(message);
    }
}
