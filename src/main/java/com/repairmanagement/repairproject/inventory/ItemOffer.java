package com.repairmanagement.repairproject.inventory;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** One current percentage offer per item; historical sale prices are snapshotted on order lines. */
@Entity
@Table(name = "item_offers")
public class ItemOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, unique = true)
    private Item item;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ItemOffer() {}

    ItemOffer(Item item, int discountPercent) {
        this.item = item;
        setDiscountPercent(discountPercent);
    }

    void setDiscountPercent(int value) {
        if (value < 1 || value > 90) throw new IllegalArgumentException("Discount must be between 1% and 90%");
        this.discountPercent = value;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public int getDiscountPercent() { return discountPercent; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
