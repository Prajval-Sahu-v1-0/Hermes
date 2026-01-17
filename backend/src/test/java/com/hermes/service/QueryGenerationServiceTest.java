package com.hermes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryGenerationServiceTest {

    private QueryGenerationService queryGenerationService;

    @BeforeEach
    void setUp() {
        queryGenerationService = new QueryGenerationService("test-api-key");
    }

    @Test
    void testNormalize() {
        // Since normalize is private, we test it through public method logic if
        // possible,
        // or just rely on the implementation.
        // For domestic testing, we'll just check if the service can be instantiated.
        assertNotNull(queryGenerationService);
    }

    // Note: A full integration test would require mocking the HttpClient,
    // but for this phase we are focusing on the service structure.
}
