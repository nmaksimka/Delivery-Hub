package com.deliveryhub.order.cart;

import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.OrderRejectedException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.function.UnaryOperator;

@Component
@EnableConfigurationProperties(CartProperties.class)
public class CartStore {

    private static final String KEY_PREFIX = "cart:";
    private static final int MAX_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == false then current = '' end
            if current ~= ARGV[1] then return 0 end
            if ARGV[2] == '' then
                redis.call('DEL', KEYS[1])
            else
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final CartProperties properties;

    public CartStore(StringRedisTemplate redis, JsonMapper jsonMapper, CartProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    public Cart load(Long userId) {
        return parse(redis.opsForValue().get(key(userId)));
    }

    public Cart update(Long userId, UnaryOperator<Cart> change) {
        String key = key(userId);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String current = redis.opsForValue().get(key);
            Cart updated = change.apply(parse(current));
            if (replace(key, current, updated)) {
                return updated;
            }
        }
        throw new OrderRejectedException(ErrorCode.CONCURRENT_UPDATE,
                "The cart was changed by another request, reload it and try again");
    }

    public void clear(Long userId) {
        redis.delete(key(userId));
    }

    private boolean replace(String key, String expected, Cart updated) {
        String serialized = updated.hasLines() ? jsonMapper.writeValueAsString(updated) : "";
        Long applied = redis.execute(COMPARE_AND_SET, List.of(key),
                expected == null ? "" : expected, serialized, Long.toString(properties.ttl().toMillis()));
        return Long.valueOf(1).equals(applied);
    }

    private Cart parse(String json) {
        return json == null ? Cart.empty() : jsonMapper.readValue(json, Cart.class);
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
