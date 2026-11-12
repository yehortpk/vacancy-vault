export type Filter = {
    filterId: number
    title: string
    /** The expression, e.g. "Google, (developer|engineer) java -senior". */
    filter: string
    /** The linked chats it notifies; empty for a draft. */
    connectionIds: number[]
}

/** What the router takes to create or replace a filter. */
export type FilterRequest = {
    title: string
    filter: string
    connectionIds: number[]
}
