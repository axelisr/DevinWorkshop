package com.piggymetrics.notification.security;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2ClientCredentialsFeignInterceptorTest {

	private final OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);

	private final OAuth2ClientCredentialsFeignInterceptor interceptor =
			new OAuth2ClientCredentialsFeignInterceptor(manager, "notification-service");

	@Test
	void shouldAddClientCredentialsBearerToken() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("notification-service")
				.clientId("notification-service")
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.tokenUri("http://auth-service:5000/uaa/oauth/token")
				.build();
		OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
				Instant.now(), Instant.now().plusSeconds(60));
		ArgumentCaptor<OAuth2AuthorizeRequest> captor = ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
		when(manager.authorize(captor.capture()))
				.thenReturn(new OAuth2AuthorizedClient(registration, "notification-service", token));

		RequestTemplate template = new RequestTemplate();
		interceptor.apply(template);

		assertEquals(List.of("Bearer service-token"), List.copyOf(template.headers().get(HttpHeaders.AUTHORIZATION)));
		assertEquals("notification-service", captor.getValue().getClientRegistrationId());
	}

	@Test
	void shouldFailWhenTokenCannotBeObtained() {
		when(manager.authorize(any())).thenReturn(null);

		assertThrows(IllegalStateException.class, () -> interceptor.apply(new RequestTemplate()));
	}
}
