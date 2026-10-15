package com.piggymetrics.account.client;

import com.sun.net.httpserver.HttpServer;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the client_credentials flow against a stub that mimics the legacy auth-service token endpoint.
 */
class OAuth2ClientCredentialsRequestInterceptorTest {

	private final List<String> authorizationHeaders = new ArrayList<>();

	private final List<String> requestBodies = new ArrayList<>();

	private HttpServer authService;

	private OAuth2ClientCredentialsRequestInterceptor interceptor;

	@BeforeEach
	void setup() throws Exception {
		authService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		authService.createContext("/uaa/oauth/token", exchange -> {
			authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBodies.add(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
					StandardCharsets.UTF_8));
			byte[] body = "{\"access_token\":\"legacy-token\",\"token_type\":\"bearer\",\"expires_in\":43199,\"scope\":\"server\"}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		authService.start();

		ClientRegistration registration = ClientRegistration.withRegistrationId("account-service")
				.clientId("account-service")
				.clientSecret("password")
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.scope("server")
				.tokenUri("http://localhost:" + authService.getAddress().getPort() + "/uaa/oauth/token")
				.build();
		InMemoryClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(registration);
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
				repository, new InMemoryOAuth2AuthorizedClientService(repository));
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

		interceptor = new OAuth2ClientCredentialsRequestInterceptor(manager, "account-service");
	}

	@AfterEach
	void tearDown() {
		authService.stop(0);
	}

	@Test
	void shouldAddClientCredentialsTokenAndReuseIt() {
		RequestTemplate first = new RequestTemplate();
		RequestTemplate second = new RequestTemplate();
		second.header(HttpHeaders.AUTHORIZATION, "Bearer stale");

		interceptor.apply(first);
		interceptor.apply(second);

		assertThat(first.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer legacy-token");
		assertThat(second.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer legacy-token");

		assertThat(requestBodies).hasSize(1);
		assertThat(requestBodies.get(0)).contains("grant_type=client_credentials").contains("scope=server");
		assertThat(authorizationHeaders.get(0)).isEqualTo("Basic " + Base64.getEncoder()
				.encodeToString("account-service:password".getBytes(StandardCharsets.UTF_8)));
	}
}
