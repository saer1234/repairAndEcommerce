package com.repairmanagement.repairproject.order;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutRequestValidationTest {
    @Test
    void deliveryLocationAndAddressAreRequired() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var invalid = new CheckoutRequest("", "", "Kladno", null, "Czechia", null, null, null);
            var violations = validator.validate(invalid);
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("deliveryPhone")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("streetAddress")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("latitude")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("longitude")));
        }
    }

    @Test
    void latitudeAndLongitudeOutsideEarthBoundsAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalid = new CheckoutRequest("12345", "Main Street", "City", "", "Country", "",
                new BigDecimal("91"), new BigDecimal("181"));
            assertFalse(factory.getValidator().validate(invalid).isEmpty());
        }
    }
}
