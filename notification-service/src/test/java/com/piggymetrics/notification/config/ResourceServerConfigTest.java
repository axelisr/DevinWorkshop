package com.piggymetrics.notification.config;

import com.piggymetrics.notification.domain.Recipient;
import com.piggymetrics.notification.service.RecipientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceServerConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private RecipientService recipientService;

	@Test
	void shouldRejectRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/recipients/current"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldUseTokenPrincipalNameAsAccountName() throws Exception {
		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		when(recipientService.findByAccountName("test")).thenReturn(recipient);

		mockMvc.perform(get("/recipients/current").with(opaqueToken().attributes(attrs -> attrs.put("sub", "test"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountName").value("test"));
	}
}
