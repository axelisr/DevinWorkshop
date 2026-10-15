package com.piggymetrics.notification.config;

import com.piggymetrics.notification.security.UserInfoOpaqueTokenIntrospector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

/**
 * Resource server backed by the legacy auth-service: access tokens are opaque, so they are
 * resolved against its user-info endpoint (see {@link UserInfoOpaqueTokenIntrospector}).
 */
@Configuration
public class ResourceServerConfig {

	@Bean
	public OpaqueTokenIntrospector userInfoOpaqueTokenIntrospector(
			@Value("${piggymetrics.security.oauth2.resource.user-info-uri}") String userInfoUri) {
		return new UserInfoOpaqueTokenIntrospector(userInfoUri, new RestTemplate());
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> opaque.introspector(introspector)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}
}
