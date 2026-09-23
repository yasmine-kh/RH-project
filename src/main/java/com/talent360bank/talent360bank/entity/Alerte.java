package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
@Table(name = "alerte")
public class Alerte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAlerte;

    @NotBlank(message = "Le type d'alerte est obligatoire")
    @Column(name = "type_alerte", nullable = false)
    private String typeAlerte;

    @NotBlank(message = "La sévérité est obligatoire")
    @Column(nullable = false)
    private String severite;

    @NotNull(message = "La date de création est obligatoire")
    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation;

    @NotNull(message = "Le statut est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAlerte statut;

    @NotNull(message = "L'employé est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_employe", nullable = false)
    private Employe employe;

    public Alerte() {
    }

    public Integer getIdAlerte() {
        return idAlerte;
    }

    public void setIdAlerte(Integer idAlerte) {
        this.idAlerte = idAlerte;
    }

    public String getTypeAlerte() {
        return typeAlerte;
    }

    public void setTypeAlerte(String typeAlerte) {
        this.typeAlerte = typeAlerte;
    }

    public String getSeverite() {
        return severite;
    }

    public void setSeverite(String severite) {
        this.severite = severite;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public StatutAlerte getStatut() {
        return statut;
    }

    public void setStatut(StatutAlerte statut) {
        this.statut = statut;
    }

    public Employe getEmploye() {
        return employe;
    }

    public void setEmploye(Employe employe) {
        this.employe = employe;
    }
}