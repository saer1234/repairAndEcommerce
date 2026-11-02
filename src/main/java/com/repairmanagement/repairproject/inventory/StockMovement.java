package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_user_id")
    private User performedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "movement_type", nullable = false, length = 25)
    private MovementType movementType;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StockMovement() {}

    public StockMovement(Item item, User performedBy, MovementType type, int quantityChange, String note) {
        this.item = item;
        this.performedBy = performedBy;
        this.movementType = type;
        this.quantityChange = quantityChange;
        this.note = note;
    }

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public Item getItem() { return item; }
    public User getPerformedBy() { return performedBy; }
    public MovementType getMovementType() { return movementType; }
    public int getQuantityChange() { return quantityChange; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
