package com.github.yehortpk.dashboard.models.company;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CompanyShortInfoDTO {
    @JsonProperty("company_id")
    private long companyId;
    @JsonProperty("title")
    private String companyTitle;
    @JsonProperty("company_url")
    private String companyURL;
}
