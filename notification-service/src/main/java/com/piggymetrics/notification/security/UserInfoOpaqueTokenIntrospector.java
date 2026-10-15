package com.piggymetrics.notification.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
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
 * Resolves opaque tokens issued by the legacy Spring Security OAuth2 auth-service by calling its
 * user-info endpoint ({@code /uaa/users/current}), which returns the serialized {@code OAuth2Authentication}.
 * Port of the former {@code CustomUserInfoTokenServices}: the principal name is taken from the same keys,
 * and the client scopes ({@code oauth2Request.scope}) are exposed as {@code SCOPE_<scope>} authorities.
 */
public class UserInfoOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

	private static final Logger log = LoggerFactory.getLogger(UserInfoOpaqueTokenIntrospector.class);

	private static final String[] PRINCIPAL_KEYS = {"user", "username", "userid", "user_id", "login", "id", "name"};

	private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
	};

	private final URI userInfoUri;

	private final RestOperations restOperations;

	public UserInfoOpaqueTokenIntrospector(String userInfoUri, RestOperations restOperations) {
		this.userInfoUri = URI.create(userInfoUri);
		this.restOperations = restOperations;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		Map<String, Object> userInfo = fetchUserInfo(token);
		if (userInfo == null || userInfo.isEmpty() || userInfo.containsKey("error")) {
			throw new BadOpaqueTokenException("Provided token isn't active");
		}

		Map<String, Object> request = oauth2Request(userInfo);
		Set<String> scopes = scopes(request);

		Map<String, Object> attributes = new LinkedHashMap<>(userInfo);
		attributes.put(OAuth2TokenIntrospectionClaimNames.SCOPE, new ArrayList<>(scopes));
		if (request.get("clientId") != null) {
			attributes.put(OAuth2TokenIntrospectionClaimNames.CLIENT_ID, request.get("clientId"));
		}

		return new DefaultOAuth2AuthenticatedPrincipal(principalName(userInfo), attributes, authorities(userInfo, scopes));
	}

	private Map<String, Object> fetchUserInfo(String token) {
		RequestEntity<Void> request = RequestEntity.get(userInfoUri)
				.headers(headers -> headers.setBearerAuth(token))
				.accept(MediaType.APPLICATION_JSON)
				.build();
		try {
			return restOperations.exchange(request, MAP_TYPE).getBody();
		} catch (HttpClientErrorException ex) {
			log.debug("user-info endpoint rejected token: {}", ex.getStatusCode());
			throw new BadOpaqueTokenException("Provided token isn't active");
		} catch (RestClientException ex) {
			throw new OAuth2IntrospectionException("Could not fetch user details from " + userInfoUri, ex);
		}
	}

	private String principalName(Map<String, Object> userInfo) {
		for (String key : PRINCIPAL_KEYS) {
			Object value = userInfo.get(key);
			if (value != null) {
				return value.toString();
			}
		}
		return "unknown";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> oauth2Request(Map<String, Object> userInfo) {
		Object request = userInfo.get("oauth2Request");
		return request instanceof Map ? (Map<String, Object>) request : Collections.emptyMap();
	}

	private Set<String> scopes(Map<String, Object> request) {
		Set<String> scopes = new LinkedHashSet<>();
		Object scope = request.get("scope");
		if (scope instanceof Collection<?> values) {
			values.forEach(value -> scopes.add(value.toString()));
		}
		return scopes;
	}

	private List<GrantedAuthority> authorities(Map<String, Object> userInfo, Set<String> scopes) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		Object values = userInfo.get("authorities");
		if (values instanceof Collection<?> collection) {
			for (Object value : collection) {
				Object authority = value instanceof Map<?, ?> map ? map.get("authority") : value;
				if (authority != null) {
					authorities.add(new SimpleGrantedAuthority(authority.toString()));
				}
			}
		}
		scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope)));
		return authorities;
	}
}
