package dev.abstratium.abstrauth.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Initializes metrics on application startup.
 */
@ApplicationScoped
public class MetricsInitializer {

    @Inject
    MetricsService metricsService;

    void onStart(@Observes StartupEvent ev) {
        metricsService.initialize();
    }
}
