package com.piggymetrics.account.controller;

import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.domain.User;
import com.piggymetrics.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the authorization rules preserved from the legacy resource server configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AccountService accountService;

	@MockBean
	private OpaqueTokenIntrospector introspector;

	@Test
	void shouldRejectAnonymousRequestToProtectedAccount() throws Exception {
		mockMvc.perform(get("/test")).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRejectInvalidToken() throws Exception {
		when(introspector.introspect("bad-token")).thenThrow(new BadOpaqueTokenException("inactive"));

		mockMvc.perform(get("/test").header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldForbidAccountByNameWithoutServerScope() throws Exception {
		tokenWithAuthorities("ui-token", "test", "SCOPE_ui");

		mockMvc.perform(get("/test").header(HttpHeaders.AUTHORIZATION, "Bearer ui-token"))
				.andExpect(status().isForbidden());
	}

	@Test
	void shouldAllowAccountByNameWithServerScope() throws Exception {
		tokenWithAuthorities("server-token", "statistics-service", "SCOPE_server");
		when(accountService.findByName("test")).thenReturn(account("test"));

		mockMvc.perform(get("/test").header(HttpHeaders.AUTHORIZATION, "Bearer server-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("test"));
	}

	@Test
	void shouldAllowAnonymousDemoAccount() throws Exception {
		when(accountService.findByName("demo")).thenReturn(account("demo"));

		mockMvc.perform(get("/demo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("demo"));
	}

	@Test
	void shouldAllowAuthenticatedDemoAccountWithoutServerScope() throws Exception {
		tokenWithAuthorities("ui-token", "test", "SCOPE_ui");
		when(accountService.findByName("demo")).thenReturn(account("demo"));

		mockMvc.perform(get("/demo").header(HttpHeaders.AUTHORIZATION, "Bearer ui-token"))
				.andExpect(status().isOk());
	}

	@Test
	void shouldGetCurrentAccountForTokenPrincipal() throws Exception {
		tokenWithAuthorities("user-token", "test", "SCOPE_ui");
		when(accountService.findByName("test")).thenReturn(account("test"));

		mockMvc.perform(get("/current").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("test"));
	}

	@Test
	void shouldAllowAnonymousRegistration() throws Exception {
		when(accountService.create(any(User.class))).thenReturn(account("newuser"));

		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"newuser\",\"password\":\"password\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void shouldReturnBadRequestNotUnauthorizedForInvalidAnonymousRegistration() throws Exception {
		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"t\"}"))
				.andExpect(status().isBadRequest());
	}

	private void tokenWithAuthorities(String token, String name, String... authorities) {
		when(introspector.introspect(token)).thenReturn(new DefaultOAuth2AuthenticatedPrincipal(name,
				Map.of("name", name), AuthorityUtils.createAuthorityList(authorities)));
	}

	private Account account(String name) {
		Account account = new Account();
		account.setName(name);
		return account;
	}
}
