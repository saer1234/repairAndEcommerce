package com.repairmanagement.repairproject.inventory;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.company.ItemImages;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Entity
@Table(name = "items", uniqueConstraints = @UniqueConstraint(name = "uq_items_company_sku", columnNames = {"company_id", "sku"}))
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 90)
    private String sku;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;


    // category retained for backward compatibility; new classification is deviceType -> deviceBrand.
    @Column(nullable = false, length = 80)
    private String category = "General";

    @Column(name = "device_type", nullable = false, length = 100)
    private String deviceType = "General";

    @Column(name = "device_brand", nullable = false, length = 100)
    private String deviceBrand = "Universal";

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "item_compatibilities", joinColumns = @JoinColumn(name = "item_id"))
    private Set<DeviceCompatibility> compatibleDevices = new LinkedHashSet<>();

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @OneToOne(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private ItemOffer offer;

    /** List price never changes when an offer is configured. */
    public int getDiscountPercent() { return offer == null ? 0 : offer.getDiscountPercent(); }

    public BigDecimal getEffectiveSellingPrice() {
        if (offer == null) return sellingPrice;
        return sellingPrice.multiply(BigDecimal.valueOf(100 - offer.getDiscountPercent()))
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    public void setDiscountPercent(int discountPercent) {
        if (discountPercent < 1 || discountPercent > 90) {
            throw new IllegalArgumentException("Discount must be between 1% and 90%");
        }
        if (sellingPrice == null || sellingPrice.signum() <= 0) {
            throw new IllegalStateException("A positive list price is required to set an offer");
        }
        if (offer == null) offer = new ItemOffer(this, discountPercent);
        else offer.setDiscountPercent(discountPercent);
        if (getEffectiveSellingPrice().signum() <= 0) {
            throw new IllegalArgumentException("Discounted price must be greater than zero");
        }
    }

    public void clearOffer() { offer = null; }

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean active = true;

    // Different from active: a hidden item remains in internal stock and can be used for repairs.
    @Column(name = "visible_to_clients", nullable = false)
    private boolean visibleToClients = true;

    @Column(name = "image_filename", length = 80)
    private String imageFilename;

    @Column(name = "image_content_type", length = 32)
    private String imageContentType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "items")
    private List<ItemImages> itemImages=new ArrayList<>();

    protected Item() {

    }

    public Item(Company company, String sku, String name, String description,
                BigDecimal costPrice, BigDecimal sellingPrice, int openingStock) {

        this.company = company;
        this.sku = sku;
        updateDetails(sku, name, description, costPrice, sellingPrice);
        this.stockQuantity = openingStock;
    }

    public Item(Company company, String sku, String name, String description,
                BigDecimal costPrice, BigDecimal sellingPrice, int openingStock, String category) {
        this(company, sku, name, description, costPrice, sellingPrice, openingStock);
        setCategory(category);

    }

    public void updateDetails(String sku, String name, String description,
                              BigDecimal costPrice, BigDecimal sellingPrice) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
    }

    public void setDeviceClassification(String type, String brand, List<DeviceCompatibilityInput> compatibility) {
        if (type == null || type.isBlank() || type.length() > 100 || brand == null || brand.isBlank() || brand.length() > 100) {
            throw new IllegalArgumentException("Device type and brand are required (max 100 characters)");
        }
        DeviceClassification.requireDeviceType(type, false);
        this.deviceType = type.trim();
        this.deviceBrand = brand.trim();
        // Category is a *component* category (e.g. Screen), not the device type (Phone).
        // Do not overwrite the separately saved component category.
        if (compatibility != null) {
            if (compatibility.size() > 50) throw new IllegalArgumentException("Maximum 50 compatible devices per product");
            Set<DeviceCompatibility> normalized = new LinkedHashSet<>();
            for (DeviceCompatibilityInput d : compatibility) {
                if (d == null || d.deviceType() == null || d.deviceType().isBlank() || d.deviceType().length() > 100 ||
                    d.deviceBrand() == null || d.deviceBrand().isBlank() || d.deviceBrand().length() > 100 ||
                    d.deviceModel() == null || d.deviceModel().isBlank() || d.deviceModel().length() > 120) {
                    throw new IllegalArgumentException("Every compatible device needs type, brand and model");
                }
                DeviceClassification.requireDeviceType(d.deviceType(), true);
                normalized.add(new DeviceCompatibility(d.deviceType(), d.deviceBrand(), d.deviceModel()));
            }
            compatibleDevices.clear();
            compatibleDevices.addAll(normalized);
        }
    }
    /** For legacy parts with no compatibility records, fit is unknown, not confirmed. */
    public boolean compatibilityUnspecified() { return compatibleDevices.isEmpty(); }
    public boolean supportsDevice(String type, String brand, String model) {
        return compatibleDevices.stream().anyMatch(c -> c.matches(type, brand, model));
    }
    public String getDeviceType() { return deviceType; }
    public String getDeviceBrand() { return deviceBrand; }
    public List<DeviceCompatibilityView> getCompatibleDevices() {
        return compatibleDevices.stream().map(DeviceCompatibilityView::of).toList();
    }

    public void setCategory(String category) {
        this.category = category == null || category.isBlank() ? "General" : category.trim();
    }
    public String getCategory() { return category; }

    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public void deactivate() {
        this.active = false;
        this.visibleToClients = false;
    }
    public void restore() {
        this.active = true;
        // Do not unexpectedly publish a restored item to customers.
        this.visibleToClients = false;
    }
    public void setVisibleToClients(boolean visible) {
        if (!active && visible) throw new IllegalStateException("Restore the item before publishing");
        this.visibleToClients = visible;
    }
    public boolean isVisibleToClients() { return visibleToClients; }
    public void setImage(String filename, String contentType) {
        this.imageFilename = filename;
        this.imageContentType = contentType;
    }
    public void clearImage() { setImage(null, null); }
    public String getImageFilename() { return imageFilename; }
    public String getImageContentType() { return imageContentType; }
    public String getImageUrl() {
        return imageFilename == null ? null :
            "/api/companies/" + company.getId() + "/items/" + id + "/image";
    }

    @PrePersist
    void onCreate() {

        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public int getStockQuantity() { return stockQuantity; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
