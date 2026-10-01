import http from './http'

export const catalogApi = {
  roles: () => http.get('/roles'),
  levels: () => http.get('/levels')
}
