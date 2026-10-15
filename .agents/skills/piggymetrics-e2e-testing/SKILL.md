---
name: piggymetrics-e2e-testing
description: Levantar PiggyMetrics (axelisr/DevinWorkshop) completo con Docker y testear end-to-end la UI web (gateway), las APIs vía gateway, Eureka y el dashboard de Hystrix. Usar al testear, verificar o reproducir bugs de la app en axelisr/DevinWorkshop.
---

# PiggyMetrics: testing E2E

Repo: `~/repos/DevinWorkshop` (Spring Boot 2.0.3, Spring Cloud Finchley, 9 módulos Maven más MongoDB y RabbitMQ).
La UI es una SPA jQuery estática que sirve el gateway (`gateway/src/main/resources/static`).

## 1. Build (JDK 8 obligatorio)

```bash
cd ~/repos/DevinWorkshop
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -B -q package -DskipTests   # ~2-4 min, genera <module>/target/<module>.jar
```
- Con el JDK 17 por defecto de la VM falla: siempre exportar `JAVA_HOME` a JDK 8.
- Si Maven Central responde 429, `~/.m2/settings.xml` debe apuntar al mirror de Google `https://maven-central.storage-download.googleapis.com/maven2/` (el blueprint ya lo configura).
- Smoke test rápido sin Docker: `mvn -B test -pl account-service,auth-service` (usa Mongo embebido).

## 2. Levantar el stack con Docker

La imagen base `java:8-jre` que usan los Dockerfiles ya no existe en Docker Hub, así que `up --build` falla con `java:8-jre: not found`. Para no tocar el repo, re-taguear una imagen equivalente:

```bash
docker pull eclipse-temurin:8-jre && docker tag eclipse-temurin:8-jre java:8-jre
cd ~/repos/DevinWorkshop
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d > /tmp/up.log 2>&1   # ~3-4 min la primera vez
```
- El build de `mongodb` muestra un warning `KEYEXPIRED` del repo apt de mongo 3.6: se puede ignorar.
- Las contraseñas de desarrollo están en `.env`, que compose lee solo. No hace falta cargarlas a mano.
- Esperar ~90 s y verificar:
```bash
docker compose ps --format "table {{.Service}}\t{{.Status}}"         # 14 servicios Up, config (healthy)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/           # 200
curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps | grep -o '"name":"[A-Z-]*"' | sort -u
# Deben aparecer ACCOUNT-SERVICE, AUTH-SERVICE, GATEWAY, NOTIFICATION-SERVICE, STATISTICS-SERVICE, TURBINE-STREAM-SERVICE
```
- Si un servicio no registra, revisar `docker compose logs <servicio>`. Config tiene que estar healthy antes que el resto.

Endpoints:

| URL | Qué es |
|---|---|
| http://localhost/ | Gateway + UI |
| http://localhost:8761 | Eureka |
| http://localhost:9000/hystrix | Hystrix dashboard |
| http://localhost:15672 | RabbitMQ (guest/guest) |

En el modo dev también quedan expuestos auth 5000, account 6000, statistics 7000, notification 8000 y los Mongo 25000-28000.

## 3. Mockear la API de tasas de cambio (necesario)

`api.exchangeratesapi.io` ahora exige access key (`missing_access_key`). Sin mock pasa lo siguiente:
- La UI (`static/js/launch.js`, `$.getJSON(...base=RUB&symbols=EUR,USD)`) muestra `NaN` en todo el dashboard.
- El primer save manda los ahorros como `NaN` y quedan guardados como 0.
- statistics-service tira NPE en `ExchangeRatesServiceImpl.getCurrentRates` y no guarda estadísticas (`/statistics/current` devuelve `[]`).

Esto es un bug real: reportarlo como FAIL sin mock y seguir testeando la UI con mock, marcando esos checks como "tested against mocks". No adivinar ni inventar una API key.

Mock del lado del browser, vía CDP del Chrome de Devin (dejarlo corriendo en un shell aparte mientras se usa el browser):
```bash
pip install -q playwright
cat > ~/e2e/mock_rates.py <<'PY'
import asyncio, json
from playwright.async_api import async_playwright
BODY = json.dumps({"base": "RUB", "date": "2026-01-01", "rates": {"EUR": 0.0105, "USD": 0.0122}})
async def main():
    async with async_playwright() as p:
        ctx = (await p.chromium.connect_over_cdp("http://localhost:29229")).contexts[0]
        async def handler(route):
            print("mocked", route.request.url, flush=True)
            await route.fulfill(status=200, content_type="application/json", body=BODY,
                                headers={"Access-Control-Allow-Origin": "*"})
        await ctx.route("**/api.exchangeratesapi.io/**", handler)
        print("route installed", flush=True)
        await asyncio.Event().wait()
asyncio.run(main())
PY
python3 -u ~/e2e/mock_rates.py    # en background; después recargar la página (F5)
```
Confirmar que el log imprime `mocked https://api.exchangeratesapi.io/latest?...` después de recargar. Este mock no arregla el backend: statistics-service va a seguir con la NPE.

## 4. Flujos de UI a testear (grabar pantalla y anotar)

1. **Carga inicial**: en `http://localhost/` aparecen el logo del chanchito, el input "enter your login" y el link "create new account".
2. **Registro**:
   - Click en "create new account", completar usuario y contraseña, "register".
   - Debe aparecer "Thank you for registration".
   - Cargar un email y "Subscribe to notifications" (o "Skip this step").
   - Termina en la pantalla de perfil con "<USER> metrics", "last seen" y los botones "Get in" y "Log out".
3. **Cargar datos**:
   - "Get in" abre Incomes, Expenses y Savings.
   - "+" en Incomes: monto 5000, título "Salary", "Save changes".
   - "+" en Expenses: 1200, "Rent".
   - Savings: 10000.
   - Click en "SAVE CHANGES" (abajo) abre el dashboard. Si falta un item en alguna columna, aparece un alert.
4. **Dashboard** (con mock): Spare 3 800 USD/month, "In a year" 55 600, círculos per hour/day/year y gráfico de savings. Sin mock todo da `NaN`, y eso cuenta como FAIL.
5. **Cambio de moneda**: el botón EUR (arriba a la derecha) convierte los valores. Con el mock, 3 800 USD pasan a 3 270 EUR. RUB funciona igual.
6. **Logout y login**: "Log out", después login con el mismo usuario. "Get in" debe mostrar los datos guardados.

Notas para interactuar con la UI:
- El login es en dos pasos: se escribe el usuario y Enter, y recién ahí aparece el campo de contraseña.
- Los modales usan inputs autoNumeric: hacer triple click en el campo y escribir.

## 5. Verificación por API (vía gateway)

```bash
TOKEN=$(curl -s -X POST -H "Authorization: Basic YnJvd3Nlcjo=" \
  -d "scope=ui&username=<USER>&password=<PASS>&grant_type=password" \
  http://localhost/uaa/oauth/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/accounts/current                  # incomes/expenses/saving
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/statistics/current                # [] mientras siga el bug de tasas
curl -s -H "Authorization: Bearer $TOKEN" http://localhost/notifications/recipients/current  # email + REMIND
```
`browser:` (Basic `YnJvd3Nlcjo=`) es el cliente OAuth público que usa la UI.

Errores en el backend:
```bash
docker compose logs statistics-service account-service | grep -E "ERROR|Exception"
```

## 6. Herramientas de infraestructura

- **Eureka** (`:8761`): los 6 servicios en estado UP.
- **Hystrix**: usar `http://localhost:9000/hystrix/monitor?stream=http%3A%2F%2Fturbine-stream-service%3A8080%2Fturbine%2Fturbine.stream`.
  - No usar `http://localhost:8989`: el dashboard hace proxy del stream desde su propio contenedor y da "Unable to connect to Command Metric Stream".
  - Con la URL correcta aparecen circuitos de auth y statistics (Closed) y los thread pools.

## 7. Qué saltear

- El envío real de emails de notification-service: necesita SMTP y no hay credenciales.
- No hacer commit, push ni PR durante el testing. Si aparece un bug, preguntarle al usuario antes de arreglarlo.

## 8. Cleanup

```bash
cd ~/repos/DevinWorkshop && docker compose -f docker-compose.yml -f docker-compose.dev.yml down   # agregar -v para borrar los datos de Mongo
```
