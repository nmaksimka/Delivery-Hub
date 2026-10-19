# Журнал изменений

Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/), версии — [SemVer](https://semver.org/lang/ru/).

## [2.0.0]

### Добавлено
- Контрактные тесты Spring Cloud Contract: контракты кабинета владельца в restaurantService и consumer-тест проверки владельца ресторана в orderService на стабах.
- CI на GitHub Actions: сборка и тесты бэкенда на JDK 25, lint, vitest и сборка фронтенда.
- Release-workflow: образы сервисов собираются и публикуются в GHCR по тегу `v*`.
- Dependabot: Gradle, npm, GitHub Actions и Docker раз в неделю.
- Helm-чарт `helm/delivery-hub`: десять сервисов и фронтенд, StatefulSet для Postgres, Kafka, Schema Registry, Redis, Elasticsearch и Mailpit, Garage с job инициализации бакета, Ingress, NetworkPolicy для gRPC-порта ресторанов, `values-kind` и инструкция запуска в kind.
- Документация: архитектура сервисов (`docs/architecture.md`), ADR 0001–0015, запуск в Kubernetes (`docs/k8s.md`).
- Тесты нагрузки и разрыва цепи для клиента Nominatim в userService.

### Изменено
- Gradle сам выбирает JDK 25 для демона, даже если по умолчанию установлена другая версия.
- Сборка orderService в Docker не требует модуля ресторанов для стабов контрактов.
- vite-сервер фронтенда слушает `127.0.0.1`, чтобы `localhost:5173` открывался на Windows.

## [1.7.0]

### Добавлено
- Уведомления во фронтенде.
- Тикет для подключения по WebSocket.
- Поиск с учётом русской морфологии.
- Статусы и фото блюд в кабинете ресторана.

## [1.6.0]

### Добавлено
- notificationService: уведомления о заказе из Kafka, inbox, письма через Thymeleaf и Mailpit с повторами.
- Отзывы и рейтинги ресторанов.
- searchService на Elasticsearch: индекс каталога из событий `restaurant-catalog-changed`, outbox в restaurantService.
- Аналитика для кабинета ресторана и администратора.
- Кабинет владельца ресторана.

## [1.5.0]

### Добавлено
- Адреса и избранное пользователя.
- Курьеры на PostGIS с автоназначением.
- Геокодинг через Nominatim, маршрут OSRM и погода Open-Meteo в цене и времени доставки.
- Живой трекинг курьера по WebSocket STOMP, симулятор курьера.
- Карта Leaflet во фронтенде.

## [1.4.0]

### Добавлено
- Корзина в Redis и промокоды.
- Идемпотентность создания заказа через заголовок `Idempotency-Key`.
- paymentService (OpenAPI-first) с портом `PaymentProvider` и демо-кошельками.
- Хореографическая сага заказа с компенсациями: холд, списание, снятие холда, возврат.
- Оформление заказа и кошелёк во фронтенде.

## [1.3.0]

### Добавлено
- Импорт меню из TheMealDB и Open Food Facts.
- Фото блюд в S3-хранилище Garage.
- Кэш каталога в Redis.
- gRPC-расчёт цены заказа между orderService и restaurantService.
- Кабинет ресторана.
- Внутренний токен шлюза для межсервисных вызовов.
- Новый каталог во фронтенде.

## [1.2.0]

### Добавлено
- Avro-схемы событий и Schema Registry, Kafka UI.
- Eureka (discoveryServer) и Config Server (configServer), конфигурация в `config-repo`.
- Resilience4j для вызовов restaurantService.
- Refresh-токены, подпись RS256 и JWKS в userService.
- Rate limit на шлюзе на Redis.
- Grafana, Loki, Prometheus и дашборды.
- Фронтенд на React и TypeScript.

### Изменено
- Payload outbox-событий хранится в Avro-байтах.

## [1.1.0]

### Добавлено
- Переход на Spring Boot 4 и Java 25, каталог версий Gradle.
- Отказ от Lombok: records и богатая доменная модель.
- Transactional outbox и inbox в orderService и deliveryService.
- Интеграционные тесты на Testcontainers.
- Kafka в режиме KRaft.

### Исправлено
- Не компилировавшийся `ApiGatewayApplication` (лишняя скобка).
