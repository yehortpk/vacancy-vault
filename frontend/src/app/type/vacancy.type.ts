export type Vacancy = {
    id: number
    title: string
    /** null when the source did not publish a lower bound */
    minSalary: number|null
    /** null when the source did not publish an upper bound */
    maxSalary: number|null
    /** Currency code such as USD; null when the source did not say */
    salaryCurrency: string|null
    /** Payout period such as MONTH; null when the source did not say */
    salaryPeriod: string|null
    url: string
    company: {
        company_id: number
        title: string|null
        imageUrl?: string|null
        company_url: string|null
    }
    crawledAt: string|null
    remote: boolean
    /** ISO 3166-1 alpha-2 codes such as PL; empty when the source did not say */
    countries: string[]
}

/** A vacancy as its own page shows it; listings leave the description out. */
export type VacancyDetails = Vacancy & {
    description: string|null
}

export type VacanciesListDTO = {
    vacancies: Vacancy[]
    currentPage: number
    pageSize: number
    totalVacancies: number
    totalPages: number
}

/** Narrowing a vacancy listing on top of its query; false or null leaves that condition off. */
export type VacancyConditions = {
    remoteRequired: boolean
    salaryRequired: boolean
    /** ISO 3166-1 alpha-2 code the vacancy must be offered in */
    country: string|null
}

/** Query params the dashboard service expects for the conditions; only the switched-on ones are sent. */
export function createConditionParams(conditions: VacancyConditions): Record<string, boolean|string> {
    return {
        ...(conditions.remoteRequired ? { remote: true } : {}),
        ...(conditions.salaryRequired ? { withSalary: true } : {}),
        ...(conditions.country ? { country: conditions.country } : {}),
    }
}
