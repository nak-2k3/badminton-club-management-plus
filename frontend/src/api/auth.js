import http from './http'

export const authApi = {
  login: (credentials) => http.post('/auth/login', credentials),
  me: () => http.get('/auth/me')
}
