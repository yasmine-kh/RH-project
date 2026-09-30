package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Compte de connexion. Remplace UtilisateurRH : les quatre profils ont un
 * compte, pas seulement le RH.
 *
 * <p>Un compte COLLABORATEUR ou MANAGER est rattache a son collaborateur :
 * c'est par lui que l'application saura quels resultats il peut voir. Un
 * collaborateur a au plus un compte.
 */
@Entity
@Table(name = "utilisateur", uniqueConstraints = {
        @UniqueConstraint(name = "uk_utilisateur_login", columnNames = "login"),
        @UniqueConstraint(name = "uk_utilisateur_collaborateur", columnNames = "id_collaborateur")})
public class Utilisateur {

    public static final int LONGUEUR_LOGIN = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_utilisateur")
    private Integer idUtilisateur;

    @NotBlank
    @Size(max = LONGUEUR_LOGIN)
    @Column(nullable = false, length = LONGUEUR_LOGIN)
    private String login;

    /** Empreinte BCrypt (60 caracteres), jamais le mot de passe. */
    @NotBlank
    @Column(name = "mot_de_passe_hash", nullable = false, length = 100)
    private String motDePasseHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Obligatoire pour COLLABORATEUR et MANAGER, facultatif sinon. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_collaborateur")
    private Collaborateur collaborateur;

    /** Un compte inactif ne peut plus se connecter ; il n'est jamais supprime. */
    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    public Utilisateur() {
    }

    public Utilisateur(String login, String motDePasseHash, Role role, Collaborateur collaborateur) {
        this.login = login;
        this.motDePasseHash = motDePasseHash;
        this.role = role;
        this.collaborateur = collaborateur;
    }

    @PrePersist
    void avantCreation() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    @AssertTrue(message = "Un compte COLLABORATEUR ou MANAGER doit etre rattache a un collaborateur")
    boolean isCollaborateurRenseigne() {
        return role == null || !role.exigeCollaborateur() || collaborateur != null;
    }

    public Integer getIdUtilisateur() {
        return idUtilisateur;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public void setMotDePasseHash(String motDePasseHash) {
        this.motDePasseHash = motDePasseHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Collaborateur getCollaborateur() {
        return collaborateur;
    }

    public void setCollaborateur(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Utilisateur autre)) {
            return false;
        }
        return login != null && login.equals(autre.getLogin());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(login);
    }

    /** Sans l'empreinte du mot de passe : toString finit dans les logs. */
    @Override
    public String toString() {
        return "Utilisateur{login='" + login + "', role=" + role + ", actif=" + actif + "}";
    }
}
