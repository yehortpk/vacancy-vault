package com.github.yehortpk.crawler.models.company;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_cookies")
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCookie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;
    @Column(name="cookie_key")
    private String key;
    @Column(name="cookie_value", columnDefinition = "TEXT")
    private String value;
}