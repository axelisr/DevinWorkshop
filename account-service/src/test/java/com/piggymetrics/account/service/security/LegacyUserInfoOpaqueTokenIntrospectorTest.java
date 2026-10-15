package com.piggymetrics.account.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LegacyUserInfoOpaqueTokenIntrospectorTest {

	private static final String USER_INFO_URI = "http://auth-service:5000/uaa/users/current";

	// OAuth2Authentication as serialized by the legacy auth-service for a client_credentials token
	private static final String CLIENT_CREDENTIALS_USER_INFO = """
			{"authorities":[],"details":{"tokenValue":"server-token","tokenType":"Bearer"},
			 "authenticated":true,"userAuthentication":null,"credentials":"","principal":"statistics-service",
			 "clientOnly":true,
			 "oauth2Request":{"clientId":"statistics-service","scope":["server"],"approved":true,
			                  "grantType":"client_credentials","authorities":[]},
			 "name":"statistics-service"}
			""";

	// OAuth2Authentication for a password grant token issued to the browser client
	private static final String USER_USER_INFO = """
			{"authorities":[],"authenticated":true,
			 "userAuthentication":{"principal":{"username":"demo","password":null},"name":"demo"},
			 "principal":{"username":"demo","password":null},"clientOnly":false,
			 "oauth2Request":{"clientId":"browser","scope":["ui"],"grantType":"password"},
			 "name":"demo"}
			""";

	private MockRestServiceServer server;

	private LegacyUserInfoOpaqueTokenIntrospector introspector;

	@BeforeEach
	void setup() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		introspector = new LegacyUserInfoOpaqueTokenIntrospector(USER_INFO_URI, restTemplate);
	}

	@Test
	void shouldMapClientCredentialsTokenScopesToAuthorities() {
		server.expect(requestTo(USER_INFO_URI))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer server-token"))
				.andRespond(withSuccess(CLIENT_CREDENTIALS_USER_INFO, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("server-token");

		assertThat(principal.getName()).isEqualTo("statistics-service");
		assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("SCOPE_server");
		assertThat((Object) principal.getAttribute("client_id")).isEqualTo("statistics-service");
		server.verify();
	}

	@Test
	void shouldResolveUserTokenPrincipalName() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess(USER_USER_INFO, MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("user-token");

		assertThat(principal.getName()).isEqualTo("demo");
		assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("SCOPE_ui");
	}

	@Test
	void shouldAddDefaultRoleWhenAuthoritiesAreMissing() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"name\":\"demo\",\"oauth2Request\":{\"clientId\":\"browser\",\"scope\":[\"ui\"]}}",
						MediaType.APPLICATION_JSON));

		OAuth2AuthenticatedPrincipal principal = introspector.introspect("user-token");

		assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_USER", "SCOPE_ui");
	}

	@Test
	void shouldRejectTokenUnknownToAuthService() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\":\"invalid_token\",\"error_description\":\"Invalid access token: x\"}"));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("x"));
	}

	@Test
	void shouldRejectErrorPayload() {
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{\"error\":\"invalid_token\"}", MediaType.APPLICATION_JSON));

		assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("x"));
	}

	@Test
	void shouldFailIntrospectionWhenAuthServiceIsUnavailable() {
		server.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

		OAuth2IntrospectionException exception = assertThrows(OAuth2IntrospectionException.class,
				() -> introspector.introspect("x"));
		assertThat(exception).isNotInstanceOf(BadOpaqueTokenException.class);
	}
}
