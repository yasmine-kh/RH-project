package com.talent360bank.talent360bank.dataset;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lecteur .xlsx minimal, sans dependance : il ne rend que les valeurs deja
 * calculees et enregistrees par Excel, jamais les formules. C'est exactement
 * ce qu'il faut pour comparer nos resultats a ceux du classeur.
 */
final class LecteurXlsx {

    private static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** Feuille -> numero de ligne -> lettre de colonne -> valeur. */
    private final Map<String, Map<Integer, Map<String, String>>> feuilles = new HashMap<>();

    LecteurXlsx(Path fichier) throws Exception {
        try (ZipFile zip = new ZipFile(fichier.toFile())) {
            List<String> chaines = lireChainesPartagees(zip);

            Map<String, String> cibles = new HashMap<>();
            NodeList relations = lire(zip, "xl/_rels/workbook.xml.rels").getElementsByTagName("Relationship");
            for (int i = 0; i < relations.getLength(); i++) {
                Element relation = (Element) relations.item(i);
                String cible = relation.getAttribute("Target").replaceFirst("^/", "");
                cibles.put(relation.getAttribute("Id"), cible.startsWith("xl/") ? cible : "xl/" + cible);
            }

            NodeList sheets = lire(zip, "xl/workbook.xml").getElementsByTagNameNS(NS_MAIN, "sheet");
            for (int i = 0; i < sheets.getLength(); i++) {
                Element sheet = (Element) sheets.item(i);
                String chemin = cibles.get(sheet.getAttributeNS(NS_REL, "id"));
                feuilles.put(sheet.getAttribute("name"), lireFeuille(lire(zip, chemin), chaines));
            }
        }
    }

    /** Lignes de la feuille, dans l'ordre. */
    Map<Integer, Map<String, String>> feuille(String nom) {
        Map<Integer, Map<String, String>> lignes = feuilles.get(nom);
        if (lignes == null) {
            throw new IllegalArgumentException("Feuille absente du classeur : " + nom);
        }
        return lignes;
    }

    private static Map<Integer, Map<String, String>> lireFeuille(Document document, List<String> chaines) {
        Map<Integer, Map<String, String>> lignes = new LinkedHashMap<>();
        NodeList cellules = document.getElementsByTagNameNS(NS_MAIN, "c");
        for (int i = 0; i < cellules.getLength(); i++) {
            Element cellule = (Element) cellules.item(i);
            String reference = cellule.getAttribute("r");
            String valeur = valeur(cellule, chaines);
            if (valeur == null || valeur.isEmpty()) {
                continue;
            }
            String colonne = reference.replaceAll("[0-9]", "");
            int ligne = Integer.parseInt(reference.replaceAll("[A-Z]", ""));
            lignes.computeIfAbsent(ligne, cle -> new LinkedHashMap<>()).put(colonne, valeur);
        }
        return lignes;
    }

    private static String valeur(Element cellule, List<String> chaines) {
        String type = cellule.getAttribute("t");
        if ("inlineStr".equals(type)) {
            return texte(cellule);
        }
        NodeList v = cellule.getElementsByTagNameNS(NS_MAIN, "v");
        if (v.getLength() == 0) {
            return null;
        }
        String brut = v.item(0).getTextContent();
        return "s".equals(type) ? chaines.get(Integer.parseInt(brut)) : brut;
    }

    private static List<String> lireChainesPartagees(ZipFile zip) throws Exception {
        List<String> chaines = new ArrayList<>();
        if (zip.getEntry("xl/sharedStrings.xml") == null) {
            return chaines;
        }
        NodeList items = lire(zip, "xl/sharedStrings.xml").getElementsByTagNameNS(NS_MAIN, "si");
        for (int i = 0; i < items.getLength(); i++) {
            chaines.add(texte((Element) items.item(i)));
        }
        return chaines;
    }

    /** Concatene les runs de texte, sans les annotations phonetiques. */
    private static String texte(Element element) {
        StringBuilder texte = new StringBuilder();
        NodeList morceaux = element.getElementsByTagNameNS(NS_MAIN, "t");
        for (int i = 0; i < morceaux.getLength(); i++) {
            if (!"rPh".equals(morceaux.item(i).getParentNode().getLocalName())) {
                texte.append(morceaux.item(i).getTextContent());
            }
        }
        return texte.toString();
    }

    private static Document lire(ZipFile zip, String chemin) throws Exception {
        ZipEntry entree = zip.getEntry(chemin);
        if (entree == null) {
            throw new IOException("Entree absente du classeur : " + chemin);
        }
        DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
        fabrique.setNamespaceAware(true);
        try (InputStream flux = zip.getInputStream(entree)) {
            return fabrique.newDocumentBuilder().parse(flux);
        }
    }
}
