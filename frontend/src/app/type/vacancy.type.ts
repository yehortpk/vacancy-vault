export type Vacancy = {
    title: string
    /** 0 when the source did not publish a lower bound */
    minSalary: number
    /** 0 when the source did not publish an upper bound */
    maxSalary: number
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