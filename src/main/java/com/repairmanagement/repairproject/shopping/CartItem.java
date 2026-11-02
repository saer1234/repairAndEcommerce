package com.repairmanagement.repairproject.shopping;

import com.repairmanagement.repairproject.inventory.Item;
import jakarta.persistence.*;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private ShoppingCart cart;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @Column(nullable = false)
    private int quantity;

    protected CartItem() {}
    public CartItem(ShoppingCart cart, Item item, int quantity) {
        this.cart = cart;
        this.item = item;
        this.quantity = quantity;
    }
    public Long getId() { return id; }
    public ShoppingCart getCart() { return cart; }
    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
