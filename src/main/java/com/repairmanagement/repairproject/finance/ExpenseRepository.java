package com.repairmanagement.repairproject.finance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExpenseRepository extends JpaRepository<Expense,Long>{
    List<Expense> findByCompany_IdOrderByExpenseDateDescIdDesc(Long companyId);
    List<Expense> findByCompanyIsNullOrderByExpenseDateDescIdDesc();
}
