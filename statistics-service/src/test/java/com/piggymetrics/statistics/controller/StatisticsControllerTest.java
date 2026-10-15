package com.piggymetrics.statistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.piggymetrics.statistics.domain.Account;
import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.Item;
import com.piggymetrics.statistics.domain.Saving;
import com.piggymetrics.statistics.domain.TimePeriod;
import com.piggymetrics.statistics.domain.timeseries.DataPoint;
import com.piggymetrics.statistics.domain.timeseries.DataPointId;
import com.piggymetrics.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatisticsControllerTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private StatisticsService statisticsService;

	@MockBean
	private OpaqueTokenIntrospector introspector;

	@Test
	void shouldGetStatisticsByAccountName() throws Exception {

		final DataPoint dataPoint = dataPoint("test");

		when(statisticsService.findByAccountName(dataPoint.getId().getAccount()))
				.thenReturn(ImmutableList.of(dataPoint));

		mockMvc.perform(get("/test").with(serverScope()))
				.andExpect(jsonPath("$[0].id.account").value(dataPoint.getId().getAccount()))
				.andExpect(status().isOk());
	}

	@Test
	void shouldGetCurrentAccountStatistics() throws Exception {

		final DataPoint dataPoint = dataPoint("test");

		when(statisticsService.findByAccountName(dataPoint.getId().getAccount()))
				.thenReturn(ImmutableList.of(dataPoint));

		mockMvc.perform(get("/current").with(opaqueToken().attributes(a -> a.put("sub", "test"))
						.principal(principal("test", "ui"))))
				.andExpect(jsonPath("$[0].id.account").value(dataPoint.getId().getAccount()))
				.andExpect(status().isOk());
	}

	@Test
	void shouldSaveAccountStatistics() throws Exception {

		mockMvc.perform(put("/test").with(serverScope())
						.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(account())))
				.andExpect(status().isOk());

		verify(statisticsService, times(1)).save(anyString(), any(Account.class));
	}

	@Test
	void shouldRejectInvalidAccountStatistics() throws Exception {

		Account account = account();
		account.setSaving(null);

		mockMvc.perform(put("/test").with(serverScope())
						.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(account)))
				.andExpect(status().isBadRequest());

		verify(statisticsService, never()).save(anyString(), any(Account.class));
	}

	@Test
	void shouldRequireAuthentication() throws Exception {
		mockMvc.perform(get("/demo")).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldAllowDemoStatisticsWithoutServerScope() throws Exception {

		when(statisticsService.findByAccountName("demo")).thenReturn(ImmutableList.of(dataPoint("demo")));

		mockMvc.perform(get("/demo").with(opaqueToken().principal(principal("user", "ui"))))
				.andExpect(jsonPath("$[0].id.account").value("demo"))
				.andExpect(status().isOk());
	}

	@Test
	void shouldForbidOtherAccountStatisticsWithoutServerScope() throws Exception {

		mockMvc.perform(get("/test").with(opaqueToken().principal(principal("user", "ui"))))
				.andExpect(status().isForbidden());

		verify(statisticsService, never()).findByAccountName(anyString());
	}

	@Test
	void shouldForbidSavingStatisticsWithoutServerScope() throws Exception {

		mockMvc.perform(put("/test").with(opaqueToken().principal(principal("user", "ui")))
						.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(account())))
				.andExpect(status().isForbidden());

		verify(statisticsService, never()).save(anyString(), any(Account.class));
	}

	@Test
	void shouldAuthenticateBearerTokenThroughIntrospector() throws Exception {

		when(introspector.introspect("server-token")).thenReturn(principal("account-service", "server"));
		when(statisticsService.findByAccountName("test")).thenReturn(ImmutableList.of(dataPoint("test")));

		mockMvc.perform(get("/test").header(HttpHeaders.AUTHORIZATION, "Bearer server-token"))
				.andExpect(status().isOk());

		verify(introspector).introspect(eq("server-token"));
	}

	@Test
	void shouldRejectInvalidBearerToken() throws Exception {

		when(introspector.introspect("bad-token")).thenThrow(new BadOpaqueTokenException("invalid"));

		mockMvc.perform(get("/demo").header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
				.andExpect(status().isUnauthorized());
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor serverScope() {
		return opaqueToken().principal(principal("account-service", "server"));
	}

	private static OAuth2IntrospectionAuthenticatedPrincipal principal(String name, String scope) {
		return new OAuth2IntrospectionAuthenticatedPrincipal(name, Map.of("name", name, "scope", List.of(scope)),
				List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
	}

	private static DataPoint dataPoint(String account) {
		final DataPoint dataPoint = new DataPoint();
		dataPoint.setId(new DataPointId(account, new Date()));
		return dataPoint;
	}

	private static Account account() {

		Saving saving = new Saving();
		saving.setAmount(new BigDecimal(1500));
		saving.setCurrency(Currency.USD);
		saving.setInterest(new BigDecimal("3.32"));
		saving.setDeposit(true);
		saving.setCapitalization(false);

		Item grocery = new Item();
		grocery.setTitle("Grocery");
		grocery.setAmount(new BigDecimal(10));
		grocery.setCurrency(Currency.USD);
		grocery.setPeriod(TimePeriod.DAY);

		Item salary = new Item();
		salary.setTitle("Salary");
		salary.setAmount(new BigDecimal(9100));
		salary.setCurrency(Currency.USD);
		salary.setPeriod(TimePeriod.MONTH);

		final Account account = new Account();
		account.setSaving(saving);
		account.setExpenses(ImmutableList.of(grocery));
		account.setIncomes(ImmutableList.of(salary));
		return account;
	}
}
