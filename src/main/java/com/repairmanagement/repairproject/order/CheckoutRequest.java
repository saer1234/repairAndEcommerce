package com.repairmanagement.repairproject.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// Coordinates identify a delivery pin selected explicitly by the customer.
// Do not accept a client name or user ID: the account comes from authentication.
public record CheckoutRequest(
    @NotBlank @Size(max=40) String deliveryPhone,
    @NotBlank @Size(max=255) String streetAddress,
    @NotBlank @Size(max=120) String city,
    @Size(max=24) String postalCode,
    @NotBlank @Size(max=100) String country,
    @Size(max=500) String deliveryNotes,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
) {}
