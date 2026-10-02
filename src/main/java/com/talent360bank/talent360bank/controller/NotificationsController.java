package com.talent360bank.talent360bank.controller;

import com.talent360bank.talent360bank.ui.model.Notifications;
import com.talent360bank.talent360bank.ui.service.NotificationsViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Notifications de l'utilisateur RH, derivees des alertes du dernier trimestre qui a des scores. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    private final NotificationsViewService service;

    public NotificationsController(NotificationsViewService service) {
        this.service = service;
    }

    /** Compteurs et les 10 premieres alertes, toujours recalcules. */
    @GetMapping
    public Notifications notifications() {
        return service.notifications();
    }

    /** Badge de la cloche, pour le layout : peu couteux (voir NotificationsViewService). */
    @GetMapping("/badge")
    public Notifications.Badge badge() {
        return service.badge();
    }
}
