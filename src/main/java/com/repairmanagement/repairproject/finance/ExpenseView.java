package com.repairmanagement.repairproject.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseView(Long id,String category,String description,BigDecimal amount,String currencyCode,LocalDate expenseDate){
    static ExpenseView from(Expense e){return new ExpenseView(e.getId(),e.getCategory(),e.getDescription(),e.getAmount(),e.getCurrencyCode(),e.getExpenseDate());}
}
