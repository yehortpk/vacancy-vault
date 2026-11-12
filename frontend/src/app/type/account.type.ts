export type Role = 'USER' | 'ADMIN'

export type Account = {
    id: number
    email: string
    role: Role
    createdAt: string
}

export type TokenPair = {
    accessToken: string
    refreshToken: string
}

/** A provider the user can link, from the router's catalog. */
export type Provider = {
    id: number
    title: string
}

export type Connection = {
    id: number
    providerId: number
    title: string | null
    /** The provider's display form of the destination; null if its details are missing. */
    address: string | null
    linkedAt: string
}

export type LinkSessionStatus = 'PENDING' | 'LINKED' | 'EXPIRED'

export type LinkSession = {
    id: number
    providerId: number
    status: LinkSessionStatus
    expiresAt: string
    /** The one-time token; only in the response that started the session. */
    token: string | null
    /** The provider's instructions with the token filled in; only in that same response. */
    instructions: string | null
}
