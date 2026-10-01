package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Presence d'un collaborateur dans un vivier pour un trimestre. Une seule ligne par
 * (collaborateur, vivier, trimestre), quelle que soit l'origine : un collaborateur deja
 * present par un import ou une saisie RH n'est pas double par le moteur.
 */
@Entity
@Table(name = "appartenance_vivier", uniqueConstraints = @UniqueConstraint(
        name = "uk_appartenance_collaborateur_vivier_trimestre",
        columnNames = {"id_collaborateur", "id_vivier", "id_trimestre"}))
public class AppartenanceVivier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAppartenance;

    @NotBlank(message = "L'origine est obligatoire")
    @Column(nullable = false)
    private String origine;

    @NotNull(message = "Le collaborateur est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @NotNull(message = "Le vivier est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_vivier", nullable = false)
    private Vivier vivier;

    @NotNull(message = "Le trimestre est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    public AppartenanceVivier() {
    }

    public Integer getIdAppartenance() {
        return idAppartenance;
    }

    public void setIdAppartenance(Integer idAppartenance) {
        this.idAppartenance = idAppartenance;
    }

    public String getOrigine() {
        return origine;
    }

    public void setOrigine(String origine) {
        this.origine = origine;
    }

    public Collaborateur getCollaborateur() {
        return collaborateur;
    }

    public void setCollaborateur(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }

    public Vivier getVivier() {
        return vivier;
    }

    public void setVivier(Vivier vivier) {
        this.vivier = vivier;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }
}