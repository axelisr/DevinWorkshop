package com.piggymetrics.statistics.client;

import com.piggymetrics.statistics.domain.Currency;
import com.piggymetrics.statistics.domain.ExchangeRatesContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real rates API (api.exchangeratesapi.io) now requires an access key, so it is stubbed with WireMock.
 */
@SpringBootTest(properties = "rates.url=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class ExchangeRatesClientTest {

	private static final String RATES_RESPONSE = "{\"base\":\"USD\",\"date\":\"2018-06-29\","
			+ "\"rates\":{\"EUR\":0.8567,\"RUB\":62.7565,\"USD\":1.0}}";

	@Autowired
	private ExchangeRatesClient client;

	@Test
	void shouldRetrieveExchangeRates() {

		stubRates(Currency.getBase());

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(container.getDate(), LocalDate.now());
		assertEquals(container.getBase(), Currency.getBase());

		assertNotNull(container.getRates());
		assertNotNull(container.getRates().get(Currency.USD.name()));
		assertNotNull(container.getRates().get(Currency.EUR.name()));
		assertNotNull(container.getRates().get(Currency.RUB.name()));
	}

	@Test
	void shouldRetrieveExchangeRatesForSpecifiedCurrency() {

		Currency requestedCurrency = Currency.EUR;
		stubRates(Currency.getBase());

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(container.getDate(), LocalDate.now());
		assertEquals(container.getBase(), Currency.getBase());

		assertNotNull(container.getRates());
		assertNotNull(container.getRates().get(requestedCurrency.name()));
		verify(getRequestedFor(urlPathEqualTo("/latest")).withQueryParam("base", equalTo(Currency.getBase().name())));
	}

	@Test
	void shouldFallbackWhenRatesApiFails() {

		stubFor(get(urlPathEqualTo("/latest")).willReturn(serverError()));

		ExchangeRatesContainer container = client.getRates(Currency.getBase());

		assertEquals(Currency.getBase(), container.getBase());
		assertTrue(container.getRates().isEmpty());
	}

	private void stubRates(Currency base) {
		stubFor(get(urlPathEqualTo("/latest"))
				.withQueryParam("base", equalTo(base.name()))
				.willReturn(aResponse()
						.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.withBody(RATES_RESPONSE)));
	}
}
