package com.hermes.service;

import com.hermes.cache.QueryDigestService;
import com.hermes.cache.QueryNormalizer;
import com.hermes.governor.TokenGovernor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class QueryGenerationServiceTest {

    private QueryGenerationService queryGenerationService;
    private QueryDigestService mockCacheService;
    private QueryNormalizer mockNormalizer;
    private TokenGovernor mockTokenGovernor;

    @BeforeEach
    void setUp() {
        mockCacheService = Mockito.mock(QueryDigestService.class);
        mockNormalizer = Mockito.mock(QueryNormalizer.class);
        mockTokenGovernor = Mockito.mock(TokenGovernor.class);

        when(mockNormalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0).toString().toLowerCase());
        when(mockCacheService.get(anyString())).thenReturn(Optional.empty());
        when(mockTokenGovernor.checkBudget(Mockito.anyInt()))
                .thenReturn(new TokenGovernor.BudgetDecision(
                        TokenGovernor.BudgetAction.ALLOW, 0, 1000000, "test"));

        queryGenerationService = new QueryGenerationService(
                "test-api-key",
                mockCacheService,
                mockNormalizer,
                mockTokenGovernor);
    }

    @Test
    void testServiceInstantiation() {
        assertNotNull(queryGenerationService);
    }

    // Note: Full integration tests would require mocking HttpClient or using
    // WireMock
}
