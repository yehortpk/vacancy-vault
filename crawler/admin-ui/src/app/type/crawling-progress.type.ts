export interface PageLog {
    page_id: number,
    message: string
}

export interface PageProgress {
    id: number,
    status: number,
    crawledVacanciesCnt: number
    logs: PageLog[]
}

export type CrawlerStatus = 'RUNNING' | 'FINISHED' | 'ERROR';

export type CrawlingStatus = 'IDLE' | 'RUNNING' | 'STOPPING' | 'FINISHED' | 'STOPPED' | 'FAILED';

export interface CrawlerProgress {
    id: number
    title: string
    status: CrawlerStatus
    pages: PageProgress[]
}

export interface CrawlingProgress {
    crawlers: CrawlerProgress[]
    status: CrawlingStatus
    finished: boolean
    total: number
    new: number
    outdated: number
    started_at: string | null
    finished_at: string | null
}
