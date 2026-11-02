package com.repairmanagement.repairproject.user;

import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationTypeValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test void requiresOwnerOrClient() {
        assertFalse(validator.validate(new RegisterRequest("Person", "a@b.com", "password123", null, null)).isEmpty());
        assertTrue(validator.validate(new RegisterRequest("Person", "a@b.com", "password123", null, RegistrationType.CLIENT)).isEmpty());
    }

    @Test void cannotRegisterAsAdminOrManager() {
        assertThrows(Exception.class, () -> mapper.readValue("{\"fullName\":\"Test\",\"email\":\"a@b.com\",\"password\":\"password123\",\"accountType\":\"SUPER_ADMIN\"}", RegisterRequest.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"fullName\":\"Test\",\"email\":\"a@b.com\",\"password\":\"password123\",\"accountType\":\"MANAGER\"}", RegisterRequest.class));
    }
}
