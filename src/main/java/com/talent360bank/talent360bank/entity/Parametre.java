package com.talent360bank.talent360bank.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Jeu de reglages du moteur de calcul, configurable par le RH.
 * Un seul Parametre par trimestre : rejouer un calcul d'une periode passee
 * revient a charger le Parametre de son trimestre.
 */
@Entity
@Table(name = "parametre", uniqueConstraints = @UniqueConstraint(
        name = "uk_parametre_trimestre", columnNames = "id_trimestre"))
public class Parametre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idParametre;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trimestre", nullable = false)
    private Trimestre trimestre;

    @Size(max = 100)
    @Column(length = 100)
    private String libelle;

    @Valid
    @NotNull
    @Embedded
    private PoidsPerformance poidsPerformance;

    @Valid
    @NotNull
    @Embedded
    private PoidsPotentiel poidsPotentiel;

    @Valid
    @NotNull
    @Embedded
    private PoidsSuccession poidsSuccession;

    /**
     * Part du manager et de l'auto-evaluation dans les scores officiels
     * (100 / 0 par defaut : manager seul). Ajoute apres la mise en service,
     * voir {@link #completerBlocsAjoutes()}.
     */
    @Valid
    @NotNull
    @Embedded
    private PonderationSources ponderationSources;

    /** Ecart auto / manager signale dans la vue manager (15 points par defaut). */
    @Valid
    @NotNull
    @Embedded
    private SeuilsAutoEvaluation seuilsAutoEvaluation;

    @Valid
    @NotNull
    @Embedded
    private BaremeExperience baremeExperience;

    @Valid
    @NotNull
    @Embedded
    private BaremeCompetences baremeCompetences;

    /** Axe performance de la matrice 9-box (00_PARAMETRES lignes 24 et 25). */
    @Valid
    @NotNull
    @Embedded
    private SeuilsNeufBox seuilsNeufBox;

    /**
     * Axe potentiel de la matrice 9-box (00_PARAMETRES lignes 26 et 27). Sans
     * valeur par defaut en base : une ligne existante recoit les seuils de son
     * axe performance (voir {@link #completerBlocsAjoutes()}), pour que
     * l'ajout du bloc ne deplace personne dans la matrice.
     */
    @Valid
    @NotNull
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "seuilEleve",
                    column = @Column(name = "seuil_box_pot_eleve", precision = 5, scale = 2)),
            @AttributeOverride(name = "seuilMoyen",
                    column = @Column(name = "seuil_box_pot_moyen", precision = 5, scale = 2))
    })
    private SeuilsNeufBox seuilsNeufBoxPotentiel;

    @Valid
    @NotNull
    @Embedded
    private SeuilsCategoriePerformance seuilsCategoriePerformance;

    @Valid
    @NotNull
    @Embedded
    private SeuilsGapCompetence seuilsGapCompetence;

    @Valid
    @NotNull
    @Embedded
    private SeuilsReadiness seuilsReadiness;

    @Valid
    @NotNull
    @Embedded
    private SeuilsCouverture seuilsCouverture;

    @Valid
    @NotNull
    @Embedded
    private SeuilsTalent seuilsTalent;

    @Valid
    @NotNull
    @Embedded
    private PointsVigilance pointsVigilance;

    @Valid
    @NotNull
    @Embedded
    private SeuilsVigilance seuilsVigilance;

    public Parametre() {
    }

    /**
     * Jeu de reglages initial pour un trimestre, aux valeurs de la specification
     * (00_PARAMETRES du classeur).
     */
    public static Parametre parDefaut(Trimestre trimestre) {
        Parametre parametre = new Parametre();
        parametre.setTrimestre(trimestre);
        parametre.setLibelle("Réglages par défaut");
        parametre.setPoidsPerformance(new PoidsPerformance(
                new BigDecimal("40"), new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("10")));
        parametre.setPoidsPotentiel(new PoidsPotentiel(
                new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("15"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10")));
        parametre.setPoidsSuccession(new PoidsSuccession(
                new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("15"), new BigDecimal("10"), new BigDecimal("10")));
        // Manager seul, comme le classeur, tant que le client n'a pas choisi de melange.
        parametre.setPonderationSources(new PonderationSources(new BigDecimal("100"), new BigDecimal("0")));
        parametre.setSeuilsAutoEvaluation(new SeuilsAutoEvaluation(new BigDecimal("15")));
        parametre.setBaremeExperience(new BaremeExperience(
                new BigDecimal("8"), new BigDecimal("100")));
        parametre.setBaremeCompetences(new BaremeCompetences(new BigDecimal("20"), 3));
        parametre.setSeuilsNeufBox(new SeuilsNeufBox(
                new BigDecimal("85"), new BigDecimal("70")));
        parametre.setSeuilsNeufBoxPotentiel(new SeuilsNeufBox(
                new BigDecimal("85"), new BigDecimal("70")));
        parametre.setSeuilsCategoriePerformance(new SeuilsCategoriePerformance(
                new BigDecimal("90"), new BigDecimal("80"),
                new BigDecimal("70"), new BigDecimal("60")));
        parametre.setSeuilsGapCompetence(new SeuilsGapCompetence(2));
        parametre.setSeuilsReadiness(new SeuilsReadiness(
                new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("65")));
        parametre.setSeuilsCouverture(new SeuilsCouverture(1));
        parametre.setSeuilsTalent(new SeuilsTalent(
                new BigDecimal("85"), new BigDecimal("85"),
                new BigDecimal("85"), new BigDecimal("75")));
        parametre.setPointsVigilance(new PointsVigilance(
                new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("15"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("5")));
        parametre.setSeuilsVigilance(new SeuilsVigilance(
                new BigDecimal("30"), new BigDecimal("60"), new BigDecimal("60")));
        return parametre;
    }

    /**
     * Copie de ces reglages pour un autre trimestre : une nouvelle campagne
     * reprend les reglages du trimestre precedent, pas les valeurs par defaut.
     * Chaque bloc est recopie, jamais partage entre deux lignes.
     */
    public Parametre copiePour(Trimestre autreTrimestre) {
        Parametre copie = new Parametre();
        copie.setTrimestre(autreTrimestre);
        copie.setLibelle(libelle);
        copie.setPoidsPerformance(copierBloc(poidsPerformance));
        copie.setPoidsPotentiel(copierBloc(poidsPotentiel));
        copie.setPoidsSuccession(copierBloc(poidsSuccession));
        copie.setPonderationSources(copierBloc(ponderationSources));
        copie.setSeuilsAutoEvaluation(copierBloc(seuilsAutoEvaluation));
        copie.setBaremeExperience(copierBloc(baremeExperience));
        copie.setBaremeCompetences(copierBloc(baremeCompetences));
        copie.setSeuilsNeufBox(copierBloc(seuilsNeufBox));
        copie.setSeuilsNeufBoxPotentiel(copierBloc(seuilsNeufBoxPotentiel));
        copie.setSeuilsCategoriePerformance(copierBloc(seuilsCategoriePerformance));
        copie.setSeuilsGapCompetence(copierBloc(seuilsGapCompetence));
        copie.setSeuilsReadiness(copierBloc(seuilsReadiness));
        copie.setSeuilsCouverture(copierBloc(seuilsCouverture));
        copie.setSeuilsTalent(copierBloc(seuilsTalent));
        copie.setPointsVigilance(copierBloc(pointsVigilance));
        copie.setSeuilsVigilance(copierBloc(seuilsVigilance));
        return copie;
    }

    /**
     * Copie champ a champ d'un bloc @Embeddable. Les champs des blocs sont des
     * valeurs immuables (BigDecimal, Integer) : une copie superficielle suffit.
     * Par reflexion pour qu'un champ ajoute a un bloc soit copie sans y penser.
     */
    @SuppressWarnings("unchecked")
    private static <T> T copierBloc(T bloc) {
        if (bloc == null) {
            return null;
        }
        try {
            java.lang.reflect.Constructor<?> constructeur = bloc.getClass().getDeclaredConstructor();
            constructeur.setAccessible(true);
            T copie = (T) constructeur.newInstance();
            for (java.lang.reflect.Field champ : bloc.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(champ.getModifiers())) {
                    continue;
                }
                champ.setAccessible(true);
                champ.set(copie, champ.get(bloc));
            }
            return copie;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Copie impossible du bloc " + bloc.getClass().getSimpleName(), e);
        }
    }

    /**
     * Complete, avec les valeurs de {@link #parDefaut}, les reglages ajoutes
     * apres la mise en service : bareme d'experience, bareme des competences,
     * seuils de haut potentiel, seuil de couverture des postes critiques,
     * seuils des categories de performance, seuil de gap de competence,
     * ponderation des sources d'evaluation, seuil d'ecart auto / manager et axe potentiel de la 9-box (ce
     * dernier repris de l'axe performance de la ligne, pas des defauts).
     *
     * <p>Une ligne creee avant leur ajout a ces colonnes a NULL ; quand toutes
     * les colonnes d'un bloc sont NULL, Hibernate charge le bloc entier a null.
     * Seuls les champs absents sont remplis : un reglage saisi par le RH n'est
     * jamais ecrase.
     *
     * @return vrai si au moins un champ a ete complete
     */
    public boolean completerBlocsAjoutes() {
        Parametre defauts = parDefaut(trimestre);
        boolean complete = false;

        if (baremeExperience == null) {
            baremeExperience = defauts.getBaremeExperience();
            complete = true;
        } else {
            if (baremeExperience.getPointsParAnnee() == null) {
                baremeExperience.setPointsParAnnee(defauts.getBaremeExperience().getPointsParAnnee());
                complete = true;
            }
            if (baremeExperience.getPlafond() == null) {
                baremeExperience.setPlafond(defauts.getBaremeExperience().getPlafond());
                complete = true;
            }
        }

        if (baremeCompetences == null) {
            baremeCompetences = defauts.getBaremeCompetences();
            complete = true;
        } else {
            if (baremeCompetences.getPointsParNiveauManquant() == null) {
                baremeCompetences.setPointsParNiveauManquant(
                        defauts.getBaremeCompetences().getPointsParNiveauManquant());
                complete = true;
            }
            if (baremeCompetences.getNiveauParDefaut() == null) {
                baremeCompetences.setNiveauParDefaut(defauts.getBaremeCompetences().getNiveauParDefaut());
                complete = true;
            }
        }

        if (seuilsTalent != null) {
            if (seuilsTalent.getSeuilHautPotentielPotentiel() == null) {
                seuilsTalent.setSeuilHautPotentielPotentiel(
                        defauts.getSeuilsTalent().getSeuilHautPotentielPotentiel());
                complete = true;
            }
            if (seuilsTalent.getSeuilHautPotentielPerformance() == null) {
                seuilsTalent.setSeuilHautPotentielPerformance(
                        defauts.getSeuilsTalent().getSeuilHautPotentielPerformance());
                complete = true;
            }
        }

        // Bloc a une seule colonne : NULL en base, Hibernate le rend a null.
        if (seuilsCouverture == null) {
            seuilsCouverture = defauts.getSeuilsCouverture();
            complete = true;
        } else if (seuilsCouverture.getNbMinSuccesseurs() == null) {
            seuilsCouverture.setNbMinSuccesseurs(defauts.getSeuilsCouverture().getNbMinSuccesseurs());
            complete = true;
        }

        // Axe potentiel de la 9-box : repris de l'axe performance de la meme
        // ligne, qui s'appliquait jusque-la aux deux axes. Les valeurs par
        // defaut deplaceraient les collaborateurs d'un trimestre aux seuils modifies.
        SeuilsNeufBox reference = seuilsNeufBox != null ? seuilsNeufBox : defauts.getSeuilsNeufBox();
        if (seuilsNeufBoxPotentiel == null || estLeZeroImpliciteDeMySql(seuilsNeufBoxPotentiel)) {
            seuilsNeufBoxPotentiel = new SeuilsNeufBox(reference.getSeuilEleve(), reference.getSeuilMoyen());
            complete = true;
        } else {
            if (seuilsNeufBoxPotentiel.getSeuilEleve() == null) {
                seuilsNeufBoxPotentiel.setSeuilEleve(reference.getSeuilEleve());
                complete = true;
            }
            if (seuilsNeufBoxPotentiel.getSeuilMoyen() == null) {
                seuilsNeufBoxPotentiel.setSeuilMoyen(reference.getSeuilMoyen());
                complete = true;
            }
        }

        SeuilsCategoriePerformance categoriesParDefaut = defauts.getSeuilsCategoriePerformance();
        if (seuilsCategoriePerformance == null) {
            seuilsCategoriePerformance = categoriesParDefaut;
            complete = true;
        } else {
            if (seuilsCategoriePerformance.getSeuilExceptionnelle() == null) {
                seuilsCategoriePerformance.setSeuilExceptionnelle(categoriesParDefaut.getSeuilExceptionnelle());
                complete = true;
            }
            if (seuilsCategoriePerformance.getSeuilElevee() == null) {
                seuilsCategoriePerformance.setSeuilElevee(categoriesParDefaut.getSeuilElevee());
                complete = true;
            }
            if (seuilsCategoriePerformance.getSeuilSolide() == null) {
                seuilsCategoriePerformance.setSeuilSolide(categoriesParDefaut.getSeuilSolide());
                complete = true;
            }
            if (seuilsCategoriePerformance.getSeuilARenforcer() == null) {
                seuilsCategoriePerformance.setSeuilARenforcer(categoriesParDefaut.getSeuilARenforcer());
                complete = true;
            }
        }

        // Bloc a une seule colonne : NULL en base, Hibernate le rend a null.
        if (seuilsGapCompetence == null) {
            seuilsGapCompetence = defauts.getSeuilsGapCompetence();
            complete = true;
        } else if (seuilsGapCompetence.getSeuilPrioritaire() == null) {
            seuilsGapCompetence.setSeuilPrioritaire(defauts.getSeuilsGapCompetence().getSeuilPrioritaire());
            complete = true;
        }

        // Ponderation des sources : manager seul, le score officiel d'avant l'auto-evaluation.
        // 0 / 0 (zero implicite de MySQL) ne totalise pas 100 : traite comme absent.
        PonderationSources sourcesParDefaut = defauts.getPonderationSources();
        if (ponderationSources == null || (ponderationSources.getPoidsManager() != null
                && ponderationSources.getPoidsAuto() != null && ponderationSources.getPoidsManager().signum() == 0
                && ponderationSources.getPoidsAuto().signum() == 0)) {
            ponderationSources = sourcesParDefaut;
            complete = true;
        } else {
            if (ponderationSources.getPoidsManager() == null) {
                ponderationSources.setPoidsManager(sourcesParDefaut.getPoidsManager());
                complete = true;
            }
            if (ponderationSources.getPoidsAuto() == null) {
                ponderationSources.setPoidsAuto(sourcesParDefaut.getPoidsAuto());
                complete = true;
            }
        }

        // Bloc a une seule colonne : NULL en base, Hibernate le rend a null.
        if (seuilsAutoEvaluation == null || seuilsAutoEvaluation.getSeuilEcartImportant() == null) {
            seuilsAutoEvaluation = defauts.getSeuilsAutoEvaluation();
            complete = true;
        }

        return complete;
    }

    /**
     * Colonnes de l'axe potentiel ajoutees NOT NULL sans valeur par defaut :
     * MySQL remplit les lignes existantes avec 0. Le couple 0 / 0 est de toute
     * facon invalide (le seuil eleve doit depasser le moyen) : ce n'est pas un
     * reglage du RH, on le traite comme absent.
     */
    private static boolean estLeZeroImpliciteDeMySql(SeuilsNeufBox seuils) {
        return seuils.getSeuilEleve() != null && seuils.getSeuilMoyen() != null
                && seuils.getSeuilEleve().signum() == 0 && seuils.getSeuilMoyen().signum() == 0;
    }

    /**
     * Controle inter-blocs : un seuil de vigilance plus haut que la somme des
     * points rendrait le niveau ELEVEE impossible a atteindre.
     */
    @Transient
    @AssertTrue(message = "Le seuil de vigilance élevée dépasse le total des points attribuables")
    public boolean isSeuilVigilanceAtteignable() {
        if (pointsVigilance == null || seuilsVigilance == null) {
            return true;
        }
        BigDecimal maximum = pointsVigilance.getTotalMaximum();
        BigDecimal seuil = seuilsVigilance.getSeuilEleve();
        if (maximum == null || seuil == null) {
            return true;
        }
        return seuil.compareTo(maximum) <= 0;
    }

    public Integer getIdParametre() {
        return idParametre;
    }

    public void setIdParametre(Integer idParametre) {
        this.idParametre = idParametre;
    }

    public Trimestre getTrimestre() {
        return trimestre;
    }

    public void setTrimestre(Trimestre trimestre) {
        this.trimestre = trimestre;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public PoidsPerformance getPoidsPerformance() {
        return poidsPerformance;
    }

    public void setPoidsPerformance(PoidsPerformance poidsPerformance) {
        this.poidsPerformance = poidsPerformance;
    }

    public PoidsPotentiel getPoidsPotentiel() {
        return poidsPotentiel;
    }

    public void setPoidsPotentiel(PoidsPotentiel poidsPotentiel) {
        this.poidsPotentiel = poidsPotentiel;
    }

    public PoidsSuccession getPoidsSuccession() {
        return poidsSuccession;
    }

    public void setPoidsSuccession(PoidsSuccession poidsSuccession) {
        this.poidsSuccession = poidsSuccession;
    }

    public BaremeExperience getBaremeExperience() {
        return baremeExperience;
    }

    public void setBaremeExperience(BaremeExperience baremeExperience) {
        this.baremeExperience = baremeExperience;
    }

    public BaremeCompetences getBaremeCompetences() {
        return baremeCompetences;
    }

    public void setBaremeCompetences(BaremeCompetences baremeCompetences) {
        this.baremeCompetences = baremeCompetences;
    }

    public SeuilsNeufBox getSeuilsNeufBox() {
        return seuilsNeufBox;
    }

    public void setSeuilsNeufBox(SeuilsNeufBox seuilsNeufBox) {
        this.seuilsNeufBox = seuilsNeufBox;
    }

    public SeuilsNeufBox getSeuilsNeufBoxPotentiel() {
        return seuilsNeufBoxPotentiel;
    }

    public void setSeuilsNeufBoxPotentiel(SeuilsNeufBox seuilsNeufBoxPotentiel) {
        this.seuilsNeufBoxPotentiel = seuilsNeufBoxPotentiel;
    }

    public SeuilsCategoriePerformance getSeuilsCategoriePerformance() {
        return seuilsCategoriePerformance;
    }

    public void setSeuilsCategoriePerformance(SeuilsCategoriePerformance seuilsCategoriePerformance) {
        this.seuilsCategoriePerformance = seuilsCategoriePerformance;
    }

    public SeuilsGapCompetence getSeuilsGapCompetence() {
        return seuilsGapCompetence;
    }

    public void setSeuilsGapCompetence(SeuilsGapCompetence seuilsGapCompetence) {
        this.seuilsGapCompetence = seuilsGapCompetence;
    }

    public SeuilsReadiness getSeuilsReadiness() {
        return seuilsReadiness;
    }

    public void setSeuilsReadiness(SeuilsReadiness seuilsReadiness) {
        this.seuilsReadiness = seuilsReadiness;
    }

    public SeuilsCouverture getSeuilsCouverture() {
        return seuilsCouverture;
    }

    public void setSeuilsCouverture(SeuilsCouverture seuilsCouverture) {
        this.seuilsCouverture = seuilsCouverture;
    }

    public SeuilsTalent getSeuilsTalent() {
        return seuilsTalent;
    }

    public void setSeuilsTalent(SeuilsTalent seuilsTalent) {
        this.seuilsTalent = seuilsTalent;
    }

    public PointsVigilance getPointsVigilance() {
        return pointsVigilance;
    }

    public void setPointsVigilance(PointsVigilance pointsVigilance) {
        this.pointsVigilance = pointsVigilance;
    }

    public SeuilsVigilance getSeuilsVigilance() {
        return seuilsVigilance;
    }

    public PonderationSources getPonderationSources() {
        return ponderationSources;
    }

    public void setPonderationSources(PonderationSources ponderationSources) {
        this.ponderationSources = ponderationSources;
    }

    public SeuilsAutoEvaluation getSeuilsAutoEvaluation() {
        return seuilsAutoEvaluation;
    }

    public void setSeuilsAutoEvaluation(SeuilsAutoEvaluation seuilsAutoEvaluation) {
        this.seuilsAutoEvaluation = seuilsAutoEvaluation;
    }

    public void setSeuilsVigilance(SeuilsVigilance seuilsVigilance) {
        this.seuilsVigilance = seuilsVigilance;
    }
}
