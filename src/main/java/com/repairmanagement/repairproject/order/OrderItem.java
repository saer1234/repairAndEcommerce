package com.repairmanagement.repairproject.order;

import com.repairmanagement.repairproject.inventory.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @Column(name = "item_name_snapshot", nullable = false, length = 180)
    private String itemNameSnapshot;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitSellingPrice;
    @Column(name = "unit_cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCostPrice;

    protected OrderItem() {}
    public OrderItem(CustomerOrder order, Item item, int quantity) {
        this.order = order;
        this.item = item;
        this.quantity = quantity;
        this.itemNameSnapshot = item.getName();
        this.unitSellingPrice = item.getSellingPrice();
        this.unitCostPrice = item.getCostPrice();
    }
    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public Item getItem() { return item; }
    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitSellingPrice() { return unitSellingPrice; }
    public BigDecimal getUnitCostPrice() { return unitCostPrice; }
}
