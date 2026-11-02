package com.repairmanagement.repairproject.inventory;
import com.repairmanagement.repairproject.company.Company;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ItemCategoryTest {
    @Test void categoryIsShownToCustomersWithoutCostPrice() {
        Item i=new Item(new Company("Shop",null,null,null,"EUR"),"SCR-01","Screen",null,
            new BigDecimal("25.00"),new BigDecimal("50.00"),3,"Replacement parts");
        assertEquals("Replacement parts",PublicItemView.of(i).category());
        assertEquals("Replacement parts",ManagerItemView.of(i).category());
        assertFalse(java.util.Arrays.stream(PublicItemView.class.getRecordComponents())
            .anyMatch(c->c.getName().equals("costPrice")));
    }
}
