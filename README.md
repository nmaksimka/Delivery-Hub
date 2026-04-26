# DeliveryHub - Микросервисная платформа доставки еды

<div align="center">

![Java](https://img.shields.io/badge/Java_25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.4.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=json-web-tokens&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)

</div>

## Принцип работы
```text
1. Пользователь авторизуется через User Service, получает JWT-токен.
2. Просматривает рестораны и меню через Restaurant Service.
3. Создаёт заказ через Order Service (JWT обязателен).
4. Order Service проверяет ресторан и блюда по HTTP (Feign), сохраняет заказ и отправляет событие OrderCreatedEvent в Kafka.
5. Delivery Service получает событие, автоматически создаёт доставку и отправляет обратное событие DeliveryStatusUpdatedEvent.
6. Order Service обновляет статус заказа.
```

## Архитектура системы

```mermaid
graph TB
    Client[Пользователь / Postman]
    Gateway[API Gateway :3335]
    
    subgraph Docker_Environment["Docker Environment"]
        Request[HTTP Request]
        
        subgraph Business_Services["Бизнес-сервисы"]
            Restaurant[Restaurant Service<br/>Порт 3331]
            Order[Order Service<br/>Порт 3332]
            Delivery[Delivery Service<br/>Порт 3333]
            User[User Service<br/>Порт 3334]
        end
        
        subgraph Databases["Базы данных"]
            DB1[(restaurant_db<br/>Порт 5331)]
            DB2[(order_db<br/>Порт 5332)]
            DB3[(delivery_db<br/>Порт 5333)]
            DB4[(user_db<br/>Порт 5334)]
        end
        
        subgraph EventBus["Event Bus"]
            Kafka{{Apache Kafka<br/>Порт 9092}}
            Topic1["topic: order-created"]
            Topic2["topic: delivery-status-updated"]
        end
    end

    Client --> Request
    Request --> Gateway
    
    Gateway -->|"/api/auth/**"| User
    Gateway -->|"/api/restaurants/**"| Restaurant
    Gateway -->|"/api/orders/**"| Order
    Gateway -->|"/api/deliveries/**"| Delivery

    Order -->|Feign Client| Restaurant
    
    Order -->|Публикация события| Topic1
    Topic1 -->|Чтение события| Delivery
    Delivery -->|Публикация события| Topic2
    Topic2 -->|Чтение события| Order
    
    Kafka --- Topic1
    Kafka --- Topic2

    Restaurant --- DB1
    Order --- DB2
    Delivery --- DB3
    User --- DB4
```

## Технологический стек

| Категория            | Технологии                          |
|:---------------------|:------------------------------------|
| **Язык**             | Java 25                             |
| **Фреймворк**        | Spring Boot 3.4.5                   |
| **Микросервисы**     | Spring Cloud (OpenFeign, Gateway)   |
| **Асинхронность**    | Apache Kafka                        |
| **Базы данных**      | PostgreSQL (4 отдельных БД)         |
| **Миграции**         | Flyway                              |
| **Безопасность**     | Spring Security + JWT (jjwt 0.13.0) |
| **Документирование** | SpringDoc OpenAPI (Swagger UI)      |
| **Сборка**           | Gradle 9.3 + Kotlin DSL             |
| **Контейнеризация**  | Docker + Docker Compose             |

## 🐳 Быстрый старт

### Запуск всего проекта одной командой
```bash
git clone https://github.com/nmaksimka/Delivery-Hub.git
cd Delivery-Hub
docker-compose up --build
Доступные сервисыСервисURLAPI Gateway (Swagger)http://localhost:3335/swagger-ui.htmlUser Servicehttp://localhost:3334/swagger-ui.htmlOrder Servicehttp://localhost:3332/swagger-ui.htmlPostgreSQL (user)localhost:5334
```
## Примеры API запросов (Postman)
### Тестирование API
#### 1. Регистрация
```markdown
```http
POST http://localhost:3335/api/auth/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "123456",
  "name": "Test User",
  "phone": "+79991234567"
}
```
#### 2. Создание заказа (JWT)
```markdown
HTTP
POST http://localhost:3335/api/orders
Content-Type: application/json
Authorization: Bearer <your_token>

{
    "userId": 1,
    "restaurantId": 1,
    "orderItemsRequest": [{"menuItemId": 1, "quantity": 2}]
}
```

### 📂 Структура проекта

```text
DeliveryHub/
├── apiGateway/          # Gateway + JWT фильтр
├── userService/         # Auth & Users
├── orderService/        # Orders (Feign + Kafka)
├── deliveryService/     # Delivery (Kafka)
├── restaurantService/   # Catalog & Menu
└── docker-compose.yml   # Infrastructure
```

