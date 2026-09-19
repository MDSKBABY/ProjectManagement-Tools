export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface UserSummary {
  id: number
  username: string
  displayName: string
  email: string | null
  mobile: string | null
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface Pagination {
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export interface PageResponse<T> {
  data: T[]
  pagination: Pagination
}

export interface LoginInput {
  username: string
  password: string
}

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
}

export interface CreateUserInput {
  username: string
  initialPassword: string
  displayName: string
  email?: string
  mobile?: string
}
