# PiggyMetrics: Dependency Freshness and Security Report

**Repository:** `axelisr/DevinWorkshop` (`master` @ `6bb2cf9`). **Analysis date:** 2026-10-13.
**Scope:** analysis only. No `pom.xml` or other source file was changed.

Three parallel analysis sessions each covered one application layer. This report combines their results:

| Layer | Modules | Session |
|---|---|---|
| Platform infrastructure | `config`, `registry`, `gateway`, `monitoring`, `turbine-stream-service` | [platform session](https://accenture-ar.devinenterprise.com/sessions/fdeca18921464a709050ef22d3115c75) |
| Security / authentication | `auth-service` | [auth session](https://accenture-ar.devinenterprise.com/sessions/da08abd5d3f44b2ba0ede31e1d970539) |
| Business microservices | `account-service`, `statistics-service`, `notification-service` | [business session](https://accenture-ar.devinenterprise.com/sessions/744dc2dd422f4cafaa91507b75e52dda) |

**Method (common to all layers):**
- Resolved versions: `mvn -B dependency:tree` on JDK 8, through the Google Maven Central mirror.
- Newer versions: `versions-maven-plugin:2.16.2` (`display-dependency-updates`, `display-plugin-updates`, `display-parent-updates`). Milestones and RCs were filtered out using `maven-metadata.xml` from the mirror.
- CVEs: the [OSV API](https://osv.dev), queried for every resolved `group:artifact:version`. Severity is the GitHub-advisory severity that OSV reports.
- Support and EOL dates: [endoflife.date](https://endoflife.date) API. Spring Cloud removals were checked against the contents of the BOM poms.
- Items that come from documentation rather than tool output are marked *(doc)*.

Status legend: `up to date` · `minor behind` · `major behind` · `deprecated` · `EOL` · `vulnerable` (statuses can combine).

---

## 1. Executive summary

- **No dependency in any layer is up to date.** The project is still on the original 2018 GA versions: Spring Boot 2.0.3.RELEASE and Spring Cloud Finchley.RELEASE. Even the later 2.0.9 and Finchley.SR4 patch releases were never applied. The resolved stack includes:
  - Spring Framework 5.0.7
  - Spring Security 5.0.6
  - Tomcat 8.5.31
  - Jackson 2.9.6
  - Java 8

  All of these are end-of-life.
- **Security exposure is severe and affects every layer.** In each business module, OSV reports **54 vulnerable artifacts and about 323 unique advisories, 41 of them CRITICAL and 144 HIGH**. The platform and auth layers have the same shared core libraries, so they are in the same range.
- **A large part of the stack has been removed upstream, not just left behind.** Spring Cloud 2020.0 dropped Zuul 1, Hystrix, Hystrix Dashboard, Turbine, Ribbon, Archaius and `spring-cloud-security` / `spring-cloud-starter-oauth2`, and 2022.0 dropped Sleuth. This was confirmed from the BOM contents. These components cannot be bumped to a newer version. They have to be **replaced**.
- **Recommended target (the only GA-supported pairing as of today): Spring Boot 4.0.x + Spring Cloud 2025.1.x ("Oakwood") on Java 17/21.**
  - Boot 4.1.1 is the newest GA, but no GA Spring Cloud train supports Boot 4.1 yet (2026.0.0 is at M1).
  - Boot 3.5 OSS support ended on 2026-06-30.
  - Boot 4.0 OSS support ends on 2026-12-31. Plan a follow-up move to Boot 4.1 + Spring Cloud 2026.0 once that train is GA.

## 2. Cross-cutting findings (parent `pom.xml` and shared libraries)

### 2.1 Parent and BOMs: these set the versions for every module

| Item (root `pom.xml`) | Current | Latest stable | Status | Notes |
|---|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | 2.0.3.RELEASE | **4.1.1**. Other lines: 4.0.8, 3.5.16, 2.7.18 (last 2.x), 2.0.9 (last 2.0.x) | major behind, EOL, vulnerable | Boot 2.0 OSS support ended 2019-03-01 and commercial support ended 2020-06-01. Boot 3 and later need Java 17 and `jakarta.*`. Boot 4 brings Spring Framework 7, Spring Security 7, Jackson 3 (`tools.jackson`) and Tomcat 11. spring-boot 2.0.3 has CVE-2022-27772, CVE-2025-22235 and CVE-2026-40973 (all HIGH). |
| `org.springframework.cloud:spring-cloud-dependencies` | Finchley.RELEASE | **2025.1.3** for Boot 4.0.x. 2025.0.3 for Boot 3.5.x. 2021.0.9 is the last train for Boot 2.x / Java 8. Hoxton.SR12 is the last train with Netflix Zuul/Hystrix/Ribbon. | major behind, EOL | Finchley is older than every train endoflife.date still tracks. 2020.0 removed the Netflix stack (except Eureka) and `spring-cloud-security`, and turned off bootstrap context by default. 2022.0 removed Sleuth. |
| `java.version` | 1.8 | 17 is the minimum for Boot 3 and 4. LTS releases: 21, 25 | major behind | Java 8 limits the project to Boot 2.7 / Spring Cloud 2021.0, which are OSS EOL. The Dockerfiles use `java:8-jre`, which has been removed from Docker Hub. |
| Inherited Maven plugins: compiler, surefire, jar, resources, install, deploy, clean, site | 3.7.0 / 2.21.0 / 3.0.2 / 3.0.1 / 2.5.2 / 2.8.2 / 3.0.0 / 3.6 | 3.16.0 / 3.6.0 / 3.5.1 / 3.5.0 / 3.2.0 / 3.2.0 / 3.5.0 / 3.22.0 | minor/major behind | These move with the Boot parent. JUnit 5 needs surefire 2.22 or later. No `maven-enforcer-plugin` and no minimum Maven version is set (the versions plugin warns about this). |
| `spring-boot-maven-plugin` (all modules) | 2.0.3.RELEASE | 4.1.1 | major behind | The plugin version must match Boot. On newer versions, `<configuration><finalName>` has to move to `<build><finalName>`. |

### 2.2 Shared resolved libraries (found in every layer)

| Library | Current | Latest stable | Status | Key advisories (OSV) |
|---|---|---|---|---|
| Spring Framework (`spring-core`/`beans`/`web`/`webmvc`/`expression`/`context`) | 5.0.7.RELEASE | 7.0.9 (5.3.39 is the last 5.x) | EOL, vulnerable | **CVE-2022-22965 Spring4Shell (CRITICAL)**, CVE-2016-1000027 (CRITICAL, spring-web), CVE-2026-47884 (CRITICAL, webmvc). HIGH: CVE-2024-22243/22259/22262, CVE-2024-38819, CVE-2020-5398, CVE-2022-22970, CVE-2023-20863, CVE-2018-15756. webmvc alone has 13 advisories. |
| Spring Security (`core`/`web`/`config`/`crypto`) | 5.0.6.RELEASE | 7.1.1 (5.8.16 is the last 5.x) | EOL, vulnerable | **CVE-2022-22978, CVE-2024-38821, CVE-2026-22732 (all CRITICAL)**. HIGH: CVE-2021-22112, CVE-2024-22257, CVE-2025-22228 (BCrypt). |
| `org.apache.tomcat.embed:tomcat-embed-core` | 8.5.31 | 11.0.27. Other lines: 10.1.61, 9.0.123, 8.5.100 | EOL, vulnerable | **51 advisories: 9 CRITICAL, 22 HIGH.** Includes CVE-2020-1938 Ghostcat (AJP is off by default in embedded Boot) and CVE-2025-24813 (partial PUT RCE). The 11.0.24 that Boot 4.0.8 manages is still flagged, so override it to 11.0.25 or later. |
| `com.fasterxml.jackson.core:jackson-databind` | 2.9.6 | 2.22.3, or 3.x as `tools.jackson.core` | vulnerable | **66 advisories: 19 CRITICAL, 41 HIGH**, mostly polymorphic deserialization gadget chains. |
| `org.yaml:snakeyaml` | 1.19 | 2.7 | major behind, vulnerable | CVE-2022-1471 (HIGH, Constructor RCE), CVE-2022-25857, CVE-2017-18640, plus 5 MODERATE. |
| `ch.qos.logback:logback-classic`/`-core` | 1.2.3 | 1.6.5 (Boot 4.x manages 1.5.38) | vulnerable | CVE-2023-6378 (HIGH), CVE-2021-42550, CVE-2024-12798, CVE-2025-11226. |
| `com.thoughtworks.xstream:xstream` (via Eureka client 1.x) | 1.4.10 | 1.4.21 | vulnerable | **35 advisories: 2 CRITICAL (CVE-2013-7285, CVE-2019-10173), 20 HIGH.** |
| `org.codehaus.jettison:jettison` (via Eureka) | 1.3.7 | 1.5.7 | vulnerable | 4 HIGH (CVE-2022-45685/45693/40150, CVE-2023-1436). |
| `io.netty:netty-codec-http` & co. (via Ribbon/rxnetty, WebFlux) | 4.1.25.Final | 4.2.19.Final (4.1.139.Final is the last 4.1) | vulnerable | 23 advisories on codec-http, including **CVE-2019-20444 (CRITICAL)**. codec: CVE-2021-37136/37137 (HIGH). |
| `org.bouncycastle:bcprov-jdk15on`/`bcpkix-jdk15on` | 1.56 | 1.70 is the final jdk15on release. Successor: `bcprov-jdk18on` 1.86 | deprecated, vulnerable | CVE-2018-1000180 (HIGH), plus 6 MODERATE. |
| `org.hibernate.validator:hibernate-validator` | 6.0.10.Final | 9.1.4.Final | vulnerable | 4 MODERATE. |
| `org.apache.httpcomponents:httpclient` | 4.5.5 | 4.5.14 (successor: HttpClient 5) | minor behind, vulnerable | CVE-2020-13956 (MODERATE). |
| `com.google.guava:guava` (transitive 15.0/16.0; pinned 19.0 in statistics-service) | 15.0 / 16.0 / 19.0 | 33.7.2-jre | major behind, vulnerable | CVE-2018-10237, CVE-2023-2976 (MODERATE), CVE-2020-8908 (LOW). |
| `com.rabbitmq:amqp-client` (bus-amqp, stream-rabbit) | 5.1.2 | 5.37.0 | vulnerable | 12 advisories, 4 HIGH (for example CVE-2026-63337, CVE-2026-69219). |
| `org.springframework.cloud:spring-cloud-starter-config` (8 modules) | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | Breaking change: `bootstrap.yml` is ignored from 2020.0 onwards. Use `spring.config.import=optional:configserver:` or add `spring-cloud-starter-bootstrap`. |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` | 2.0.0.RELEASE (eureka-client 1.9.2) | 5.0.2 (eureka-client 2.0.6) | major behind (still supported) | Moves to Jakarta and Jersey 3. The xstream, jettison, Jersey 1 and Ribbon transitives go away. |
| `org.springframework.cloud:spring-cloud-starter-sleuth` (gateway, auth, business) | 2.0.0.RELEASE | 3.1.11 (final release) | deprecated, EOL | Removed in 2022.0. Replace with Micrometer Tracing (`micrometer-tracing-bridge-brave` or `-otel`). Default propagation changes from B3 to W3C. |
| `spring-cloud-starter-oauth2` / `spring-security-oauth2` (auth + business) | 2.0.0.RELEASE / 2.2.1.RELEASE | 2.2.5.RELEASE / 2.5.2.RELEASE (final releases) | deprecated, EOL, vulnerable | **CVE-2018-1260 (CRITICAL, RCE)**, CVE-2018-15758 (HIGH), CVE-2019-3778. It also pulls in `jackson-mapper-asl` 1.9.13 (CVE-2019-10202 CRITICAL, CVE-2019-10172 HIGH), which has no fix. |
| Test stack: `spring-boot-starter-test` → junit 4.12, assertj 3.9.1, json-smart | 2.0.3.RELEASE | 4.1.1 | major behind, vulnerable (test only) | junit CVE-2020-15250, assertj CVE-2026-24400 (HIGH), json-smart CVE-2023-1370 (HIGH). JUnit 4 tests need porting to JUnit 5/6. |

### 2.3 Netflix OSS and other removed components

| Component | Used in | Final release | Spring Cloud status | Replacement |
|---|---|---|---|---|
| Zuul 1 (`spring-cloud-starter-netflix-zuul`) | gateway | 2.2.10.RELEASE | Removed in 2020.0 | Spring Cloud Gateway: Server WebMVC (closest to Zuul) or WebFlux |
| Hystrix (`-netflix-hystrix`, `hystrix-stream`, `feign-hystrix`) | account, statistics, notification, gateway | 2.2.10.RELEASE (hystrix-core 1.5.18) | Removed in 2020.0 | Resilience4j through `spring-cloud-starter-circuitbreaker-resilience4j` |
| Hystrix Dashboard | monitoring | 2.2.10.RELEASE | Removed in 2020.0. **CVE-2021-22053 (HIGH)**, fixed only in 2.2.10 | Micrometer → Prometheus + Grafana, optionally Spring Boot Admin |
| Turbine Stream (`turbine-core` 2.0.0-DP.2, never GA) | turbine-stream-service | 2.2.10.RELEASE | Removed in 2020.0 | Prometheus scraping with service discovery. The module can be retired. |
| Ribbon | transitive in nearly every module | 2.2.10.RELEASE (ribbon 2.7.18) | Removed in 2020.0 | Spring Cloud LoadBalancer, used automatically by the Eureka client and OpenFeign |
| Archaius 1 | transitive | 0.7.12 | Removed in 2020.0 | Spring Boot configuration |
| Spring Security OAuth / `spring-cloud-security` | auth + business | 2.5.2.RELEASE / 2.2.5.RELEASE | Project EOL *(doc: 2022)*. Removed from the train in 2020.0 | Spring Authorization Server (`spring-boot-starter-oauth2-authorization-server`) + `spring-boot-starter-oauth2-resource-server` / `-client` |
| Sleuth | gateway, auth, business | 3.1.11 | Removed in 2022.0 | Micrometer Tracing |
| Spring Cloud Stream annotation model | turbine-stream, bus | — | Removed in Stream 4.0 | Functional bindings (bus usage is config-driven, so impact is low) |

---

## 3. Per-layer dependency tables

Each table lists one row per declared dependency or plugin, plus the transitives that only that layer brings in. The shared libraries in §2.2 apply to every layer and are not repeated here.

### 3.1 Platform infrastructure layer (`config`, `registry`, `gateway`, `monitoring`, `turbine-stream-service`)

| Module | Dependency / plugin | Source | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|---|
| config | `spring-cloud-config-server` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.5 (4.3.4) | major behind, EOL, vulnerable | **CVE-2026-40982 (CRITICAL, path traversal)**, CVE-2026-22739 (HIGH), CVE-2019-3799. Uses the `native` profile (`classpath:/shared`). |
| config | `spring-boot-starter-security` | declared / Boot BOM | 2.0.3.RELEASE (Security 5.0.6) | 4.1.1 (Security 7.1.1) | major behind, EOL, vulnerable | Basic auth through properties only. See §2.2. |
| config | `org.eclipse.jgit:org.eclipse.jgit` (+ `jsch` 0.1.54) | transitive | 4.11.0.201803080745-r | 7.8.0.202609011348-r | major behind, vulnerable | CVE-2023-4759 (HIGH), CVE-2025-4949. The Git backend appears unused because of the `native` profile (inferred). The original JSch is unmaintained. |
| registry | `spring-cloud-starter-netflix-eureka-server` | declared / Cloud BOM | 2.0.0.RELEASE (eureka-core 1.9.2) | 5.0.2 (eureka 2.0.6) | major behind, EOL | Supported upgrade path. Jakarta + Jersey 3. |
| registry, monitoring | `org.freemarker:freemarker` | transitive (dashboards) | 2.3.28 | 2.3.35 | vulnerable | CVE-2026-84939 (CRITICAL). |
| registry | `com.fasterxml.woodstox:woodstox-core` | transitive | 5.0.3 | 7.3.0 | major behind, vulnerable | CVE-2022-40152 (MODERATE). |
| registry, gateway, monitoring, turbine | `spring-cloud-starter-config` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | Uses `bootstrap.yml`. See §2.2. |
| gateway | `spring-cloud-starter-netflix-zuul` (zuul-core 1.3.1) | declared / Cloud BOM | 2.0.0.RELEASE | 2.2.10.RELEASE (final) | deprecated, EOL, vulnerable | CVE-2021-22113 (MODERATE). The routes in `config/.../shared/gateway.yml`, the static SPA and the `/uaa/**` proxy all need porting to Spring Cloud Gateway. |
| gateway | `spring-cloud-starter` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.x | major behind | |
| gateway, turbine | `spring-cloud-starter-netflix-eureka-client` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.2 | major behind | See §2.2. |
| gateway | `spring-cloud-starter-sleuth` (brave 5.1.0) | declared / Cloud BOM | 2.0.0.RELEASE | 3.1.11 (final) | deprecated, EOL | Replace with Micrometer Tracing. |
| gateway | Hystrix 1.5.12 / Ribbon 2.2.5 (via Zuul) | transitive | — | 1.5.18 / 2.7.18 (final) | deprecated, EOL | Replace with Resilience4j / LoadBalancer. |
| monitoring | `spring-cloud-starter-netflix-hystrix-dashboard` | declared / Cloud BOM | 2.0.0.RELEASE | 2.2.10.RELEASE (final) | deprecated, EOL, vulnerable | **CVE-2021-22053 (HIGH, code injection)**. No 1:1 successor. |
| turbine | `spring-cloud-starter-netflix-turbine-stream` | declared / Cloud BOM | 2.0.0.RELEASE (turbine-core 2.0.0-DP.2) | 2.2.10.RELEASE (final) | deprecated, EOL | No successor. Candidate for retirement. |
| turbine | `spring-cloud-starter-stream-rabbit` (spring-rabbit 2.0.4, amqp-client 5.1.2) | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.3 | major behind, EOL, vulnerable | spring-amqp CVE-2018-11087 and others. amqp-client has 4 HIGH. The annotation model was removed in Stream 4. |
| turbine | `reactor-netty` 0.7.8 / `reactor-core` 3.1.8 / Netty 4.1.25 | transitive (WebFlux) | — | reactor-netty 1.x / reactor-core 3.8.7 / Netty 4.2.19 | major behind, vulnerable | netty-handler CVE-2026-75595 (CRITICAL). See §2.2. |
| all | `spring-boot-starter-test` | declared (test) / Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind | |
| all | `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | `jacoco` is not used in this layer. |

### 3.2 Security / authentication layer (`auth-service`)

| Dependency / plugin | Source | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `spring-cloud-starter-oauth2` → `spring-security-oauth2` 2.2.1, `spring-security-oauth2-autoconfigure` 2.0.0, `spring-security-jwt` 1.0.9 | declared / Cloud BOM | 2.0.0.RELEASE | 2.2.5 / 2.5.2 / 2.6.8 / 1.1.1 (all final) | **deprecated, EOL, vulnerable** | CVE-2018-1260 (CRITICAL RCE), CVE-2018-15758 (HIGH), CVE-2019-3778. Also `jackson-mapper-asl` 1.9.13 (CVE-2019-10202 CRITICAL, no fix). The code uses `@EnableAuthorizationServer`, `InMemoryTokenStore` and `NoOpPasswordEncoder` for client secrets. |
| `spring-boot-starter-security` | declared / Boot BOM | 2.0.3.RELEASE (Security 5.0.6) | 4.1.1 (Security 7.1.1) | major behind, vulnerable | 3 CRITICAL authorization bypasses (see §2.2). CVE-2025-22228 is relevant because `BCryptPasswordEncoder` is used for user passwords. |
| `spring-boot-starter-web` | declared / Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, vulnerable | Brings Framework 5.0.7 and Tomcat 8.5.31 (see §2.2). |
| `spring-boot-starter-data-mongodb` → `spring-data-mongodb` 2.0.8, `mongodb-driver` 3.6.4 | declared / Boot BOM | 2.0.3.RELEASE | 4.1.1 (spring-data-mongodb 5.1.1, `mongodb-driver-sync` 5.13.0) | major behind, vulnerable | **CVE-2022-22980 (CRITICAL, SpEL injection)**, CVE-2026-41717 (HIGH). The legacy driver artifact ends at 3.12.14. The server image is `mongo:3`, which is EOL. |
| `spring-cloud-starter-config` → `spring-security-rsa` 1.0.5 → bcprov 1.56 | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind, vulnerable (bcprov) | Uses `bootstrap.yml`. |
| `spring-cloud-starter-netflix-eureka-client` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.2 | major behind | Pulls in xstream, jettison, netty, Ribbon, Hystrix and Archaius, none of which this service needs. |
| `spring-cloud-starter-sleuth` | declared / Cloud BOM | 2.0.0.RELEASE | 3.1.11 (final) | deprecated, EOL | Replace with Micrometer Tracing. |
| `spring-boot-starter-actuator` (via spring-cloud-security), `micrometer-core` 1.0.5 | transitive | 2.0.3.RELEASE / 1.0.5 | 4.1.1 / 1.17.1 | vulnerable | CVE-2023-20873 (CRITICAL, Cloud Foundry), CVE-2026-40984 (HIGH). |
| `spring-boot-starter-test` | declared (test) / Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, vulnerable (test) | See §2.2. |
| `de.flapdoodle.embed:de.flapdoodle.embed.mongo` | **pinned in module** (test) | 1.50.3 | 5.0.0 (`embed.mongo.spring4x` 4.33.0) | major behind | The pin is *older* than the 2.0.3 that Boot 2.0.3 manages. Pulls in commons-compress 1.10 and commons-io 2.4 (HIGH). Boot 3 removed `EmbeddedMongoAutoConfiguration`, which `UserRepositoryTest` uses. |
| `com.jayway.jsonpath:json-path` | **pinned in module** (test) | 2.2.0 | 3.0.0 (Boot 4.0.8 manages 2.10.0) | major behind, vulnerable | The pin is *older* than Boot 2.0.3's 2.4.0. CVE-2023-51074. json-smart 2.2.1 has CVE-2023-1370 (HIGH). |
| `org.jacoco:jacoco-maven-plugin` | **pinned in module** | 0.7.6.201602180812 | 0.8.15 | major behind | Cannot instrument Java 9+ bytecode, so it blocks the move to Java 17/21. |
| `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

### 3.3 Business microservices layer (`account-service`, `statistics-service`, `notification-service`)

All three modules declare almost the same set of dependencies. Differences are shown in the *Module* column.

| Module | Dependency / plugin | Source | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|---|
| all | `spring-cloud-starter-oauth2` | declared / Cloud BOM | 2.0.0.RELEASE (`spring-security-oauth2` 2.2.1) | 2.2.5.RELEASE (final) | **deprecated, EOL, vulnerable** | Same CVEs as auth-service. Uses `@EnableResourceServer`, a custom `user-info-uri` token service, `OAuth2RestTemplate` client_credentials, and `#oauth2.hasScope('server')` SpEL (which must become `hasAuthority('SCOPE_server')`). |
| all | `spring-boot-starter-security` | declared / Boot BOM | 2.0.3.RELEASE (Security 5.0.6) | 4.1.1 | major behind, vulnerable | See §2.2. |
| all | `spring-boot-starter-web` | declared / Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, vulnerable | Spring4Shell, Tomcat (see §2.2). |
| all | `spring-cloud-starter-config` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | `bootstrap.yml`. |
| all | `spring-cloud-starter-openfeign` (feign-core 9.5.1) | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.3 (feign-core 13.17) | major behind, EOL | Uses Ribbon + `feign-hystrix` today. On new trains it uses LoadBalancer + CircuitBreaker (`spring.cloud.openfeign.circuitbreaker.enabled`). OpenFeign is feature-complete; `@HttpExchange` is recommended for new code *(doc)*. |
| all | Ribbon 2.2.5 (via openfeign) | transitive | 2.2.5 | 2.7.18 / starter 2.2.10 (final) | deprecated, EOL | Replace with Spring Cloud LoadBalancer. Also brings in netty 4.1.25 (CRITICAL). |
| all | `spring-cloud-starter-netflix-eureka-client` | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.2 | major behind | See §2.2. |
| all | `spring-cloud-starter-sleuth` | declared / Cloud BOM | 2.0.0.RELEASE | 3.1.11 (final) | deprecated, EOL | Replace with Micrometer Tracing. |
| all | `spring-boot-starter-data-mongodb` | declared / Boot BOM | 2.0.3.RELEASE (spring-data-mongodb 2.0.8, driver 3.6.4) | 4.1.1 | major behind, vulnerable | CVE-2022-22980 (CRITICAL), CVE-2026-41717 / CVE-2026-41716 (HIGH). |
| all | `spring-boot-starter-actuator` (micrometer 1.0.5) | declared / Boot BOM | 2.0.3.RELEASE | 4.1.1 (micrometer 1.17.1) | major behind, vulnerable | CVE-2023-20873 (CRITICAL), CVE-2026-22733 (HIGH), CVE-2026-40984 (HIGH). |
| all | `spring-cloud-starter-bus-amqp` (spring-rabbit 2.0.4, amqp-client 5.1.2, spring-retry 1.2.2) | declared / Cloud BOM | 2.0.0.RELEASE | 5.0.3 | major behind, vulnerable | amqp-client has 12 advisories (4 HIGH). spring-retry CVE-2026-41710. |
| all | `spring-cloud-netflix-hystrix-stream` | declared / Cloud BOM | 2.0.0.RELEASE | 2.2.10.RELEASE (final) | deprecated, EOL | Feeds Turbine. Replace with Micrometer/Prometheus. Pulls in abandoned kryo-shaded 3.0.3 and spring-tuple 1.0.0. |
| account | `spring-cloud-starter-netflix-hystrix` (hystrix-javanica, guava 15.0) | declared / Cloud BOM | 2.0.0.RELEASE (hystrix 1.5.12) | 2.2.10.RELEASE (hystrix 1.5.18, final) | deprecated, EOL | Replace with Resilience4j. `@EnableCircuitBreaker` has been removed. |
| statistics | `com.google.guava:guava` | **pinned in module** | 19.0 | 33.7.2-jre | major behind, vulnerable | 3 advisories (MODERATE/LOW). Only `ImmutableMap` is used, so it can be replaced with `Map.of`. |
| statistics | (observation) Feign fallback | config | — | — | note | `ExchangeRatesClientFallback` looks inactive because `feign.hystrix.enabled` is not set for statistics-service. This matches the known NPE when exchangeratesapi.io fails. Inferred from config, not verified at runtime. |
| notification | `spring-boot-starter-mail` (javax.mail 1.6.1) | declared / Boot BOM | 2.0.3.RELEASE | 4.1.1 (jakarta.mail / Eclipse Angus 2.0.5) | major behind, EOL | No advisories. `javax.mail.*` imports change to `jakarta.mail.*`. |
| all | `spring-boot-starter-test` | declared (test) / Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, vulnerable (test) | See §2.2. |
| all | `de.flapdoodle.embed.mongo` | **pinned in module** (test) | 1.50.3 | 5.0.0 | major behind | The pin is older than the BOM-managed version (see 3.2). |
| all | `com.jayway.jsonpath:json-path` | **pinned in module** (test) | 2.2.0 | 3.0.0 | major behind, vulnerable | See 3.2. Remove the pin. |
| all | `org.jacoco:jacoco-maven-plugin` | **pinned in module** | 0.7.6.201602180812 | 0.8.15 | major behind | Blocks Java 9+. |
| all | `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

OSV totals per business module: about 210 resolved artifacts, of which 54 have advisories. That is about 323 unique advisories in total (41 CRITICAL, 144 HIGH, 121 MODERATE, 17 LOW), and about 309 of them are on compile or runtime scope.

---

## 4. Breaking-change risks for the upgrade

| Change | Affects | Impact |
|---|---|---|
| Java 8 → 17/21 | all | Update `java.version`, the build JDK and the Dockerfiles (`java:8-jre` → `eclipse-temurin:21-jre`). jacoco has to be at 0.8.11 or later first. |
| `javax.*` → `jakarta.*` (Boot 3+) | all | servlet, validation, annotation, and mail (notification-service). Eureka moves to Jersey 3. |
| Boot 4: Spring Framework 7, Security 7, Jackson 3 (`tools.jackson`) | all | Jackson package rename. The Security lambda DSL becomes mandatory. |
| Spring Security 5 → 7 | all | `WebSecurityConfigurerAdapter` → `SecurityFilterChain` beans. `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`. `antMatchers` → `requestMatchers`. |
| Legacy OAuth2 → Spring Authorization Server + resource server | auth, business, gateway/UI | **Largest item. Requires a rewrite.** The UI uses `grant_type=password`, which OAuth 2.1 / Spring Authorization Server do not support, so the login has to move to authorization code + PKCE (gateway as BFF). `/oauth/token` becomes `/oauth2/token`. Services switch from `user-info-uri` to JWT validation. client_credentials maps over directly. Client secrets should move from `NoOpPasswordEncoder` to `{bcrypt}`. |
| Zuul → Spring Cloud Gateway | gateway | Rewrite the routes, `stripPrefix` and sensitive headers, and re-test the SPA and the `/uaa/**` flow. |
| Hystrix → Resilience4j; Ribbon → LoadBalancer | gateway, business | `hystrix.*` and `ribbon.*` timeouts and properties have no 1:1 mapping. Feign fallbacks should use `fallbackFactory`. |
| Hystrix Dashboard + Turbine → Micrometer/Prometheus/Grafana | monitoring, turbine-stream-service, hystrix-stream in business | There is no upgrade path. Retire these modules. |
| Sleuth → Micrometer Tracing | gateway, auth, business | `spring.sleuth.*` → `management.tracing.*`. B3 → W3C, so traces break while mixed versions run during a rolling upgrade. |
| Config client bootstrap | 8 modules | Replace `bootstrap.yml` with `spring.config.import`. |
| Spring Data MongoDB 2 → 5, driver 3.6 → 5.x, `mongo:3` server | auth, business | Legacy `com.mongodb.MongoClient` API removed. Set `uuidRepresentation` explicitly. Upgrade the server image. Driver/server compatibility is unverified. |
| Tests | auth, business | JUnit 4 → JUnit 5/6. Embedded-Mongo auto-config removed: use Testcontainers or `embed.mongo.spring4x`. |
| Build | all | `spring-boot-maven-plugin` `<configuration><finalName>` → `<build><finalName>`. |

## 5. Prioritized upgrade recommendations

**P0: Contain now (no code changes)**
1. Keep `config` (8888), `registry` (8761), `monitoring`, `turbine-stream-service`, RabbitMQ and MongoDB off any public network. Expose only `gateway`.
2. Confirm that AJP is disabled and rotate the config-server and OAuth client credentials. Client secrets are stored in plain text through `NoOpPasswordEncoder`.

**P1: Low-risk cleanup you can do now, independent of the platform upgrade**
3. Remove the `json-path` `<version>` pins (4 modules) so the Boot BOM manages them.
4. Bump `jacoco-maven-plugin` to 0.8.15 (4 modules). This is required before any JDK upgrade.
5. Replace or upgrade `guava` 19.0 in statistics-service (`Map.of`, or 33.x-jre).
6. Replace flapdoodle 1.50.3 with Testcontainers MongoDB (or `embed.mongo.spring4x`).
7. Add `maven-enforcer-plugin`, an SCA gate in CI (OSV-Scanner or OWASP dependency-check) and Renovate/Dependabot. Replace the `java:8-jre` images with `eclipse-temurin`.

**P2: Optional interim stop-gap (only if the migration below cannot start soon; everything here is still EOL)**
8. Move to **Boot 2.3.12 + Hoxton.SR12**. This is the last train that still has Zuul, Hystrix, Turbine and Ribbon, so no rewrite is needed and it still runs on Java 8. Override `hystrix-dashboard` to 2.2.10.RELEASE (CVE-2021-22053), `spring-security-oauth2` to 2.5.2, and `jackson-bom`, `snakeyaml`, `tomcat`, `logback`, `xstream`, `netty`, `amqp-client` and `freemarker` to their latest compatible patches. This removes many CVEs but leaves the stack unsupported. Treat it only as risk reduction.

**P3: Target platform migration: Boot 4.0.x + Spring Cloud 2025.1.x, Java 21**
9. Upgrade Java, the parent and the BOM, migrate to `jakarta.*` and the Security 7 DSL, switch to `spring.config.import`, and override `tomcat.version` to 11.0.25 or later. Suggested order:
   1. `config`, then `registry`. These have supported upgrade paths.
   2. Then each client service.
10. **OAuth2 rewrite (security-critical, cross-layer).** Move auth-service to Spring Authorization Server. Move the business services to `oauth2-resource-server` (JWT) and `oauth2-client` (client_credentials). Move the UI login to authorization code + PKCE through the gateway.
11. **Gateway:** Zuul → Spring Cloud Gateway (Server WebMVC is the closest to the current model).
12. **Resilience:** Hystrix → Resilience4j and Ribbon → Spring Cloud LoadBalancer in the business services. Drop `hystrix-stream`.
13. **Observability:** Sleuth → Micrometer Tracing. Retire `monitoring` and `turbine-stream-service` in favour of actuator + Micrometer Prometheus + Grafana. This also removes the RabbitMQ/Netty/Turbine attack surface, provided Bus still has its own broker.
14. **Data:** Spring Data MongoDB 5 / driver 5, set `uuidRepresentation`, and upgrade the `mongo:3` server image.
15. **Tests:** port to JUnit 5/6.

**P4: Follow-up**
16. Move to Boot 4.1.x + Spring Cloud 2026.0.x once that train is GA. Boot 4.0 OSS support ends on 2026-12-31.

## 6. Where the layer reports disagreed, and how this report resolves it

- **Target version.** The business session suggested "Boot 3.5.x + 2025.0.x, or Boot 4.1.x + 2025.1.x". The platform and auth sessions point out, and endoflife.date confirms, that **2025.1 supports only Boot 4.0.x**. In addition, Boot 3.5 OSS support ended on 2026-06-30. This report therefore recommends Boot 4.0.x + 2025.1.x now, then 4.1.x + 2026.0.x later.
- **Boot 4.0 end of support.** The auth session gave 2026-12-31 and the platform session gave 2027-07-31. endoflife.date lists 2026-12-31 for Boot 4.0 OSS. 2027-07-31 is the OSS EOL of Spring Cloud 2025.1 and Boot 4.1.

## 7. Caveats

- OSV data is as of 2026-10-13. Severity is the GitHub-advisory rating, not checked against NVD CVSS. Several 2026 CVE IDs were taken as reported.
- Exploitability in PiggyMetrics was not tested. Spring4Shell needs JDK 9+ and WAR deployment, and Ghostcat needs AJP. So the practical risk can be lower than the counts suggest, but the scanners will still flag them.
- These come from documentation, not tool output:
  - EOL dates for Finchley, Tomcat 8.5 and Spring Security OAuth.
  - The JaCoCo JDK support matrix.
  - The removal of the `spring-boot-maven-plugin` `finalName` parameter.
  - MongoDB driver/server compatibility.
- No tests were run and no upgrade was attempted (analysis-only scope).

---

## Appendix: full per-layer reports

The complete layer reports, with every transitive row, are reproduced below exactly as each session produced them.

<details>
<summary><b>platform layer: full report</b></summary>

### Dependency freshness & security analysis — Platform infrastructure layer

**Repo:** `axelisr/DevinWorkshop` (`master` @ `6bb2cf9`) · **Modules:** `config`, `registry`, `gateway`, `monitoring`, `turbine-stream-service` (+ root `pom.xml`) · **Date of analysis:** 2026-10-13
Analysis only. No repo files were modified.

#### Method and data provenance

| Data | Source |
|---|---|
| Resolved versions (direct + transitive) | **Tool:** `mvn -B dependency:tree -pl config,registry,gateway,monitoring,turbine-stream-service` (JDK 8, Google Maven Central mirror) |
| Latest versions, declared deps/parent/plugins | **Tool:** `versions-maven-plugin:2.16.2` `display-dependency-updates`, `display-plugin-updates`, `display-parent-updates` (pre-releases filtered out) |
| Latest versions, transitive libs, plugins inherited from Boot parent, per-line latest (e.g. last 8.5.x Tomcat) | **Tool:** `maven-metadata.xml` from `https://maven-central.storage-download.googleapis.com/maven2/` (pre-releases filtered); `help:effective-pom -pl registry` for the inherited plugin versions |
| CVEs | **Tool:** OSV API `POST https://api.osv.dev/v1/query` for every resolved `group:artifact:version` listed below. Severity is the GitHub-advisory severity that OSV reports. |
| EOL / support dates | **Docs/API:** `https://endoflife.date/api/{spring-boot,spring-framework,spring-security,spring-cloud,tomcat,eclipse-temurin,oracle-jdk}.json` |
| What was removed from Spring Cloud | **Tool:** BOM contents of `spring-cloud-netflix-dependencies` 2.2.10.RELEASE vs 3.0.6 / 5.0.2, plus `spring-cloud-dependencies` Finchley.SR4 / Hoxton.SR12 / 2020.0.6 / 2025.x. Spring Cloud docs/blog for the narrative. |
| Code usage (affects how hard migration is) | **Tool:** `grep` of the module sources/config: `@EnableConfigServer`, `@EnableEurekaServer`, `@EnableZuulProxy`, `@EnableHystrixDashboard`, `@EnableTurbineStream`, `@EnableDiscoveryClient`, `WebSecurityConfigurerAdapter`, `bootstrap.yml` in 4 modules, Config Server `native` profile (`classpath:/shared`), Zuul routes in `shared/gateway.yml`, Dockerfiles `FROM java:8-jre` |

Status legend: `up to date` / `minor behind` / `major behind` / `deprecated` / `EOL` / `vulnerable`. "Latest stable" means the newest GA on Maven Central today; where it matters, the last patch of the current line is given too.

---

#### Parent POM / BOMs / build plugins (shared by every module in this layer)

| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | root `pom.xml` `<parent>` | 2.0.3.RELEASE | 4.1.1 (2.0.x last: 2.0.9; 2.7.x last: 2.7.18; 3.5.x: 3.5.16) | major behind, EOL | Boot 2.0 OSS EOL 2019-03-01 and commercial EOL 2020-06-01. Boot 3.x/4.x need Java 17 (the parent pom sets `java.version` 17 in 3.5.16 and 4.1.1) and Jakarta EE (`javax.*` → `jakarta.*`). Boot 4.x moves to Jackson 3 (`jackson-bom` 3.1.5 in 4.1.1), Spring Framework 7, Security 7, Tomcat 11. |
| `org.springframework.cloud:spring-cloud-dependencies` | root `pom.xml` `dependencyManagement` (`spring-cloud.version`) | Finchley.RELEASE | 2025.1.3 "Oakwood" (supports Boot 4.0.x); 2025.0.3 "Northfields" (Boot 3.5.x). Finchley last: SR4 | major behind, EOL | Finchley is older than every train endoflife.date still tracks; even 2020.0 Ilford reached OSS EOL 2022-05-31. 2020.0 removed Zuul, Hystrix, Hystrix Dashboard, Turbine, Ribbon and Archaius: the netflix BOM 3.0.6 and 5.0.2 only manage eureka-client and eureka-server. 2020.0 also turned bootstrap context off by default. 2022.0 removed Sleuth. **Train/Boot mismatch:** no released train supports Boot 4.1.x yet, because 2025.1 covers Boot 4.0.x only. Upgrading the parent to 4.1.1 as the versions plugin suggests would break Spring Cloud. Target Boot 4.0.x + 2025.1.x, or Boot 3.5.x + 2025.0.x. |
| `java.version` | root `pom.xml` property | 1.8 | 17 is the minimum for Boot 3/4. Current LTS: 21, 25 | major behind | Temurin 8 is still supported (EOL 2030-12) but Boot 3+ does not run on it. Dockerfiles use `java:8-jre`, which is gone from Docker Hub. |
| `org.springframework.boot:spring-boot-maven-plugin` | each module `<build>` (version from Boot parent) | 2.0.3.RELEASE | 4.1.1 | major behind | Upgrades with the parent. `<finalName>` inside the plugin `<configuration>` is no longer supported in newer plugin versions; move it to `<build><finalName>`. |
| `org.apache.maven.plugins:maven-compiler-plugin` | Boot parent pluginManagement | 3.7.0 | 3.16.0 | major behind (minor numbers) | Upgrades with the parent. |
| `org.apache.maven.plugins:maven-surefire-plugin` | Boot parent | 2.21.0 | 3.6.0 | major behind | JUnit 5 (Boot 2.2+ default) needs Surefire 2.22+/3.x. |
| `org.apache.maven.plugins:maven-jar-plugin` | Boot parent | 3.0.2 | 3.5.1 | minor behind | |
| `org.apache.maven.plugins:maven-resources-plugin` | Boot parent | 3.0.1 | 3.5.0 | minor behind | |
| `org.apache.maven.plugins:maven-clean-plugin` | Boot parent | 3.0.0 | 3.5.0 | minor behind | |
| `org.apache.maven.plugins:maven-install-plugin` / `maven-deploy-plugin` | Boot parent | 2.5.2 / 2.8.2 | 3.2.0 / 3.2.0 | major behind | |
| `org.apache.maven.plugins:maven-site-plugin` | Boot parent | 3.6 | 3.22.0 | minor behind | |
| `org.jacoco:jacoco-maven-plugin` | not used in this layer | — | — | n/a | Only in other layers' modules. |
| Maven enforcer / minimum Maven version | none | — | — | finding | `display-plugin-updates` warns that no minimum Maven version is set. |

**Shared transitive libraries.** These are resolved in most or all modules of this layer (the module where each appears is given):

| Library | Modules | Current | Latest stable | Status | Key CVEs (OSV; severity) |
|---|---|---|---|---|---|
| `org.springframework:spring-core` / `spring-beans` / `spring-web` / `spring-webmvc` / `spring-expression` / `spring-messaging` / `spring-webflux` | all (webmvc: config, gateway, monitoring, registry; webflux/messaging: turbine-stream-service) | 5.0.7.RELEASE | 7.0.9 (5.3.x last 5.3.39, 6.2.19) | major behind, EOL, vulnerable | Framework 5.0 EOL 2020-12-31 (5.3 OSS EOL 2024-08-31). **CVE-2022-22965 "Spring4Shell" (CRITICAL, fixed 5.2.20/5.3.18)**: needs JDK 9+ for RCE, so the JDK 8 runtime limits it, but it is still flagged. CVE-2016-1000027 (CRITICAL, spring-web, fixed 6.0.0). CVE-2024-22243/22259/22262 (HIGH, URL parsing/SSRF). CVE-2024-38819 (HIGH, path traversal, webmvc/webflux). CVE-2020-5398 (HIGH, RFD). CVE-2022-22970 (HIGH, beans). CVE-2023-20863 (HIGH, SpEL DoS). CVE-2018-15756 (HIGH, core). CVE-2026-47884 (CRITICAL, XsltView, fixed 7.0.9). OSV counts: webmvc 13, webflux 14, expression 8, web 6. |
| `org.springframework.boot:spring-boot` / `-autoconfigure` / `-actuator` | all (actuator: config, gateway, registry) | 2.0.3.RELEASE | 4.1.1 | EOL, vulnerable | CVE-2022-27772 (HIGH), CVE-2025-22235 (HIGH, EndpointRequest matcher), CVE-2026-40973 (HIGH), CVE-2023-20883 (HIGH, autoconfigure), CVE-2023-34055 (MODERATE, actuator). |
| `org.springframework.security:spring-security-crypto` | all (via spring-security-rsa / spring-cloud-context) | 5.0.6.RELEASE | 7.1.1 | EOL, vulnerable | CVE-2025-22228 (HIGH, BCrypt password length not enforced). |
| `com.fasterxml.jackson.core:jackson-databind` | all | 2.9.6 | 2.22.3 (Jackson 3 = `tools.jackson.core`, used by Boot 4) | major behind, EOL line, vulnerable | **66 OSV advisories: 19 CRITICAL, 41 HIGH.** Mostly polymorphic deserialization gadgets, e.g. CVE-2018-14718/14719/14721, CVE-2018-19360/19362, CVE-2019-14379, CVE-2019-16335, CVE-2019-17267, CVE-2020-8840, CVE-2020-9546 (all CRITICAL). |
| `org.yaml:snakeyaml` | all | 1.19 | 2.7 | major behind, vulnerable | CVE-2022-1471 (HIGH, Constructor RCE, fixed 2.0), CVE-2022-25857 (HIGH), CVE-2017-18640 (HIGH), plus 5 MODERATE (CVE-2022-38749/38750/38751/38752/41854). The config server parses YAML from `shared/`. |
| `ch.qos.logback:logback-classic` / `logback-core` | all | 1.2.3 | 1.6.5 (Boot 4.1.1 manages 1.5.38) | major behind, vulnerable | CVE-2023-6378 (HIGH), CVE-2021-42550 (MODERATE), CVE-2025-11226 (MODERATE), CVE-2024-12798 (MODERATE), plus 4 LOW. |
| `org.hibernate.validator:hibernate-validator` | all | 6.0.10.Final | 9.1.4.Final | major behind, vulnerable | CVE-2019-10219, CVE-2020-10693, CVE-2023-1932, CVE-2025-35036 (all MODERATE). |
| `org.bouncycastle:bcprov-jdk15on` / `bcpkix-jdk15on` | all (via `spring-security-rsa` 1.0.5) | 1.56 | 1.70 (final `-jdk15on`, now deprecated; successor `bcprov-jdk18on`) | deprecated, vulnerable | CVE-2018-1000180 (HIGH), plus 6 MODERATE (e.g. CVE-2020-15522, CVE-2023-33201, CVE-2024-29857, CVE-2024-30171) and bcpkix CVE-2025-8916 / CVE-2026-5588 (MODERATE). |
| `org.apache.httpcomponents:httpclient` | all | 4.5.5 | 4.5.14 (HttpClient 5.x is the successor) | minor behind, vulnerable | CVE-2020-13956 (MODERATE). |
| `org.springframework.security:spring-security-rsa` | all | 1.0.5.RELEASE | 1.1.5 | minor behind | No OSV entries. |
| Netflix: `archaius-core` 0.7.6, `commons-configuration` 1.8, `commons-lang` 2.6 | gateway, monitoring, registry, turbine-stream-service | 0.7.6 / 1.8 / 2.6 | 0.7.12 / 1.10 / 2.6 | deprecated (Archaius 1 is removed from Spring Cloud 2020.0+) | commons-configuration CVE-2025-46392 (LOW). commons-lang 2.6 CVE-2025-48924 (MODERATE, no fix in 2.x; fix is in commons-lang3 3.18.0). |
| `net.minidev:json-smart` (test scope) | gateway, monitoring, registry, turbine-stream-service | 2.3 | 2.6.0 | vulnerable (test only) | CVE-2023-1370 (HIGH), CVE-2021-27568 (MODERATE). |

---

#### Module: `config` (Spring Cloud Config Server)

| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `org.springframework.cloud:spring-cloud-config-server` | declared in module, version from Cloud BOM | 2.0.0.RELEASE | 5.0.5 (2.0.x last: 2.0.5.RELEASE; 4.3.x: 4.3.4) | major behind, EOL, vulnerable | **CVE-2026-40982 (CRITICAL, path traversal, fixed 4.3.3/5.0.3)**, CVE-2026-22739 (HIGH, path traversal via profile parameter, fixed 4.3.2/5.0.2), CVE-2019-3799 (MODERATE, path traversal, fixed 2.0.4). Uses the `native` profile with `search-locations: classpath:/shared`. Config Server 4.x+ is Jakarta-based (Boot 3+). |
| `org.springframework.boot:spring-boot-starter-security` | declared, Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, EOL | Brings Security 5.0.6. |
| `org.springframework.security:spring-security-core` / `-web` / `-config` | transitive (Boot BOM) | 5.0.6.RELEASE | 7.1.1 (5.8.x last 5.8.16; 6.5.11) | EOL, vulnerable | Security 5.0 EOL 2019-03-31. **CVE-2022-22978 (CRITICAL, regex authorization bypass)**, CVE-2024-38821 (CRITICAL, static-resource authorization bypass, WebFlux), CVE-2026-22732 (CRITICAL, headers not written), CVE-2021-22112 (HIGH), CVE-2024-22257 (HIGH), plus MODERATE (CVE-2020-5408, CVE-2019-3795, CVE-2024-38827, CVE-2026-41706, CVE-2026-47838). Breaking change: `WebSecurityConfigurerAdapter` is removed in Security 6 and this repo extends it (in another layer). Here the config server only uses basic auth through properties. |
| `org.eclipse.jgit:org.eclipse.jgit` / `.http.apache` | transitive (config-server) | 4.11.0.201803080745-r | 7.8.0.202609011348-r | major behind, vulnerable | CVE-2023-4759 (HIGH, arbitrary file overwrite), CVE-2025-4949 (MODERATE, XXE). The Git backend is not active (`native` profile), so exposure is lower; I inferred this from `application.yml`. |
| `com.jcraft:jsch` | transitive (jgit) | 0.1.54 | 0.1.55 | minor behind, deprecated | No OSV entries for `com.jcraft:jsch`. The original JSch is unmaintained; current JGit uses the maintained fork `com.github.mwiede:jsch` (from documentation). |
| `org.apache.tomcat.embed:tomcat-embed-core` | transitive (Boot `starter-web`) | 8.5.31 | 11.0.27 (8.5 last 8.5.100; 9.0.123; 10.1.61) | major behind, EOL, vulnerable | Tomcat 8.5 EOL 2024-03-31. **51 OSV advisories: 9 CRITICAL, 22 HIGH**, e.g. CVE-2020-1938 "Ghostcat" (CRITICAL, AJP), CVE-2025-24813 (CRITICAL, partial PUT RCE), CVE-2018-8014 (CRITICAL, CORS defaults), several 2026 CRITICAL auth-bypass issues fixed only in 9.0.118+/10.1.55+/11.0.22+. Same Tomcat in registry, gateway and monitoring. |
| shared libs (Spring Framework 5.0.7, Boot 2.0.3, jackson 2.9.6, snakeyaml 1.19, logback 1.2.3, bcprov 1.56, hibernate-validator 6.0.10, httpclient 4.5.5) | transitive | see shared table | | EOL, vulnerable | |
| `spring-boot-maven-plugin` | module build, Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

#### Module: `registry` (Eureka server)

| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-server` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.2 (2.0.x last 2.0.4; 2.2.10.RELEASE; 4.3.3) | major behind, EOL | No OSV entries. Eureka is the only Netflix component still in Spring Cloud. Breaking: Spring Cloud Netflix 4.x/5.x Eureka uses Jakarta and Jersey 3 (`org.glassfish.jersey`, `jakarta.ws.rs`, verified in `eureka-client` 2.0.6 pom). Old Jersey 1 / XStream / Ribbon dependencies are dropped. |
| `com.netflix.eureka:eureka-core` / `eureka-client` | transitive | 1.9.2 | 2.0.6 (1.x last 1.10.19) | major behind | No OSV entries for these coordinates. |
| `org.springframework.cloud:spring-cloud-starter-config` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | Breaking: `bootstrap.yml` is ignored from 2020.0 unless you add `spring-cloud-starter-bootstrap`. Prefer `spring.config.import=configserver:`. Applies to registry, gateway, monitoring and turbine-stream-service. |
| `org.springframework.boot:spring-boot-starter-test` | declared (test), Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind | JUnit 4 → JUnit 5 (Boot 2.4+ drops the vintage engine by default, and Boot 4 drops JUnit 4 support). |
| `com.thoughtworks.xstream:xstream` | transitive (eureka-client 1.x) | 1.4.10 | 1.4.21 | minor behind, vulnerable | **35 OSV advisories: 2 CRITICAL (CVE-2013-7285, CVE-2019-10173), 20 HIGH** (e.g. CVE-2021-21341, CVE-2021-29505 RCE, CVE-2021-39139/39145/39149/39150/39153/39154). Also in gateway and turbine-stream-service. |
| `org.codehaus.jettison:jettison` | transitive (eureka-client) | 1.3.7 | 1.5.7 | minor behind, vulnerable | CVE-2022-45685, CVE-2022-45693, CVE-2022-40150, CVE-2023-1436 (HIGH), CVE-2022-40149 (MODERATE). |
| `com.sun.jersey:jersey-core` / `-server` / `-servlet` / `-client` | transitive (eureka 1.x) | 1.19.1 | 1.19.4 | deprecated | Jersey 1.x is a dead line (from documentation); replaced by Jersey 3 in Eureka 2.x. No OSV entries. |
| `org.freemarker:freemarker` (via `spring-boot-starter-freemarker`) | transitive (Eureka dashboard) | 2.3.28 | 2.3.35 | minor behind, vulnerable | CVE-2026-84939 (CRITICAL, template-loading path traversal, fixed 2.3.35). Also in monitoring. |
| `com.fasterxml.woodstox:woodstox-core` | transitive | 5.0.3 | 7.3.0 | major behind, vulnerable | CVE-2022-40152 (MODERATE). |
| `com.google.guava:guava` | transitive (runtime) | 16.0 | 33.7.2-jre | major behind, vulnerable | CVE-2018-10237, CVE-2023-2976 (MODERATE), CVE-2020-8908 (LOW). |
| Ribbon `ribbon*` 2.2.5, Hystrix `hystrix-core` 1.5.12, Netty 4.1.25 (runtime, via ribbon-transport/rxnetty) | transitive (eureka-server starter) | see gateway | | deprecated, vulnerable | Pulled in only by the Finchley Eureka starter; gone in newer Eureka. |
| Tomcat 8.5.31 + shared libs | transitive | see config / shared | | EOL, vulnerable | |
| `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

#### Module: `gateway` (Zuul 1 edge + static UI)

| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `org.springframework.cloud:spring-cloud-starter-netflix-zuul` | declared, Cloud BOM | 2.0.0.RELEASE | 2.2.10.RELEASE (final; not in any train ≥ 2020.0) | deprecated, EOL, vulnerable | CVE-2021-22113 (MODERATE, authorization, fixed 2.2.7). **Replacement:** Spring Cloud Gateway: `spring-cloud-starter-gateway` (reactive, latest 4.3.5 for 2025.0; for 2025.1 the starter is renamed `spring-cloud-starter-gateway-server-webflux`, I did not verify the exact name) or `spring-cloud-starter-gateway-server-webmvc` (5.0.3, servlet-based, closest to Zuul's model). Breaking: `@EnableZuulProxy` and the `zuul.routes.*` in `config/.../shared/gateway.yml` (path/serviceId/url/`stripPrefix`/`sensitiveHeaders`/`ignoredServices`) must be rewritten as `spring.cloud.gateway.routes` with predicates/filters. Hystrix/Ribbon timeouts (`hystrix.command.default...`, `ribbon.ReadTimeout`) become Gateway httpclient timeouts plus Resilience4j/LoadBalancer config. The gateway also serves the static SPA. |
| `com.netflix.zuul:zuul-core` | transitive | 1.3.1 | 4.1.9 (Zuul 2+/Netty, not a drop-in replacement) | deprecated | Zuul 1 is in maintenance. No OSV entries. |
| `com.netflix.hystrix:hystrix-core` / `hystrix-javanica` / `hystrix-metrics-event-stream` | transitive (zuul starter) | 1.5.12 | 1.5.18 (final) | deprecated, EOL | Netflix put Hystrix in maintenance mode and Spring Cloud removed it in 2020.0. **Replacement:** Resilience4j through `spring-cloud-starter-circuitbreaker-resilience4j` (Cloud BOM; `resilience4j-spring-boot3` 2.4.0). |
| `com.netflix.ribbon:ribbon*` | transitive | 2.2.5 | 2.7.18 | deprecated, EOL | Removed in 2020.0. **Replacement:** Spring Cloud LoadBalancer (`spring-cloud-starter-loadbalancer`, 5.0.3 / 4.3.3). `ribbon.*` properties no longer apply. |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.2 | major behind | See registry for the Jakarta/Jersey 3 change. |
| `org.springframework.cloud:spring-cloud-starter-sleuth` (`spring-cloud-sleuth-core`) | declared, Cloud BOM | 2.0.0.RELEASE | 3.1.11 (final; core artifact last 2.2.8.RELEASE) | deprecated, EOL | Removed in 2022.0. **Replacement:** Micrometer Tracing (`io.micrometer:micrometer-tracing-bridge-brave` 1.7.1, or the OTel bridge) + Boot actuator. Brave 5.1.0 → 6.3.1. The log MDC keys (`X-B3-*` → `traceId`/`spanId`) and the default propagation (B3 → W3C) change. |
| `org.springframework.cloud:spring-cloud-starter` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.3 | major behind | |
| `org.springframework.cloud:spring-cloud-starter-config` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind | The `bootstrap.yml` issue (see registry). |
| `org.springframework.boot:spring-boot-starter-test` | declared (test), Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind | |
| `com.google.guava:guava` | transitive (compile) | 15.0 | 33.7.2-jre | major behind, vulnerable | CVE-2018-10237, CVE-2023-2976 (MODERATE), CVE-2020-8908 (LOW). |
| `commons-io:commons-io` | transitive (runtime) | 2.4 | 2.22.0 | major behind, vulnerable | CVE-2024-47554 (HIGH), CVE-2021-29425 (MODERATE). |
| `org.apache.commons:commons-lang3` | transitive (runtime) | 3.7 | 3.21.0 | minor behind, vulnerable | CVE-2025-48924 (MODERATE). |
| `io.netty:netty-codec-http` & co. (runtime, via ribbon-transport/rxnetty) | transitive | 4.1.25.Final | 4.2.19.Final (4.1 last 4.1.139) | major behind, vulnerable | See turbine-stream-service. |
| `io.zipkin.brave:brave` (+ instrumentation modules) | transitive (sleuth) | 5.1.0 | 6.3.1 | major behind | No OSV entries. |
| XStream 1.4.10, Jettison 1.3.7, Jersey 1.19.1 (via eureka-client 1.x), Tomcat 8.5.31 + shared libs | transitive | see registry / config / shared | | vulnerable, EOL | |
| `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

#### Module: `monitoring` (Hystrix Dashboard)

| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `org.springframework.cloud:spring-cloud-starter-netflix-hystrix-dashboard` | declared, Cloud BOM | 2.0.0.RELEASE | 2.2.10.RELEASE (final; not in any train ≥ 2020.0) | deprecated, EOL, vulnerable | **CVE-2021-22053 (HIGH, code injection via the FreeMarker-based dashboard, fixed 2.2.10.RELEASE)**. `@EnableHystrixDashboard` is gone. **Replacement:** there is no 1:1 equivalent. Expose Resilience4j/actuator metrics with Micrometer → Prometheus + Grafana dashboards (or Spring Boot Admin). |
| `com.netflix.hystrix:hystrix-core` etc. | transitive | 1.5.12 | 1.5.18 (final) | deprecated, EOL | |
| `org.freemarker:freemarker` | transitive (dashboard UI) | 2.3.28 | 2.3.35 | vulnerable | CVE-2026-84939 (CRITICAL). |
| `org.springframework.cloud:spring-cloud-starter-config` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind | The `bootstrap.yml` issue (see registry). |
| `org.springframework.boot:spring-boot-starter-test` | declared (test), Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind | |
| Archaius 0.7.6, Ribbon 2.2.5 (via spring-cloud-netflix-ribbon), Guava 16.0, Tomcat 8.5.31 + shared libs | transitive | see above | | deprecated, vulnerable, EOL | |
| `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

#### Module: `turbine-stream-service` (Turbine Stream over RabbitMQ)

| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| `org.springframework.cloud:spring-cloud-starter-netflix-turbine-stream` | declared, Cloud BOM | 2.0.0.RELEASE | 2.2.10.RELEASE (final; not in any train ≥ 2020.0) | deprecated, EOL | No OSV entries. `@EnableTurbineStream` is gone. **Replacement:** no direct one. Aggregate metrics with Micrometer + Prometheus scraping, Eureka/Consul service discovery for scrape targets, and Grafana. That makes this whole service, together with `monitoring`, a candidate for removal. |
| `com.netflix.turbine:turbine-core` | transitive | 2.0.0-DP.2 | 2.0.0-DP.2 (only "developer preview"; the last GA is 1.0.0) | deprecated | Never reached GA, unmaintained. |
| `org.springframework.cloud:spring-cloud-starter-stream-rabbit` / `spring-cloud-stream` / `spring-cloud-stream-binder-rabbit` | declared, Cloud BOM | 2.0.0.RELEASE (Elmhurst) | 5.0.3 (4.3.3 for 2025.0) | major behind, EOL | No OSV entries. Breaking: the annotation model (`@EnableBinding`, `@StreamListener`, `@Input/@Output`) was deprecated in 3.1 and removed in 4.0, so only the functional model (`Supplier/Function/Consumer` + `spring.cloud.stream.bindings.<fn>-in-0`) remains. Turbine-stream itself is built on the old model. Binding/property names change. |
| `org.springframework.amqp:spring-amqp` / `spring-rabbit` | transitive (Boot BOM via binder) | 2.0.4.RELEASE | 4.1.1 | major behind, vulnerable | CVE-2018-11087 (MODERATE, fixed 2.0.6), CVE-2026-41701, CVE-2026-41714 (MODERATE, fixed 3.2.11/4.0.4). |
| `com.rabbitmq:amqp-client` | transitive | 5.1.2 | 5.37.0 | minor behind (many releases), vulnerable | **12 OSV advisories: 4 HIGH**: CVE-2026-63337 (unvalidated `Class.forName` in JSON-RPC), CVE-2026-69219, CVE-2026-69220, CVE-2026-75516 (frame/ValueReader DoS/OOM, fixed 5.33.x–5.34.0), plus 7 MODERATE (incl. CVE-2018-11087) and 1 LOW. |
| `org.springframework.boot:spring-boot-starter-webflux` / `io.projectreactor.ipc:reactor-netty` / `io.projectreactor:reactor-core` | transitive (turbine-stream uses WebFlux/Netty, not Tomcat) | 2.0.3 / 0.7.8.RELEASE / 3.1.8.RELEASE | 4.1.1 / 0.7.15.RELEASE (line moved to `io.projectreactor.netty:reactor-netty` 1.x) / 3.8.7 | major behind, EOL | No OSV entries for these coordinates. Spring WebFlux 5.0.7 CVEs apply (see shared table). |
| `io.netty:netty-codec-http` / `netty-handler` / `netty-codec` / `netty-common` | transitive | 4.1.25.Final | 4.2.19.Final (4.1 last 4.1.139.Final) | major behind, vulnerable | netty-codec-http: **23 advisories (1 CRITICAL CVE-2019-20444 request smuggling, 6 HIGH, e.g. CVE-2026-33870)**. netty-handler: 8 (CRITICAL CVE-2026-75595 SNI routing bypass; HIGH CVE-2020-11612, CVE-2026-44249/45416/50010). netty-codec: 5 (HIGH CVE-2021-37136/37137, CVE-2026-42583/59901). netty-common: 2 MODERATE. |
| `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.2 | major behind | XStream 1.4.10, Jettison 1.3.7, Jersey 1.19.1, Ribbon 2.2.5 come in through it (see registry). |
| `org.springframework.cloud:spring-cloud-starter-config` | declared, Cloud BOM | 2.0.0.RELEASE | 5.0.5 | major behind | The `bootstrap.yml` issue. |
| `org.springframework.boot:spring-boot-starter-test` | declared (test), Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind | |
| `spring-boot-maven-plugin` | Boot parent | 2.0.3.RELEASE | 4.1.1 | major behind | |

---

#### Layer findings

1. **The whole layer runs on unsupported platforms.** Boot 2.0.3 (EOL 2019), Spring Framework 5.0.7 (EOL 2020), Spring Security 5.0.6 (EOL 2019), Spring Cloud Finchley (EOL), Tomcat 8.5 (EOL 2024-03) and Java 8 base images (`java:8-jre`, removed from Docker Hub). None of these can be fixed with patch bumps on the current line.
2. **Many critical CVEs in the internet-facing pieces.** The `gateway` (edge, Tomcat 8.5.31 with Ghostcat, CVE-2025-24813 and the 2026 auth bypasses, plus Spring Web/MVC CVEs) and the `config` server (CVE-2026-40982 CRITICAL / CVE-2026-22739 HIGH path traversal, CVE-2019-3799). Deserialization gadget libraries are on every classpath: jackson-databind 2.9.6 (19 CRITICAL), XStream 1.4.10 (2 CRITICAL / 20 HIGH), snakeyaml 1.19 (CVE-2022-1471).
3. **Most of this layer is removed upstream, not just outdated.** Zuul 1, Hystrix, Hystrix Dashboard, Turbine/Turbine Stream, Ribbon and Archaius stop at Spring Cloud Netflix 2.2.10.RELEASE. Tool evidence: the netflix BOM ≥ 3.0 manages only Eureka. Of the five modules, only `config` (Config Server) and `registry` (Eureka) have a supported upgrade path in Spring Cloud. `gateway` needs a rewrite. `monitoring` and `turbine-stream-service` have no successor and should be replaced by Micrometer/Prometheus/Grafana.
4. **The Hystrix dashboard has its own known code-injection flaw** (CVE-2021-22053, HIGH), fixed only in the final 2.2.10.RELEASE.
5. **Turbine-stream path:** RabbitMQ `amqp-client` 5.1.2 (4 HIGH), Netty 4.1.25 (CRITICAL request smuggling / SNI bypass), and the Spring Cloud Stream annotation model that was removed in 4.0.

### Breaking-change concerns for upgrading
- **Java 8 → 17+ baseline** (Boot 3/4). Update `java.version`, the build JDK, and the Dockerfiles (`eclipse-temurin:17/21-jre`).
- **`javax.*` → `jakarta.*`** (Boot 3+). This affects Servlet filters, validation and annotations. Eureka moves to Jersey 3, Tomcat to 10.1/11.
- **Boot/Cloud train pairing.** Boot 4.1.x has no supported Spring Cloud train yet (2025.1 = Boot 4.0.x). Pick **Boot 3.5.x + 2025.0.x** (OSS EOL 2026-06-30, commercial to 2032) or **Boot 4.0.x + 2025.1.x** (OSS EOL 2027-07-31). Boot 4 also brings Jackson 3 (`tools.jackson`), Spring Framework 7 and Security 7.
- **Zuul → Spring Cloud Gateway.** Rewrite `@EnableZuulProxy` and the `zuul.routes`. The reactive Gateway (WebFlux/Netty) is a different programming model; Gateway Server MVC is the closer servlet-based option. Sensitive-headers/cookie forwarding and `stripPrefix` semantics differ. The gateway also serves the SPA static files and proxies `/uaa/**` to auth-service, which needs careful re-testing of the OAuth2 token flow.
- **Hystrix → Resilience4j** (Spring Cloud CircuitBreaker). There is no `@HystrixCommand`. Thread-isolation timeouts (`hystrix.command.default...timeoutInMilliseconds: 20000`) map to TimeLimiter/Bulkhead. The dashboard/Turbine metrics format is gone.
- **Ribbon → Spring Cloud LoadBalancer.** `ribbon.ReadTimeout/ConnectTimeout` and Ribbon `IRule` customizations no longer apply.
- **Sleuth → Micrometer Tracing.** Trace headers default to W3C, and log-correlation property names change.
- **Config client bootstrap.** Four modules rely on `bootstrap.yml`, which is ignored from 2020.0 unless `spring-cloud-starter-bootstrap` is added. The recommended setting is `spring.config.import=optional:configserver:http://config:8888`.
- **Spring Cloud Stream 2.0 → 4.x/5.x.** The functional binding model and new binding names. Turbine-stream itself doesn't migrate.
- **Spring Security 5.0 → 6/7.** `WebSecurityConfigurerAdapter` is removed (the repo extends it), and so are `authorizeRequests`/`antMatchers`. For the config server's basic auth this is a small change; the larger OAuth2 (`spring-cloud-starter-oauth2`) migration belongs to the other layers.
- **Build:** `<finalName>` inside the `spring-boot-maven-plugin` configuration must move to `<build><finalName>`. Tests need JUnit 5 and Surefire 3.

### Prioritized recommendations (highest first)
1. **Contain now (no code change):** do not expose `config` (8888), `registry` (8761), `monitoring` or `turbine-stream-service` outside the internal network. Keep only `gateway` public. Disable the AJP connector (it is off by default in embedded Tomcat; verify it). Rotate config-server credentials.
2. **Stop-gap on the current Finchley stack, if a full migration isn't scheduled yet.** Move to **Finchley.SR4 + Boot 2.0.9** first. The better short-term option is **Hoxton.SR12 + Boot 2.3.x**, which still has Zuul/Hystrix/Turbine and pulls in hystrix-dashboard 2.2.9 → override to 2.2.10.RELEASE for CVE-2021-22053. Also override `tomcat.version` (9.0.x via Boot 2.3), `jackson-bom` 2.12+/2.13.x, `snakeyaml` ≥ 1.33, `xstream` 1.4.21, `logback` 1.2.13, `freemarker` 2.3.35, `amqp-client` 5.x latest and `netty` 4.1.139 where the Boot BOM allows it. This is a stop-gap only: all of it is still EOL.
3. **Target platform:** Java 17/21, **Boot 3.5.x + Spring Cloud 2025.0.x** (or Boot 4.0.x + 2025.1.x). Upgrade in this order:
   1. `config` (Config Server 4.3.x/5.0.x, security DSL).
   2. `registry` (Eureka server 4.3.x/5.0.x, Jakarta).
   3. Switch all clients to `spring.config.import`.
4. **Replace `gateway` Zuul with Spring Cloud Gateway**, move Hystrix to Resilience4j (CircuitBreaker) and Ribbon to Spring Cloud LoadBalancer, and Sleuth to Micrometer Tracing. Port the routes from `shared/gateway.yml` and run regression tests on the UI and the `/uaa/**` auth flow.
5. **Retire `monitoring` and `turbine-stream-service`.** Replace them with actuator + Micrometer Prometheus registry, Prometheus/Grafana (Resilience4j dashboards), and optionally Spring Boot Admin. That also removes the RabbitMQ/Netty/Turbine attack surface from this layer. Keep RabbitMQ only if other layers need it.
6. **Build hygiene:**
   - Update the Boot parent so it brings current Maven plugins.
   - Add `maven-enforcer-plugin` (minimum Maven/Java version).
   - Move `finalName` to `<build><finalName>`.
   - Add an automated SCA gate (OSV-Scanner / OWASP dependency-check / Dependabot).
   - Replace `java:8-jre` with `eclipse-temurin` images.

#### Unverified / limitations
- CVE applicability to how the code is actually used, such as reachable deserialization or Spring4Shell (which needs JDK 9+), was not tested at runtime. The statuses reflect the resolved versions only. Severity is as reported by OSV (GitHub advisories); NVD CVSS was not cross-checked.
- The OSV counts include advisories published through 2026-10. A few advisories have no fixed version listed for the old lines (e.g. CVE-2026-41849, CVE-2025-46392).
- The "latest" for `logback` (1.6.5) and `jackson-databind` (2.22.3) is the highest GA in Maven metadata. I did not check the release notes or whether these are the recommended lines. Boot 4.1.1 itself manages logback 1.5.38.
- The Finchley/Greenwich EOL date is not on endoflife.date. "EOL" is inferred from it being older than 2020.0 (OSS EOL 2022-05-31).
- I did not fetch the narrative release notes for Netflix OSS maintenance mode (Hystrix/Ribbon/Zuul 1/Archaius/Turbine) or the exact Spring Cloud Gateway 2025.1 starter names. Removal is confirmed by the BOM contents. The rest comes from Spring Cloud documentation (from knowledge).
- `com.jcraft:jsch` being unmaintained, and Jersey 1.x being end of line, are from documentation/knowledge. OSV returned no entries for them.
- The Git backend of the config server (and so the JGit CVEs) looks unused, because `application.yml` sets the `native` profile. This is inferred from config, not verified at runtime.

</details>

<details>
<summary><b>auth layer: full report</b></summary>

### Dependency freshness & security analysis: Security/authentication layer (`auth-service`)

Repository: `axelisr/DevinWorkshop`, branch `master` @ `6bb2cf9`. Analysis only; nothing in the repo was changed.
Analysis date: 2026-10-13. Build JDK: OpenJDK 1.8.0_504. Maven 3.6.3, through the Google Maven Central mirror.

#### Sources and method
| Data | Source |
|---|---|
| Resolved versions (direct + transitive) | **Tool**: `mvn -B dependency:tree -pl auth-service`, `mvn help:effective-pom` (plugin versions) |
| "Newer version" checks | **Tool**: `versions-maven-plugin:2.16.2` `display-dependency-updates` / `display-plugin-updates` / `display-parent-updates`. The plugin reports milestones as "latest" (e.g. Boot `4.2.0-M2`, Cloud `5.1.0-M1`), so the **latest stable** column comes from filtering `maven-metadata.xml` on the mirror to drop M/RC/alpha/beta versions |
| BOM contents of newer trains (which managed versions apply, and which modules were removed) | **Tool**: `curl` of `spring-cloud-dependencies` (Hoxton.SR12, 2020.0.6, 2021.0.9, 2022.0.5, 2025.1.3), `spring-cloud-netflix-dependencies` (2.2.10, 3.0.6, 5.0.2), and `spring-boot-dependencies` (2.0.3, 2.7.18, 3.5.16, 4.0.8) POMs |
| CVEs | **Tool**: OSV API `querybatch` over every resolved jar in the tree (plus jacoco), then `/v1/vulns/{id}` for severity (GitHub-reviewed `database_specific.severity`) and fixed versions |
| Support / EOL dates | **Tool**: endoflife.date API (spring-boot, spring-framework, spring-cloud, mongodb). **Documentation / prior knowledge**: Spring Security OAuth EOL, Tomcat 8.5 EOL, Finchley EOL, Java 8 support, jacoco JDK support. These are marked *(doc)* |

Legend: *(doc)* = from documentation or knowledge, not checked with a tool in this session.

#### Module: `auth-service`

What the code uses: `@EnableAuthorizationServer` with `AuthorizationServerConfigurerAdapter`, `InMemoryTokenStore`, `ClientDetailsServiceConfigurer`, `@EnableResourceServer`, `WebSecurityConfigurerAdapter`, `@EnableGlobalMethodSecurity(prePostEnabled=true)`, `BCryptPasswordEncoder` (users) and `NoOpPasswordEncoder` (clients), and `@EnableDiscoveryClient`. It is configured through `bootstrap.yml` against the config server. `UserRepositoryTest` imports `org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration`.
Consumers of this layer: the gateway UI calls `/uaa/oauth/token` with `grant_type: 'password'` (`gateway/.../launch.js`). account-service and notification-service use `clientId`/`clientSecret` (client credentials). All resource servers validate tokens through `user-info-uri: http://auth-service:5000/uaa/users/current`.

### Parent / BOMs
| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.boot:spring-boot-starter-parent | root `pom.xml` parent | 2.0.3.RELEASE | 4.1.1 (4.0.x line: 4.0.8; last 2.x: 2.7.18; last 2.0.x: 2.0.9) | major behind, **EOL** | endoflife.date: 2.0 OSS EOL 2019-03-01, commercial support ended 2020-06-01. Only 4.1 and 4.0 (EOL 2026-12-31) are still OSS-supported; 3.5 OSS support ended 2026-06-30 (commercial until 2032). Boot 3+ needs Java 17 and moves `javax.*` to `jakarta.*`. Boot 4 uses Jakarta EE 11, Spring Framework 7, Spring Security 7, and Jackson 3 (`tools.jackson`) by default |
| org.springframework.cloud:spring-cloud-dependencies | root `dependencyManagement` (`spring-cloud.version`) | Finchley.RELEASE | 2025.1.3 "Oakwood" (for Boot 4.0.x); 2026.0.0-M1 is only a milestone (for Boot 4.1) | major behind, **EOL** | Last Finchley release is SR4. Finchley EOL *(doc: 2019-12-31)*. BOMs checked with the tool: `spring-cloud-security` is missing from 2020.0.6 onwards, `spring-cloud-sleuth` is missing from 2022.0.5 onwards, and Netflix Ribbon/Hystrix/Archaius starters are missing from spring-cloud-netflix 3.0.6 onwards (Eureka is still there). **No GA Spring Cloud train supports Boot 4.1 yet**, so the realistic target is Boot 4.0.8 + 2025.1.3 |

### Declared dependencies (and their key transitives)
| Dependency/plugin | Declared/inherited from | Current (resolved) | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.cloud:spring-cloud-starter-oauth2 | declared; version from Cloud BOM | 2.0.0.RELEASE | 2.2.5.RELEASE (final, 2021-04) | **deprecated, EOL**, removed from release train | Not in any Spring Cloud BOM from 2020.0 onwards (tool-verified). Replace with **Spring Authorization Server**, which is now part of Spring Security 7 as `org.springframework.security:spring-security-oauth2-authorization-server` 7.1.1, with Boot starter `spring-boot-starter-oauth2-authorization-server` 4.1.1. Resource servers move to `spring-boot-starter-oauth2-resource-server` (JWT/opaque) |
| ↳ org.springframework.security.oauth:spring-security-oauth2 | transitive (starter-oauth2 → oauth2-autoconfigure) | 2.2.1.RELEASE | 2.5.2.RELEASE (final, 2022-04) | **EOL, vulnerable** | OSV: **CVE-2018-1260 CRITICAL** (RCE through the approval endpoint, fixed 2.2.2), **CVE-2018-15758 HIGH** (authorization bypass, fixed 2.2.3), CVE-2019-3778 MODERATE (open redirect, fixed 2.2.4). OSV lists nothing for 2.5.2, but the project is EOL *(doc: Spring Security OAuth EOL announced 2022)* and gets no further fixes |
| ↳ org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure | transitive | 2.0.0.RELEASE | 2.6.8 (final, 2022-05) | **EOL** | Same replacement as above |
| ↳ org.springframework.security:spring-security-jwt | transitive | 1.0.9.RELEASE | 1.1.1.RELEASE (2020) | **deprecated/EOL** | OSV: none. Replacement: `spring-security-oauth2-jose` (Nimbus) |
| ↳ org.springframework.cloud:spring-cloud-security / spring-cloud-starter-security | transitive | 2.0.0.RELEASE | 2.2.5.RELEASE (final) | **EOL**, removed from train 2020.0 | Pulls in `spring-boot-starter-actuator` |
| ↳ org.codehaus.jackson:jackson-mapper-asl | transitive (spring-security-oauth2) | 1.9.13 | 1.9.13 (abandoned Jackson 1.x) | **EOL, vulnerable** | OSV: **CVE-2019-10202 CRITICAL**, **CVE-2019-10172 HIGH**. No fixed version exists; it goes away only when legacy OAuth2 is removed |
| ↳ commons-codec:commons-codec | transitive | 1.11 | 1.23.0 | minor behind | OSV: none |
| org.springframework.boot:spring-boot-starter-security | declared; Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, **vulnerable** | Resolves Spring Security **5.0.6.RELEASE** (latest 7.1.1; Boot 4.0.8 manages 7.0.7). OSV, spring-security-web: **CVE-2024-38821 CRITICAL**, **CVE-2022-22978 CRITICAL** (regex authorization bypass), **CVE-2026-22732 CRITICAL** (headers not written; fixed 6.5.9/7.0.4), **CVE-2021-22112 HIGH**, plus 2 MODERATE. spring-security-core: CVE-2022-22978 CRITICAL, **CVE-2024-22257 HIGH**, 3 MODERATE. spring-security-crypto: **CVE-2025-22228 HIGH** (BCrypt does not enforce the 72-byte password limit; relevant because `BCryptPasswordEncoder` is used). Breaking changes: `WebSecurityConfigurerAdapter` removed in 6.0 (use `SecurityFilterChain` beans); `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`; `authorizeRequests`/`antMatchers` → `authorizeHttpRequests`/`requestMatchers`; `NoOpPasswordEncoder` (client secrets) should become `{bcrypt}` through `DelegatingPasswordEncoder` |
| org.springframework.boot:spring-boot-starter-web | declared; Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, **vulnerable** | spring-web/webmvc **5.0.7.RELEASE** (Framework 5.0 EOL 2020-12-31 per endoflife.date; latest 7.0.9 GA). OSV: **CVE-2022-22965 CRITICAL** (Spring4Shell; *(doc)* the exploit needs JDK 9+ and WAR on Tomcat, but this service runs a JDK 8 fat jar, so lower practical risk), **CVE-2016-1000027 CRITICAL** (HttpInvoker deserialization, fixed only in 6.0), **CVE-2026-47884 CRITICAL** (XsltView), HIGH: CVE-2024-22243/22259/22262, CVE-2024-38819, CVE-2020-5398, CVE-2026-41842/41845; spring-beans CVE-2022-22970 HIGH; spring-context CVE-2022-22968 HIGH; spring-expression CVE-2023-20863, CVE-2026-41849/41850 HIGH; spring-core CVE-2018-15756 HIGH |
| ↳ org.apache.tomcat.embed:tomcat-embed-core (+ -el, -websocket) | transitive (starter-tomcat) | 8.5.31 | 11.0.27 | major behind, **EOL**, **vulnerable** | 8.5 branch EOL *(doc: 2024-03-31)*. OSV: **51 advisories (9 CRITICAL, 22 HIGH)**, e.g. CVE-2020-1938 "Ghostcat" (AJP, which Boot does not enable by default), CVE-2025-24813, CVE-2018-8014, CVE-2026-43515/65905/65182/68525. tomcat-embed-websocket: CVE-2020-13935 HIGH. Note: Boot 4.0.8 manages **11.0.24**, which OSV still flags for CVE-2026-65905/65182/68525 (fixed 11.0.25), so the `tomcat.version` property needs an override even after the upgrade |
| ↳ com.fasterxml.jackson.core:jackson-databind / jackson-core | transitive (starter-json, config-client) | 2.9.6 | 2.22.3 (2.x) / 3.2.3 (`tools.jackson.core`) | major behind, **vulnerable** | OSV, jackson-databind: **66 advisories (19 CRITICAL, 41 HIGH)**, polymorphic-typing gadget CVEs such as CVE-2018-14718/14719/14721, CVE-2019-14379, CVE-2020-8840, CVE-2020-9546. jackson-core: CVE-2025-52999, CVE-2026-68494, CVE-2026-89425 HIGH. Boot 4 defaults to Jackson 3 (new package `tools.jackson`) |
| ↳ org.yaml:snakeyaml | transitive (spring-boot-starter) | 1.19 | 2.7 | major behind, **vulnerable** | OSV: **CVE-2022-1471 HIGH** (Constructor RCE, fixed 2.0), CVE-2022-25857 HIGH, CVE-2017-18640 HIGH, 5 MODERATE |
| ↳ ch.qos.logback:logback-classic / logback-core | transitive (starter-logging) | 1.2.3 | 1.6.5 (Boot 4.0.8 manages 1.5.38) | major behind, **vulnerable** | OSV: **CVE-2023-6378 HIGH**, CVE-2021-42550, CVE-2024-12798, CVE-2025-11226 MODERATE, 4 LOW |
| ↳ org.hibernate.validator:hibernate-validator | transitive | 6.0.10.Final | 9.1.4.Final | major behind, vulnerable | OSV: 4 MODERATE. Boot 3+ uses `jakarta.validation` |
| ↳ org.springframework.boot:spring-boot / -autoconfigure / -actuator* | transitive | 2.0.3.RELEASE | 4.1.1 | **vulnerable** | OSV: spring-boot CVE-2022-27772, CVE-2025-22235, CVE-2026-40973 HIGH; autoconfigure CVE-2023-20883 HIGH; actuator-autoconfigure **CVE-2023-20873 CRITICAL** (Cloud Foundry wildcard bypass); starter-actuator CVE-2026-22733 HIGH |
| ↳ io.micrometer:micrometer-core | transitive (actuator) | 1.0.5 | 1.17.1 | major behind, vulnerable | OSV: CVE-2026-40984 HIGH |
| org.springframework.boot:spring-boot-starter-data-mongodb | declared; Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, **vulnerable** | spring-data-mongodb **2.0.8.RELEASE** (latest 5.1.1). OSV: **CVE-2022-22980 CRITICAL** (SpEL injection in `@Query`, fixed 3.3.5/3.4.1), CVE-2026-41717 HIGH, CVE-2026-41696 MODERATE (fixed 4.5.12/5.0.6). spring-data-commons 2.0.8: CVE-2026-41716 HIGH, 2 MODERATE |
| ↳ org.mongodb:mongodb-driver (+ driver-core, bson) | transitive | 3.6.4 | 3.12.14 (final of the legacy `mongodb-driver` artifact); successor `mongodb-driver-sync` 5.13.0 (Boot 4.0.8 manages 5.6.5) | major behind, **legacy artifact EOL** | OSV: no advisories. Spring Data MongoDB 3.x+ needs driver 4.x+ (`com.mongodb.client.MongoClient`; the legacy `com.mongodb.MongoClient` API is gone). The MongoDB **server** image is `mongo:3` (`mongodb/Dockerfile`); per endoflife.date MongoDB 3.6 is EOL (2021-04-30). Check that newer drivers still support the old server *(unverified)*; plan a server upgrade alongside |
| org.springframework.cloud:spring-cloud-starter-config | declared; Cloud BOM | 2.0.0.RELEASE | 5.0.5 (in 2025.1.3) | major behind | Breaking change: since 2020.0, `bootstrap.yml` is not processed by default (auth-service relies on it). Use `spring.config.import=configserver:` or add `spring-cloud-starter-bootstrap` |
| ↳ org.springframework.security:spring-security-rsa | transitive | 1.0.5.RELEASE | 1.1.5 | minor behind | OSV: none |
| ↳ org.bouncycastle:bcprov-jdk15on / bcpkix-jdk15on | transitive (spring-security-rsa) | 1.56 | 1.70 (final of jdk15on); successor `bcprov-jdk18on` 1.86 | **EOL artifact, vulnerable** | OSV: **CVE-2018-1000180 HIGH**, 6 MODERATE (bcprov) + 2 MODERATE (bcpkix) |
| org.springframework.cloud:spring-cloud-starter-netflix-eureka-client | declared; Cloud BOM | 2.0.0.RELEASE | 5.0.2 (in 2025.1.3) | major behind (supported) | com.netflix.eureka:eureka-client 1.9.2 → 2.0.6 (Jakarta). OSV: nothing on eureka-client itself, but its transitives are vulnerable: **xstream 1.4.10: 35 advisories (2 CRITICAL CVE-2013-7285, CVE-2019-10173; 20 HIGH; fixed 1.4.21)**, jettison 1.3.7 (4 HIGH, fixed 1.5.4; latest 1.5.7), gson 2.8.5 (CVE-2022-25647 HIGH), guava 16.0 (2 MOD/1 LOW), httpclient 4.5.5 (1 MOD), commons-lang 2.6 (MOD), commons-configuration 1.8 (LOW), netty 4.1.25.Final (codec-http 23 advisories incl. **CVE-2019-20444 CRITICAL**; codec CVE-2021-37136/37137 HIGH; latest 4.2.19.Final). It also pulls in **Ribbon 2.2.5, hystrix-core 1.5.12, Archaius 0.7.6, rxjava 1.3.8** (Netflix OSS in maintenance mode; removed from Spring Cloud 2020.0, tool-verified). Replace Ribbon with Spring Cloud LoadBalancer, which Eureka client 3.x+ uses automatically |
| org.springframework.cloud:spring-cloud-starter-sleuth | declared; Cloud BOM | 2.0.0.RELEASE | 3.1.11 (final, 2024-02) | major behind, **EOL** (removed from train 2022.0, tool-verified) | brave 5.1.0 (latest 6.3.1); OSV: none on sleuth/brave. Replacement: **Micrometer Tracing** (`micrometer-tracing-bridge-brave` or `-otel`, latest 1.7.1; Boot 4.0.8 manages 1.6.7) + actuator. `spring.sleuth.*` → `management.tracing.*`; the propagation and log-correlation format changes |
| org.springframework.boot:spring-boot-starter-test | declared (test); Boot BOM | 2.0.3.RELEASE | 4.1.1 | major behind, vulnerable (test-only) | junit 4.12 (CVE-2020-15250 MODERATE; latest 4.13.2); assertj-core 3.9.1 (**CVE-2026-24400 HIGH**, fixed 3.27.7); xmlunit-core 2.5.1 (CVE-2024-31573 LOW); mockito 2.15.0. Boot 2.4+ dropped JUnit 4 (vintage) from the starter, and Boot 4.0.8 manages JUnit Jupiter 6.0.3, so tests need migrating to JUnit 5/6 |
| de.flapdoodle.embed:de.flapdoodle.embed.mongo | declared (test), **pinned in module** | 1.50.3 | 5.0.0 (Spring integration: `de.flapdoodle.embed.mongo.spring3x` / `spring4x` 4.33.0) | major behind, vulnerable transitives (test-only) | The pin is **older than the version Boot 2.0.3 itself manages (2.0.3)** (tool-verified in the Boot 2.0.3 BOM). OSV: nothing on the artifact. Transitives: commons-compress 1.10 (4 HIGH, e.g. CVE-2021-35515/35516/35517/36090, fixed 1.21), commons-io 2.4 (**CVE-2024-47554 HIGH**, CVE-2021-29425 MOD), commons-lang3 3.7 (CVE-2025-48924 MOD). Breaking change: Boot 3.0 removed `EmbeddedMongoAutoConfiguration` (imported in `UserRepositoryTest`). Use flapdoodle `spring3x/spring4x` or, preferably, Testcontainers MongoDB |
| com.jayway.jsonpath:json-path | declared (test), **pinned in module** | 2.2.0 | 3.0.0 (Boot 4.0.8 manages 2.10.0) | major behind, **vulnerable** (test-only) | The pin is **older than Boot 2.0.3's managed 2.4.0** (tool-verified). OSV: CVE-2023-51074 MODERATE (fixed 2.9.0); json-smart 2.2.1: **CVE-2023-1370 HIGH** (fixed 2.4.9), CVE-2021-27568 MOD (latest 2.6.0). Remove the pin and let the Boot BOM manage it (it already arrives through spring-boot-starter-test) |

### Build plugins
| Dependency/plugin | Declared/inherited from | Current | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.boot:spring-boot-maven-plugin | declared; version from Boot parent pluginManagement | 2.0.3.RELEASE | 4.1.1 | major behind | `<configuration><finalName>` *(doc)* was removed as a writable repackage parameter in Boot 3.0; use `<build><finalName>` instead |
| org.jacoco:jacoco-maven-plugin | declared, **pinned in module** | 0.7.6.201602180812 | 0.8.15 | major behind | versions plugin: 0.8.15 needs Maven 3.0+. OSV: none. *(doc)* 0.7.x cannot instrument Java 9+ class files, and Java 17/21 need roughly 0.8.7/0.8.11+. Required for the Java 17 baseline |
| org.apache.maven.plugins:maven-compiler-plugin | Boot 2.0.3 parent | 3.7.0 | 3.16.0 | minor behind | Use `maven.compiler.release=17` after the upgrade |
| org.apache.maven.plugins:maven-surefire-plugin (and failsafe) | Boot 2.0.3 parent | 2.21.0 | 3.6.0 | major behind | 2.21 has limited JUnit Platform support; managed automatically by the newer Boot parent |
| maven-jar / resources / install / deploy / clean / site plugins | Boot 2.0.3 parent | 3.0.2 / 3.0.1 / 2.5.2 / 2.8.2 / 3.0.0 / 3.6 | 3.5.1 / 3.5.0 / 3.2.0 / 3.2.0 / 3.5.0 / 3.22.0 | minor/major behind | Low risk; they move with the parent. Note: `display-plugin-updates` printed "All plugins ... using the latest versions" for parent-managed plugins, so these latest versions were read from maven-metadata instead |
| (build) Java | root `java.version` | 1.8 | 17/21/25 LTS | **EOL for target stack** | Boot 3/4 and Spring Framework 6/7 need **Java 17+** (endoflife.date: Boot 4.0 supports 17–25). *(doc)* Public OpenJDK 8 updates continue from some vendors, but the current Spring stack no longer supports it |

#### Layer findings

### Most important risks
1. **The authorization server is built on the EOL Spring Security OAuth 2.2.1 project with known critical CVEs.** It resolves spring-security-oauth2 2.2.1.RELEASE with CVE-2018-1260 (CRITICAL RCE) and CVE-2018-15758 (HIGH authorization bypass), and pulls in abandoned Jackson 1.x (`jackson-mapper-asl` CVE-2019-10202 CRITICAL, no fix). The final 2.5.2 release clears the OSV hits, but the project is EOL and `spring-cloud-starter-oauth2` / `spring-cloud-security` were dropped from the release train in 2020.0. This is the token issuer for the whole system.
2. **Spring Security 5.0.6 has several CRITICAL authorization-bypass CVEs** (CVE-2022-22978, CVE-2024-38821, CVE-2026-22732), plus CVE-2025-22228 against the BCrypt encoder this service uses. Client secrets are stored with `NoOpPasswordEncoder` (plain text).
3. **The whole platform stack is EOL**: Boot 2.0.3 (EOL 2019), Spring Framework 5.0 (EOL 2020), Finchley, Tomcat 8.5 (51 OSV advisories, 9 CRITICAL), jackson-databind 2.9.6 (66 advisories, 19 CRITICAL), snakeyaml 1.19 (CVE-2022-1471), logback 1.2.3, and Spring Data MongoDB 2.0.8 (CVE-2022-22980 CRITICAL SpEL injection).
4. **Eureka client drags in vulnerable legacy Netflix transitives**: xstream 1.4.10 (2 CRITICAL, 20 HIGH), jettison, netty 4.1.25 (CVE-2019-20444 CRITICAL), and Ribbon/Hystrix/Archaius, which this service does not need.
5. **Module-level pins have gone stale**: `de.flapdoodle.embed.mongo` 1.50.3 and `json-path` 2.2.0 are older than what even Boot 2.0.3 manages (2.0.3 / 2.4.0), and json-smart 2.2.1 carries CVE-2023-1370 HIGH. jacoco 0.7.6 blocks any move beyond Java 8.

### Breaking-change concerns for upgrading this layer
- **OAuth2 rewrite (largest item).** `@EnableAuthorizationServer`, `@EnableResourceServer` and `InMemoryTokenStore` have no drop-in successor.
  - Move to Spring Authorization Server, now `spring-security-oauth2-authorization-server` in Spring Security 7 with `spring-boot-starter-oauth2-authorization-server`: `RegisteredClientRepository`, `JWKSource` and JWT tokens.
  - Move all downstream services to `spring-boot-starter-oauth2-resource-server` (JWT validation through `issuer-uri`/`jwk-set-uri`) instead of the `user-info-uri` (`/uaa/users/current`) callback.
  - **The UI's `grant_type=password` flow is not supported by Spring Authorization Server** (OAuth 2.1 drops the password grant). The gateway/UI login has to move to authorization code + PKCE (e.g. gateway as the OAuth2 client / BFF). This crosses layers, so coordinate with the gateway session.
  - `client_credentials` for account-service and notification-service maps directly.
  - Token endpoint paths change (`/oauth/token` → `/oauth2/token`), and the `/uaa` context path should be reviewed.
- **Spring Security 6/7 API.** Remove `WebSecurityConfigurerAdapter` (use `SecurityFilterChain` beans), replace `@EnableGlobalMethodSecurity` with `@EnableMethodSecurity`, use `authorizeHttpRequests`/`requestMatchers`, use the lambda DSL (required in 7), and move to `DelegatingPasswordEncoder` with migration of the stored client secrets.
- **Boot 3/4 baseline.** Java 17+; `javax.*` → `jakarta.*` (validation, servlet, annotation); Jackson 3 default in Boot 4 (`tools.jackson` packages; `com.fasterxml.jackson.annotation` stays); property renames (use `spring-boot-properties-migrator` temporarily).
- **Spring Cloud.** `bootstrap.yml` → `spring.config.import=configserver:...` (or add `spring-cloud-starter-bootstrap`). Sleuth → Micrometer Tracing (`management.tracing.*`, new log MDC keys). Ribbon/Hystrix/Archaius disappear; Spring Cloud LoadBalancer arrives through the Eureka client.
- **MongoDB.** Driver 3.6 → 5.x: the legacy API is removed and codec/UUID representation defaults changed *(doc: Spring Data 3 requires an explicit `uuidRepresentation`; verify)*. The server image is `mongo:3` (EOL), so check driver/server compatibility.
- **Tests.** Boot 3 removed `EmbeddedMongoAutoConfiguration` (used by `UserRepositoryTest`); use Testcontainers or flapdoodle `spring3x/spring4x`. Migrate JUnit 4 to JUnit Jupiter 5/6 (Boot 4.0.8 manages 6.0.3), and move jacoco to ≥0.8.12 for Java 17–21+ *(doc)*.
- **Build.** spring-boot-maven-plugin `<configuration><finalName>` → `<build><finalName>` *(doc)*.

### Prioritized recommendations for this layer
1. **(Immediate stop-gap, without the platform upgrade)** If the full migration cannot start right away, move up within the 2.x line, e.g. Boot 2.3.12 + Hoxton.SR12 (still Java 8 compatible), so spring-security-oauth2 lands on 2.5.x (2.5.2.RELEASE clears CVE-2018-1260/15758/2019-3778 per OSV; *exact Boot/autoconfigure/oauth2 version alignment not verified*). Also override `jackson-databind`, `snakeyaml`, `tomcat`, `logback` and `xstream` where binary-compatible. Everything in the 2.x line remains EOL, so treat this only as risk reduction.
2. **Replace the legacy authorization server with Spring Authorization Server** (Spring Security 7.x on Boot 4.0.x) and switch downstream services to `oauth2-resource-server` with JWT. Replace the UI's password grant with authorization code + PKCE through the gateway. Store client secrets with `{bcrypt}`. Do this together with the gateway and the other resource-server layers.
3. **Upgrade the platform to Boot 4.0.8 + Spring Cloud 2025.1.3 on Java 17 or 21** (Boot 3.5 has left OSS support; Boot 4.1 has no GA Cloud train yet). Migrate javax → jakarta, the Spring Security 6/7 DSL, bootstrap → `spring.config.import`, and Sleuth → Micrometer Tracing (`micrometer-tracing-bridge-brave` or `-otel`).
4. **Override `tomcat.version` to ≥11.0.25** (latest 11.0.27) on top of Boot 4.0.8, because OSV still flags the managed 11.0.24.
5. **Upgrade the data layer**: spring-data-mongodb 5.x / driver 5.x (managed by Boot), set `uuidRepresentation` explicitly, and upgrade the `mongo:3` server image to a supported MongoDB release (8.x/9.0 per endoflife.date) after checking driver compatibility.
6. **Clean up test and build pins**: remove the `json-path` pin (Boot-managed), replace flapdoodle 1.50.3 with Testcontainers MongoDB (or `de.flapdoodle.embed.mongo.spring4x` 4.33.0), move tests to JUnit 5/6, set jacoco to 0.8.15, and add `maven-enforcer-plugin` with `requireMavenVersion` (the versions plugin flagged that it is missing).
7. **Drop unneeded transitives**: once on Spring Cloud 2020.0+, Ribbon/Hystrix/Archaius and the old xstream/jettison chain go away. Verify with `dependency:tree` that `jackson-mapper-asl`, `bcprov-jdk15on` and `spring-security-jwt` are gone after the OAuth2 migration.

#### Could not verify / caveats
- OSV results reflect the OSV database on 2026-10-13. Severity is GitHub-reviewed severity, not CVSS scores. Exploitability in this deployment was not tested; e.g. Spring4Shell needs JDK 9+ and a WAR, and Ghostcat needs AJP, which is off by default.
- EOL dates for Finchley (2019-12-31), Tomcat 8.5 (2024-03-31) and Spring Security OAuth (2022) come from documentation and knowledge, not tool output. endoflife.date did not return Finchley/Java/Eureka entries in the form queried.
- Not verified: whether newer MongoDB Java drivers still support the `mongo:3` server, the exact jacoco minimum per JDK, and the removal of the spring-boot-maven-plugin `finalName` parameter.
- The tests were not run and no upgrade was attempted (analysis-only constraint).

</details>

<details>
<summary><b>business layer: full report</b></summary>

### Dependency freshness & security analysis — Business microservices layer

**Repository:** `axelisr/DevinWorkshop` (PiggyMetrics), branch `master`. **Modules:** `account-service`, `statistics-service`, `notification-service` (plus the root `pom.xml` as it affects them). **Analysis date:** 2026-10-13. **Scope:** analysis only; no files were modified.

#### Method & data sources

- **Resolved versions** come from tool output: `mvn -B dependency:tree -pl <module>` (JDK 8, Google Maven Central mirror).
- **Latest versions** come from tool output: `versions-maven-plugin:2.16.2` `display-dependency-updates` / `display-plugin-updates` / `display-parent-updates` (pre-releases filtered), cross-checked against `maven-metadata.xml` on the mirror for transitive and replacement artifacts. Line-specific latests (e.g. 2.7.x, 5.3.x, 4.1.x) were also read from `maven-metadata.xml`.
- **CVEs** come from tool output: OSV `querybatch` API for every resolved artifact (221 unique coordinates across the three trees) plus the plugins. Severity is the GHSA severity in OSV `database_specific.severity`. "Fixed in" is the highest fixed version OSV reports across that artifact's advisories.
- **EOL dates:** Spring Boot / Spring Framework come from tool output (endoflife.date API). Netflix OSS maintenance status, the Spring Security OAuth EOL, Spring Cloud Finchley EOL, Tomcat 8.5 EOL and the JaCoCo JDK support matrix come from documentation and knowledge (not fetched this session). They're marked "docs".
- **Spring Cloud removals** (Hystrix/Ribbon/Zuul/Turbine/Archaius in 2020.0, spring-cloud-security in 2020.0, Sleuth in 2022.0) were verified by diffing the `spring-cloud-dependencies` BOM poms (2020.0.0, 2022.0.0, 2025.1.3) and `spring-cloud-netflix-dependencies` 2.2.10 vs 3.0.0.

### Vulnerability summary (OSV, resolved trees)

| Module | Resolved artifacts | Artifacts with advisories | Unique advisories (all scopes) | Unique advisories (non-test) | By severity (all scopes) |
|---|---|---|---|---|---|
| account-service | 214 | 54 | 323 | 309 | CRITICAL 41, HIGH 144, MODERATE 121, LOW 17 |
| statistics-service | 208 | 54 | 323 | 309 | CRITICAL 41, HIGH 144, MODERATE 121, LOW 17 |
| notification-service | 212 | 54 | 323 | 309 | CRITICAL 41, HIGH 144, MODERATE 121, LOW 17 |

#### Parent / BOM (root `pom.xml`, inherited by all three modules)

| Dependency / plugin (groupId:artifactId) | Declared / inherited from | Current (resolved) | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.boot:spring-boot-starter-parent | root pom `<parent>` | 2.0.3.RELEASE | 4.1.1 (2.x: 2.7.18; 3.x: 3.5.16) | major behind, EOL, vulnerable | Boot 2.0 OSS EOL 2019-03-01, commercial support ended 2020-06-01 (endoflife.date). It governs every Spring/Tomcat/Jackson/Mongo/Rabbit version below. Boot 3 needs Java 17 + jakarta.*; Boot 4 needs Spring Framework 7. |
| org.springframework.cloud:spring-cloud-dependencies | root pom `<dependencyManagement>` import | Finchley.RELEASE | 2025.1.3 (Boot 4.x); 2025.0.3 (Boot 3.5); 2021.0.9 (last Boot 2.x-compatible train) | major behind, EOL | Finchley never got its SR updates here (it's still on .RELEASE). The last train with Netflix Hystrix/Ribbon was Hoxton.SR12; 2020.0 dropped them (spring-cloud-netflix-dependencies 3.0.0 has no hystrix/ribbon/zuul/turbine artifacts — verified). 2020.0+ also has no spring-cloud-security. 2022.0+ has no spring-cloud-sleuth (verified from BOM poms). The EOL date for Finchley comes from docs and wasn't verified from tool output. |
| java.version (maven-compiler source/target) | root pom property | 1.8 | 17 / 21 / 25 LTS | EOL for Boot 3+ | Java 8 caps you at Boot 2.7 / Spring Cloud 2021.0. Boot 3.x and 4.x need Java 17+. |

#### Module: `account-service`

Declared: oauth2, security, config, web, openfeign, eureka-client, sleuth, data-mongodb, actuator, bus-amqp, **netflix-hystrix**, hystrix-stream, test, flapdoodle 1.50.3, json-path 2.2.0. Plugins: spring-boot-maven-plugin, jacoco 0.7.6.

| Dependency / plugin (groupId:artifactId) | Declared / inherited from | Current (resolved) | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.cloud:spring-cloud-starter-oauth2 | module pom (version from Cloud BOM) | 2.0.0.RELEASE | 2.2.5.RELEASE (final; Apr 2021) | deprecated, EOL, vulnerable (transitive) | Pulls spring-security-oauth2 **2.2.1.RELEASE** (CVE-2018-1260 CRITICAL, CVE-2018-15758 HIGH, CVE-2019-3778 MODERATE; fixed in 2.3.4+, last release 2.5.2), spring-security-oauth2-autoconfigure 2.0.0 (last 2.6.8) and spring-security-jwt 1.0.9 (last 1.1.1, deprecated). Spring Security OAuth has been EOL since 2022 (docs). Code uses `@EnableResourceServer`, `ResourceServerConfigurerAdapter`, `ResourceServerTokenServices` (custom `CustomUserInfoTokenServices` → `user-info-uri`), `@EnableOAuth2Client`, `OAuth2RestTemplate` + `ClientCredentialsResourceDetails`. **Replacement:** `spring-boot-starter-oauth2-resource-server` (JWT/opaque introspection) + `spring-boot-starter-oauth2-client` (client_credentials via `OAuth2AuthorizedClientManager`), with Spring Authorization Server on the auth side. That's a rewrite, not a version bump. |
| org.springframework.boot:spring-boot-starter-security | module pom (Boot BOM) | 2.0.3.RELEASE (spring-security 5.0.6.RELEASE) | 4.1.1 (spring-security 7.1.1; 5.x line: 5.8.16) | major behind, EOL, vulnerable | spring-security-web 5.0.6: CVE-2022-22978 CRITICAL (regex authz bypass), CVE-2024-38821 CRITICAL, CVE-2026-22732 CRITICAL, CVE-2021-22112 HIGH. spring-security-core: CVE-2024-22257 HIGH. spring-security-crypto: CVE-2025-22228 HIGH. `@EnableGlobalMethodSecurity` was replaced by `@EnableMethodSecurity` (6.x). `WebSecurityConfigurerAdapter` was removed in 6.x. |
| org.springframework.cloud:spring-cloud-starter-config | module pom (Cloud BOM) | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | Spring Cloud 2020.0+ turns bootstrap.yml off by default. Use `spring.config.import=configserver:` or add `spring-cloud-starter-bootstrap`. Pulls spring-security-rsa 1.0.5 (last 1.1.5). |
| org.springframework.boot:spring-boot-starter-web | module pom (Boot BOM) | 2.0.3.RELEASE (spring-web/webmvc 5.0.7, tomcat-embed 8.5.31) | 4.1.1 | major behind, EOL, vulnerable | CVE-2022-22965 "Spring4Shell" CRITICAL (spring-beans/webmvc; fixed in 5.3.18 / 5.2.20). CVE-2016-1000027 CRITICAL (spring-web HttpInvoker). Also many webmvc/web HIGH CVEs (CVE-2024-38819, CVE-2024-22243/22259/22262, CVE-2020-5398). See the Tomcat row below. |
| org.springframework.cloud:spring-cloud-starter-openfeign | module pom (Cloud BOM) | 2.0.0.RELEASE (feign-core 9.5.1) | 5.0.3 (feign-core 13.17) | major behind, EOL | The Finchley integration routes Feign through Ribbon + Hystrix (`feign-hystrix`). In 2020.0+, Feign uses Spring Cloud LoadBalancer and Spring Cloud CircuitBreaker (`spring.cloud.openfeign.circuitbreaker.enabled`). The `feign.hystrix.enabled` property and the Hystrix-style `fallback` classes need migrating. Spring Cloud OpenFeign is now feature-complete and the team recommends `@HttpExchange` HTTP interfaces for new code (docs). |
| com.netflix.ribbon:ribbon* (transitive via openfeign → spring-cloud-starter-netflix-ribbon) | transitive (Cloud BOM) | 2.2.5 (spring-cloud-starter-netflix-ribbon 2.0.0.RELEASE) | 2.7.18 (Netflix); Spring starter last 2.2.10.RELEASE | deprecated, EOL | Maintenance mode, then removed in Spring Cloud 2020.0. **Replacement:** `spring-cloud-starter-loadbalancer` (5.0.3). Ribbon config (`<client>.ribbon.*`) has no 1:1 equivalent. Pulls rxnetty 0.4.9 + netty **4.1.25.Final** (codec-http: 23 OSV advisories incl. CVE-2019-20444 CRITICAL; netty-codec CVE-2021-37136/37137 HIGH; latest 4.1.139 / 4.2.19). |
| org.springframework.cloud:spring-cloud-starter-netflix-eureka-client | module pom (Cloud BOM) | 2.0.0.RELEASE (com.netflix.eureka:eureka-client 1.9.2) | 5.0.2 (eureka-client 2.0.6) | major behind, EOL, vulnerable (transitive) | Eureka client is still supported in current trains. In 4.x/5.x it uses Spring RestTemplate/WebClient/RestClient instead of Jersey 1. Current transitive deps: xstream **1.4.10** (35 advisories incl. CVE-2013-7285 & CVE-2019-10173 CRITICAL, many HIGH RCE CVE-2021-39139…39154; fixed 1.4.21), jettison 1.3.7 (CVE-2022-45685/45693/40150, CVE-2023-1436 HIGH; fixed 1.5.4), jersey 1.19.1, gson 2.8.5 (CVE-2022-25647 HIGH), httpclient 4.5.5 (CVE-2020-13956 MODERATE), commons-configuration 1.8 / archaius-core 0.7.6 (Archaius 1 is EOL). |
| org.springframework.cloud:spring-cloud-starter-sleuth | module pom (Cloud BOM) | 2.0.0.RELEASE (brave 5.1.0, zipkin-reporter 2.7.3) | 3.1.11 (final; Boot 2.x only) | deprecated, EOL | Sleuth was dropped from Spring Cloud 2022.0 (verified: not in the 2022.0.0 BOM). **Replacement:** Micrometer Tracing (`micrometer-tracing-bridge-brave` or `-otel`, 1.7.1) + `spring-boot-starter-actuator`. Log MDC keys change (`traceId`/`spanId` stay, but `X-B3-*` → W3C `traceparent` by default). |
| org.springframework.boot:spring-boot-starter-data-mongodb | module pom (Boot BOM) | 2.0.3.RELEASE (spring-data-mongodb 2.0.8, mongodb-driver 3.6.4) | 4.1.1 (spring-data-mongodb 5.1.1, mongodb-driver-sync 5.13.0) | major behind, EOL, vulnerable | spring-data-mongodb 2.0.8: CVE-2022-22980 CRITICAL (SpEL injection in `@Query`/repository methods; fixed in 3.3.5/3.4.1), CVE-2026-41717 HIGH. spring-data-commons 2.0.8: CVE-2026-41716 HIGH. The legacy `mongodb-driver` 3.x artifact (last 3.12.14) was replaced by `mongodb-driver-sync` 4.x/5.x. Boot 3 renamed `spring.data.mongodb.*` properties in parts (e.g. `spring.mongodb.*` in Boot 4; not verified in detail). |
| org.springframework.boot:spring-boot-starter-actuator | module pom (Boot BOM) | 2.0.3.RELEASE (micrometer-core 1.0.5) | 4.1.1 (micrometer 1.17.1) | major behind, EOL, vulnerable | CVE-2023-20873 CRITICAL (actuator on Cloud Foundry), CVE-2026-22733 HIGH, CVE-2023-34055 MODERATE. micrometer-core 1.0.5: CVE-2026-40984 HIGH. The `hystrix.stream` endpoint goes away with Hystrix. |
| org.springframework.cloud:spring-cloud-starter-bus-amqp | module pom (Cloud BOM) | 2.0.0.RELEASE (spring-cloud-stream 2.0.0, spring-rabbit 2.0.4, amqp-client 5.1.2) | 5.0.3 (spring-rabbit 4.1.1, amqp-client 5.37.0) | major behind, EOL, vulnerable (transitive) | amqp-client 5.1.2: 12 advisories (4 HIGH, e.g. CVE-2026-69219, CVE-2026-63337; fixed 5.36.1+). spring-amqp 2.0.4: CVE-2018-11087 and others (MODERATE). spring-retry 1.2.2: CVE-2026-41710 MODERATE. Spring Cloud Stream 3+ dropped the annotation model (`@EnableBinding`/`@StreamListener`) in favour of functional bindings. Bus itself is mostly config-driven. |
| org.springframework.cloud:spring-cloud-netflix-hystrix-stream | module pom (Cloud BOM) | 2.0.0.RELEASE (hystrix-core 1.5.12 via feign-hystrix) | 2.2.10.RELEASE (final) | deprecated, EOL | Publishes Hystrix metrics over Rabbit to Turbine. Netflix put Hystrix in maintenance mode in 2018 (last release 1.5.18, 2018). Removed from Spring Cloud 2020.0. **Replacement:** Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` (5.0.3), with metrics through Micrometer → Prometheus/Grafana instead of Turbine/Hystrix Dashboard. Pulls kryo-shaded 3.0.3 and spring-tuple 1.0.0 (both abandoned). |
| org.springframework.boot:spring-boot-starter-test | module pom (Boot BOM), test | 2.0.3.RELEASE (junit 4.12, mockito 2.15.0, assertj 3.9.1) | 4.1.1 (JUnit Jupiter; mockito 5.24.0) | major behind, vulnerable (test) | junit 4.12: CVE-2020-15250 MODERATE. assertj-core 3.9.1: CVE-2026-24400 HIGH. xmlunit 2.5.1: CVE-2024-31573 LOW. Boot 2.4+ dropped JUnit 4 vintage from the starter, so `@RunWith(SpringRunner.class)` tests need porting to JUnit 5. |
| de.flapdoodle.embed:de.flapdoodle.embed.mongo | **pinned in module pom** 1.50.3, test | 1.50.3 (embed.process 1.50.2) | 5.0.0 (Aug 2026); 4.33.0; Boot 3 integration `de.flapdoodle.embed.mongo.spring3x` 4.33.0 | major behind, vulnerable (test, transitive) | The pin *downgrades* the Boot 2.0.3 BOM-managed version (2.0.3). Boot 3 removed embedded-Mongo auto-configuration entirely, so you need flapdoodle's `spring3x`/`spring4x` module or Testcontainers MongoDB (recommended). 1.50.x can only download old MongoDB binaries. Transitive commons-compress 1.10 (CVE-2021-35515/35516/35517/36090 HIGH) and commons-io 2.4 (CVE-2024-47554 HIGH). Java baseline of 4.x/5.x not verified. |
| com.jayway.jsonpath:json-path | **pinned in module pom** 2.2.0, test | 2.2.0 (json-smart 2.2.1) | 3.0.0 (Feb 2026); 2.x: 2.10.0 | major behind, vulnerable (test) | The pin *downgrades* the Boot 2.0.3 BOM-managed version (2.4.0). CVE-2023-51074 MODERATE (fixed 2.9.0). json-smart 2.2.1: CVE-2023-1370 HIGH, CVE-2021-27568 MODERATE (fixed 2.4.9). Simplest fix: delete the `<version>` and let the Boot BOM manage it. 3.0.0 breaking changes not verified. |
| org.springframework.boot:spring-boot-maven-plugin | module pom (version from Boot parent pluginManagement) | 2.0.3.RELEASE | 4.1.1 | major behind | Boot 2.0 jar layout (layered jars / buildpack images came in 2.3+). `<configuration><finalName>` is an editable `repackage` parameter in 2.0.3 (verified from the plugin descriptor). Newer plugin versions make it read-only (docs; exact version not verified), so move it to `<build><finalName>` when upgrading. Plugin major must match the Boot version. |
| org.jacoco:jacoco-maven-plugin | **pinned in module pom** | 0.7.6.201602180812 (2016) | 0.8.15 | major behind | 0.7.x can't instrument classes newer than Java 8 bytecode (Java 11 support from 0.8.2, Java 17 from 0.8.7, Java 21 from 0.8.11 — from JaCoCo changelog, docs). So it blocks any JDK upgrade. versions-plugin also offers 0.6.3 under a 'Maven 2.1.0' heading, but that's an older-requirement artifact, not a real upgrade. |
| maven-surefire-plugin / maven-compiler-plugin / maven-jar-plugin / maven-resources-plugin / maven-install / maven-deploy / maven-clean | inherited from spring-boot-starter-parent pluginManagement | 2.21.0 / 3.7.0 / 3.0.2 / 3.0.1 / 2.5.2 / 2.8.2 / 3.0.0 | 3.6.0 / 3.16.0 / 3.5.1 / 3.5.0 / 3.2.0 / 3.2.0 / 3.5.0 | major/minor behind | These move with the Boot parent. Surefire 2.21 needs 2.22+ for JUnit 5 platform. Versions plugin reports 'All plugins with a version specified are using the latest versions' only because the plugin-updates goal compares against Maven-requirement buckets; latest values were read from maven-metadata.xml. |
| org.springframework.cloud:spring-cloud-starter-netflix-hystrix | module pom (Cloud BOM) | 2.0.0.RELEASE (hystrix-core/javanica 1.5.12) | 2.2.10.RELEASE (final) | deprecated, EOL | Only account-service declares it. Used through `@EnableCircuitBreaker` and Feign `fallback = StatisticsServiceClientFallback.class` with `feign.hystrix.enabled: true` (config/shared/account-service.yml). The global `hystrix.command.default...timeoutInMilliseconds` lives in config/shared/application.yml. **Replacement:** `spring-cloud-starter-circuitbreaker-resilience4j` + `spring.cloud.openfeign.circuitbreaker.enabled=true`. `@EnableCircuitBreaker` was removed. Timeout config moves to `resilience4j.timelimiter.*`. |
| com.google.guava:guava (transitive) | transitive via hystrix-javanica | 15.0 | 33.7.2-jre | major behind, vulnerable | CVE-2018-10237 MODERATE, CVE-2023-2976 MODERATE, CVE-2020-8908 LOW (fixed 32.0.0). Not used directly by account-service code. |

#### Module: `statistics-service`

Declared: security, config, oauth2, web, openfeign, eureka-client, sleuth, data-mongodb, actuator, bus-amqp, hystrix-stream, **guava 19.0**, test, flapdoodle 1.50.3, json-path 2.2.0. Plugins: spring-boot-maven-plugin, jacoco 0.7.6. (No `spring-cloud-starter-netflix-hystrix`; Hystrix arrives transitively via feign-hystrix.)

| Dependency / plugin (groupId:artifactId) | Declared / inherited from | Current (resolved) | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.cloud:spring-cloud-starter-oauth2 | module pom (version from Cloud BOM) | 2.0.0.RELEASE | 2.2.5.RELEASE (final; Apr 2021) | deprecated, EOL, vulnerable (transitive) | Pulls spring-security-oauth2 **2.2.1.RELEASE** (CVE-2018-1260 CRITICAL, CVE-2018-15758 HIGH, CVE-2019-3778 MODERATE; fixed in 2.3.4+, last release 2.5.2), spring-security-oauth2-autoconfigure 2.0.0 (last 2.6.8) and spring-security-jwt 1.0.9 (last 1.1.1, deprecated). Spring Security OAuth has been EOL since 2022 (docs). Code uses `@EnableResourceServer`, `ResourceServerConfigurerAdapter`, `ResourceServerTokenServices` (custom `CustomUserInfoTokenServices` → `user-info-uri`), `@EnableOAuth2Client`, `OAuth2RestTemplate` + `ClientCredentialsResourceDetails`. **Replacement:** `spring-boot-starter-oauth2-resource-server` (JWT/opaque introspection) + `spring-boot-starter-oauth2-client` (client_credentials via `OAuth2AuthorizedClientManager`), with Spring Authorization Server on the auth side. That's a rewrite, not a version bump. |
| org.springframework.boot:spring-boot-starter-security | module pom (Boot BOM) | 2.0.3.RELEASE (spring-security 5.0.6.RELEASE) | 4.1.1 (spring-security 7.1.1; 5.x line: 5.8.16) | major behind, EOL, vulnerable | spring-security-web 5.0.6: CVE-2022-22978 CRITICAL (regex authz bypass), CVE-2024-38821 CRITICAL, CVE-2026-22732 CRITICAL, CVE-2021-22112 HIGH. spring-security-core: CVE-2024-22257 HIGH. spring-security-crypto: CVE-2025-22228 HIGH. `@EnableGlobalMethodSecurity` was replaced by `@EnableMethodSecurity` (6.x). `WebSecurityConfigurerAdapter` was removed in 6.x. |
| org.springframework.cloud:spring-cloud-starter-config | module pom (Cloud BOM) | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | Spring Cloud 2020.0+ turns bootstrap.yml off by default. Use `spring.config.import=configserver:` or add `spring-cloud-starter-bootstrap`. Pulls spring-security-rsa 1.0.5 (last 1.1.5). |
| org.springframework.boot:spring-boot-starter-web | module pom (Boot BOM) | 2.0.3.RELEASE (spring-web/webmvc 5.0.7, tomcat-embed 8.5.31) | 4.1.1 | major behind, EOL, vulnerable | CVE-2022-22965 "Spring4Shell" CRITICAL (spring-beans/webmvc; fixed in 5.3.18 / 5.2.20). CVE-2016-1000027 CRITICAL (spring-web HttpInvoker). Also many webmvc/web HIGH CVEs (CVE-2024-38819, CVE-2024-22243/22259/22262, CVE-2020-5398). See the Tomcat row below. |
| org.springframework.cloud:spring-cloud-starter-openfeign | module pom (Cloud BOM) | 2.0.0.RELEASE (feign-core 9.5.1) | 5.0.3 (feign-core 13.17) | major behind, EOL | The Finchley integration routes Feign through Ribbon + Hystrix (`feign-hystrix`). In 2020.0+, Feign uses Spring Cloud LoadBalancer and Spring Cloud CircuitBreaker (`spring.cloud.openfeign.circuitbreaker.enabled`). The `feign.hystrix.enabled` property and the Hystrix-style `fallback` classes need migrating. Spring Cloud OpenFeign is now feature-complete and the team recommends `@HttpExchange` HTTP interfaces for new code (docs). |
| com.netflix.ribbon:ribbon* (transitive via openfeign → spring-cloud-starter-netflix-ribbon) | transitive (Cloud BOM) | 2.2.5 (spring-cloud-starter-netflix-ribbon 2.0.0.RELEASE) | 2.7.18 (Netflix); Spring starter last 2.2.10.RELEASE | deprecated, EOL | Maintenance mode, then removed in Spring Cloud 2020.0. **Replacement:** `spring-cloud-starter-loadbalancer` (5.0.3). Ribbon config (`<client>.ribbon.*`) has no 1:1 equivalent. Pulls rxnetty 0.4.9 + netty **4.1.25.Final** (codec-http: 23 OSV advisories incl. CVE-2019-20444 CRITICAL; netty-codec CVE-2021-37136/37137 HIGH; latest 4.1.139 / 4.2.19). |
| org.springframework.cloud:spring-cloud-starter-netflix-eureka-client | module pom (Cloud BOM) | 2.0.0.RELEASE (com.netflix.eureka:eureka-client 1.9.2) | 5.0.2 (eureka-client 2.0.6) | major behind, EOL, vulnerable (transitive) | Eureka client is still supported in current trains. In 4.x/5.x it uses Spring RestTemplate/WebClient/RestClient instead of Jersey 1. Current transitive deps: xstream **1.4.10** (35 advisories incl. CVE-2013-7285 & CVE-2019-10173 CRITICAL, many HIGH RCE CVE-2021-39139…39154; fixed 1.4.21), jettison 1.3.7 (CVE-2022-45685/45693/40150, CVE-2023-1436 HIGH; fixed 1.5.4), jersey 1.19.1, gson 2.8.5 (CVE-2022-25647 HIGH), httpclient 4.5.5 (CVE-2020-13956 MODERATE), commons-configuration 1.8 / archaius-core 0.7.6 (Archaius 1 is EOL). |
| org.springframework.cloud:spring-cloud-starter-sleuth | module pom (Cloud BOM) | 2.0.0.RELEASE (brave 5.1.0, zipkin-reporter 2.7.3) | 3.1.11 (final; Boot 2.x only) | deprecated, EOL | Sleuth was dropped from Spring Cloud 2022.0 (verified: not in the 2022.0.0 BOM). **Replacement:** Micrometer Tracing (`micrometer-tracing-bridge-brave` or `-otel`, 1.7.1) + `spring-boot-starter-actuator`. Log MDC keys change (`traceId`/`spanId` stay, but `X-B3-*` → W3C `traceparent` by default). |
| org.springframework.boot:spring-boot-starter-data-mongodb | module pom (Boot BOM) | 2.0.3.RELEASE (spring-data-mongodb 2.0.8, mongodb-driver 3.6.4) | 4.1.1 (spring-data-mongodb 5.1.1, mongodb-driver-sync 5.13.0) | major behind, EOL, vulnerable | spring-data-mongodb 2.0.8: CVE-2022-22980 CRITICAL (SpEL injection in `@Query`/repository methods; fixed in 3.3.5/3.4.1), CVE-2026-41717 HIGH. spring-data-commons 2.0.8: CVE-2026-41716 HIGH. The legacy `mongodb-driver` 3.x artifact (last 3.12.14) was replaced by `mongodb-driver-sync` 4.x/5.x. Boot 3 renamed `spring.data.mongodb.*` properties in parts (e.g. `spring.mongodb.*` in Boot 4; not verified in detail). |
| org.springframework.boot:spring-boot-starter-actuator | module pom (Boot BOM) | 2.0.3.RELEASE (micrometer-core 1.0.5) | 4.1.1 (micrometer 1.17.1) | major behind, EOL, vulnerable | CVE-2023-20873 CRITICAL (actuator on Cloud Foundry), CVE-2026-22733 HIGH, CVE-2023-34055 MODERATE. micrometer-core 1.0.5: CVE-2026-40984 HIGH. The `hystrix.stream` endpoint goes away with Hystrix. |
| org.springframework.cloud:spring-cloud-starter-bus-amqp | module pom (Cloud BOM) | 2.0.0.RELEASE (spring-cloud-stream 2.0.0, spring-rabbit 2.0.4, amqp-client 5.1.2) | 5.0.3 (spring-rabbit 4.1.1, amqp-client 5.37.0) | major behind, EOL, vulnerable (transitive) | amqp-client 5.1.2: 12 advisories (4 HIGH, e.g. CVE-2026-69219, CVE-2026-63337; fixed 5.36.1+). spring-amqp 2.0.4: CVE-2018-11087 and others (MODERATE). spring-retry 1.2.2: CVE-2026-41710 MODERATE. Spring Cloud Stream 3+ dropped the annotation model (`@EnableBinding`/`@StreamListener`) in favour of functional bindings. Bus itself is mostly config-driven. |
| org.springframework.cloud:spring-cloud-netflix-hystrix-stream | module pom (Cloud BOM) | 2.0.0.RELEASE (hystrix-core 1.5.12 via feign-hystrix) | 2.2.10.RELEASE (final) | deprecated, EOL | Publishes Hystrix metrics over Rabbit to Turbine. Netflix put Hystrix in maintenance mode in 2018 (last release 1.5.18, 2018). Removed from Spring Cloud 2020.0. **Replacement:** Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` (5.0.3), with metrics through Micrometer → Prometheus/Grafana instead of Turbine/Hystrix Dashboard. Pulls kryo-shaded 3.0.3 and spring-tuple 1.0.0 (both abandoned). |
| org.springframework.boot:spring-boot-starter-test | module pom (Boot BOM), test | 2.0.3.RELEASE (junit 4.12, mockito 2.15.0, assertj 3.9.1) | 4.1.1 (JUnit Jupiter; mockito 5.24.0) | major behind, vulnerable (test) | junit 4.12: CVE-2020-15250 MODERATE. assertj-core 3.9.1: CVE-2026-24400 HIGH. xmlunit 2.5.1: CVE-2024-31573 LOW. Boot 2.4+ dropped JUnit 4 vintage from the starter, so `@RunWith(SpringRunner.class)` tests need porting to JUnit 5. |
| de.flapdoodle.embed:de.flapdoodle.embed.mongo | **pinned in module pom** 1.50.3, test | 1.50.3 (embed.process 1.50.2) | 5.0.0 (Aug 2026); 4.33.0; Boot 3 integration `de.flapdoodle.embed.mongo.spring3x` 4.33.0 | major behind, vulnerable (test, transitive) | The pin *downgrades* the Boot 2.0.3 BOM-managed version (2.0.3). Boot 3 removed embedded-Mongo auto-configuration entirely, so you need flapdoodle's `spring3x`/`spring4x` module or Testcontainers MongoDB (recommended). 1.50.x can only download old MongoDB binaries. Transitive commons-compress 1.10 (CVE-2021-35515/35516/35517/36090 HIGH) and commons-io 2.4 (CVE-2024-47554 HIGH). Java baseline of 4.x/5.x not verified. |
| com.jayway.jsonpath:json-path | **pinned in module pom** 2.2.0, test | 2.2.0 (json-smart 2.2.1) | 3.0.0 (Feb 2026); 2.x: 2.10.0 | major behind, vulnerable (test) | The pin *downgrades* the Boot 2.0.3 BOM-managed version (2.4.0). CVE-2023-51074 MODERATE (fixed 2.9.0). json-smart 2.2.1: CVE-2023-1370 HIGH, CVE-2021-27568 MODERATE (fixed 2.4.9). Simplest fix: delete the `<version>` and let the Boot BOM manage it. 3.0.0 breaking changes not verified. |
| org.springframework.boot:spring-boot-maven-plugin | module pom (version from Boot parent pluginManagement) | 2.0.3.RELEASE | 4.1.1 | major behind | Boot 2.0 jar layout (layered jars / buildpack images came in 2.3+). `<configuration><finalName>` is an editable `repackage` parameter in 2.0.3 (verified from the plugin descriptor). Newer plugin versions make it read-only (docs; exact version not verified), so move it to `<build><finalName>` when upgrading. Plugin major must match the Boot version. |
| org.jacoco:jacoco-maven-plugin | **pinned in module pom** | 0.7.6.201602180812 (2016) | 0.8.15 | major behind | 0.7.x can't instrument classes newer than Java 8 bytecode (Java 11 support from 0.8.2, Java 17 from 0.8.7, Java 21 from 0.8.11 — from JaCoCo changelog, docs). So it blocks any JDK upgrade. versions-plugin also offers 0.6.3 under a 'Maven 2.1.0' heading, but that's an older-requirement artifact, not a real upgrade. |
| maven-surefire-plugin / maven-compiler-plugin / maven-jar-plugin / maven-resources-plugin / maven-install / maven-deploy / maven-clean | inherited from spring-boot-starter-parent pluginManagement | 2.21.0 / 3.7.0 / 3.0.2 / 3.0.1 / 2.5.2 / 2.8.2 / 3.0.0 | 3.6.0 / 3.16.0 / 3.5.1 / 3.5.0 / 3.2.0 / 3.2.0 / 3.5.0 | major/minor behind | These move with the Boot parent. Surefire 2.21 needs 2.22+ for JUnit 5 platform. Versions plugin reports 'All plugins with a version specified are using the latest versions' only because the plugin-updates goal compares against Maven-requirement buckets; latest values were read from maven-metadata.xml. |
| com.google.guava:guava | **pinned in module pom** 19.0 | 19.0 (overrides the transitive 16.0 from netflix-infix) | 33.7.2-jre | major behind, vulnerable | CVE-2018-10237 MODERATE (unbounded alloc in AtomicDoubleArray/CompoundOrdering deserialization), CVE-2023-2976 MODERATE (FileBackedOutputStream temp-file perms), CVE-2020-8908 LOW (`Files.createTempDir`). Fixed in 32.0.0. Code only uses `com.google.common.collect.ImmutableMap` (StatisticsServiceImpl, ExchangeRatesServiceImpl). The upgrade is low risk, or you can replace it with `Map.of(...)` (Java 9+) and drop guava entirely. |
| Hystrix fallback in statistics-service (observation) | — | — | — | note | statistics-service declares `ExchangeRatesClient` with `fallback = ExchangeRatesClientFallback.class` but does not set `feign.hystrix.enabled` (only account-service.yml does), so on Finchley the fallback is probably inactive. That fits the known NPE when exchangeratesapi.io fails. Inference from config, not runtime-verified. hystrix-core is on the classpath only transitively (feign-hystrix). |

#### Module: `notification-service`

Declared: oauth2, security, config, web, openfeign, eureka-client, sleuth, data-mongodb, actuator, bus-amqp, hystrix-stream, **starter-mail**, flapdoodle 1.50.3, json-path 2.2.0, test. Plugins: spring-boot-maven-plugin, jacoco 0.7.6. Module version is `1.0.0-SNAPSHOT` (the other two are `1.0-SNAPSHOT`).

| Dependency / plugin (groupId:artifactId) | Declared / inherited from | Current (resolved) | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework.cloud:spring-cloud-starter-oauth2 | module pom (version from Cloud BOM) | 2.0.0.RELEASE | 2.2.5.RELEASE (final; Apr 2021) | deprecated, EOL, vulnerable (transitive) | Pulls spring-security-oauth2 **2.2.1.RELEASE** (CVE-2018-1260 CRITICAL, CVE-2018-15758 HIGH, CVE-2019-3778 MODERATE; fixed in 2.3.4+, last release 2.5.2), spring-security-oauth2-autoconfigure 2.0.0 (last 2.6.8) and spring-security-jwt 1.0.9 (last 1.1.1, deprecated). Spring Security OAuth has been EOL since 2022 (docs). Code uses `@EnableResourceServer`, `ResourceServerConfigurerAdapter`, `ResourceServerTokenServices` (custom `CustomUserInfoTokenServices` → `user-info-uri`), `@EnableOAuth2Client`, `OAuth2RestTemplate` + `ClientCredentialsResourceDetails`. **Replacement:** `spring-boot-starter-oauth2-resource-server` (JWT/opaque introspection) + `spring-boot-starter-oauth2-client` (client_credentials via `OAuth2AuthorizedClientManager`), with Spring Authorization Server on the auth side. That's a rewrite, not a version bump. |
| org.springframework.boot:spring-boot-starter-security | module pom (Boot BOM) | 2.0.3.RELEASE (spring-security 5.0.6.RELEASE) | 4.1.1 (spring-security 7.1.1; 5.x line: 5.8.16) | major behind, EOL, vulnerable | spring-security-web 5.0.6: CVE-2022-22978 CRITICAL (regex authz bypass), CVE-2024-38821 CRITICAL, CVE-2026-22732 CRITICAL, CVE-2021-22112 HIGH. spring-security-core: CVE-2024-22257 HIGH. spring-security-crypto: CVE-2025-22228 HIGH. `@EnableGlobalMethodSecurity` was replaced by `@EnableMethodSecurity` (6.x). `WebSecurityConfigurerAdapter` was removed in 6.x. |
| org.springframework.cloud:spring-cloud-starter-config | module pom (Cloud BOM) | 2.0.0.RELEASE | 5.0.5 | major behind, EOL | Spring Cloud 2020.0+ turns bootstrap.yml off by default. Use `spring.config.import=configserver:` or add `spring-cloud-starter-bootstrap`. Pulls spring-security-rsa 1.0.5 (last 1.1.5). |
| org.springframework.boot:spring-boot-starter-web | module pom (Boot BOM) | 2.0.3.RELEASE (spring-web/webmvc 5.0.7, tomcat-embed 8.5.31) | 4.1.1 | major behind, EOL, vulnerable | CVE-2022-22965 "Spring4Shell" CRITICAL (spring-beans/webmvc; fixed in 5.3.18 / 5.2.20). CVE-2016-1000027 CRITICAL (spring-web HttpInvoker). Also many webmvc/web HIGH CVEs (CVE-2024-38819, CVE-2024-22243/22259/22262, CVE-2020-5398). See the Tomcat row below. |
| org.springframework.cloud:spring-cloud-starter-openfeign | module pom (Cloud BOM) | 2.0.0.RELEASE (feign-core 9.5.1) | 5.0.3 (feign-core 13.17) | major behind, EOL | The Finchley integration routes Feign through Ribbon + Hystrix (`feign-hystrix`). In 2020.0+, Feign uses Spring Cloud LoadBalancer and Spring Cloud CircuitBreaker (`spring.cloud.openfeign.circuitbreaker.enabled`). The `feign.hystrix.enabled` property and the Hystrix-style `fallback` classes need migrating. Spring Cloud OpenFeign is now feature-complete and the team recommends `@HttpExchange` HTTP interfaces for new code (docs). |
| com.netflix.ribbon:ribbon* (transitive via openfeign → spring-cloud-starter-netflix-ribbon) | transitive (Cloud BOM) | 2.2.5 (spring-cloud-starter-netflix-ribbon 2.0.0.RELEASE) | 2.7.18 (Netflix); Spring starter last 2.2.10.RELEASE | deprecated, EOL | Maintenance mode, then removed in Spring Cloud 2020.0. **Replacement:** `spring-cloud-starter-loadbalancer` (5.0.3). Ribbon config (`<client>.ribbon.*`) has no 1:1 equivalent. Pulls rxnetty 0.4.9 + netty **4.1.25.Final** (codec-http: 23 OSV advisories incl. CVE-2019-20444 CRITICAL; netty-codec CVE-2021-37136/37137 HIGH; latest 4.1.139 / 4.2.19). |
| org.springframework.cloud:spring-cloud-starter-netflix-eureka-client | module pom (Cloud BOM) | 2.0.0.RELEASE (com.netflix.eureka:eureka-client 1.9.2) | 5.0.2 (eureka-client 2.0.6) | major behind, EOL, vulnerable (transitive) | Eureka client is still supported in current trains. In 4.x/5.x it uses Spring RestTemplate/WebClient/RestClient instead of Jersey 1. Current transitive deps: xstream **1.4.10** (35 advisories incl. CVE-2013-7285 & CVE-2019-10173 CRITICAL, many HIGH RCE CVE-2021-39139…39154; fixed 1.4.21), jettison 1.3.7 (CVE-2022-45685/45693/40150, CVE-2023-1436 HIGH; fixed 1.5.4), jersey 1.19.1, gson 2.8.5 (CVE-2022-25647 HIGH), httpclient 4.5.5 (CVE-2020-13956 MODERATE), commons-configuration 1.8 / archaius-core 0.7.6 (Archaius 1 is EOL). |
| org.springframework.cloud:spring-cloud-starter-sleuth | module pom (Cloud BOM) | 2.0.0.RELEASE (brave 5.1.0, zipkin-reporter 2.7.3) | 3.1.11 (final; Boot 2.x only) | deprecated, EOL | Sleuth was dropped from Spring Cloud 2022.0 (verified: not in the 2022.0.0 BOM). **Replacement:** Micrometer Tracing (`micrometer-tracing-bridge-brave` or `-otel`, 1.7.1) + `spring-boot-starter-actuator`. Log MDC keys change (`traceId`/`spanId` stay, but `X-B3-*` → W3C `traceparent` by default). |
| org.springframework.boot:spring-boot-starter-data-mongodb | module pom (Boot BOM) | 2.0.3.RELEASE (spring-data-mongodb 2.0.8, mongodb-driver 3.6.4) | 4.1.1 (spring-data-mongodb 5.1.1, mongodb-driver-sync 5.13.0) | major behind, EOL, vulnerable | spring-data-mongodb 2.0.8: CVE-2022-22980 CRITICAL (SpEL injection in `@Query`/repository methods; fixed in 3.3.5/3.4.1), CVE-2026-41717 HIGH. spring-data-commons 2.0.8: CVE-2026-41716 HIGH. The legacy `mongodb-driver` 3.x artifact (last 3.12.14) was replaced by `mongodb-driver-sync` 4.x/5.x. Boot 3 renamed `spring.data.mongodb.*` properties in parts (e.g. `spring.mongodb.*` in Boot 4; not verified in detail). |
| org.springframework.boot:spring-boot-starter-actuator | module pom (Boot BOM) | 2.0.3.RELEASE (micrometer-core 1.0.5) | 4.1.1 (micrometer 1.17.1) | major behind, EOL, vulnerable | CVE-2023-20873 CRITICAL (actuator on Cloud Foundry), CVE-2026-22733 HIGH, CVE-2023-34055 MODERATE. micrometer-core 1.0.5: CVE-2026-40984 HIGH. The `hystrix.stream` endpoint goes away with Hystrix. |
| org.springframework.cloud:spring-cloud-starter-bus-amqp | module pom (Cloud BOM) | 2.0.0.RELEASE (spring-cloud-stream 2.0.0, spring-rabbit 2.0.4, amqp-client 5.1.2) | 5.0.3 (spring-rabbit 4.1.1, amqp-client 5.37.0) | major behind, EOL, vulnerable (transitive) | amqp-client 5.1.2: 12 advisories (4 HIGH, e.g. CVE-2026-69219, CVE-2026-63337; fixed 5.36.1+). spring-amqp 2.0.4: CVE-2018-11087 and others (MODERATE). spring-retry 1.2.2: CVE-2026-41710 MODERATE. Spring Cloud Stream 3+ dropped the annotation model (`@EnableBinding`/`@StreamListener`) in favour of functional bindings. Bus itself is mostly config-driven. |
| org.springframework.cloud:spring-cloud-netflix-hystrix-stream | module pom (Cloud BOM) | 2.0.0.RELEASE (hystrix-core 1.5.12 via feign-hystrix) | 2.2.10.RELEASE (final) | deprecated, EOL | Publishes Hystrix metrics over Rabbit to Turbine. Netflix put Hystrix in maintenance mode in 2018 (last release 1.5.18, 2018). Removed from Spring Cloud 2020.0. **Replacement:** Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` (5.0.3), with metrics through Micrometer → Prometheus/Grafana instead of Turbine/Hystrix Dashboard. Pulls kryo-shaded 3.0.3 and spring-tuple 1.0.0 (both abandoned). |
| org.springframework.boot:spring-boot-starter-test | module pom (Boot BOM), test | 2.0.3.RELEASE (junit 4.12, mockito 2.15.0, assertj 3.9.1) | 4.1.1 (JUnit Jupiter; mockito 5.24.0) | major behind, vulnerable (test) | junit 4.12: CVE-2020-15250 MODERATE. assertj-core 3.9.1: CVE-2026-24400 HIGH. xmlunit 2.5.1: CVE-2024-31573 LOW. Boot 2.4+ dropped JUnit 4 vintage from the starter, so `@RunWith(SpringRunner.class)` tests need porting to JUnit 5. |
| de.flapdoodle.embed:de.flapdoodle.embed.mongo | **pinned in module pom** 1.50.3, test | 1.50.3 (embed.process 1.50.2) | 5.0.0 (Aug 2026); 4.33.0; Boot 3 integration `de.flapdoodle.embed.mongo.spring3x` 4.33.0 | major behind, vulnerable (test, transitive) | The pin *downgrades* the Boot 2.0.3 BOM-managed version (2.0.3). Boot 3 removed embedded-Mongo auto-configuration entirely, so you need flapdoodle's `spring3x`/`spring4x` module or Testcontainers MongoDB (recommended). 1.50.x can only download old MongoDB binaries. Transitive commons-compress 1.10 (CVE-2021-35515/35516/35517/36090 HIGH) and commons-io 2.4 (CVE-2024-47554 HIGH). Java baseline of 4.x/5.x not verified. |
| com.jayway.jsonpath:json-path | **pinned in module pom** 2.2.0, test | 2.2.0 (json-smart 2.2.1) | 3.0.0 (Feb 2026); 2.x: 2.10.0 | major behind, vulnerable (test) | The pin *downgrades* the Boot 2.0.3 BOM-managed version (2.4.0). CVE-2023-51074 MODERATE (fixed 2.9.0). json-smart 2.2.1: CVE-2023-1370 HIGH, CVE-2021-27568 MODERATE (fixed 2.4.9). Simplest fix: delete the `<version>` and let the Boot BOM manage it. 3.0.0 breaking changes not verified. |
| org.springframework.boot:spring-boot-maven-plugin | module pom (version from Boot parent pluginManagement) | 2.0.3.RELEASE | 4.1.1 | major behind | Boot 2.0 jar layout (layered jars / buildpack images came in 2.3+). `<configuration><finalName>` is an editable `repackage` parameter in 2.0.3 (verified from the plugin descriptor). Newer plugin versions make it read-only (docs; exact version not verified), so move it to `<build><finalName>` when upgrading. Plugin major must match the Boot version. |
| org.jacoco:jacoco-maven-plugin | **pinned in module pom** | 0.7.6.201602180812 (2016) | 0.8.15 | major behind | 0.7.x can't instrument classes newer than Java 8 bytecode (Java 11 support from 0.8.2, Java 17 from 0.8.7, Java 21 from 0.8.11 — from JaCoCo changelog, docs). So it blocks any JDK upgrade. versions-plugin also offers 0.6.3 under a 'Maven 2.1.0' heading, but that's an older-requirement artifact, not a real upgrade. |
| maven-surefire-plugin / maven-compiler-plugin / maven-jar-plugin / maven-resources-plugin / maven-install / maven-deploy / maven-clean | inherited from spring-boot-starter-parent pluginManagement | 2.21.0 / 3.7.0 / 3.0.2 / 3.0.1 / 2.5.2 / 2.8.2 / 3.0.0 | 3.6.0 / 3.16.0 / 3.5.1 / 3.5.0 / 3.2.0 / 3.2.0 / 3.5.0 | major/minor behind | These move with the Boot parent. Surefire 2.21 needs 2.22+ for JUnit 5 platform. Versions plugin reports 'All plugins with a version specified are using the latest versions' only because the plugin-updates goal compares against Maven-requirement buckets; latest values were read from maven-metadata.xml. |
| org.springframework.boot:spring-boot-starter-mail | module pom (Boot BOM) | 2.0.3.RELEASE (com.sun.mail:javax.mail 1.6.1, javax.activation 1.1, spring-context-support 5.0.7) | 4.1.1 (jakarta.mail via org.eclipse.angus:jakarta.mail 2.0.5) | major behind, EOL | No OSV advisories for javax.mail 1.6.1 (last javax line 1.6.2). Boot 3 moves to `jakarta.mail` (Eclipse Angus). Code uses `JavaMailSender` / `MimeMessageHelper`, so imports change `javax.mail.*` → `jakarta.mail.*`. Scheduled jobs (`@EnableScheduling`, `@Scheduled(cron=...)`) aren't affected. |
| com.google.guava:guava (transitive) | transitive via eureka/netflix-infix | 16.0 (runtime) | 33.7.2-jre | major behind, vulnerable | CVE-2018-10237 MODERATE, CVE-2023-2976 MODERATE, CVE-2020-8908 LOW. |

#### Key transitive libraries (identical resolved versions in all three modules unless noted)

| Dependency / plugin (groupId:artifactId) | Declared / inherited from | Current (resolved) | Latest stable | Status | Notes |
|---|---|---|---|---|---|
| org.springframework:spring-core / beans / context / expression / web / webmvc / messaging | Boot BOM (spring.version) | 5.0.7.RELEASE | 7.0.9 (5.3.x final OSS 5.3.39) | EOL, vulnerable | Framework 5.0 EOL 2020-12-31 (endoflife.date). spring-beans: CVE-2022-22965 CRITICAL, CVE-2022-22970 HIGH. spring-webmvc: 13 advisories (2 CRITICAL incl. CVE-2026-47884). spring-expression: 8 (CVE-2023-20863, CVE-2026-41849/41850 HIGH). spring-core: CVE-2018-15756 HIGH. spring-context: CVE-2022-22968 HIGH. spring-messaging: CVE-2022-22971 MODERATE. |
| org.apache.tomcat.embed:tomcat-embed-core / -websocket / -el | Boot BOM (tomcat.version) | 8.5.31 | 11.0.27 (10.1.61; 9.0.123) | EOL, vulnerable | Tomcat 8.5 is EOL (since 2024-03, docs). tomcat-embed-core: 51 advisories, 9 CRITICAL (e.g. CVE-2025-24813 partial-PUT RCE, CVE-2020-1938 'Ghostcat' (AJP connector; embedded Boot doesn't enable AJP by default)), 22 HIGH. websocket: CVE-2020-13935 HIGH. |
| com.fasterxml.jackson.core:jackson-databind / -core / jsr310 | Boot BOM (jackson.version) | 2.9.6 | 2.22.3 (Jackson 3.x line exists under `tools.jackson`) | vulnerable | jackson-databind 2.9.6: **66 advisories, 19 CRITICAL / 41 HIGH** (polymorphic-typing gadget chains e.g. CVE-2018-14718/14719/14721, CVE-2019-14379, CVE-2020-8840, CVE-2020-9546). jackson-core: CVE-2025-52999 HIGH plus 2026 HIGHs. jsr310: CVE-2018-1000873 MODERATE. |
| org.codehaus.jackson:jackson-mapper-asl | transitive via spring-security-oauth2 | 1.9.13 | 1.9.13 (abandoned) | EOL, vulnerable | CVE-2019-10202 CRITICAL, CVE-2019-10172 HIGH. No fix; it goes away with spring-security-oauth2. |
| org.yaml:snakeyaml | Boot BOM | 1.19 | 2.7 | major behind, vulnerable | CVE-2022-1471 HIGH (unsafe Constructor RCE), CVE-2022-25857 HIGH, CVE-2017-18640 HIGH, +5 MODERATE. Fixed in 2.0. Boot 2.7 still manages 1.30. |
| ch.qos.logback:logback-classic / logback-core | Boot BOM | 1.2.3 | 1.6.5 (Java 8 line: 1.3.16) | vulnerable | CVE-2023-6378 HIGH (fixed 1.2.13 / 1.3.12), CVE-2021-42550 MODERATE, CVE-2024-12798 MODERATE, CVE-2025-11226 MODERATE, plus LOW. |
| com.thoughtworks.xstream:xstream | transitive via eureka-client (Cloud BOM) | 1.4.10 | 1.4.21 | vulnerable | 35 advisories: CVE-2013-7285 & CVE-2019-10173 CRITICAL, 20 HIGH (RCE/DoS deserialization, e.g. CVE-2021-21341, CVE-2021-29505, CVE-2021-39139…39154). |
| org.codehaus.jettison:jettison | transitive via eureka-client | 1.3.7 | 1.5.7 | vulnerable | CVE-2022-45685, CVE-2022-45693, CVE-2022-40150, CVE-2023-1436 HIGH (DoS); fixed 1.5.4. |
| io.netty:netty-codec-http / codec / common / transport-native-epoll | transitive via ribbon-transport → rxnetty | 4.1.25.Final | 4.2.19.Final (4.1.139.Final) | vulnerable | codec-http 23 advisories (CVE-2019-20444 CRITICAL request smuggling, 6 HIGH). codec: CVE-2021-37136/37137 HIGH. These go away once Ribbon is removed. |
| org.bouncycastle:bcprov-jdk15on / bcpkix-jdk15on | transitive via spring-security-jwt | 1.56 | jdk15on discontinued at 1.70 → bcprov-jdk18on 1.86 | EOL artifact, vulnerable | bcprov: CVE-2018-1000180 HIGH, 6 MODERATE (e.g. CVE-2024-29857, CVE-2023-33201). bcpkix: CVE-2025-8916 MODERATE. |
| com.rabbitmq:amqp-client | Boot BOM via bus-amqp | 5.1.2 | 5.37.0 | vulnerable | See the bus-amqp row (12 advisories, 4 HIGH; fixed 5.36.1+). |
| io.github.openfeign:feign-core / feign-hystrix / feign-slf4j / feign-java8 | Cloud BOM via openfeign | 9.5.1 | 13.17 | major behind, deprecated (feign-hystrix) | No OSV advisories for feign 9.5.1. `feign-hystrix` and `feign-java8` were dropped in later Feign versions/Spring Cloud integrations. |
| com.netflix.hystrix:hystrix-core / -serialization / -javanica / -metrics-event-stream | Cloud BOM | 1.5.12 | 1.5.18 (final, 2018) | deprecated, EOL | No OSV advisories, but no maintenance either. Javanica (account-service) pulls guava 15.0 and asm 5.0.4. |
| com.netflix.eureka:eureka-client / eureka-core | Cloud BOM | 1.9.2 | 2.0.6 (1.x: 1.10.19) | major behind | No direct OSV advisories for 1.9.2; the risk is in the bundled deps (xstream, jettison, jersey 1, gson, httpclient). |
| com.netflix.archaius:archaius-core + commons-configuration:commons-configuration | Cloud BOM via archaius starter | 0.7.6 / 1.8 | 0.7.12 / 1.10 (commons-configuration2 2.x is the successor) | EOL, vulnerable | commons-configuration 1.8: CVE-2025-46392 LOW. Archaius 1 was removed in Spring Cloud 2020.0. commons-lang 2.6: CVE-2025-48924 MODERATE (fixed only in commons-lang3 3.18.0). |
| io.zipkin.brave:brave (+ instrumentation-*) | Cloud BOM via sleuth | 5.1.0 | 6.3.1 (5.x: 5.18.1) | major behind | No OSV advisories. Goes away with the Micrometer Tracing migration (brave bridge still uses brave 6). |
| org.hibernate.validator:hibernate-validator | Boot BOM via starter-web | 6.0.10.Final | 9.1.4.Final (javax line: 6.2.5.Final) | vulnerable | CVE-2019-10219, CVE-2020-10693, CVE-2023-1932, CVE-2025-35036 MODERATE. 7.x+ is jakarta.*. |
| org.mongodb:mongodb-driver / mongodb-driver-core / bson | Boot BOM via data-mongodb | 3.6.4 | mongodb-driver-sync 5.13.0 (legacy artifact final 3.12.14) | major behind | No OSV advisories found for 3.6.4. Check MongoDB server compatibility: driver 3.6 supports MongoDB server up to 3.6/4.0 (docs, not verified). |

#### Layer findings

### Most important risks (highest first)

1. **The whole layer runs on an EOL platform: Boot 2.0.3 / Spring Framework 5.0.7 / Spring Security 5.0.6 / Tomcat 8.5.31 / Spring Cloud Finchley.RELEASE.** These are the original 2018 GA versions, with not even the 2.0.x / Finchley.SRx patch releases applied. Each module resolves **54 artifacts with OSV advisories, about 323 unique advisories (41 CRITICAL, 144 HIGH)**, about 309 of them on compile/runtime scope. Headline ones: Spring4Shell CVE-2022-22965 (CRITICAL), CVE-2022-22978 Spring Security regex authz bypass (CRITICAL), CVE-2022-22980 Spring Data MongoDB SpEL injection (CRITICAL), Tomcat CVE-2025-24813 (CRITICAL), jackson-databind 2.9.6 (19 CRITICAL gadget-chain CVEs), snakeyaml 1.19 CVE-2022-1471 (HIGH), logback 1.2.3 CVE-2023-6378 (HIGH).
2. **Legacy OAuth2 stack on every service (resource-server and client side).** `spring-cloud-starter-oauth2` → `spring-security-oauth2` 2.2.1 (CVE-2018-1260 CRITICAL, CVE-2018-15758 HIGH) and `jackson-mapper-asl` 1.9.13 (CVE-2019-10202 CRITICAL, no fix). Spring Security OAuth is EOL and spring-cloud-security isn't in any train from 2020.0 on. All three services use `@EnableResourceServer`, a custom `ResourceServerTokenServices` that calls `user-info-uri`, `@EnableOAuth2Client`, and `OAuth2RestTemplate` client_credentials. There's no drop-in upgrade path; this is a code rewrite and depends on what auth-service becomes.
3. **Netflix OSS components that are EOL or removed.** Hystrix (account-service starter, plus hystrix-stream and feign-hystrix in all three), Ribbon (transitive via OpenFeign) and Archaius 1 are no longer in Spring Cloud 2020.0+ (verified). Through Eureka 1.9.2 and Ribbon/rxnetty they also drag in xstream 1.4.10 (35 advisories, 2 CRITICAL), jettison 1.3.7, netty 4.1.25 (CVE-2019-20444 CRITICAL) and jersey 1.
4. **Sleuth is gone in 2022.0+** (verified). Tracing has to move to Micrometer Tracing.
5. **Test tooling blocks upgrades.** The module poms pin flapdoodle 1.50.3 and json-path 2.2.0 *below* the Boot 2.0.3 BOM versions (2.0.3 / 2.4.0). Boot 3 removed embedded-Mongo auto-config. jacoco 0.7.6 can't handle Java 11+ bytecode, so it blocks any JDK upgrade on its own. JUnit 4 / Mockito 2 / assertj 3.9.1 carry advisories (test scope only).
6. **Direct pin guava 19.0 (statistics-service).** CVE-2018-10237, CVE-2023-2976 (MODERATE), CVE-2020-8908 (LOW). It's only used for `ImmutableMap`.
7. **Observation:** statistics-service's Feign fallback (`ExchangeRatesClientFallback`) appears inactive because `feign.hystrix.enabled` is set only for account-service. This is inferred from config, not runtime-tested.

### Breaking-change concerns for upgrading

- **Java 8 → 17 baseline.** Boot 3.x/4.x and Spring Cloud 2022.0+ need Java 17+. Staying on Java 8 caps the layer at Boot 2.7.18 / Spring Cloud 2021.0.9, which is OSS-EOL (Boot 2.7 OSS EOL 2023-06-30; commercial support to 2029-06-30 per endoflife.date). jacoco has to go to ≥0.8.7 (Java 17) / ≥0.8.11 (Java 21) first.
- **javax → jakarta (Boot 3).** Affects `javax.servlet`, `javax.validation` (hibernate-validator 8+/9), `javax.mail` → `jakarta.mail` (notification-service `EmailServiceImpl`), and `javax.annotation`.
- **Hystrix → Resilience4j.** Replace `@EnableCircuitBreaker` (removed), `feign.hystrix.enabled` and `hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds` with `spring-cloud-starter-circuitbreaker-resilience4j`, `spring.cloud.openfeign.circuitbreaker.enabled=true` and `resilience4j.*` config. Feign `fallback` classes can stay but the semantics differ (fallbackFactory recommended). hystrix-stream / Turbine / Hystrix Dashboard metrics (monitoring and turbine-stream-service, other layer) need replacing with Micrometer + Prometheus/Grafana. Removing hystrix-stream also removes the Rabbit metrics publishing.
- **Ribbon → Spring Cloud LoadBalancer.** It's automatic once Ribbon is off the classpath, but `*.ribbon.*` properties (timeouts/retries) need reworking (`spring.cloud.loadbalancer.*`, Feign `connectTimeout/readTimeout`).
- **spring-cloud-starter-oauth2 → `spring-boot-starter-oauth2-resource-server` + `spring-boot-starter-oauth2-client`.** The resource server should validate JWTs (or use opaque-token introspection) instead of calling `user-info-uri`, which needs a matching change in auth-service (Spring Authorization Server). Inter-service client_credentials calls via `OAuth2RestTemplate` move to `OAuth2AuthorizedClientManager`, with a Feign `RequestInterceptor` or `RestClient`. `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`. `WebSecurityConfigurerAdapter` / `ResourceServerConfigurerAdapter` → `SecurityFilterChain` beans. The `#oauth2.hasScope('server')` SpEL in `@PreAuthorize` (AccountController, StatisticsController; verified) no longer works without spring-security-oauth2 and has to be rewritten as `hasAuthority('SCOPE_server')`.
- **Sleuth → Micrometer Tracing.** Swap the dependency (`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` or OTel). Default propagation changes from B3 to W3C, so mixed-version services can lose trace continuity during a rolling migration.
- **Spring Cloud Config client.** bootstrap context disabled by default since 2020.0. Use `spring.config.import=optional:configserver:` (or add `spring-cloud-starter-bootstrap`).
- **Spring Cloud Bus / Stream.** Annotation-based Stream programming model removed (Stream 4). Bus usage here is config-driven, so impact is likely low (not exhaustively verified).
- **Data MongoDB.** Driver 3.6 → 5.x (`mongodb-driver-sync`). Spring Data 2.0 → 4.x/5.x drops some deprecated APIs. For tests, use flapdoodle `spring3x`/`spring4x` modules or Testcontainers.
- **Boot 2.x → 3/4 config renames** (actuator endpoints, `spring.data.mongodb`, `management.*`). Use `spring-boot-properties-migrator` during the migration.
- **Boot 4 / Spring Framework 7 / Spring Cloud 2025.1** add more removals (Jackson 3 by default, etc.). Not analysed in detail here.

### Prioritized recommendations for this layer

1. **Short-term risk reduction (Java 8, no code rewrite):** move to the latest patch of the current lines (Boot 2.0.9 / Finchley.SR4) as a stop-gap. Better: go to **Boot 2.7.18 + Spring Cloud 2021.0.9** (Java 8-compatible). That fixes most Framework/Security/Tomcat/Jackson/snakeyaml/logback CVEs. Hystrix/Ribbon are gone in 2021.0, though, so step 2 is needed in the same change. The alternative is Boot 2.3.12 + Hoxton.SR12, the last train with Netflix Hystrix/Ribbon, at the cost of a still-EOL stack.
2. **Replace Hystrix + Ribbon with Resilience4j + Spring Cloud LoadBalancer** in all three services (drop `spring-cloud-starter-netflix-hystrix` and `spring-cloud-netflix-hystrix-stream`). Coordinate with the monitoring/turbine layer.
3. **Replace spring-cloud-starter-oauth2 / spring-security-oauth2** with Spring Security's native resource-server and client support, coordinated with the auth-service rewrite (Spring Authorization Server). This removes the critical OAuth2 CVEs and jackson-mapper-asl.
4. **Fix the module-level pins now (low risk, independent of the platform upgrade):** remove the `<version>` on `json-path` (let the BOM manage it), bump `jacoco-maven-plugin` to 0.8.15, bump `guava` to 33.x-jre or replace it with `Map.of`/Collections in statistics-service, and replace flapdoodle 1.50.3 with Testcontainers MongoDB or the BOM/flapdoodle-spring module.
5. **Migrate to Java 17+ and Boot 3.5.x (+ Spring Cloud 2025.0.x) or straight to Boot 4.1.x (+ Spring Cloud 2025.1.x).** This covers the jakarta namespace, Sleuth → Micrometer Tracing, JUnit 5, mail → jakarta.mail, and the config-import changes. Boot 3.5 OSS support ended 2026-06-30 per endoflife.date, so Boot 4.x is the better landing target if the team can absorb Spring Framework 7 / Jackson 3 changes.
6. **Add dependency hygiene to CI:** OWASP dependency-check or OSV-Scanner, plus Renovate/Dependabot on the poms. Optionally enforce Maven ≥3.6.3 via `maven-enforcer-plugin`; `display-plugin-updates` warned that no Maven prerequisite is enforced.

#### Could not verify / caveats

- Spring Cloud Finchley EOL date, the Spring Security OAuth EOL date, Netflix Hystrix/Ribbon maintenance-mode announcements, the Tomcat 8.5 EOL date and the JaCoCo Java-support matrix come from documentation and knowledge. They weren't fetched this session (spring.io pages returned redirects / JS-rendered content).
- OSV severities are GHSA ratings as returned by OSV. Several 2026-dated CVE IDs (e.g. CVE-2026-xxxxx for netty, amqp-client, tomcat, spring-*) were taken as reported and not cross-checked in NVD. Reachability/exploitability in PiggyMetrics wasn't assessed (e.g. Ghostcat requires an AJP connector, which embedded Boot doesn't enable by default).
- Java baseline and breaking changes of flapdoodle 4.x/5.x and json-path 3.0.0 weren't verified.
- Runtime behavior (e.g. the inactive statistics-service fallback) was inferred from config, not executed.
- `display-dependency-updates` "latest" values are the highest non-pre-release version. For artifacts whose development stopped on a branch (Hystrix, Ribbon starter, Sleuth, spring-cloud-starter-oauth2), the "latest" shown is the final release, not a supported one.

</details>

