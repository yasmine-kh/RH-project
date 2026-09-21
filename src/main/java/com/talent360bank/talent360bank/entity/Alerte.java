package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "alerte")
public class Alerte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAlerte;

    @Column(name = "type_alerte", nullable = false)
    private String typeAlerte;

    @Column(nullable = false)
    private String severite;

    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAlerte statut;

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