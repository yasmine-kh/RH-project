package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Trimestre d'une campagne : toutes les evaluations et tous les resultats
 * lui appartiennent.
 *
 * <p>La date de reference fixe l'instant auquel le trimestre est evalue :
 * l'anciennete (critere experience du matching, fiches) se mesure a cette
 * date, jamais a la date du jour, pour qu'un trimestre donne toujours le meme
 * resultat. Par defaut, le dernier jour du trimestre ; le RH peut la changer
 * (PUT /api/trimestres/{annee}/{numero}), par exemple a la date de campagne.
 */
@Entity
@Table(name = "trimestre", uniqueConstraints = @UniqueConstraint(
        name = "uk_trimestre_numero_annee", columnNames = {"numero", "annee"}))
public class Trimestre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTrimestre;

    @NotNull(message = "Le numéro de trimestre est obligatoire")
    @Min(value = 1, message = "Le numéro doit être entre 1 et 4")
    @Max(value = 4, message = "Le numéro doit être entre 1 et 4")
    @Column(nullable = false)
    private Integer numero;

    @NotNull(message = "L'année est obligatoire")
    @Column(nullable = false)
    private Integer annee;

    /** Posee par defaut a la creation ({@link #avantCreation()}), avant la validation. */
    @NotNull(message = "La date de reference est obligatoire")
    @Column(name = "date_reference", nullable = false)
    private LocalDate dateReference;

    public Trimestre() {
    }

    /** Dernier jour du trimestre : 31/03, 30/06, 30/09 ou 31/12. */
    public static LocalDate dernierJour(int annee, int numero) {
        return YearMonth.of(annee, numero * 3).atEndOfMonth();
    }

    @PrePersist
    void avantCreation() {
        if (dateReference == null && annee != null && numero != null) {
            dateReference = dernierJour(annee, numero);
        }
    }

    public Integer getIdTrimestre() {
        return idTrimestre;
    }

    public void setIdTrimestre(Integer idTrimestre) {
        this.idTrimestre = idTrimestre;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public Integer getAnnee() {
        return annee;
    }

    public void setAnnee(Integer annee) {
        this.annee = annee;
    }

    /**
     * Date a laquelle le trimestre est evalue. Tant qu'elle n'est pas
     * enregistree (trimestre pas encore persiste), le dernier jour du trimestre.
     */
    public LocalDate getDateReference() {
        if (dateReference == null && annee != null && numero != null) {
            return dernierJour(annee, numero);
        }
        return dateReference;
    }

    public void setDateReference(LocalDate dateReference) {
        this.dateReference = dateReference;
    }
}
