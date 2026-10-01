package com.talent360bank.talent360bank.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.talent360bank.talent360bank.service.ImportClasseurService.*;

/**
 * Petit classeur au format de TALENT_360_BANK_Dataset, genere en memoire : le
 * vrai jeu de donnees est gitignore. Meme disposition que l'original : titre
 * et commentaire en lignes 1-2, en-tete en ligne 4 (ligne 5 pour
 * 09_SUCCESSION), donnees dessous, puis une ligne vide et une note qui ne
 * doit pas etre importee.
 *
 * <p>Contenu de {@link #complet()} : trois collaborateurs TST001 a TST003 (TST003
 * manager des deux autres), deux competences, deux postes dont TSTP01
 * critique, notes, successeurs, decisions du comite, viviers et vigilance.
 */
final class ClasseurDeTest {

    static final String E1 = "TST001";
    static final String E2 = "TST002";
    static final String E3 = "TST003";
    static final String C1 = "TSTC01";
    static final String C2 = "TSTC02";
    static final String P1 = "TSTP01";
    static final String P2 = "TSTP02";
    static final String COMPETENCE_1 = "Credit test";
    static final String COMPETENCE_2 = "Leadership test";

    private static final Map<String, String[]> ENTETES = new LinkedHashMap<>();

    static {
        ENTETES.put(FEUILLE_COLLABORATEURS, new String[]{"Employee_ID", "Nom", "Prenom", "Sexe", "Date_Naissance",
                "Date_Entree", "Anciennete (ans)", "Direction", "Departement", "Region", "Agence / Site", "Fonction",
                "Grade", "Manager_ID", "Statut"});
        ENTETES.put(FEUILLE_PERFORMANCE, new String[]{"Employee_ID", "Nom Prenom", "Direction", "Objectifs (/100)",
                "Competences (/100)", "Comportement (/100)", "Contribution (/100)", "Developpement (/100)"});
        ENTETES.put(FEUILLE_POTENTIEL, new String[]{"Employee_ID", "Nom Prenom", "Direction", "Agilite apprentissage",
                "Leadership", "Adaptabilite", "Gestion complexite", "Mobilite", "Vision strategique", "Autonomie"});
        ENTETES.put(FEUILLE_COMPETENCES, new String[]{"Competence_ID", "Nom", "Categorie"});
        ENTETES.put(FEUILLE_SKILLS, new String[]{"Cle (lookup)", "Employee_ID", "Competence", "Categorie",
                "Niveau Actuel (1-5)", "Niveau Cible (1-5)", "Gap", "Statut Gap"});
        ENTETES.put(FEUILLE_POSTES, new String[]{"Poste_ID", "Nom du poste", "Direction", "Grade cible", "Criticite",
                "Competence requise 1", "Niveau 1", "Competence requise 2", "Niveau 2", "Competence requise 3",
                "Niveau 3", "Competence requise 4", "Niveau 4", "Competence requise 5", "Niveau 5",
                "Poste critique ?"});
        ENTETES.put(FEUILLE_POSTES_CRITIQUES, new String[]{"Poste_ID", "Nom du poste", "Direction", "Criticite",
                "Titulaire_ID", "Titulaire (Nom)"});
        ENTETES.put(FEUILLE_SUCCESSION, new String[]{"Poste_ID", "Employee_ID", "Nom du candidat"});
        ENTETES.put(FEUILLE_TALENTS, new String[]{"Employee_ID", "Nom Prenom", "Direction", "Case 9-Box",
                "Talent propose (auto)", "Haut Potentiel propose (auto)", "Validation Comite Talent",
                "Talent valide", "Vivier thematique"});
        ENTETES.put(FEUILLE_VIGILANCE, new String[]{"Employee_ID", "Nom Prenom", "Direction", "Engagement (/100)",
                "Engagement faible ?", "Aucun mouvement 4 ans", "Mobilite non traitee", "Aucun dev. recent",
                "Baisse performance", "Faible reconnaissance", "Formation non realisee"});
    }

    private final Map<String, List<Object[]>> feuilles = new LinkedHashMap<>();
    private final Map<String, String> entetesRemplaces = new LinkedHashMap<>();
    private final Map<String, Map<Integer, String>> colonnesRenommees = new LinkedHashMap<>();
    private final Map<String, String> commentaires = new LinkedHashMap<>();

    private ClasseurDeTest() {
        ENTETES.keySet().forEach(feuille -> feuilles.put(feuille, new ArrayList<>()));
    }

    static ClasseurDeTest vide() {
        return new ClasseurDeTest();
    }

    static ClasseurDeTest complet() {
        return new ClasseurDeTest()
                .ligne(FEUILLE_COLLABORATEURS, E1, "Alaoui", "Siham", "F", LocalDate.of(1990, 5, 12),
                        LocalDate.of(2015, 3, 1), 11, "Reseau Retail", "Reseau Retail - Nord", "Tanger",
                        "Agence Tanger Nord", "Conseiller", "Confirme", E3, "Actif")
                .ligne(FEUILLE_COLLABORATEURS, E2, "Saadi", "Omar", "M", LocalDate.of(1985, 1, 20),
                        LocalDate.of(2010, 9, 1), 16, "Risques", "Risques - Credit", "Casablanca",
                        "Siege", "Analyste", "Senior", E3, "Inactif")
                // Le manager apparait apres ses collaborateurs : il faut la seconde passe.
                .ligne(FEUILLE_COLLABORATEURS, E3, "Tahiri", "Mounir", "M", LocalDate.of(1975, 7, 2),
                        LocalDate.of(2000, 1, 15), 26, "Reseau Retail", "Reseau Retail - Nord", "Tanger",
                        "Direction regionale", "Directeur regional", "Directeur", null, "Actif")

                .ligne(FEUILLE_COMPETENCES, C1, COMPETENCE_1, "Metier")
                .ligne(FEUILLE_COMPETENCES, C2, COMPETENCE_2, "Manageriale")

                .ligne(FEUILLE_SKILLS, E1 + "|" + COMPETENCE_1, E1, COMPETENCE_1, "Metier", 3, 4, 1, "A developper")
                .ligne(FEUILLE_SKILLS, E1 + "|" + COMPETENCE_2, E1, COMPETENCE_2, "Manageriale", 2, 4, 2, "Prioritaire")
                .ligne(FEUILLE_SKILLS, E2 + "|" + COMPETENCE_1, E2, COMPETENCE_1, "Metier", 5, 4, -1, "Maitrise")

                .ligne(FEUILLE_POSTES, P1, "Directeur regional test", "Reseau Retail", "Directeur", "Tres elevee",
                        COMPETENCE_2, 5, COMPETENCE_1, 3, null, null, null, null, null, null, "Oui")
                .ligne(FEUILLE_POSTES, P2, "Analyste test", "Risques", "Confirme", "Moderee",
                        COMPETENCE_1, 4, null, null, null, null, null, null, null, null, "Non")

                .ligne(FEUILLE_POSTES_CRITIQUES, P1, "Directeur regional test", "Reseau Retail", "Tres elevee",
                        E3, "Mounir Tahiri")

                .ligne(FEUILLE_PERFORMANCE, E1, "Siham Alaoui", "Reseau Retail", 85, 99, 88, 89, 90)
                .ligne(FEUILLE_PERFORMANCE, E2, "Omar Saadi", "Risques", 64, 50, 66, 63, 65)
                .ligne(FEUILLE_PERFORMANCE, E3, "Mounir Tahiri", "Reseau Retail", 70.5, 72, 71, 70, 69)

                .ligne(FEUILLE_POTENTIEL, E1, "Siham Alaoui", "Reseau Retail", 90, 88, 87, 86, 85, 84, 83)
                .ligne(FEUILLE_POTENTIEL, E2, "Omar Saadi", "Risques", 57, 49, 59, 71, 53, 56, 64)
                .ligne(FEUILLE_POTENTIEL, E3, "Mounir Tahiri", "Reseau Retail", 73, 73, 72, 82, 78, 75, 78)

                .ligne(FEUILLE_SUCCESSION, P1, E1, "Siham Alaoui")
                .ligne(FEUILLE_SUCCESSION, P1, E2, "Omar Saadi")

                .ligne(FEUILLE_TALENTS, E1, "Siham Alaoui", "Reseau Retail", "Star", "Oui", "Oui", "Oui", "Oui",
                        "Vivier Commercial")
                .ligne(FEUILLE_TALENTS, E2, "Omar Saadi", "Risques", "A developper", "Non", "Non", "Non", "Non",
                        "Vivier Risques")
                .ligne(FEUILLE_TALENTS, E3, "Mounir Tahiri", "Reseau Retail", "Pilier", "Non", "Non", "En attente",
                        "Non", "Vivier Commercial")

                .ligne(FEUILLE_VIGILANCE, E1, "Siham Alaoui", "Reseau Retail", 76, "Non",
                        "Oui", "Non", "Oui", "Non", "Non", "Oui")
                .ligne(FEUILLE_VIGILANCE, E2, "Omar Saadi", "Risques", 54, "Oui",
                        "Oui", "Oui", "Non", "Non", "Non", "Oui")
                // Engagement inconnu, drapeaux vides : la ligne reste valide.
                .ligne(FEUILLE_VIGILANCE, E3, "Mounir Tahiri", "Reseau Retail", null, null,
                        null, null, null, null, null, null);
    }

    ClasseurDeTest ligne(String feuille, Object... valeurs) {
        feuilles.get(feuille).add(valeurs);
        return this;
    }

    /** Remplace la ligne de donnees d'index donne (0 = premiere ligne sous l'en-tete). */
    ClasseurDeTest remplacer(String feuille, int index, Object... valeurs) {
        feuilles.get(feuille).set(index, valeurs);
        return this;
    }

    /** Remplace une valeur d'une ligne de donnees existante. */
    ClasseurDeTest cellule(String feuille, int index, int colonne, Object valeur) {
        Object[] ligne = feuilles.get(feuille).get(index);
        Object[] copie = Arrays.copyOf(ligne, Math.max(ligne.length, colonne + 1));
        copie[colonne] = valeur;
        feuilles.get(feuille).set(index, copie);
        return this;
    }

    ClasseurDeTest retirerLigne(String feuille, int index) {
        feuilles.get(feuille).remove(index);
        return this;
    }

    /** Feuille sans rapport avec le jeu de donnees, sans en-tete. */
    ClasseurDeTest autreFeuille(String nom) {
        feuilles.put(nom, new ArrayList<>());
        return this;
    }

    ClasseurDeTest sans(String feuille) {
        feuilles.remove(feuille);
        return this;
    }

    /** En-tete dont la colonne A ne porte pas le libelle attendu. */
    ClasseurDeTest enteteModifie(String feuille, String libelleColonneA) {
        entetesRemplaces.put(feuille, libelleColonneA);
        return this;
    }

    /** En-tete d'une colonne quelconque renomme (0 = A). */
    ClasseurDeTest enteteColonne(String feuille, int colonne, String libelle) {
        colonnesRenommees.computeIfAbsent(feuille, f -> new LinkedHashMap<>()).put(colonne, libelle);
        return this;
    }

    /** Texte de la ligne de commentaire (ligne 2) de la feuille. */
    ClasseurDeTest commentaire(String feuille, String texte) {
        commentaires.put(feuille, texte);
        return this;
    }

    /**
     * Retire le collaborateur de toutes les feuilles, comme dans le fichier
     * d'une campagne ou il n'apparait plus (Employee_ID en colonne A, ou B
     * pour 06 et 09).
     */
    ClasseurDeTest sansCollaborateur(String idCollaborateur) {
        feuilles.forEach((nom, lignes) -> {
            int colonne = FEUILLE_SKILLS.equals(nom) || FEUILLE_SUCCESSION.equals(nom) ? 1 : 0;
            if (!FEUILLE_COMPETENCES.equals(nom) && !FEUILLE_POSTES.equals(nom)
                    && !FEUILLE_POSTES_CRITIQUES.equals(nom)) {
                lignes.removeIf(ligne -> ligne.length > colonne && idCollaborateur.equals(ligne[colonne]));
            }
        });
        return this;
    }

    /** Numero de ligne Excel de la ligne de donnees d'index donne. */
    static int ligneExcel(String feuille, int index) {
        return premiereLigneDonnees(feuille) + index + 1;
    }

    MockMultipartFile fichier() {
        return fichier("classeur-test.xlsx");
    }

    MockMultipartFile fichier(String nomFichier) {
        return new MockMultipartFile("fichier", nomFichier,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", octets());
    }

    byte[] octets() {
        try (XSSFWorkbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            feuilles.forEach((nom, lignes) -> ecrireFeuille(classeur.createSheet(nom), nom, lignes));
            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ecrireFeuille(Sheet feuille, String nom, List<Object[]> lignes) {
        ecrire(feuille.createRow(0), new Object[]{"TALENT 360 BANK - " + nom});
        ecrire(feuille.createRow(1), new Object[]{commentaires.getOrDefault(nom, "Commentaire de la feuille")});
        if (!ENTETES.containsKey(nom)) {
            return;
        }
        String[] entete = ENTETES.get(nom).clone();
        if (entetesRemplaces.containsKey(nom)) {
            entete[0] = entetesRemplaces.get(nom);
        }
        for (Map.Entry<Integer, String> renommee : colonnesRenommees.getOrDefault(nom, Map.of()).entrySet()) {
            entete = Arrays.copyOf(entete, Math.max(entete.length, renommee.getKey() + 1));
            entete[renommee.getKey()] = renommee.getValue();
        }
        int ligneEntete = premiereLigneDonnees(nom) - 1;
        ecrire(feuille.createRow(ligneEntete), entete);
        int index = ligneEntete + 1;
        for (Object[] valeurs : lignes) {
            ecrire(feuille.createRow(index++), valeurs);
        }
        // Une ligne vide puis une note : fin des donnees, comme la legende de 05.
        ecrire(feuille.createRow(index + 1), new Object[]{"Note : cette ligne n'est pas une donnee"});
    }

    /** Index (0) de la premiere ligne de donnees : l'en-tete est en ligne 4, en ligne 5 pour 09. */
    private static int premiereLigneDonnees(String feuille) {
        return FEUILLE_SUCCESSION.equals(feuille) ? 5 : 4;
    }

    private static void ecrire(Row ligne, Object[] valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            Object valeur = valeurs[i];
            if (valeur == null) {
                continue;
            }
            Cell cellule = ligne.createCell(i);
            if (valeur instanceof Number nombre) {
                cellule.setCellValue(nombre.doubleValue());
            } else if (valeur instanceof LocalDate date) {
                cellule.setCellValue(date);
            } else {
                cellule.setCellValue(valeur.toString());
            }
        }
    }
}
