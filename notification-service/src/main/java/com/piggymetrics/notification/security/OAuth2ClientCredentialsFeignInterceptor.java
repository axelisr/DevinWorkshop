package com.piggymetrics.notification.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Adds a client_credentials bearer token to every Feign request, replacing the legacy
 * {@code OAuth2FeignRequestInterceptor}.
 */
public class OAuth2ClientCredentialsFeignInterceptor implements RequestInterceptor {

	private final OAuth2AuthorizedClientManager authorizedClientManager;

	private final String registrationId;

	public OAuth2ClientCredentialsFeignInterceptor(OAuth2AuthorizedClientManager authorizedClientManager, String registrationId) {
		this.authorizedClientManager = authorizedClientManager;
		this.registrationId = registrationId;
	}

	@Override
	public void apply(RequestTemplate template) {
		OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
				.principal(registrationId)
				.build();
		OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
		if (client == null) {
			throw new IllegalStateException("Could not obtain access token for client registration " + registrationId);
		}
		template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
	}
}
