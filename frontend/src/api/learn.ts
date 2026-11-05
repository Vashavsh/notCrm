import { client } from './client'
import type { LearnAccessDto } from '../types'

export const learnApi = {
  get: (studentId: string) => client.get<LearnAccessDto>(`/students/${studentId}/learn`).then((r) => r.data),

  issue: (studentId: string) => client.post<LearnAccessDto>(`/students/${studentId}/learn/issue`).then((r) => r.data),

  resetPassword: (studentId: string) =>
    client.post<LearnAccessDto>(`/students/${studentId}/learn/reset-password`).then((r) => r.data),
}
