package com.piggymetrics.account.config;

import com.piggymetrics.account.service.security.LegacyUserInfoOpaqueTokenIntrospector;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server backed by the legacy auth-service, which issues opaque tokens
 * that can only be resolved through its user-info endpoint.
 */
@Configuration
public class ResourceServerConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/", "/demo").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> opaque.introspector(introspector)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector(RestTemplateBuilder restTemplateBuilder,
			@Value("${spring.security.oauth2.resourceserver.opaquetoken.introspection-uri}") String userInfoUri) {
		return new LegacyUserInfoOpaqueTokenIntrospector(userInfoUri, restTemplateBuilder.build());
	}
}
