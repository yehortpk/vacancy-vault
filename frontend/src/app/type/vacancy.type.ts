export type Vacancy = {
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
    parsedAt: string|null
    remote: boolean
}

export type VacanciesListDTO = {
    vacancies: Vacancy[]
    currentPage: number
    pageSize: number
    totalVacancies: number
    totalPages: number
}

/** Narrowing a vacancy listing on top of its query or saved filter; false leaves that condition off. */
export type VacancyConditions = {
    remoteRequired: boolean
    salaryRequired: boolean
}

/** Query params the router expects for the conditions; only the switched-on ones are sent. */
export function createConditionParams(conditions: VacancyConditions): Record<string, boolean> {
    return {
        ...(conditions.remoteRequired ? { remote: true } : {}),
        ...(conditions.salaryRequired ? { withSalary: true } : {}),
    }
}
