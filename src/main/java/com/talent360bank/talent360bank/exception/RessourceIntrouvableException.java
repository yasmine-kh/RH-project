package com.talent360bank.talent360bank.exception;

/** Une donnee attendue par le calcul est absente de la base. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
