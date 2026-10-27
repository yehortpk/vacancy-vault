export interface PageLog {
    page_id: number,
    message: string
}

export interface PageProgress {
    id: number,
    status: number,
    parsedVacanciesCnt: number
    logs: PageLog[]
}

export type ParserStatus = 'RUNNING' | 'FINISHED' | 'ERROR';

export type ParsingStatus = 'IDLE' | 'RUNNING' | 'STOPPING' | 'FINISHED' | 'STOPPED';

export interface ParserProgress {
    id: number
    title: string
    status: ParserStatus
    pages: PageProgress[]
}

export interface ParsingProgress {
    parsers: ParserProgress[]
    status: ParsingStatus
    finished: boolean
    total: number
    new: number
    outdated: number
    started_at: string | null
    finished_at: string | null
}
