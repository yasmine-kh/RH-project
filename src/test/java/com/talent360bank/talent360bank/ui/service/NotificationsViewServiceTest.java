package com.talent360bank.talent360bank.ui.service;

import com.talent360bank.talent360bank.entity.Trimestre;
import com.talent360bank.talent360bank.ui.model.AlertesView;
import com.talent360bank.talent360bank.ui.model.AlertesView.Compteur;
import com.talent360bank.talent360bank.ui.model.Notifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Badge de la cloche : repris du dernier calcul pendant DUREE_CACHE, recalcule ensuite. */
class NotificationsViewServiceTest {

    /** Horloge qu'on avance a la main. */
    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-10-02T10:00:00Z");

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private final AlertesViewService alertes = mock(AlertesViewService.class);
    private final TrimestreCourantService trimestreCourant = mock(TrimestreCourantService.class);
    private final Horloge horloge = new Horloge();
    private NotificationsViewService service;
    private Trimestre t3;

    @BeforeEach
    void init() {
        t3 = new Trimestre();
        t3.setIdTrimestre(3);
        t3.setAnnee(2026);
        t3.setNumero(3);
        when(trimestreCourant.selectionner(null)).thenReturn(new TrimestreCourantService.Selection(t3, List.of()));
        when(alertes.construire(t3, AlertesView.Filtres.AUCUN)).thenReturn(new AlertesView("T3 2026", 3,
                List.of(), List.of(new Compteur("CRITIQUE", "Critique", 1),
                new Compteur("ELEVEE", "Élevée", 0), new Compteur("MOYENNE", "Moyenne", 2)),
                List.of(), List.of(), AlertesView.Filtres.AUCUN, List.of(), null));
        service = new NotificationsViewService(alertes, trimestreCourant, horloge);
    }

    @Test
    void le_badge_reprend_le_dernier_calcul_puis_recalcule_apres_la_duree() {
        Notifications.Badge premier = service.badge();
        assertThat(premier.total()).isEqualTo(3);
        assertThat(premier.critiques()).isEqualTo(1);
        assertThat(premier.moyennes()).isEqualTo(2);
        assertThat(premier.lienAlertes()).isEqualTo("/alertes?trimestre=2026-3");

        horloge.avancer(NotificationsViewService.DUREE_CACHE.minusSeconds(1));
        service.badge();
        verify(alertes, times(1)).construire(t3, AlertesView.Filtres.AUCUN);

        horloge.avancer(Duration.ofSeconds(2));
        service.badge();
        verify(alertes, times(2)).construire(t3, AlertesView.Filtres.AUCUN);
    }

    @Test
    void les_notifications_sont_toujours_recalculees_et_rafraichissent_le_badge() {
        service.notifications();
        service.notifications();
        verify(alertes, times(2)).construire(t3, AlertesView.Filtres.AUCUN);
        service.badge();
        verify(alertes, times(2)).construire(t3, AlertesView.Filtres.AUCUN);
    }

    @Test
    void sans_trimestre_le_badge_est_vide() {
        when(trimestreCourant.selectionner(null)).thenReturn(new TrimestreCourantService.Selection(null, List.of()));
        when(alertes.construire(null, AlertesView.Filtres.AUCUN)).thenReturn(new AlertesView(null, 0, List.of(),
                List.of(), List.of(), List.of(), AlertesView.Filtres.AUCUN, List.of(), null));

        Notifications.Badge badge = service.badge();
        assertThat(badge.total()).isZero();
        assertThat(badge.lienAlertes()).isNull();
    }
}
