package com.talent360bank.talent360bank.config;

import com.talent360bank.talent360bank.service.VivierReleveService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Pose le vivier de releve au premier demarrage, pour qu'il apparaisse a
 * l'ecran des viviers avant meme le premier calcul. Un vivier existant, meme
 * renomme par le RH, n'est pas modifie.
 */
@Component
public class VivierReleveInitializer implements ApplicationRunner {

    private final VivierReleveService vivierReleveService;

    public VivierReleveInitializer(VivierReleveService vivierReleveService) {
        this.vivierReleveService = vivierReleveService;
    }

    @Override
    public void run(ApplicationArguments args) {
        vivierReleveService.vivierReleve();
    }
}
