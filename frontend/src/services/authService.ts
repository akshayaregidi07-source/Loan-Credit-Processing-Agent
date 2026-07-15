import type { AxiosInstance } from 'axios'

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token: string
  role: string
}

/** Authenticates the user and returns a signed JWT with the user's role. */
export async function login(api: AxiosInstance, request: LoginRequest): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>('/auth/login', request)
  return response.data
}
