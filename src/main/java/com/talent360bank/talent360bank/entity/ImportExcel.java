package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Journal des imports de classeur : un enregistrement par appel, reussi ou
 * non. Le detail des erreurs de ligne est rendu a l'appel, seul leur nombre
 * est conserve ici.
 */
@Entity
@Table(name = "import_excel")
public class ImportExcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idImport;

    @NotBlank(message = "La source est obligatoire")
    @Column(nullable = false)
    private String source;

    @NotBlank(message = "Le nom du fichier est obligatoire")
    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @NotNull(message = "La date d'import est obligatoire")
    @Column(name = "date_import", nullable = false)
    private LocalDate dateImport;

    @NotBlank(message = "Le statut est obligatoire")
    @Column(nullable = false)
    private String statut;

    /**
     * Compte RH connecte qui a lance l'import (UtilisateurCourant) ; nul pour
     * les imports d'avant ce suivi, ou lances hors requete. Colonne creee NOT
     * NULL a l'origine : ImportExcelInitializer la libere.
     */
    @ManyToOne
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    /** Trimestre des notes importees ; nul si l'import a echoue avant de le connaitre. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre")
    private Trimestre trimestre;

    /** Lignes ecrites en base, toutes feuilles confondues. */
    @Column(name = "nb_lignes")
    private Integer nbLignes;

    /** Lignes ou feuilles ecartees, detaillees dans la reponse de l'import. */
    @Column(name = "nb_erreurs")
    private Integer nbErreurs;

    /** Cause d'un echec complet (fichier illisible, erreur inattendue). */
    @Column(length = 1000)
    private String message;

    public ImportExcel() {
    }

    public Integer getIdImport() {
        return idImport;
    }

    public void setIdImport(Integer idImport) {
        this.idImport = idImport;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public void setNomFichier(String nomFichier) {
        this.nomFichier = nomFichier;
    }

    public LocalDate getDateImport() {
        return dateImport;
    }

    public void setDateImport(LocalDate dateImport) {
        this.dateImport = dateImport;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }

    public Integer getNbLignes() {
        return nbLignes;
    }

    public void setNbLignes(Integer nbLignes) {
        this.nbLignes = nbLignes;
    }

    public Integer getNbErreurs() {
        return nbErreurs;
    }

    public void setNbErreurs(Integer nbErreurs) {
        this.nbErreurs = nbErreurs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}