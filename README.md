# Food Delivery

Учебное микросервисное приложение доставки еды на Spring Boot и Spring Cloud.

## Состав системы

| Приложение | Порт | Назначение |
|---|---:|---|
| Config Server | 8888 | Централизованная конфигурация |
| Eureka Server | 8761 | Регистрация и обнаружение сервисов |
| API Gateway | 8080 | Единая точка входа и маршрутизация |
| Restaurant Service | 8081 | Рестораны, меню и расчёт стоимости |
| Order Service | 8082 | Создание и просмотр заказов |
| Delivery Service | 8083 | Назначение курьера и статусы доставки |
| API Docs | 8090 | Общий Swagger UI |

## Реализованные требования

- Три независимых доменных микросервиса и три отдельные базы PostgreSQL.
- Spring Cloud Config с локальным репозиторием `config-repo`.
- Eureka Server и Eureka Client во всех маршрутизируемых сервисах.
- Spring Cloud Gateway с маршрутизацией через логические имена сервисов.
- Circuit Breaker Resilience4j для вызовов `order-service → restaurant-service` и `delivery-service → order-service`.
- Spring Boot Actuator с health, info, metrics и метриками Circuit Breaker.
- Структурированное логирование основных бизнес-операций и ошибок.
- OpenAPI для каждого доменного сервиса и общий Swagger UI.

## Требования к окружению

- Java 17 или новее.
- PostgreSQL на `localhost:5432`.
- Пользователь PostgreSQL `postgres`.
- Базы `food_restaurants`, `food_orders`, `food_deliveries`.

Пароль PostgreSQL берётся из переменной окружения `DB_PASSWORD`. Перед запуском задайте её в IntelliJ IDEA или терминале.

Пример для PowerShell:

```powershell
$env:DB_PASSWORD="ваш_пароль_PostgreSQL"
```

## Проверка и сборка

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

## Порядок запуска

Каждую команду нужно выполнить в отдельном терминале из корня проекта:

```powershell
java -jar config-server\target\config-server-1.0.0-SNAPSHOT.jar
java -jar discovery-server\target\discovery-server-1.0.0-SNAPSHOT.jar
java -jar restaurant-service\target\restaurant-service-1.0.0-SNAPSHOT.jar
java -jar order-service\target\order-service-1.0.0-SNAPSHOT.jar
java -jar delivery-service\target\delivery-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar
java -jar api-docs\target\api-docs-1.0.0-SNAPSHOT.jar
```

Config Server следует запускать первым, Eureka вторым. После их запуска остальные приложения можно запускать в любом порядке.

## Полезные адреса

- Gateway: http://localhost:8080
- Eureka: http://localhost:8761
- Swagger UI: http://localhost:8090/swagger-ui.html
- Config Server: http://localhost:8888/order-service/default
- Health Order Service: http://localhost:8082/actuator/health
- Metrics Order Service: http://localhost:8082/actuator/metrics
- Circuit Breakers: http://localhost:8082/actuator/circuitbreakers

## Сценарий демонстрации

1. Открыть Eureka и показать зарегистрированные сервисы.
2. Открыть общий Swagger UI и выполнить запрос списка ресторанов.
3. Создать заказ через Gateway.
4. Создать доставку для заказа и изменить статус на `IN_TRANSIT`, затем на `DELIVERED`.
5. Открыть Actuator health и metrics.
6. Остановить Restaurant Service, выполнить несколько запросов создания заказа и показать состояние Circuit Breaker.
