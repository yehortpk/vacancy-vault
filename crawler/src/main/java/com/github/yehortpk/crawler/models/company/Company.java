package com.github.yehortpk.crawler.models.company;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

/**
 * A site the crawler crawls. Companies are added by hand, in this schema only.
 */
@Entity
@Table(name = "company")
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    @Id
    @EqualsAndHashCode.Include
    private int companyId;
    private String beanClass;
    @ToString.Include
    private String title;

    @Column(name = "main_page_url")
    private String mainPageURL;
    @Column(name = "vacancies_url")
    private String vacanciesURL;
    @Column(name = "api_vacancies_url")
    private String apiVacanciesURL;

    @Column(name = "crawling_enabled")
    private boolean isCrawlingEnabled;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Set<CompanyData> companyData;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Set<CompanyHeader> companyHeaders;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "company")
    private Set<CompanyCookie> companyCookies;
}
