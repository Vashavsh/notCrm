import { useCallback, useEffect, useState } from 'react'
import { learnApi } from '../api/learn'
import { apiErrorMessage, useToast } from './useToast'
import type { LearnAccessDto } from '../types'

/** The student's login on the course site and their progress; loaded when the student's card opens. */
export function useLearnAccess(studentId: string) {
  const { push } = useToast()
  const [data, setData] = useState<LearnAccessDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    let cancelled = false
    setLoading(true)
    setFailed(false)
    learnApi
      .get(studentId)
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [studentId])

  useEffect(() => load(), [load])

  async function act(call: (id: string) => Promise<LearnAccessDto>, success: string) {
    setBusy(true)
    try {
      setData(await call(studentId))
      push('success', success)
    } catch (err) {
      push('error', apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return {
    data,
    loading,
    failed,
    busy,
    reload: load,
    issue: () => act(learnApi.issue, 'Доступ выдан'),
    resetPassword: () => act(learnApi.resetPassword, 'Новый пароль создан'),
    close: () => act(learnApi.close, 'Доступ закрыт'),
    reopen: () => act(learnApi.reopen, 'Доступ открыт снова'),
  }
}

/** How long the student has been away: from the last visit, or from the day access was issued if they never came. */
export function daysAway(data: LearnAccessDto | null): number | null {
  const account = data?.account
  if (!account || account.status !== 'ACTIVE') return null
  const since = new Date(account.lastSeenAt ?? account.issuedAt).getTime()
  return Math.max(0, Math.floor((Date.now() - since) / 86_400_000))
}
