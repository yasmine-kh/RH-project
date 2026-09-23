package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
@Table(name = "rapport")
public class Rapport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRapport;

    @NotBlank(message = "Le type de rapport est obligatoire")
    @Column(nullable = false)
    private String type;

    @NotBlank(message = "Le format est obligatoire")
    @Column(nullable = false)
    private String format;

    @NotNull(message = "La date de génération est obligatoire")
    @Column(name = "date_generation", nullable = false)
    private LocalDate dateGeneration;

    @NotNull(message = "L'utilisateur est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_utilisateur", nullable = false)
    private UtilisateurRH utilisateur;

    public Rapport() {
    }

    public Integer getIdRapport() {
        return idRapport;
    }

    public void setIdRapport(Integer idRapport) {
        this.idRapport = idRapport;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public LocalDate getDateGeneration() {
        return dateGeneration;
    }

    public void setDateGeneration(LocalDate dateGeneration) {
        this.dateGeneration = dateGeneration;
    }

    public UtilisateurRH getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(UtilisateurRH utilisateur) {
        this.utilisateur = utilisateur;
    }
}