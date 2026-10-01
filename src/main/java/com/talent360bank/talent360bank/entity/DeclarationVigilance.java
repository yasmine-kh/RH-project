package com.talent360bank.talent360bank.entity;

import com.talent360bank.talent360bank.service.FaitsVigilance;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Faits de vigilance declares pour un collaborateur sur un trimestre (12_VIGILANCE,
 * colonnes F a K). Chaque drapeau est a trois etats : Oui, Non, ou nul si la
 * cellule est vide (inconnu, ne leve pas le signal).
 *
 * <p>Nommee Declaration et non Faits : {@link FaitsVigilance} est deja la
 * valeur que le moteur consomme, construite a partir de cette ligne.
 */
@Entity
@Table(name = "declaration_vigilance", uniqueConstraints = @UniqueConstraint(
        name = "uk_declaration_vigilance_collaborateur_trimestre",
        columnNames = {"id_collaborateur", "id_trimestre"}))
public class DeclarationVigilance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDeclaration;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    /** F : aucun mouvement depuis 4 ans. */
    @Column(name = "sans_mobilite_4_ans")
    private Boolean sansMobilite4Ans;

    /** G : demande de mobilite non traitee. */
    @Column(name = "mobilite_non_traitee")
    private Boolean mobiliteNonTraitee;

    /** H : aucune action de developpement recente. */
    @Column(name = "sans_developpement_recent")
    private Boolean sansDeveloppementRecent;

    /** I : baisse de performance. */
    @Column(name = "baisse_performance")
    private Boolean baissePerformance;

    /** J : faible reconnaissance. */
    @Column(name = "faible_reconnaissance")
    private Boolean faibleReconnaissance;

    /** K : formation prevue non realisee. */
    @Column(name = "formation_non_faite")
    private Boolean formationNonFaite;

    public DeclarationVigilance() {
    }

    public DeclarationVigilance(Collaborateur collaborateur, Trimestre trimestre) {
        this.collaborateur = collaborateur;
        this.trimestre = trimestre;
    }

    /** Les drapeaux sous la forme que lit le moteur de vigilance. */
    public FaitsVigilance versFaits() {
        return FaitsVigilance.builder()
                .sansMobilite4Ans(sansMobilite4Ans)
                .mobiliteNonTraitee(mobiliteNonTraitee)
                .sansDeveloppementRecent(sansDeveloppementRecent)
                .baissePerformance(baissePerformance)
                .faibleReconnaissance(faibleReconnaissance)
                .formationNonFaite(formationNonFaite)
                .build();
    }

    public Integer getIdDeclaration() {
        return idDeclaration;
    }

    public Collaborateur getCollaborateur() {
        return collaborateur;
    }

    public void setCollaborateur(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }

    public Boolean getSansMobilite4Ans() {
        return sansMobilite4Ans;
    }

    public void setSansMobilite4Ans(Boolean sansMobilite4Ans) {
        this.sansMobilite4Ans = sansMobilite4Ans;
    }

    public Boolean getMobiliteNonTraitee() {
        return mobiliteNonTraitee;
    }

    public void setMobiliteNonTraitee(Boolean mobiliteNonTraitee) {
        this.mobiliteNonTraitee = mobiliteNonTraitee;
    }

    public Boolean getSansDeveloppementRecent() {
        return sansDeveloppementRecent;
    }

    public void setSansDeveloppementRecent(Boolean sansDeveloppementRecent) {
        this.sansDeveloppementRecent = sansDeveloppementRecent;
    }

    public Boolean getBaissePerformance() {
        return baissePerformance;
    }

    public void setBaissePerformance(Boolean baissePerformance) {
        this.baissePerformance = baissePerformance;
    }

    public Boolean getFaibleReconnaissance() {
        return faibleReconnaissance;
    }

    public void setFaibleReconnaissance(Boolean faibleReconnaissance) {
        this.faibleReconnaissance = faibleReconnaissance;
    }

    public Boolean getFormationNonFaite() {
        return formationNonFaite;
    }

    public void setFormationNonFaite(Boolean formationNonFaite) {
        this.formationNonFaite = formationNonFaite;
    }
}
