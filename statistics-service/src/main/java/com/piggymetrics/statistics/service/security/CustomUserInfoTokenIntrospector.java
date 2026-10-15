package com.piggymetrics.statistics.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates opaque tokens issued by the legacy Spring Security OAuth2 auth-service by calling its
 * user-info endpoint (successor of the former {@code CustomUserInfoTokenServices}).
 *
 * Besides the user authorities, the client scopes found in {@code oauth2Request.scope} are exposed
 * as {@code SCOPE_<scope>} authorities, so {@code hasAuthority('SCOPE_server')} replaces
 * {@code #oauth2.hasScope('server')}.
 */
public class CustomUserInfoTokenIntrospector implements OpaqueTokenIntrospector {

	private static final Logger log = LoggerFactory.getLogger(CustomUserInfoTokenIntrospector.class);

	private static final String[] PRINCIPAL_KEYS = new String[] { "user", "username",
			"userid", "user_id", "login", "id", "name" };

	private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
	};

	private final URI userInfoEndpointUrl;

	private final RestOperations restOperations;

	public CustomUserInfoTokenIntrospector(String userInfoEndpointUrl, RestOperations restOperations) {
		this.userInfoEndpointUrl = URI.create(userInfoEndpointUrl);
		this.restOperations = restOperations;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		Map<String, Object> map = getMap(token);
		if (map == null || map.isEmpty() || map.containsKey("error")) {
			log.debug("userinfo returned error: {}", map == null ? null : map.get("error"));
			throw new BadOpaqueTokenException("Invalid access token");
		}

		Map<String, Object> request = getRequest(map);
		Set<String> scopes = getScopes(request);

		List<GrantedAuthority> authorities = new ArrayList<>(extractAuthorities(map));
		scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope)));

		Map<String, Object> attributes = new LinkedHashMap<>(map);
		attributes.put(OAuth2TokenIntrospectionClaimNames.SCOPE, new ArrayList<>(scopes));
		if (request.get("clientId") != null) {
			attributes.put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, request.get("clientId"));
		}

		return new OAuth2IntrospectionAuthenticatedPrincipal(getPrincipal(map), attributes, authorities);
	}

	private Map<String, Object> getMap(String token) {
		log.debug("Getting user info from: {}", userInfoEndpointUrl);
		try {
			RequestEntity<Void> request = RequestEntity.get(userInfoEndpointUrl)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.build();
			return restOperations.exchange(request, MAP_TYPE).getBody();
		} catch (RestClientException ex) {
			log.info("Could not fetch user details: {}, {}", ex.getClass(), ex.getMessage());
			throw new BadOpaqueTokenException("Could not fetch user details", ex);
		}
	}

	private String getPrincipal(Map<String, Object> map) {
		for (String key : PRINCIPAL_KEYS) {
			if (map.get(key) != null) {
				return String.valueOf(map.get(key));
			}
		}
		return "unknown";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getRequest(Map<String, Object> map) {
		Object request = map.get("oauth2Request");
		return request instanceof Map ? (Map<String, Object>) request : Collections.emptyMap();
	}

	private Set<String> getScopes(Map<String, Object> request) {
		Set<String> scopes = new LinkedHashSet<>();
		Object scope = request.get("scope");
		if (scope instanceof Collection<?> values) {
			values.forEach(value -> scopes.add(String.valueOf(value)));
		} else if (scope instanceof String value) {
			scopes.addAll(List.of(value.split(" ")));
		}
		return scopes;
	}

	/**
	 * Same semantics as Spring Boot 2 {@code FixedAuthoritiesExtractor}: reads the {@code authorities} key
	 * (strings or {@code {"authority": ...}} objects) and defaults to {@code ROLE_USER}.
	 */
	private List<GrantedAuthority> extractAuthorities(Map<String, Object> map) {
		Object authorities = map.get("authorities");
		if (authorities == null) {
			return AuthorityUtils.createAuthorityList("ROLE_USER");
		}
		if (authorities instanceof Collection<?> values) {
			List<GrantedAuthority> result = new ArrayList<>();
			for (Object value : values) {
				Object authority = value instanceof Map<?, ?> m ? m.get("authority") : value;
				if (authority != null) {
					result.add(new SimpleGrantedAuthority(String.valueOf(authority)));
				}
			}
			return result;
		}
		return AuthorityUtils.commaSeparatedStringToAuthorityList(String.valueOf(authorities));
	}
}
