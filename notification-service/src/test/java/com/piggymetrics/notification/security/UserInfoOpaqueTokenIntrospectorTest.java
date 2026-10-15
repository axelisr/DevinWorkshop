package com.piggymetrics.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserInfoOpaqueTokenIntrospectorTest {

	private static final String USER_INFO_URI = "http://auth-service:5000/uaa/users/current";

	// shape of the serialized OAuth2Authentication returned by the legacy auth-service for a password grant token
	private static final String USER_TOKEN_RESPONSE = "{\"authorities\":[],\"details\":{\"tokenValue\":\"token\"}," +
			"\"authenticated\":true,\"userAuthentication\":{\"name\":\"test\"},\"principal\":{\"username\":\"test\"}," +
			"\"credentials\":\"\",\"oauth2Request\":{\"clientId\":\"browser\",\"scope\":[\"ui\"]," +
			"\"grantType\":\"password\"},\"clientOnly\":false,\"name\":\"test\"}";

	private static final String CLIENT_TOKEN_RESPONSE = "{\"authorities\":[{\"authority\":\"ROLE_SERVICE\"}]," +
			"\"authenticated\":true,\"userAuthentication\":null,\"principal\":\"account-service\",\"credentials\":\"\"," +
			"\"oauth2Request\":{\"clientId\":\"account-service\",\"scope\":[\"server\"],\"grantType\":\"client_credentials\"}," +
			"\"clientOnly\":true,\"name\":\"account-service\"}";

	private MockRestServiceServer server;

	private UserInfoOpaqueTokenIntrospector introspector;

	@BeforeEach
	void setup() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		introspector = new UserInfoOpaqueTokenIntrospector(USER_INFO_URI, restTemplate);
	}

	@Test
	void shouldResolveUserTokenWithUiScope() {
		server.expect(requestTo(USER_INFO_URI))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
				.andRespond(withSuccess(USER_TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");

		assertEquals("test", principal.getName());
		assertEquals(List.of("SCOPE_ui"), authorities(principal));
		assertEquals("browser", principal.getAttribute("client_id"));
		server.verify();
	}

	@Test
	void shouldResolveClientCredentialsTokenWithServerScopeAndAuthorities() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess(CLIENT_TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");

		assertEquals("account-service", principal.getName());
		assertTrue(authorities(principal).containsAll(List.of("ROLE_SERVICE", "SCOPE_server")));
	}

	@Test
	void shouldRejectTokenWhenAuthServiceReturnsUnauthorized() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\":\"invalid_token\"}"));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("invalid"));
	}

	@Test
	void shouldRejectTokenWhenUserInfoContainsError() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"error\":\"invalid_token\"}", MediaType.APPLICATION_JSON));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("invalid"));
	}

	@Test
	void shouldFailIntrospectionWhenAuthServiceIsUnavailable() {
		server.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

		assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect("token"));
	}

	private List<String> authorities(OAuth2AuthenticatedPrincipal principal) {
		return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}
}
