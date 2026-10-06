# Запуск в Kubernetes (kind)

Чарт лежит в `helm/delivery-hub`. Он ставит десять сервисов и фронтенд (Deployment + Service), инфраструктуру
(шесть postgres, kafka, schema registry, redis, elasticsearch, garage, mailpit — StatefulSet с томами),
Ingress на шлюз и фронтенд и NetworkPolicy. Мониторинг (Prometheus, Grafana, Loki) в чарт не входит — он остаётся в docker compose.

## Что нужно

docker, kubectl, kind, helm 3.21+. Кластеру нужно около 12 ГБ памяти (в Docker Desktop выдай не меньше).

## Шаги

1. Кластер и ingress-контроллер с классом `nginx`:

```
kind create cluster --name deliveryhub --config helm/delivery-hub/kind-cluster.yaml
```

Дальше поставь ingress-nginx по инструкции для kind из его документации. Сайт будет на http://localhost:8080.

2. Образы собираем локально и грузим в kind (тег `local`, как в `values-kind.yaml`):

```
for pair in discoveryServer:discovery-server configServer:config-server apiGateway:api-gateway userService:user-service restaurantService:restaurant-service orderService:order-service deliveryService:delivery-service paymentService:payment-service notificationService:notification-service searchService:search-service; do
  dir=${pair%%:*}; name=${pair##*:}
  docker build -t ghcr.io/nmaksimka/delivery-hub-$name:local -f $dir/Dockerfile .
  kind load docker-image ghcr.io/nmaksimka/delivery-hub-$name:local --name deliveryhub
done
docker build -t ghcr.io/nmaksimka/delivery-hub-frontend:local frontend
kind load docker-image ghcr.io/nmaksimka/delivery-hub-frontend:local --name deliveryhub
```

3. Файлы, которые чарт не может прочитать сам (они лежат вне папки чарта), кладём в ConfigMap:

```
kubectl create namespace deliveryhub
kubectl -n deliveryhub create configmap config-repo --from-file=config-repo
kubectl -n deliveryhub create configmap garage-files --from-file=garage/garage.toml --from-file=garage/init.sh
```

4. Установка:

```
helm install deliveryhub helm/delivery-hub -n deliveryhub -f helm/delivery-hub/values-kind.yaml
kubectl -n deliveryhub get pods -w
```

Сервисы ждут свои зависимости в init-контейнерах, поэтому первые минуты поды будут в `Init`. Полный старт занимает 5–10 минут.

5. Проверка: http://localhost:8080 — фронтенд, `/api/...` идёт через шлюз. Картинки меню отдаёт garage:
`kubectl -n deliveryhub port-forward svc/garage 3900:3900` (адрес в values: `STORAGE_PUBLIC_ENDPOINT`).
Письма: `kubectl -n deliveryhub port-forward svc/mailpit 8025:8025`.

## Как устроено

- Имена Service совпадают с хостами из `config-repo/*-docker.yml`, но в нижнем регистре (`orderservice` вместо `orderService`):
  имена в Kubernetes не бывают с заглавными буквами, а DNS регистр не различает, так что `orderService` резолвится.
  Из-за фиксированных имён один релиз ставится в один namespace.
- Сервисы описаны в `values.yaml` в блоке `services`, инфраструктура — в `infra`. Общие шаблоны: `templates/services.yaml`, `templates/infra.yaml`.
  Новый сервис — это новый элемент в values, шаблоны не меняются.
- Пароли и ключи лежат в Secret (`secrets` в values). Значения по умолчанию — заглушки, для настоящего окружения
  передавай свои через `--set-file` или отдельный values-файл. В `values-kind.yaml` ключи только для локального кластера.
- Внешняя инфраструктура: чтобы использовать свой postgres или kafka, убери нужный элемент из `infra` и поправь адреса в `config-repo`.
- Фронтенд в кластере не проксирует `/api` сам (его nginx настроен под docker compose): `/api` и `/ws` Ingress отправляет прямо на шлюз.
- NetworkPolicy: порт gRPC `9331` сервиса `restaurantservice` доступен только подам `orderservice`, HTTP-порт — всем.
  Политики работают, только если сетевой плагин их поддерживает. Проверка:

```
kubectl -n deliveryhub run probe --rm -it --restart=Never --image=busybox:1.37.0 -- nc -zv -w 3 restaurantservice 9331
```

  Из этого пода соединение должно не пройти, а из пода `orderservice` — пройти.

## Проверка чарта без кластера

```
helm lint helm/delivery-hub -f helm/delivery-hub/values-kind.yaml
helm template deliveryhub helm/delivery-hub -f helm/delivery-hub/values-kind.yaml
```
