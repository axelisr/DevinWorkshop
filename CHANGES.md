# Changes

## Add Spring Boot Actuator to `gateway` and `auth-service`

- Added `org.springframework.boot:spring-boot-starter-actuator` to `gateway/pom.xml` and `auth-service/pom.xml`. As in `account-service/pom.xml`, it has no explicit version because the parent pom (Spring Boot 2.0.3) manages it.
- There are no config changes. `config/src/main/resources/shared/` has no `management.*` settings for any service, so every service uses the Spring Boot defaults.
- The health endpoint is `/actuator/health`. Boot 2.x moved it from `/health`, and by default only `health` and `info` are exposed over HTTP.
- `auth-service` requires authentication for every request (`anyRequest().authenticated()`), including the health endpoint. `account-service` behaves the same way.

### Verification

- Ran `mvn -pl gateway,auth-service -am package -DskipTests` with JDK 8. The build succeeded, and both jars include `spring-boot-starter-actuator-2.0.3.RELEASE.jar`.
- Ran `mvn -B test -pl gateway,auth-service`. All 9 tests passed.
- PR: https://github.com/axelisr/DevinWorkshop/pull/6
