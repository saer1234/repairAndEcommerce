package com.repairmanagement.repairproject.finance;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.order.*;
import com.repairmanagement.repairproject.repair.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyRevenueRangeService {
    private final InventoryAccess access;
    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final RepairRequestRepository repairs;
    private final RepairPartRepository parts;
    public CompanyRevenueRangeService(InventoryAccess access, CustomerOrderRepository orders,
       OrderItemRepository orderItems, RepairRequestRepository repairs, RepairPartRepository parts) {
        this.access=access; this.orders=orders; this.orderItems=orderItems; this.repairs=repairs; this.parts=parts;
    }
    @Transactional(readOnly=true)
    public CompanyRevenueRangeView report(Long companyId, LocalDate from, LocalDate to, String email) {
        access.requireManager(companyId,email);
        if (from == null || to == null || from.isAfter(to) ||
            from.isBefore(LocalDate.now(java.time.ZoneOffset.UTC).minusYears(5)) ||
            to.isAfter(LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1)) ||
            java.time.temporal.ChronoUnit.DAYS.between(from,to)>366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid date range up to one year");
        var start=from.atStartOfDay(); var end=to.plusDays(1).atStartOfDay();
        var completed=orders.findByCompany_IdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            companyId,OrderStatus.COMPLETED,start,end);
        BigDecimal product=BigDecimal.ZERO, delivery=BigDecimal.ZERO, repair=BigDecimal.ZERO;
        for (CustomerOrder o:completed) {
            delivery=delivery.add(o.getDeliveryFee());
            for (OrderItem i:orderItems.findByOrder_IdOrderByIdAsc(o.getId()))
                product=product.add(i.getUnitSellingPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        }
        var completedRepairs=repairs.findByCompany_IdAndStatusInAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            companyId,List.of(RepairStatus.COMPLETED,RepairStatus.DELIVERED),start,end);
        for (RepairRequest r:completedRepairs) {
            repair=repair.add(r.getLaborCharge());
            for (RepairPart part:parts.findByRepair_IdOrderByIdAsc(r.getId()))
                repair=repair.add(part.getUnitSellingPrice().multiply(BigDecimal.valueOf(part.getQuantity())));
        }
        return new CompanyRevenueRangeView(from,to,access.requireActiveCompany(companyId).getCurrencyCode(),
            completed.size(),completedRepairs.size(),product,delivery,repair,product.add(delivery).add(repair));
    }
}
