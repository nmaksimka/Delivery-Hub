package com.deliveryhub.order;

import com.deliveryhub.order.cart.Cart;
import com.deliveryhub.order.cart.CartLine;
import com.deliveryhub.order.cart.CartProperties;
import com.deliveryhub.order.cart.CartStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.cart.ttl=2h")
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class, RedisContainerConfig.class,
        GatewayTokenConfig.class})
class CartStoreIT {

    @Autowired
    private CartStore store;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private CartProperties properties;

    @Test
    void returnsEmptyCartForUnknownUser() {
        assertThat(store.load(9001L).hasLines()).isFalse();
    }

    @Test
    void savesCartAsJsonWithTtlFromProperties() {
        store.update(9002L, cart -> cart.add(1L, 10L, 2, false));

        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(2));
        assertThat(redis.opsForValue().get("cart:9002")).contains("\"restaurantId\":1").contains("\"menuItemId\":10");
        assertThat(redis.getExpire("cart:9002", TimeUnit.SECONDS)).isBetween(7000L, 7200L);
        assertThat(store.load(9002L).lines()).containsExactly(new CartLine(10L, 2));
    }

    @Test
    void updateRefreshesTtl() {
        store.update(9003L, cart -> cart.add(1L, 10L, 1, false));
        redis.expire("cart:9003", Duration.ofSeconds(30));

        store.update(9003L, cart -> cart.add(1L, 10L, 1, false));

        assertThat(redis.getExpire("cart:9003", TimeUnit.SECONDS)).isGreaterThan(7000L);
    }

    @Test
    void emptyCartIsRemovedFromRedis() {
        store.update(9004L, cart -> cart.add(1L, 10L, 1, false));

        Cart result = store.update(9004L, cart -> cart.remove(10L));

        assertThat(result.hasLines()).isFalse();
        assertThat(redis.hasKey("cart:9004")).isFalse();
    }

    @Test
    void clearDeletesCart() {
        store.update(9005L, cart -> cart.add(1L, 10L, 1, false));

        store.clear(9005L);

        assertThat(store.load(9005L).hasLines()).isFalse();
    }

    @Test
    void parallelUpdatesDoNotLoseQuantity() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger conflicts = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    store.update(9006L, cart -> cart.add(1L, 10L, 1, false));
                } catch (RuntimeException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(store.load(9006L).lines()).containsExactly(new CartLine(10L, threads - conflicts.get()));
    }
}
