package com.repairmanagement.repairproject.repair;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateRepairRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validClientContactAndDeviceAreAccepted() {
        assertTrue(validator.validate(new CreateRepairRequest(
            "+961 70 123 456", "Phone", "Samsung", "S24", "Cracked screen"
        )).isEmpty());
    }

    @Test
    void rejectsMissingPhone() {
        assertFalse(validator.validate(new CreateRepairRequest(
            "", "Phone", "Samsung", "S24", "Broken screen"
        )).isEmpty());
    }

    @Test
    void selfServiceRequestHasNoArbitraryClientNameField() {
        boolean hasClientName = java.util.Arrays.stream(CreateRepairRequest.class.getRecordComponents())
            .anyMatch(component -> component.getName().equals("clientName"));
        assertFalse(hasClientName);
    }

    @Test
    void rejectsShortPhoneNumber() {
        assertFalse(validator.validate(new CreateRepairRequest(
            "123", "Phone", "Samsung", "S24", "Broken screen"
        )).isEmpty());
    }
}
