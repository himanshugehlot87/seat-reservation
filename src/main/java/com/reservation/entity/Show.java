package com.reservation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shows")
@Getter
@Setter
public class Show {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private Integer pricePaise;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 4;
}
