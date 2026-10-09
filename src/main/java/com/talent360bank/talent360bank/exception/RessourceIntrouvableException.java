package com.talent360bank.talent360bank.exception;

/** Une donnee attendue par le calcul est absente de la base. */
public class RessourceIntrouvableException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
