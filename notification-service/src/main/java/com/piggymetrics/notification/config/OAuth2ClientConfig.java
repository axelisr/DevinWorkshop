package com.piggymetrics.notification.config;

import com.piggymetrics.notification.security.OAuth2ClientCredentialsFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Obtains client_credentials tokens from the auth-service for outgoing Feign calls.
 * The service-based manager works outside of an HTTP request (e.g. scheduled notifications).
 */
@Configuration
public class OAuth2ClientConfig {

	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
																 OAuth2AuthorizedClientService authorizedClientService) {
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
				new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
		return manager;
	}

	@Bean
	public RequestInterceptor oauth2FeignRequestInterceptor(OAuth2AuthorizedClientManager authorizedClientManager,
			@Value("${piggymetrics.security.oauth2.client.registration-id:notification-service}") String registrationId) {
		return new OAuth2ClientCredentialsFeignInterceptor(authorizedClientManager, registrationId);
	}
}
