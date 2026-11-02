package com.repairmanagement.repairproject.whish;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhishReferenceRequestValidationTest {
    private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();

    @Test void validTransferContactAccepted(){
        var request=new WhishReferenceRequest("WHISH-XYZ123","Sender Example","+961 70 123456");
        assertTrue(validator.validate(request).isEmpty());
    }
    @Test void blankSenderNameRejected(){
        var request=new WhishReferenceRequest("WHISH-XYZ123"," ","+96170123456");
        assertFalse(validator.validate(request).isEmpty());
    }
    @Test void invalidSenderPhoneRejected(){
        var request=new WhishReferenceRequest("WHISH-XYZ123","Sender Example","abc<script>");
        assertFalse(validator.validate(request).isEmpty());
    }
}
