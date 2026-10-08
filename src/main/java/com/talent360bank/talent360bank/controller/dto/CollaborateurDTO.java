package com.talent360bank.talent360bank.controller.dto;

import com.talent360bank.talent360bank.entity.StatutCollaborateur;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CollaborateurDTO {

    @NotBlank(message = "Le matricule est obligatoire")
    private String idCollaborateur;

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @Email(message = "Format d'email invalide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotNull(message = "Le statut est obligatoire")
    private StatutCollaborateur statut;

    public CollaborateurDTO() {}

    public String getIdCollaborateur() { return idCollaborateur; }
    public void setIdCollaborateur(String idCollaborateur) { this.idCollaborateur = idCollaborateur; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public StatutCollaborateur getStatut() { return statut; }
    public void setStatut(StatutCollaborateur statut) { this.statut = statut; }
}