/** A company as the dashboard knows it: from its stored vacancies. */
export type Company = {
    company_id: number
    title: string|null
    /** The company's main page; null when the crawler has none */
    company_url: string|null
}
