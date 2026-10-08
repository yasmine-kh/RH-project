package com.talent360bank.talent360bank.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class CompetenceDTO {

    private Integer idCompetence;

    @NotBlank(message = "Le libellé de la compétence est obligatoire")
    private String libelle;

    private String description;

    private boolean archivee = false;

    public CompetenceDTO() {}

    public Integer getIdCompetence() {
        return idCompetence;
    }
    public void setIdCompetence(Integer idCompetence) {
        this.idCompetence = idCompetence;
    }

    public String getLibelle() {
        return libelle;
    }
    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArchivee() {
        return archivee;
    }
    public void setArchivee(boolean archivee) {
        this.archivee = archivee;
    }
}