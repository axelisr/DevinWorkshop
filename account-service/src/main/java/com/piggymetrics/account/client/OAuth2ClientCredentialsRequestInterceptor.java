package com.piggymetrics.account.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Adds a client_credentials access token issued by auth-service to every outgoing Feign request.
 */
public class OAuth2ClientCredentialsRequestInterceptor implements RequestInterceptor {

	private final OAuth2AuthorizedClientManager authorizedClientManager;

	private final String clientRegistrationId;

	public OAuth2ClientCredentialsRequestInterceptor(OAuth2AuthorizedClientManager authorizedClientManager,
			String clientRegistrationId) {
		this.authorizedClientManager = authorizedClientManager;
		this.clientRegistrationId = clientRegistrationId;
	}

	@Override
	public void apply(RequestTemplate template) {
		OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
				.withClientRegistrationId(clientRegistrationId)
				.principal(clientRegistrationId)
				.build();

		OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
		if (client == null) {
			throw new IllegalStateException("Could not obtain access token for client registration " + clientRegistrationId);
		}

		template.removeHeader(HttpHeaders.AUTHORIZATION);
		template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
	}
}
