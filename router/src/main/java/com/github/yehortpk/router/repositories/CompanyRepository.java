package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.company.Company;
import lombok.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {
    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = {"companyData", "companyHeaders"})
    @NonNull
    List<Company> findAll();
    Company findByCompanyId(long companyId);
}
