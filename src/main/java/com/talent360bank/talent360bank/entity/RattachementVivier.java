package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.service.enums.VivierThematique;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Vivier thématique d'une direction (10_TALENTS, colonnes Direction et Vivier thématique).
 */
@Entity
@Table(name = "rattachement_vivier", uniqueConstraints = @UniqueConstraint(
        name = "uk_rattachement_vivier_direction",
        columnNames = {"entite_id"}))
public class RattachementVivier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRattachement;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entite_id", nullable = false)
    private Entite direction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VivierThematique vivier;

    public RattachementVivier() {
    }

    /** Jamais de direction absente ou non enregistree : la colonne recevrait 0 ou une cle inexistante. */
    @PrePersist
    @PreUpdate
    void verifierDirection() {
        if (direction == null || direction.getIdEntite() == null || direction.getIdEntite() <= 0) {
            throw new IllegalStateException("Rattachement de vivier sans direction enregistree : " + vivier);
        }
    }

    public RattachementVivier(Entite direction, VivierThematique vivier) {
        this.direction = direction;
        this.vivier = vivier;
    }

    public Integer getIdRattachement() {
        return idRattachement;
    }

    /** Direction rattachee ; null si elle n'existe pas en base (ligne a reparer, EntiteExistante). */
    public Entite getDirection() {
        return EntiteExistante.ou(direction, "rattachement_vivier " + idRattachement);
    }

    public void setDirection(Entite direction) {
        this.direction = direction;
    }

    public VivierThematique getVivier() {
        return vivier;
    }

    public void setVivier(VivierThematique vivier) {
        this.vivier = vivier;
    }
}