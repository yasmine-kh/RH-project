package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "vivier")
public class Vivier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idVivier;

    @NotBlank(message = "Le nom de la catégorie est obligatoire")
    @Column(name = "nom_categorie", nullable = false)
    private String nomCategorie;

    private String description;

    /**
     * Code technique fixe d'un vivier gere par le moteur (ex. RELEVE), null
     * pour un vivier saisi par le RH. Le moteur retrouve ses viviers par ce
     * code et jamais par leur nom, que le RH peut renommer a l'ecran.
     */
    @Column(unique = true, length = 30)
    private String code;

    public Vivier() {
    }

    public Integer getIdVivier() {
        return idVivier;
    }

    public void setIdVivier(Integer idVivier) {
        this.idVivier = idVivier;
    }

    public String getNomCategorie() {
        return nomCategorie;
    }

    public void setNomCategorie(String nomCategorie) {
        this.nomCategorie = nomCategorie;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}