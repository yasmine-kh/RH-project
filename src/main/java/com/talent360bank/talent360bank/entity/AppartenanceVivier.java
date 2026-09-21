package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "appartenance_vivier")
public class AppartenanceVivier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAppartenance;

    @Column(nullable = false)
    private String origine;

    @ManyToOne
    @JoinColumn(name = "id_employe", nullable = false)
    private Employe employe;

    @ManyToOne
    @JoinColumn(name = "id_vivier", nullable = false)
    private Vivier vivier;

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

    public Employe getEmploye() {
        return employe;
    }

    public void setEmploye(Employe employe) {
        this.employe = employe;
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