package dev.abstratium.abstrauth.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class StageService {

    @ConfigProperty(name = "abstratium.stage", defaultValue = "dev")
    String stage;

    public boolean isProd() {
        return "prod".equalsIgnoreCase(stage);
    }

    public boolean isTest() {
        return "test".equalsIgnoreCase(stage);
    }

    public boolean isDev() {
        return "dev".equalsIgnoreCase(stage);
    }

    public String getStage() {
        return stage;
    }
}
