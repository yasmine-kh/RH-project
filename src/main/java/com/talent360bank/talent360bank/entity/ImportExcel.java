package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

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

    @NotNull(message = "L'utilisateur est obligatoire")
    @ManyToOne
    @JoinColumn(name = "id_utilisateur", nullable = false)
    private UtilisateurRH utilisateur;

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

    public UtilisateurRH getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(UtilisateurRH utilisateur) {
        this.utilisateur = utilisateur;
    }
}