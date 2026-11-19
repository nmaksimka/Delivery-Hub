# Инструкция по запуску

> Всё поднимается одной командой в Docker. Понадобится около 8 ГБ свободной памяти и 10 минут на первую сборку.

## Что нужно установить

| | Версия |
|---|---|
| Docker Desktop | с Docker Compose v2 |
| Node.js | 22 или новее — для фронта |
| JDK | 25 — только если запускать сервисы из IDE |

## 1. Склонировать проект

```bash
git clone https://github.com/nmaksimka/Delivery-Hub.git
cd Delivery-Hub
cp .env.example .env
```

## 2. Заполнить секреты

В `.env.example` есть пустые строки для секретов. Удалите их из `.env` и сгенерируйте свои:

```bash
echo "GARAGE_RPC_SECRET=$(openssl rand -hex 32)" >> .env
echo "GARAGE_ADMIN_TOKEN=$(openssl rand -hex 32)" >> .env
echo "STORAGE_ACCESS_KEY=GK$(openssl rand -hex 12)" >> .env
echo "STORAGE_SECRET_KEY=$(openssl rand -hex 32)" >> .env
echo "REDIS_PASSWORD=$(openssl rand -hex 32)" >> .env
echo "INTERNAL_TOKEN=$(openssl rand -hex 32)" >> .env
```

Остальные пароли (базы, Eureka, Grafana) задайте в `.env` любые.

<details>
<summary>Постоянный ключ для JWT (необязательно)</summary>

Без `JWT_PRIVATE_KEY` сервис пользователей сгенерирует временный ключ, и после перезапуска
все выданные токены станут недействительны. Постоянный ключ:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER | base64 -w0
```

</details>

## 3. Поднять бэкенд

```bash
docker compose up -d --build
docker compose ps
```

Подождите, пока у сервисов в колонке STATUS появится `healthy` — обычно 1–2 минуты.

## 4. Запустить фронт

```bash
cd frontend
npm ci
npm run dev
```

Откройте http://127.0.0.1:5173.

## 5. Наполнить меню

1. Зарегистрируйтесь на сайте.
2. Выдайте себе роль администратора:
   ```bash
   docker exec postgres-user psql -U user_user -d user_db -c "UPDATE users SET role='ADMIN' WHERE email='ваш@email'"
   ```
3. Выйдите и войдите снова — в шапке появится «Админка».
4. Админка → «Импорт меню». За минуту загрузятся 19 ресторанов и 150 блюд.

## 6. Сделать заказ

1. Добавьте адрес — **в Томске**, например «Томск, проспект Ленина, 36».
2. Пополните кошелёк.
3. Соберите корзину и оформите заказ.
4. На странице заказа курьер поедет по карте и примерно через минуту доставит.

## Полезные адреса

| Адрес | Что там |
|---|---|
| http://127.0.0.1:5173 | сайт |
| http://localhost:3335/swagger-ui.html | документация API |
| http://localhost:8025 | Mailpit — письма о заказах |
| http://localhost:8086 | Kafka UI |
| http://localhost:3000 | Grafana |
| http://localhost:8761 | Eureka |

## Тесты

```bash
./gradlew build             # unit и интеграционные тесты бэкенда (нужен запущенный Docker)
cd frontend && npm test     # тесты фронта
```

## Kubernetes

Чарт лежит в `helm/delivery-hub`, запуск в kind описан в [k8s.md](k8s.md).

## Остановить

```bash
docker compose stop      # данные сохранятся
docker compose down -v   # удалить всё вместе с данными
```

<details>
<summary>Если что-то не так</summary>

| Симптом | Что сделать |
|---|---|
| `container name ... is already in use` | остались контейнеры от другого запуска: `docker compose down`, затем снова `up` |
| Gradle: `requires at least JVM runtime version 25` | в IDE выбрать JDK 25 для Gradle (Settings → Build Tools → Gradle → Gradle JVM) |
| сайт не открывается по `localhost` | открывать http://127.0.0.1:5173 |
| заказ отменён с `PAYMENT_REJECTED` | пополнить кошелёк |
| адрес не принимается | адрес должен быть в Томске |

</details>
