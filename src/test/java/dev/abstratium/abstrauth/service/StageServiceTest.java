package dev.abstratium.abstrauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(StageServiceTest.TestProfile.class)
class StageServiceTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("abstratium.stage", "test");
        }
    }

    @Inject
    StageService stageService;

    @Test
    void testIsTestReturnsTrueForTestStage() {
        assertTrue(stageService.isTest());
    }

    @Test
    void testIsDevReturnsFalseForTestStage() {
        assertFalse(stageService.isDev());
    }

    @Test
    void testIsProdReturnsFalseForTestStage() {
        assertFalse(stageService.isProd());
    }

    @Test
    void testGetStageReturnsCorrectValue() {
        assertEquals("test", stageService.getStage());
    }
}
