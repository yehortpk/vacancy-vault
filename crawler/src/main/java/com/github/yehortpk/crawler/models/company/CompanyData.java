package com.github.yehortpk.crawler.models.company;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_data")
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;
    @Column(name="data_key")
    private String key;
    @Column(name="data_value")
    private String value;
}
