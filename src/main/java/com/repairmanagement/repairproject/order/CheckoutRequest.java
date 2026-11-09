package com.repairmanagement.repairproject.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// Coordinates identify a delivery pin selected explicitly by the customer.
// Do not accept a client name or user ID: the account comes from authentication.
public record CheckoutRequest(
    @Size(max=40) String deliveryPhone,
    @Size(max=255) String streetAddress,
    @Size(max=120) String city,
    @Size(max=24) String postalCode,
    @Size(max=100) String country,
    @Size(max=500) String deliveryNotes,
    @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
    @DecimalMin("0.00") BigDecimal expectedTotal,
    FulfillmentMethod fulfillmentMethod
) {
    // Compatibility for previous client and unit tests which did not specify fulfillment.
    public CheckoutRequest(String deliveryPhone, String streetAddress, String city, String postalCode,
                           String country, String deliveryNotes, BigDecimal latitude, BigDecimal longitude,
                           BigDecimal expectedTotal) {
        this(deliveryPhone, streetAddress, city, postalCode, country, deliveryNotes,
             latitude, longitude, expectedTotal, FulfillmentMethod.DELIVERY);
    }
    public FulfillmentMethod effectiveMethod() {
        return fulfillmentMethod == null ? FulfillmentMethod.DELIVERY : fulfillmentMethod;
    }
    @AssertTrue(message="Delivery requires phone, address, city, country and map coordinates")
    public boolean isDeliveryDetailsComplete() {
        if (effectiveMethod()==FulfillmentMethod.PICKUP) return true;
        return deliveryPhone!=null && !deliveryPhone.isBlank() &&
               streetAddress!=null && !streetAddress.isBlank() &&
               city!=null && !city.isBlank() && country!=null && !country.isBlank() &&
               latitude!=null && longitude!=null;
    }
    // Keep existing code/test constructors compatible.
    public CheckoutRequest(String deliveryPhone, String streetAddress, String city, String postalCode,
                           String country, String deliveryNotes, BigDecimal latitude, BigDecimal longitude) {
        this(deliveryPhone, streetAddress, city, postalCode, country, deliveryNotes, latitude, longitude, null, FulfillmentMethod.DELIVERY);
    }
}
