package com.repairmanagement.repairproject.order;

import java.math.BigDecimal;

public record DeliveryView(String phone, String streetAddress, String city,
    String postalCode, String country, String notes, BigDecimal latitude, BigDecimal longitude) {
    static DeliveryView from(CustomerOrder order) {
        return new DeliveryView(order.getDeliveryPhone(), order.getDeliveryStreet(),
            order.getDeliveryCity(), order.getDeliveryPostalCode(), order.getDeliveryCountry(),
            order.getDeliveryNotes(), order.getDeliveryLatitude(), order.getDeliveryLongitude());
    }
}
