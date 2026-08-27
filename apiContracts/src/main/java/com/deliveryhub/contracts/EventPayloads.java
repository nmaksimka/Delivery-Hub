package com.deliveryhub.contracts;

import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRefundedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import com.deliveryhub.contracts.events.ReviewSubmittedEvent;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;

public final class EventPayloads {

    private static final Map<String, BinaryMessageDecoder<? extends SpecificRecord>> DECODERS = Map.ofEntries(
            Map.entry(Topics.ORDER_CREATED, OrderCreatedEvent.getDecoder()),
            Map.entry(Topics.ORDER_CANCELLED, OrderCancelledEvent.getDecoder()),
            Map.entry(Topics.DELIVERY_STATUS_UPDATED, DeliveryStatusUpdatedEvent.getDecoder()),
            Map.entry(Topics.COURIER_ASSIGNED, CourierAssignedEvent.getDecoder()),
            Map.entry(Topics.DELIVERY_REJECTED, DeliveryRejectedEvent.getDecoder()),
            Map.entry(Topics.PAYMENT_HELD, PaymentHeldEvent.getDecoder()),
            Map.entry(Topics.PAYMENT_REJECTED, PaymentRejectedEvent.getDecoder()),
            Map.entry(Topics.PAYMENT_CAPTURED, PaymentCapturedEvent.getDecoder()),
            Map.entry(Topics.PAYMENT_RELEASED, PaymentReleasedEvent.getDecoder()),
            Map.entry(Topics.PAYMENT_REFUNDED, PaymentRefundedEvent.getDecoder()),
            Map.entry(Topics.RESTAURANT_CATALOG_CHANGED, RestaurantCatalogChangedEvent.getDecoder()),
            Map.entry(Topics.REVIEW_SUBMITTED, ReviewSubmittedEvent.getDecoder()));

    private EventPayloads() {
    }

    public static byte[] encode(SpecificRecord event) {
        BinaryMessageEncoder<SpecificRecord> encoder =
                new BinaryMessageEncoder<>(SpecificData.getForClass(event.getClass()), event.getSchema());
        try {
            ByteBuffer buffer = encoder.encode(event);
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);
            return payload;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode " + event.getSchema().getFullName(), e);
        }
    }

    public static SpecificRecord decode(String topic, byte[] payload) {
        BinaryMessageDecoder<? extends SpecificRecord> decoder = DECODERS.get(topic);
        if (decoder == null) {
            throw new IllegalArgumentException("No event schema is registered for topic " + topic);
        }
        try {
            return decoder.decode(payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decode event of topic " + topic, e);
        }
    }
}
