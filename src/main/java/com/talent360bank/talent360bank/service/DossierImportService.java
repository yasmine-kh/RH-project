package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.service.resultat.ResultatImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Service
public class DossierImportService {

    private static final Logger log = LoggerFactory.getLogger(DossierImportService.class);

    private static final long TAILLE_MAX_FICHIER_OCTETS = 10 * 1024 * 1024; // 10 Mo max
    private static final int NOMBRE_MAX_FICHIERS = 50; // Limite S7

    private final ImportService importService;
    private final String cheminDossierImport;

    // Verrou contre le double import simultané (B6)
    private final AtomicBoolean enCoursDImportation = new AtomicBoolean(false);

    public DossierImportService(ImportService importService,
                                @Value("${talent360.import.folder:C:/talent360/imports}") String cheminDossierImport) {
        this.importService = importService;
        this.cheminDossierImport = cheminDossierImport;
    }

    public RapportDossierImport importerDossier(int annee, int numero, boolean simulation) throws IOException {

        // Anti-double import (B6)
        if (!enCoursDImportation.compareAndSet(false, true)) {
            throw new IllegalStateException("Un import de dossier est déjà en cours d'exécution.");
        }

        Map<String, ResultatImport> bilansFichiers = new LinkedHashMap<>();
        List<String> erreursGlobales = new ArrayList<>();

        try {
            Path dossierPath = Paths.get(cheminDossierImport).toAbsolutePath().normalize();

            if (!Files.exists(dossierPath) || !Files.isDirectory(dossierPath)) {
                erreursGlobales.add("Le dossier d'import spécifié n'existe pas : " + dossierPath);
                return new RapportDossierImport(false, bilansFichiers, erreursGlobales);
            }

            List<Path> fichiersValides = scannerEtFiltrerFichiers(dossierPath, erreursGlobales);

            if (fichiersValides.isEmpty()) {
                erreursGlobales.add("Aucun fichier valide à traiter dans le dossier.");
                return new RapportDossierImport(false, bilansFichiers, erreursGlobales);
            }

            for (Path fichierPath : fichiersValides) {
                String nomFichier = fichierPath.getFileName().toString();
                try {
                    MultipartFile multipartFile = convertirEnMultipartFile(fichierPath);
                    ResultatImport resultat = importService.importer(multipartFile, annee, numero, simulation);
                    bilansFichiers.put(nomFichier, resultat);
                } catch (Exception e) {
                    log.error("Erreur lors du traitement du fichier {}", nomFichier, e);
                    bilansFichiers.put(nomFichier, null);
                }
            }

            return new RapportDossierImport(true, bilansFichiers, erreursGlobales);

        } finally {
            enCoursDImportation.set(false); // Libération du verrou
        }
    }

    private List<Path> scannerEtFiltrerFichiers(Path dossierPath, List<String> erreurs) throws IOException {
        List<Path> resultat = new ArrayList<>();

        try (Stream<Path> stream = Files.list(dossierPath)) {
            List<Path> tousLesFichiers = stream.toList();

            for (Path path : tousLesFichiers) {
                // S7: Securisation via toRealPath et filtrage des fichiers verrous (~$)
                Path realPath = path.toRealPath();
                String nom = realPath.getFileName().toString();

                if (Files.isDirectory(realPath)) {
                    continue; // Ignorer les sous-dossiers
                }

                if (nom.startsWith("~$") || !nom.toLowerCase().endsWith(".xlsx")) {
                    log.info("Fichier ignoré (fichiers temporaires ou non-xlsx) : {}", nom);
                    continue;
                }

                if (Files.size(realPath) > TAILLE_MAX_FICHIER_OCTETS) {
                    erreurs.add("Fichier trop volumineux ignoration (>10Mo) : " + nom);
                    continue;
                }

                resultat.add(realPath);

                if (resultat.size() >= NOMBRE_MAX_FICHIERS) {
                    erreurs.add("Nombre maximal de fichiers atteint (" + NOMBRE_MAX_FICHIERS + "). Les suivants ont été ignorés.");
                    break;
                }
            }
        }

        return resultat;
    }

    private MultipartFile convertirEnMultipartFile(Path path) throws IOException {
        File file = path.toFile();
        return new CustomMultipartFile(file);
    }

    // Classe DTO de restitution du rapport
    public record RapportDossierImport(
            boolean succes,
            Map<String, ResultatImport> bilansParFichier,
            List<String> erreurs
    ) {}

    // Implémentation simplifiée de MultipartFile pour lire un fichier local du disque
    private static class CustomMultipartFile implements MultipartFile {
        private final File file;

        public CustomMultipartFile(File file) {
            this.file = file;
        }

        @Override public String getName() { return file.getName(); }
        @Override public String getOriginalFilename() { return file.getName(); }
        @Override public String getContentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
        @Override public boolean isEmpty() { return file.length() == 0; }
        @Override public long getSize() { return file.length(); }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(file.toPath()); }
        @Override public InputStream getInputStream() throws IOException { return new FileInputStream(file); }
        @Override public void transferTo(File dest) throws IOException, IllegalStateException { Files.copy(file.toPath(), dest.toPath()); }
    }
}