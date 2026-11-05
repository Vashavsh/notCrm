import { useState } from 'react'
import { Modal } from '../../components/Modal'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { NotesList } from '../../components/NotesList'
import { TelegramLink } from '../../components/TelegramLink'
import { Avatar } from '../../components/Avatar'
import { StudentAvatar } from '../../components/StudentAvatar'
import { useAuth } from '../../auth/useAuth'
import { useCurators } from '../../hooks/useCurators'
import { useCohorts } from '../../hooks/useCohorts'
import { useToast, apiErrorMessage } from '../../hooks/useToast'
import { useAutoListTextarea } from '../../hooks/useAutoListTextarea'
import { studentsApi } from '../../api/students'
import { formatDate, toDateInputValue } from '../../utils/dates'
import { StudentComments } from './StudentComments'
import { StudentHistory } from './StudentHistory'
import { LearnTab } from './LearnTab'
import { daysAway, useLearnAccess } from '../../hooks/useLearnAccess'
import type { StageDto, StudentDto } from '../../types'

const tabClass = (on: boolean) =>
  `flex items-center gap-1.5 text-[13px] font-semibold px-3 py-2 -mb-px border-b-2 cursor-pointer ${
    on ? 'text-accent border-accent' : 'text-ink-600 border-transparent hover:text-ink-900'
  }`

function plannedCompletionLabel(student: StudentDto, stages: StageDto[]): string {
  if (student.isPaused) return 'на паузе'
  const idx = stages.findIndex((s) => s.id === student.currentStageId)
  if (idx === -1) return '—'
  let remaining = 0
  const norm = stages[idx].normDays
  if (norm != null) remaining += Math.max(norm - student.daysOnStage, 0)
  for (let i = idx + 1; i < stages.length; i++) {
    if (stages[i].normDays != null) remaining += stages[i].normDays!
  }
  const d = new Date()
  d.setDate(d.getDate() + remaining)
  return formatDate(d.toISOString())
}

export function StudentDetailsModal({
  student,
  stages,
  onClose,
  onChanged,
}: {
  student: StudentDto
  stages: StageDto[]
  onClose: () => void
  onChanged: () => void
}) {
  const { user } = useAuth()
  const { curators, displayName } = useCurators()
  const { cohorts, refetch: refetchCohorts } = useCohorts()
  const { push } = useToast()
  const learn = useLearnAccess(student.id)
  const [tab, setTab] = useState<'card' | 'learn'>('card')
  const away = daysAway(learn.data)
  const isAdmin = user?.role === 'ADMIN'
  const canReassignCurator = isAdmin || !!user?.canReassign
  const canDelete = isAdmin || !!user?.canDeleteStudents
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [refreshingPhoto, setRefreshingPhoto] = useState(false)
  const [startedAt, setStartedAt] = useState(toDateInputValue(student.startedAt))

  const [editing, setEditing] = useState(false)
  const [draftName, setDraftName] = useState(student.fullName)
  const [postpay, setPostpay] = useState(student.postpayPercent?.toString() ?? '')
  const [draftTelegram, setDraftTelegram] = useState(student.telegramUsername ?? '')
  const draftNotes = useAutoListTextarea(student.notes.map((n) => n.text).join('\n'))

  const cur = displayName(student.curatorId)

  async function togglePause() {
    setBusy(true)
    try {
      if (student.isPaused) await studentsApi.resume(student.id)
      else await studentsApi.pause(student.id)
      onChanged()
    } catch (err) {
      push('error', apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function refreshPhoto() {
    setRefreshingPhoto(true)
    try {
      const updated = await studentsApi.refreshAvatar(student.id)
      if (updated.avatarVersion) push('success', 'Фото обновлено')
      else push('error', 'В Telegram фото не найдено или скрыто')
      onChanged()
    } catch (err) {
      push('error', apiErrorMessage(err))
    } finally {
      setRefreshingPhoto(false)
    }
  }

  async function deleteStudent() {
    setBusy(true)
    try {
      await studentsApi.remove(student.id)
      push('success', `${student.fullName} удалён`)
      onChanged()
      onClose()
    } catch (err) {
      push('error', apiErrorMessage(err))
      setBusy(false)
    }
  }

  async function reassign(newCuratorId: string) {
    setBusy(true)
    try {
      await studentsApi.assignCurator(student.id, newCuratorId)
      push('success', 'Куратор изменён')
      onChanged()
    } catch (err) {
      push('error', apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function saveStartedAt() {
    if (!startedAt) return
    const iso = new Date(startedAt).toISOString()
    if (iso === new Date(student.startedAt).toISOString()) return
    setBusy(true)
    try {
      const updated = await studentsApi.update(student.id, { startedAt: iso })
      // The backend re-derives the cohort from the new start month (any year) and may have just created it.
      await refetchCohorts()
      push('success', updated.cohortId !== student.cohortId ? 'Дата начала обновлена, когорта пересчитана' : 'Дата начала обновлена')
      onChanged()
    } catch (err) {
      push('error', apiErrorMessage(err))
      setStartedAt(toDateInputValue(student.startedAt))
    } finally {
      setBusy(false)
    }
  }

  async function savePostpay() {
    const value = Number(postpay)
    const valid = postpay.trim() !== '' && Number.isInteger(value) && value >= 0 && value <= 100
    if (!valid) {
      if (postpay.trim() !== '') push('error', 'Постоплата: целое число от 0 до 100')
      setPostpay(student.postpayPercent?.toString() ?? '')
      return
    }
    if (value === student.postpayPercent) return
    setBusy(true)
    try {
      await studentsApi.update(student.id, { postpayPercent: value })
      push('success', 'Постоплата обновлена')
      onChanged()
    } catch (err) {
      push('error', apiErrorMessage(err))
      setPostpay(student.postpayPercent?.toString() ?? '')
    } finally {
      setBusy(false)
    }
  }

  function startEditing() {
    setDraftName(student.fullName)
    setDraftTelegram(student.telegramUsername ?? '')
    draftNotes.setValue(student.notes.map((n) => n.text).join('\n'))
    setEditing(true)
  }

  async function saveEdits() {
    if (!draftName.trim()) return
    setBusy(true)
    try {
      await studentsApi.update(student.id, {
        fullName: draftName.trim(),
        telegramUsername: draftTelegram.trim(),
        notes: draftNotes.value
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean)
          .map((text, position) => ({ text, position })),
      })
      push('success', 'Данные ученика обновлены')
      setEditing(false)
      onChanged()
    } catch (err) {
      push('error', apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      leading={
        <div className="relative flex-shrink-0">
          <StudentAvatar student={student} size={64} />
          {student.telegramUsername && !editing && (
            <button
              className="absolute -right-1 -bottom-1 w-6 h-6 rounded-full bg-surface border border-border text-ink-600 hover:text-accent text-[13px] leading-none flex items-center justify-center cursor-pointer disabled:opacity-60"
              onClick={refreshPhoto}
              disabled={refreshingPhoto}
              title="Обновить фото из Telegram"
              aria-label="Обновить фото из Telegram"
            >
              <span className={refreshingPhoto ? 'inline-block animate-spin' : ''}>↻</span>
            </button>
          )}
        </div>
      }
      title={
        editing ? (
          <input
            className="input font-display font-bold text-lg w-full"
            value={draftName}
            onChange={(e) => setDraftName(e.target.value)}
          />
        ) : (
          student.fullName
        )
      }
      subtitle={
        editing ? (
          <>
            <label className="text-xs font-semibold text-ink-600 flex items-center gap-2">
              Telegram
              <input
                className="input w-40"
                value={draftTelegram}
                onChange={(e) => setDraftTelegram(e.target.value)}
                placeholder="@username"
              />
            </label>
          </>
        ) : (
          <>
            <TelegramLink value={student.telegramUsername} className="font-semibold" />
            <span className="font-mono text-xs bg-success-soft text-success px-2 py-0.5 rounded-md font-semibold">
              постоплата · {student.postpayPercent != null ? `${student.postpayPercent}%` : '—'}
            </span>
            <span className="font-mono text-xs bg-accent-soft text-accent px-2 py-0.5 rounded-md font-semibold">
              план завершения · {plannedCompletionLabel(student, stages)}
            </span>
          </>
        )
      }
      onClose={onClose}
    >
      <div className="flex gap-0.5 border-b border-border mt-3.5">
        <button className={tabClass(tab === 'card')} onClick={() => setTab('card')}>
          Карточка
        </button>
        <button className={tabClass(tab === 'learn')} onClick={() => setTab('learn')}>
          Учебный сайт
          {away != null && away >= 7 && (
            <i
              className={`w-2 h-2 rounded-full ${away >= 14 ? 'bg-warn' : 'bg-amber'}`}
              title={`Не заходил ${away} дн.`}
              aria-label={`Не заходил ${away} дн.`}
            />
          )}
        </button>
      </div>

      {tab === 'learn' ? (
        <LearnTab student={student} learn={learn} />
      ) : (
        <>
      <div className="text-xs font-semibold text-ink-600 uppercase tracking-wide mt-4 mb-2">Куратор</div>
      <div className="flex items-center gap-2 mb-3">
        <Avatar name={cur.name} color={cur.color} />
        <select
          className="input"
          value={student.curatorId}
          disabled={!canReassignCurator || busy}
          onChange={(e) => reassign(e.target.value)}
        >
          {!curators.find((c) => c.id === student.curatorId) && <option value={student.curatorId}>{cur.name}</option>}
          {curators.map((c) => (
            <option key={c.id} value={c.id}>
              {c.fullName}
            </option>
          ))}
        </select>
      </div>

      <div className="flex items-center justify-between mt-4 mb-2">
        <div className="text-xs font-semibold text-ink-600 uppercase tracking-wide">Описание</div>
        {!editing && (
          <button className="text-xs font-semibold text-accent underline" onClick={startEditing}>
            Редактировать
          </button>
        )}
      </div>

      {editing ? (
        <div className="flex flex-col gap-1.5">
          <textarea
            ref={draftNotes.ref}
            className="input min-h-[6rem] resize-y"
            rows={4}
            value={draftNotes.value}
            onChange={(e) => draftNotes.setValue(e.target.value)}
            onKeyDown={draftNotes.onKeyDown}
            placeholder={'Описание ученика'}
          />
          <div className="flex justify-end gap-2 mt-2">
            <button className="btn-ghost" onClick={() => setEditing(false)} disabled={busy}>
              Отмена
            </button>
            <button className="btn-primary" onClick={saveEdits} disabled={busy || !draftName.trim()}>
              Сохранить
            </button>
          </div>
        </div>
      ) : (
        <NotesList notes={student.notes} onAdd={startEditing} />
      )}

      <div className="flex gap-3">
        <label className="text-xs font-semibold text-ink-600 flex-1">
          Дата начала
          <input
            className="input w-full mt-1"
            type="date"
            value={startedAt}
            disabled={busy}
            onChange={(e) => setStartedAt(e.target.value)}
            onBlur={saveStartedAt}
          />
        </label>
        <label className="text-xs font-semibold text-ink-600 flex-1">
          Постоплата, %
          <input
            className="input w-full mt-1"
            type="number"
            min={0}
            max={100}
            value={postpay}
            disabled={busy}
            onChange={(e) => setPostpay(e.target.value)}
            onBlur={savePostpay}
            onKeyDown={(e) => {
              if (e.key === 'Enter') e.currentTarget.blur()
            }}
          />
        </label>
      </div>
      <div className="text-xs text-ink-600 mt-1.5 mb-3">
        Когорта: <span className="font-semibold text-ink-900">{cohorts.find((c) => c.id === student.cohortId)?.name ?? '—'}</span>
        <span className="text-ink-400"> · определяется по дате начала</span>
      </div>

      <button
        className={`btn-ghost w-full ${student.isPaused ? '!bg-pause-soft !text-pause !border-pause' : ''}`}
        disabled={busy}
        onClick={togglePause}
      >
        {student.isPaused ? '▶ Снять с паузы' : '⏸ Поставить на паузу'}
      </button>

      <div className="text-xs font-semibold text-ink-600 uppercase tracking-wide mt-4 mb-2">Комментарии</div>
      <StudentComments studentId={student.id} authorName={(id) => displayName(id).name} />

      <div className="text-xs font-semibold text-ink-600 uppercase tracking-wide mt-4 mb-2">История</div>
      <StudentHistory student={student} stages={stages} curatorName={(id) => displayName(id).name} />

      {canDelete && (
        <div className="mt-6 pt-4 border-t border-border flex justify-end">
          <button className="btn-danger" disabled={busy} onClick={() => setConfirmDelete(true)}>
            Удалить ученика
          </button>
        </div>
      )}
      {confirmDelete && (
        <ConfirmDialog
          title="Удалить ученика?"
          message={`«${student.fullName}» исчезнет с доски и из статистики. Действие попадёт в журнал.`}
          confirmLabel="Удалить"
          danger
          onConfirm={deleteStudent}
          onClose={() => setConfirmDelete(false)}
        />
      )}
        </>
      )}
    </Modal>
  )
}
