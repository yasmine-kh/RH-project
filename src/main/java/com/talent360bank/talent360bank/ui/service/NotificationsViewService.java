package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.ui.model.AlertesView;
import com.talent360bank.talent360bank.ui.model.Notifications;
import com.talent360bank.talent360bank.ui.model.Notifications.Badge;
import com.talent360bank.talent360bank.ui.model.SeveriteAlerte;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notifications et badge de la cloche, derives des alertes du dernier
 * trimestre qui a des scores ({@link TrimestreCourantService}, comme les
 * ecrans sans {@code ?trimestre=}).
 *
 * <p>REGLE : aucun calcul ici. Tout vient de {@link AlertesViewService#construire}
 * sans filtre : total, compteurs par gravite et par type, et ordre des alertes
 * sont ceux de l'ecran Alertes. Pas de table, pas d'etat lu / non lu.
 *
 * <p><strong>Cout.</strong> {@link #notifications()} recalcule les alertes (le
 * cout de l'ecran Alertes). {@link #badge()}, appele par le layout a chaque
 * page, lit le trimestre (deux requetes) puis reprend le dernier resultat de
 * ce trimestre s'il a moins de {@link #DUREE_CACHE} : apres un import ou un
 * changement de reglages, le badge peut donc avoir jusqu'a cette duree de
 * retard ; l'ecran Alertes et {@link #notifications()} sont toujours a jour.
 */
@Service
public class NotificationsViewService {

    /** Duree pendant laquelle le badge reprend le dernier calcul des alertes du trimestre. */
    public static final Duration DUREE_CACHE = Duration.ofSeconds(60);

    private final AlertesViewService alertesViewService;
    private final TrimestreCourantService trimestreCourant;
    private final Clock horloge;
    private final Map<Integer, Entree> cache = new ConcurrentHashMap<>();

    @Autowired
    public NotificationsViewService(AlertesViewService alertesViewService, TrimestreCourantService trimestreCourant) {
        this(alertesViewService, trimestreCourant, Clock.systemUTC());
    }

    NotificationsViewService(AlertesViewService alertesViewService, TrimestreCourantService trimestreCourant,
                             Clock horloge) {
        this.alertesViewService = alertesViewService;
        this.trimestreCourant = trimestreCourant;
        this.horloge = horloge;
    }

    /** Resume complet, toujours recalcule (et garde pour le badge). */
    public Notifications notifications() {
        Trimestre trimestre = trimestreCourant.selectionner(null).trimestre();
        return calculer(trimestre);
    }

    /** Resume des alertes d'un trimestre donne (ecran Notifications avec ?trimestre=), toujours recalcule. */
    public Notifications notifications(Trimestre trimestre) {
        return calculer(trimestre);
    }

    /** Badge de la cloche : le dernier resume du trimestre s'il est assez recent, sinon un nouveau. */
    public Badge badge() {
        Trimestre trimestre = trimestreCourant.selectionner(null).trimestre();
        Notifications notifications;
        Entree entree = trimestre == null ? null : cache.get(trimestre.getIdTrimestre());
        if (entree != null && entree.calcule().plus(DUREE_CACHE).isAfter(horloge.instant())) {
            notifications = entree.notifications();
        } else {
            notifications = calculer(trimestre);
        }
        return new Badge(notifications.trimestreLibelle(), notifications.total(),
                nombre(notifications, SeveriteAlerte.CRITIQUE), nombre(notifications, SeveriteAlerte.ELEVEE),
                nombre(notifications, SeveriteAlerte.MOYENNE), notifications.lienAlertes());
    }

    private Notifications calculer(Trimestre trimestre) {
        AlertesView vue = alertesViewService.construire(trimestre, AlertesView.Filtres.AUCUN);
        Notifications notifications = new Notifications(vue.trimestreLibelle(), vue.total(), vue.parSeverite(),
                vue.parType(), vue.alertes().stream().limit(Notifications.NB_ALERTES).toList(),
                trimestre == null ? null : "/alertes?trimestre=" + TrimestreCourantService.valeur(trimestre),
                vue.informations(), vue.erreur());
        if (trimestre != null) {
            cache.put(trimestre.getIdTrimestre(), new Entree(notifications, horloge.instant()));
        }
        return notifications;
    }

    private static int nombre(Notifications notifications, SeveriteAlerte severite) {
        return notifications.parSeverite().stream()
                .filter(compteur -> compteur.code().equals(severite.name()))
                .mapToInt(AlertesView.Compteur::nombre)
                .findFirst().orElse(0);
    }

    private record Entree(Notifications notifications, Instant calcule) {
    }
}
