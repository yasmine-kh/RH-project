package com.talent360bank.talent360bank.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class CollaborateurRequest {

    @NotBlank(message = "L'ID du collaborateur est obligatoire")
    private String idCollaborateur;

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    public CollaborateurRequest() {
    }

    public String getIdCollaborateur() {
        return idCollaborateur;
    }

    public void setIdCollaborateur(String idCollaborateur) {
        this.idCollaborateur = idCollaborateur;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }
}