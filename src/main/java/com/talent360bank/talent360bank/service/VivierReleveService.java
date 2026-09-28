package com.talent360bank.talent360bank.service;

import com.talent360bank.talent360bank.entity.AppartenanceVivier;
import com.talent360bank.talent360bank.entity.Employe;
import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.entity.Vivier;
import com.talent360bank.talent360bank.repository.AppartenanceVivierRepository;
import com.talent360bank.talent360bank.repository.VivierRepository;
import com.talent360bank.talent360bank.service.resultat.MembreVivierReleve;
import com.talent360bank.talent360bank.service.resultat.ResultatConstitutionVivier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ecrit le vivier de releve calcule par {@link TalentService#getVivierReleve}
 * dans AppartenanceVivier, pour que les ecrans le lisent comme les viviers
 * thematiques.
 *
 * <p>Le vivier de releve est un {@link Vivier} gere par le moteur, retrouve par
 * son code {@link #CODE_VIVIER_RELEVE} et jamais par son nom : le RH peut le
 * renommer sans casser le calcul. Les lignes du moteur portent l'origine
 * {@link #ORIGINE_MOTEUR} ; celles d'un import ou d'une saisie RH ne sont
 * jamais modifiees ni supprimees.
 */
@Service
public class VivierReleveService {

    private static final Logger log = LoggerFactory.getLogger(VivierReleveService.class);

    /** Code fixe du vivier de releve dans la table vivier. */
    public static final String CODE_VIVIER_RELEVE = "RELEVE";

    /** Nom affiche a la creation du vivier ; le RH peut le changer ensuite. */
    public static final String NOM_VIVIER_RELEVE = "Vivier de relève";

    /** Origine des appartenances ecrites par le moteur. */
    public static final String ORIGINE_MOTEUR = "MOTEUR";

    private final TalentService talentService;
    private final VivierRepository vivierRepository;
    private final AppartenanceVivierRepository appartenanceVivierRepository;

    public VivierReleveService(TalentService talentService,
                               VivierRepository vivierRepository,
                               AppartenanceVivierRepository appartenanceVivierRepository) {
        this.talentService = talentService;
        this.vivierRepository = vivierRepository;
        this.appartenanceVivierRepository = appartenanceVivierRepository;
    }

    /**
     * Le vivier de releve, cree s'il n'existe pas encore. Un vivier existant
     * n'est pas modifie : son nom a pu etre change par le RH.
     */
    @Transactional
    public Vivier vivierReleve() {
        return vivierRepository.findByCode(CODE_VIVIER_RELEVE).orElseGet(() -> {
            Vivier vivier = new Vivier();
            vivier.setCode(CODE_VIVIER_RELEVE);
            vivier.setNomCategorie(NOM_VIVIER_RELEVE);
            vivier.setDescription("Talents et hauts potentiels, alimente par le moteur de calcul");
            log.info("Vivier de releve cree (code {})", CODE_VIVIER_RELEVE);
            return vivierRepository.save(vivier);
        });
    }

    /**
     * Constitue le vivier de releve du trimestre.
     *
     * <p>Idempotent : les lignes du moteur de ce trimestre sont remplacees en
     * une transaction, un second passage rend les memes appartenances. Les
     * autres trimestres, les autres viviers et les lignes d'une autre origine
     * ne sont pas touches ; un employe deja present par une autre origine
     * n'est pas double.
     */
    @Transactional
    public ResultatConstitutionVivier constituerViviers(Trimestre trimestre) {
        Objects.requireNonNull(trimestre, "trimestre");

        // Calcule avant toute ecriture : si les reglages manquent, rien n'est supprime.
        List<MembreVivierReleve> membres = talentService.getVivierReleve(trimestre);

        Vivier vivier = vivierReleve();
        int remplaces = appartenanceVivierRepository.supprimer(trimestre, vivier, ORIGINE_MOTEUR);

        Set<String> dejaPresents = new HashSet<>();
        for (AppartenanceVivier restante : appartenanceVivierRepository.findByTrimestreEtVivier(trimestre, vivier)) {
            dejaPresents.add(restante.getEmploye().getEmployeeId());
        }

        List<AppartenanceVivier> crees = new ArrayList<>();
        List<String> ignores = new ArrayList<>();
        for (MembreVivierReleve membre : membres) {
            Employe employe = membre.score().getEmploye();
            if (dejaPresents.contains(employe.getEmployeeId())) {
                ignores.add(employe.getEmployeeId());
                continue;
            }
            AppartenanceVivier appartenance = new AppartenanceVivier();
            appartenance.setEmploye(employe);
            appartenance.setVivier(vivier);
            appartenance.setTrimestre(trimestre);
            appartenance.setOrigine(ORIGINE_MOTEUR);
            crees.add(appartenance);
        }
        crees = appartenanceVivierRepository.saveAll(crees);

        log.info("Vivier de releve {} : {} ligne(s) remplacee(s), {} ecrite(s), {} deja presente(s) par une autre origine",
                decrire(trimestre), remplaces, crees.size(), ignores.size());
        return new ResultatConstitutionVivier(remplaces, List.copyOf(crees), List.copyOf(ignores));
    }

    private String decrire(Trimestre trimestre) {
        return "T" + trimestre.getNumero() + " " + trimestre.getAnnee();
    }
}
