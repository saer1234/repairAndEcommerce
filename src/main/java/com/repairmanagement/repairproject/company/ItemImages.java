package com.repairmanagement.repairproject.company;


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.repairmanagement.repairproject.inventory.Item;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

@Entity
@Table(name="item_images")
@Getter
@Setter
@NoArgsConstructor
public class ItemImages {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY )
    private long ID;

    @ManyToOne(fetch= FetchType.LAZY, optional=false)
    @JoinColumn(name="items_id",nullable=false)
    private Item items;

    @Column(name="image_filename",nullable = false)
    private String imageFilename;
    @Column(name="image_type",nullable = false)
    private String imageType;
    @Column(name="sort_order",nullable = false)
    private int sortOrder;
    @Column(name="is_primary", nullable = false)
    private boolean isPrimary;
    @Column(name="created_at",nullable= false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate(){
    this.createdAt= LocalDateTime.now(ZoneOffset.UTC);
    }
}
