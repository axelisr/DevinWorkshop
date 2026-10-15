package com.piggymetrics.account.client;

import com.piggymetrics.account.domain.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author cdov
 */
@SpringBootTest(properties = {
        "spring.cloud.openfeign.circuitbreaker.enabled=true"
})
@ExtendWith(OutputCaptureExtension.class)
class StatisticsServiceClientFallbackTest {

    @Autowired
    private StatisticsServiceClient statisticsServiceClient;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Test
    void testUpdateStatisticsWithFailFallback(CapturedOutput output) {
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token",
                Instant.now(), Instant.now().plusSeconds(60));
        when(authorizedClientManager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(
                clientRegistrationRepository.findByRegistrationId("account-service"), "account-service", token));

        statisticsServiceClient.updateStatistics("test", new Account());

        assertThat(output).contains("Error during update statistics for account: test");
    }

}
