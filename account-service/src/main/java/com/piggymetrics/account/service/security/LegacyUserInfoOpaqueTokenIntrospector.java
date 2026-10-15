package com.piggymetrics.account.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves opaque tokens issued by the legacy Spring Security OAuth2 auth-service by calling its
 * user-info endpoint (/uaa/users/current), which returns the serialized OAuth2Authentication.
 * <p>
 * Successor of the former CustomUserInfoTokenServices: the scopes of the calling client
 * (oauth2Request.scope) are exposed as SCOPE_* authorities, so checks such as
 * hasAuthority('SCOPE_server') replace #oauth2.hasScope('server').
 */
public class LegacyUserInfoOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

	private static final Logger log = LoggerFactory.getLogger(LegacyUserInfoOpaqueTokenIntrospector.class);

	private static final String[] PRINCIPAL_KEYS = new String[] { "user", "username",
			"userid", "user_id", "login", "id", "name" };

	private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

	private static final String DEFAULT_AUTHORITY = "ROLE_USER";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
	};

	private final String userInfoUri;

	private final RestOperations restOperations;

	public LegacyUserInfoOpaqueTokenIntrospector(String userInfoUri, RestOperations restOperations) {
		this.userInfoUri = userInfoUri;
		this.restOperations = restOperations;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		Map<String, Object> userInfo = fetchUserInfo(token);
		if (userInfo == null || userInfo.isEmpty() || userInfo.containsKey("error")) {
			log.debug("user-info returned error: {}", userInfo == null ? null : userInfo.get("error"));
			throw new BadOpaqueTokenException("Provided token isn't active");
		}

		Map<String, Object> request = extractOAuth2Request(userInfo);
		Set<String> scopes = extractScopes(request);

		Collection<GrantedAuthority> authorities = new ArrayList<>(extractAuthorities(userInfo));
		scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope)));

		Map<String, Object> attributes = new LinkedHashMap<>(userInfo);
		attributes.put(OAuth2TokenIntrospectionClaimNames.SCOPE, List.copyOf(scopes));
		if (request.get("clientId") != null) {
			attributes.put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, request.get("clientId"));
		}

		return new DefaultOAuth2AuthenticatedPrincipal(extractPrincipalName(userInfo), attributes, authorities);
	}

	private Map<String, Object> fetchUserInfo(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		try {
			return restOperations.exchange(userInfoUri, HttpMethod.GET, new HttpEntity<>(headers), MAP_TYPE).getBody();
		} catch (HttpClientErrorException e) {
			log.info("Could not fetch user details: {}", e.getStatusCode());
			throw new BadOpaqueTokenException("Provided token isn't active", e);
		} catch (RestClientException e) {
			log.info("Could not fetch user details: {}, {}", e.getClass(), e.getMessage());
			throw new OAuth2IntrospectionException("Could not fetch user details", e);
		}
	}

	private String extractPrincipalName(Map<String, Object> userInfo) {
		for (String key : PRINCIPAL_KEYS) {
			Object value = userInfo.get(key);
			if (value != null) {
				return value.toString();
			}
		}
		return "unknown";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> extractOAuth2Request(Map<String, Object> userInfo) {
		Object request = userInfo.get("oauth2Request");
		return request instanceof Map ? (Map<String, Object>) request : Map.of();
	}

	private Set<String> extractScopes(Map<String, Object> request) {
		Set<String> scopes = new LinkedHashSet<>();
		Object scope = request.get("scope");
		if (scope instanceof Collection<?> values) {
			values.forEach(value -> scopes.add(value.toString()));
		}
		return scopes;
	}

	/**
	 * Same semantics as the legacy FixedAuthoritiesExtractor: ROLE_USER when no authorities are present.
	 */
	private List<GrantedAuthority> extractAuthorities(Map<String, Object> userInfo) {
		if (!userInfo.containsKey("authorities")) {
			return List.of(new SimpleGrantedAuthority(DEFAULT_AUTHORITY));
		}
		List<GrantedAuthority> authorities = new ArrayList<>();
		Object value = userInfo.get("authorities");
		if (value instanceof Collection<?> items) {
			for (Object item : items) {
				Object authority = item instanceof Map<?, ?> map ? map.get("authority") : item;
				if (authority != null && !authority.toString().isBlank()) {
					authorities.add(new SimpleGrantedAuthority(authority.toString()));
				}
			}
		} else if (value instanceof String text && !text.isBlank()) {
			for (String authority : text.split(",")) {
				authorities.add(new SimpleGrantedAuthority(authority.trim()));
			}
		}
		return authorities;
	}
}
