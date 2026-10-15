package com.piggymetrics.statistics.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomUserInfoTokenIntrospectorTest {

	private static final String USER_INFO_URI = "http://auth-service:5000/uaa/users/current";

	private MockRestServiceServer server;

	private CustomUserInfoTokenIntrospector introspector;

	@BeforeEach
	void setup() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		introspector = new CustomUserInfoTokenIntrospector(USER_INFO_URI, restTemplate);
	}

	@Test
	void shouldExtractPrincipalAuthoritiesAndScopesFromLegacyUserInfo() {

		// shape of the Principal (OAuth2Authentication) returned by the legacy auth-service
		String userInfo = "{\"name\":\"statistics-service\",\"authenticated\":true,"
				+ "\"authorities\":[{\"authority\":\"ROLE_SERVICE\"}],"
				+ "\"oauth2Request\":{\"clientId\":\"statistics-service\",\"scope\":[\"server\"],\"grantType\":\"client_credentials\"}}";

		server.expect(requestTo(USER_INFO_URI))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
				.andRespond(withSuccess(userInfo, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");

		assertEquals("statistics-service", principal.getName());
		List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		assertTrue(authorities.contains("ROLE_SERVICE"));
		assertTrue(authorities.contains("SCOPE_server"));
		assertEquals("statistics-service", principal.getAttribute("client_id"));
		assertEquals(List.of("server"), principal.getAttribute("scope"));
		server.verify();
	}

	@Test
	void shouldDefaultToRoleUserWhenNoAuthoritiesPresent() {

		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"name\":\"demo\",\"oauth2Request\":{\"clientId\":\"browser\",\"scope\":[\"ui\"]}}",
						MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");

		assertEquals("demo", principal.getName());
		List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		assertEquals(List.of("ROLE_USER", "SCOPE_ui"), authorities);
	}

	@Test
	void shouldRejectTokenWhenAuthServiceRejectsIt() {

		server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("invalid"));
	}

	@Test
	void shouldRejectTokenWhenUserInfoContainsError() {

		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"error\":\"invalid_token\"}", MediaType.APPLICATION_JSON));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("invalid"));
	}
}
