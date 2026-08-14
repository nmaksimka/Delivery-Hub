package com.deliveryhub.restaurant.grpc;

import com.deliveryhub.contracts.pricing.GeoPoint;
import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.contracts.pricing.QuotedItem;
import com.deliveryhub.restaurant.config.WorkingHoursProperties;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MenuPricingGrpcService extends MenuPricingGrpc.MenuPricingImplBase {

    public static final int MAX_ITEMS_PER_QUOTE = 100;

    private static final Logger log = LoggerFactory.getLogger(MenuPricingGrpcService.class);
    private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final WorkingHoursProperties workingHours;
    private final Clock clock;

    public MenuPricingGrpcService(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository,
                                  WorkingHoursProperties workingHours, Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.workingHours = workingHours;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public void quote(QuoteRequest request, StreamObserver<QuoteResponse> responseObserver) {
        Set<Long> menuItemIds = new LinkedHashSet<>(request.getMenuItemIdsList());
        if (request.getRestaurantId() <= 0 || menuItemIds.isEmpty() || menuItemIds.size() > MAX_ITEMS_PER_QUOTE) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("restaurant_id must be positive and 1.." + MAX_ITEMS_PER_QUOTE
                            + " menu items are expected")
                    .asRuntimeException());
            return;
        }
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId()).orElse(null);
        if (restaurant == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Restaurant " + request.getRestaurantId() + " was not found")
                    .asRuntimeException());
            return;
        }

        Map<Long, MenuItem> found = menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        QuoteResponse.Builder response = QuoteResponse.newBuilder()
                .setRestaurantId(restaurant.getId())
                .setRestaurantName(restaurant.getName())
                .setRestaurantActive(restaurant.isActive())
                .setRestaurantClosed(!restaurant.isOpenAt(LocalTime.now(clock.withZone(workingHours.zone()))));
        if (restaurant.getOpenTime() != null && restaurant.getCloseTime() != null) {
            response.setOpensAt(HOURS.format(restaurant.getOpenTime()))
                    .setClosesAt(HOURS.format(restaurant.getCloseTime()))
                    .setTimeZone(workingHours.zone().getId());
        }
        if (restaurant.hasLocation()) {
            response.setLocation(GeoPoint.newBuilder()
                    .setLatitude(restaurant.getLatitude())
                    .setLongitude(restaurant.getLongitude()));
        }
        menuItemIds.forEach(id -> response.addItems(quoted(id, found.get(id), restaurant.getId())));

        log.debug("Quoted {} menu items of restaurant {}", menuItemIds.size(), restaurant.getId());
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private static QuotedItem quoted(Long id, MenuItem item, Long restaurantId) {
        QuotedItem.Builder quoted = QuotedItem.newBuilder().setMenuItemId(id);
        if (item == null) {
            return quoted.setStatus(ItemStatus.ITEM_STATUS_NOT_FOUND).build();
        }
        if (!item.getRestaurant().getId().equals(restaurantId)) {
            return quoted.setStatus(ItemStatus.ITEM_STATUS_OTHER_RESTAURANT).build();
        }
        return quoted
                .setStatus(item.isAvailable() ? ItemStatus.ITEM_STATUS_AVAILABLE : ItemStatus.ITEM_STATUS_UNAVAILABLE)
                .setName(item.getName())
                .setPrice(item.getPrice().toPlainString())
                .build();
    }
}
