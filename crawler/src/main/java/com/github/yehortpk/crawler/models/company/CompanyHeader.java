package com.github.yehortpk.crawler.models.company;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_headers")
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyHeader {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;
    @Column(name="header_key")
    private String key;
    @Column(name="header_value", columnDefinition = "TEXT")
    private String value;
}
