package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceLineView(String description,BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal) {
    static InvoiceLineView from(InvoiceLine l){return new InvoiceLineView(l.getDescription(),l.getQuantity(),
        l.getUnitPrice(),l.getQuantity().multiply(l.getUnitPrice()));}
}
