package com.piggymetrics.statistics.config;

import com.piggymetrics.statistics.service.security.CustomUserInfoTokenIntrospector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server backed by the legacy auth-service: its tokens are opaque, so every bearer token
 * is resolved against the auth-service user-info endpoint.
 */
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(token -> token.introspector(introspector)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	public OpaqueTokenIntrospector opaqueTokenIntrospector(
			@Value("${piggymetrics.security.user-info-uri}") String userInfoUri, RestTemplateBuilder restTemplateBuilder) {
		return new CustomUserInfoTokenIntrospector(userInfoUri, restTemplateBuilder.build());
	}
}
