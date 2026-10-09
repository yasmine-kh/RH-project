package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Role de manager tenu par un collaborateur : un manager est un collaborateur,
 * au plus un role par collaborateur.
 *
 * <p>Cree a l'import pour chaque Manager_ID distinct de 01_COLLABORATEURS
 * (colonne N). L'entite geree est facultative : le classeur ne la donne pas.
 */
@Entity
@Table(name = "manager", uniqueConstraints = @UniqueConstraint(
        name = "uk_manager_collaborateur", columnNames = "id_collaborateur"))
public class Manager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_manager")
    private Integer idManager;

    @NotNull
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_collaborateur", nullable = false)
    private Collaborateur collaborateur;

    /** Entite dirigee par le manager, si elle est connue. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entite")
    private Entite entiteGeree;

    public Manager() {
    }

    public Manager(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }

    public Integer getIdManager() {
        return idManager;
    }

    public void setIdManager(Integer idManager) {
        this.idManager = idManager;
    }

    public Collaborateur getCollaborateur() {
        return collaborateur;
    }

    public void setCollaborateur(Collaborateur collaborateur) {
        this.collaborateur = collaborateur;
    }

    public Entite getEntiteGeree() {
        return EntiteExistante.ou(entiteGeree, "manager " + idManager);
    }

    public void setEntiteGeree(Entite entiteGeree) {
        this.entiteGeree = entiteGeree;
    }

    /** Identifiant du collaborateur qui tient le role (Manager_ID du classeur). */
    @Transient
    public String getIdCollaborateur() {
        return collaborateur == null ? null : collaborateur.getIdCollaborateur();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Manager autre)) {
            return false;
        }
        return getIdCollaborateur() != null && getIdCollaborateur().equals(autre.getIdCollaborateur());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getIdCollaborateur());
    }

    @Override
    public String toString() {
        return "Manager{idManager=" + idManager + ", idCollaborateur='" + getIdCollaborateur() + "'}";
    }
}
