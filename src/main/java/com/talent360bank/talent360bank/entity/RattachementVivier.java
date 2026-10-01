package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.service.enums.VivierThematique;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Vivier thematique d'une direction (10_TALENTS, colonnes Direction et Vivier
 * thematique). Les noms de direction sont une donnee de l'import, pas une
 * regle du moteur.
 */
@Entity
@Table(name = "rattachement_vivier", uniqueConstraints = @UniqueConstraint(
        name = "uk_rattachement_vivier_direction",
        columnNames = {"direction"}))
public class RattachementVivier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRattachement;

    /** Meme longueur que Collaborateur.direction. */
    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String direction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VivierThematique vivier;

    public RattachementVivier() {
    }

    public RattachementVivier(String direction, VivierThematique vivier) {
        this.direction = direction;
        this.vivier = vivier;
    }

    public Integer getIdRattachement() {
        return idRattachement;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public VivierThematique getVivier() {
        return vivier;
    }

    public void setVivier(VivierThematique vivier) {
        this.vivier = vivier;
    }
}
