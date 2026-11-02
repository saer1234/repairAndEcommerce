package com.repairmanagement.repairproject.whish;
import java.math.BigDecimal;
public record WhishInstructions(String receiverPhone,BigDecimal amount,String currencyCode,
                                String orderNumber,String invoiceNumber) {}
