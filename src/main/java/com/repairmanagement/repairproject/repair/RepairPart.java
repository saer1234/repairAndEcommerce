package com.repairmanagement.repairproject.repair;

import com.repairmanagement.repairproject.inventory.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "repair_parts")
public class RepairPart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_id", nullable = false)
    private RepairRequest repair;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitSellingPrice;

    @Column(name = "unit_cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCostPrice;

    protected RepairPart() {}

    public RepairPart(RepairRequest repair, Item item, int quantity) {
        this.repair = repair;
        this.item = item;
        this.quantity = quantity;
        this.unitSellingPrice = item.getSellingPrice();
        this.unitCostPrice = item.getCostPrice();
    }

    public Long getId() { return id; }
    public RepairRequest getRepair() { return repair; }
    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitSellingPrice() { return unitSellingPrice; }
    public BigDecimal getUnitCostPrice() { return unitCostPrice; }
}
