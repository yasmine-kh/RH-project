package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Noeud de l'organigramme : direction, departement, region ou agence, rattache
 * a son niveau superieur.
 *
 * <p>Un meme libelle peut apparaitre sous plusieurs parents (la region
 * Casablanca-Settat existe sous plusieurs departements, l'agence Siege sous
 * presque tous) : chaque occurrence est un noeud distinct, identifie par son
 * chemin depuis la direction. Ce chemin est le {@link #getCode() code}, unique
 * en base, ce qui empeche l'import de creer deux fois le meme noeud.
 */
@Entity
@Table(name = "entite", uniqueConstraints = @UniqueConstraint(name = "uk_entite_code", columnNames = "code"),
        indexes = @Index(name = "idx_entite_parent", columnList = "id_entite_parent"))
public class Entite {

    /** Longueur des libelles, celle des anciennes colonnes direction, departement, region et agence. */
    public static final int LONGUEUR_LIBELLE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entite")
    private Integer idEntite;

    /** Quatre segments de 100 caracteres au plus, avec leurs prefixes. */
    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String code;

    @NotBlank
    @Size(max = LONGUEUR_LIBELLE)
    @Column(nullable = false, length = LONGUEUR_LIBELLE)
    private String libelle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeEntite type;

    /**
     * Charge avec l'entite : les services qui regroupent par direction
     * remontent l'arbre ({@link #libelleDe}), parfois hors transaction.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_entite_parent")
    private Entite parent;

    public Entite() {
    }

    public Entite(String libelle, TypeEntite type, Entite parent) {
        this.libelle = libelle;
        this.type = type;
        this.parent = parent;
        this.code = code(parent, type, libelle);
    }

    /**
     * Code d'un noeud : chemin des segments depuis la racine, chacun prefixe
     * par son niveau, par exemple
     * {@code DIR:RESEAU_RETAIL/DEP:RESEAU_RETAIL_NORD/REG:TANGER_TETOUAN}.
     * Sans accents ni ponctuation, pour rester stable d'un import a l'autre.
     */
    public static String code(Entite parent, TypeEntite type, String libelle) {
        String segment = type.getPrefixeCode() + ":" + normaliser(libelle);
        return parent == null ? segment : parent.getCode() + "/" + segment;
    }

    private static String normaliser(String libelle) {
        String sansAccents = Normalizer.normalize(libelle.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /**
     * Entite de ce niveau parmi celle-ci et ses ancetres, ou null si
     * l'organigramme saute ce niveau.
     */
    @Transient
    public Entite ancetre(TypeEntite niveau) {
        for (Entite courante = this; courante != null; courante = courante.parent) {
            if (courante.type == niveau) {
                return courante;
            }
        }
        return null;
    }

    /** Libelle du niveau demande parmi celle-ci et ses ancetres, ou null. */
    @Transient
    public String libelleDe(TypeEntite niveau) {
        Entite ancetre = ancetre(niveau);
        return ancetre == null ? null : ancetre.libelle;
    }

    public Integer getIdEntite() {
        return idEntite;
    }

    public void setIdEntite(Integer idEntite) {
        this.idEntite = idEntite;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public TypeEntite getType() {
        return type;
    }

    public void setType(TypeEntite type) {
        this.type = type;
    }

    public Entite getParent() {
        return parent;
    }

    public void setParent(Entite parent) {
        this.parent = parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entite autre)) {
            return false;
        }
        return code != null && code.equals(autre.getCode());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return "Entite{code='" + code + "'}";
    }
}
