export type Role = 'USER' | 'ADMIN'

export type Account = {
    id: number
    email: string
    role: Role
    createdAt: string
    apiKeyPrefix: string | null
    hasApiKey: boolean
}

export type TokenPair = {
    accessToken: string
    refreshToken: string
}

export type Connection = {
    id: number
    provider: string
    title: string | null
    address: string
    linkedAt: string
}
