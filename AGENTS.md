# AGENTS.md

PiggyMetrics: microservicios con Spring Boot 2.0.3 y Spring Cloud Finchley (config, registry, gateway, auth, account, statistics, notification, monitoring, turbine), MongoDB y RabbitMQ. La UI es una SPA jQuery estática que sirve el gateway (`gateway/src/main/resources/static`).

## Build y tests

- Compilar con **JDK 8**, porque con el JDK 17 por defecto el build falla:
  ```bash
  JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B package -DskipTests
  ```
- Smoke test rápido, sin Docker y con Mongo embebido: `mvn -B test -pl account-service,auth-service`. Para correr todos los tests: `mvn -B test`.
- Si Maven Central devuelve HTTP 429, configurar en `~/.m2/settings.xml` el mirror de Google `https://maven-central.storage-download.googleapis.com/maven2/`.

## Levantar el stack

- La imagen base `java:8-jre` de los Dockerfiles ya no existe en Docker Hub. Re-taguear una equivalente antes de buildear:
  ```bash
  docker pull eclipse-temurin:8-jre && docker tag eclipse-temurin:8-jre java:8-jre
  docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
  ```
- Endpoints:
  - Gateway/UI en http://localhost/
  - Eureka en :8761
  - Hystrix en :9000/hystrix
  - RabbitMQ en :15672 (guest/guest)
- Las contraseñas de desarrollo están en `.env`, que compose lee automáticamente.

## Problemas conocidos

- **API de tasas de cambio**: `api.exchangeratesapi.io` ahora exige access key. Mientras no se resuelva:
  - La UI (`static/js/launch.js`) muestra `NaN` en el dashboard y guarda los ahorros como 0.
  - `statistics-service` tira una NPE en `ExchangeRatesServiceImpl.getCurrentRates`, así que `/statistics/current` queda vacío.
  - Para testear la UI, mockear esa API (ver la skill de E2E). No inventar API keys.
- **Hystrix**: la URL del stream es `http://turbine-stream-service:8080/turbine/turbine.stream`. No usar `localhost:8989`, porque el dashboard hace proxy del stream desde su propio contenedor.

## Testing E2E

Para levantar la app, mockear las tasas y testear la UI, las APIs, Eureka y Hystrix, seguir la skill [`.agents/skills/piggymetrics-e2e-testing/SKILL.md`](.agents/skills/piggymetrics-e2e-testing/SKILL.md).
