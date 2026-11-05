import { useState } from 'react'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { Loader } from '../../components/Loader'
import { ErrorState } from '../../components/ErrorState'
import { useToast } from '../../hooks/useToast'
import { daysAway, type useLearnAccess } from '../../hooks/useLearnAccess'
import { pluralRu } from '../../utils/plural'
import type { LearnAccountDto, LearnProgressDto, StudentDto } from '../../types'

const LEARN_URL = import.meta.env.VITE_LEARN_URL ?? 'https://learn.shabash-hub.ru'

function messageFor(account: LearnAccountDto): string {
  return [
    'Привет! Доступ к учебному сайту готов.',
    `Ссылка: ${LEARN_URL}`,
    `Логин: ${account.login}`,
    `Пароль: ${account.password}`,
    'Пароль можно сменить в разделе «Мой профиль».',
  ].join('\n')
}

function dateTime(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function date(value: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function Section({ title, right, children }: { title: string; right?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="mt-4">
      <div className="flex items-center justify-between mb-2">
        <div className="text-xs font-semibold text-ink-600 uppercase tracking-wide">{title}</div>
        {right}
      </div>
      {children}
    </div>
  )
}

function Chip({ tone, children }: { tone: 'plain' | 'ok' | 'amber' | 'red'; children: React.ReactNode }) {
  const styles = {
    plain: 'bg-pause-soft text-ink-600',
    ok: 'bg-success-soft text-success',
    amber: 'bg-amber-soft text-amber',
    red: 'bg-warn-soft text-warn',
  }[tone]
  return <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full whitespace-nowrap ${styles}`}>{children}</span>
}

function CopyRow({ label, value, onCopy }: { label: string; value: string; onCopy: () => void }) {
  return (
    <div className="grid grid-cols-[64px_1fr_auto] gap-2 items-center text-[13px]">
      <span className="text-xs text-ink-600">{label}</span>
      <code className="font-mono text-[12.5px] bg-bg px-2 py-1 rounded-md break-all">{value}</code>
      <button className="btn-ghost !px-2.5 !py-1 !text-xs" onClick={onCopy}>
        Копировать
      </button>
    </div>
  )
}

function AccessBlock({ account, busy, onReset }: { account: LearnAccountDto; busy: boolean; onReset: () => void }) {
  const { push } = useToast()
  const [showMessage, setShowMessage] = useState(false)
  const [confirmReset, setConfirmReset] = useState(false)

  async function copy(text: string, what: string) {
    try {
      await navigator.clipboard.writeText(text)
      push('success', `${what} скопирован${what.endsWith('а') ? 'а' : ''}`)
    } catch {
      push('error', 'Не удалось скопировать — выделите и скопируйте вручную')
    }
  }

  const away = daysAway({ connected: true, account, progress: null })
  const status =
    account.status === 'CLOSED' ? (
      <Chip tone="red">доступ закрыт</Chip>
    ) : account.lastSeenAt == null ? (
      <Chip tone="amber">ещё не входил</Chip>
    ) : away != null && away >= 14 ? (
      <Chip tone="red">давно не заходил</Chip>
    ) : away != null && away >= 7 ? (
      <Chip tone="amber">давно не заходил</Chip>
    ) : (
      <Chip tone="ok">активен</Chip>
    )

  return (
    <Section title="Доступ" right={status}>
      <div className="flex flex-col gap-1.5">
        <CopyRow label="Ссылка" value={LEARN_URL.replace(/^https?:\/\//, '')} onCopy={() => copy(LEARN_URL, 'Ссылка')} />
        <CopyRow label="Логин" value={account.login} onCopy={() => copy(account.login, 'Логин')} />
        {account.password ? (
          <CopyRow label="Пароль" value={account.password} onCopy={() => copy(account.password!, 'Пароль')} />
        ) : (
          <div className="grid grid-cols-[64px_1fr] gap-2 items-center text-[13px]">
            <span className="text-xs text-ink-600">Пароль</span>
            <span className="text-ink-600">ученик сменил пароль</span>
          </div>
        )}
      </div>

      <div className="mt-2.5 text-xs rounded-lg px-2.5 py-2 bg-amber-soft text-amber">
        {account.password
          ? 'Пароль виден, пока ученик не сменит его сам. Потом уберём, когда доступы начнёт отправлять бот.'
          : 'Ученик сменил пароль сам, сохранённый пароль больше не действует. Если он его забыл — сбросьте.'}
      </div>

      <div className="flex gap-2 mt-3">
        {account.password && (
          <button className="btn-primary flex-1" onClick={() => copy(messageFor(account), 'Сообщение')}>
            Скопировать сообщение
          </button>
        )}
        <button className="btn-ghost flex-1" disabled={busy} onClick={() => setConfirmReset(true)}>
          Сбросить пароль
        </button>
      </div>
      {account.password && (
        <>
          <button className="text-xs font-semibold text-accent underline mt-2" onClick={() => setShowMessage((v) => !v)}>
            {showMessage ? 'Скрыть' : 'Показать'} текст сообщения
          </button>
          {showMessage && (
            <pre className="mt-2 text-xs text-ink-600 bg-bg border border-border rounded-lg p-2.5 whitespace-pre-wrap font-sans">
              {messageFor(account)}
            </pre>
          )}
        </>
      )}

      {confirmReset && (
        <ConfirmDialog
          title="Сбросить пароль?"
          message="Старый пароль перестанет действовать, ученик будет разлогинен на всех устройствах. Новый пароль появится здесь."
          confirmLabel="Сбросить"
          onConfirm={onReset}
          onClose={() => setConfirmReset(false)}
        />
      )}
    </Section>
  )
}

function Fact({ label, value, tone }: { label: string; value: string; tone?: 'amber' | 'red' }) {
  const toneClass = tone === 'red' ? 'bg-warn-soft text-warn' : tone === 'amber' ? 'bg-amber-soft text-amber' : 'bg-bg text-ink-900'
  return (
    <div className={`rounded-lg px-2.5 py-1.5 ${toneClass}`}>
      <div className="text-[11px] opacity-75">{label}</div>
      <div className="text-[13px] font-semibold">{value}</div>
    </div>
  )
}

function ProgressBlock({ account, progress }: { account: LearnAccountDto; progress: LearnProgressDto }) {
  const percent = progress.total ? Math.round((progress.completed / progress.total) * 100) : 0
  const away = daysAway({ connected: true, account, progress })
  const lastVisit = account.lastSeenAt
    ? `${dateTime(account.lastSeenAt)}${away ? ` (${away} ${pluralRu(away, ['день', 'дня', 'дней'])} назад)` : ''}`
    : 'ещё не входил'
  const tone = away == null || !account.lastSeenAt ? undefined : away >= 14 ? 'red' : away >= 7 ? 'amber' : undefined

  return (
    <Section title="Прогресс">
      <div className="flex items-baseline gap-2 mb-1.5">
        <span className="font-display font-bold text-xl">
          {progress.completed} из {progress.total}
        </span>
        <span className="text-[13px] text-ink-600">уроков пройдено · {percent}%</span>
      </div>
      <div className="h-[7px] bg-border rounded-full overflow-hidden">
        <div className="h-full bg-accent rounded-full" style={{ width: `${percent}%` }} />
      </div>
      <div className="grid grid-cols-2 gap-2 mt-3">
        <Fact label="Последний вход" value={lastVisit} tone={tone} />
        <Fact label="Первое открытие" value={date(progress.firstOpenedAt)} />
      </div>
      <div className="mt-3 flex flex-col gap-1.5">
        {progress.blocks.map((block) => (
          <details key={block.id} className="border border-border rounded-lg">
            <summary className="list-none cursor-pointer grid grid-cols-[1fr_54px_34px] gap-2 items-center px-2.5 py-2 text-[12.5px]">
              <span className="truncate">
                Блок {block.order}. {block.title}
              </span>
              <span className="h-[7px] bg-border rounded-full overflow-hidden">
                <span className="block h-full bg-accent" style={{ width: `${block.total ? (block.completed / block.total) * 100 : 0}%` }} />
              </span>
              <span className="text-right text-xs text-ink-600 tabular-nums">
                {block.completed}/{block.total}
              </span>
            </summary>
            <div className="px-2.5 pb-2">
              {block.lessons.map((lesson, i) => (
                <div key={lesson.id} className="flex justify-between gap-2 text-xs py-1 border-t border-dashed border-border">
                  <span>
                    <i
                      className={`inline-block w-[7px] h-[7px] rounded-full mr-1.5 ${
                        lesson.completedAt ? 'bg-success' : lesson.openedAt ? 'bg-amber' : 'bg-border'
                      }`}
                    />
                    {i + 1}. {lesson.title}
                  </span>
                  <span className="text-ink-400 whitespace-nowrap">{lesson.completedAt ? 'пройден' : lesson.openedAt ? 'открыт' : '—'}</span>
                </div>
              ))}
            </div>
          </details>
        ))}
      </div>
      <p className="text-xs text-ink-600 mt-2">«Пройден» отмечает сам ученик. Ответы на задания куратору не показываются.</p>
    </Section>
  )
}

export function LearnTab({ student, learn }: { student: StudentDto; learn: ReturnType<typeof useLearnAccess> }) {
  const { data, loading, failed, busy } = learn

  if (loading && !data) return <Loader />
  if (failed || !data) return <ErrorState message="Не удалось загрузить данные учебного сайта" onRetry={learn.reload} />

  if (!data.connected) {
    return (
      <Section title="Доступ">
        <p className="text-sm text-ink-600">Учебный сайт ещё не подключён к CRM. Как только он заработает на сервере, здесь можно будет выдать доступ.</p>
      </Section>
    )
  }

  if (!data.account) {
    return (
      <>
        <Section title="Доступ" right={<Chip tone="plain">нет доступа</Chip>}>
          <p className="text-[13px] text-ink-600 mb-3">
            Логин берётся из ника в Telegram{student.telegramUsername ? ` (${student.telegramUsername.replace(/^@?/, '@')})` : ''}, пароль создаётся случайный.
            Ученик сможет сменить его сам.
          </p>
          <button className="btn-primary w-full" disabled={busy} onClick={learn.issue}>
            Выдать доступ
          </button>
        </Section>
        <Section title="Прогресс">
          <p className="text-[13px] text-ink-600">Появится после выдачи доступа и первого входа ученика.</p>
        </Section>
      </>
    )
  }

  const progress = data.progress
  return (
    <>
      <AccessBlock account={data.account} busy={busy} onReset={learn.resetPassword} />
      {progress && progress.total > 0 && data.account.lastSeenAt ? (
        <ProgressBlock account={data.account} progress={progress} />
      ) : (
        <Section title="Прогресс">
          <p className="text-[13px] text-ink-600">Доступ выдан {date(data.account.issuedAt)}, ученик ещё ни разу не входил.</p>
        </Section>
      )}
    </>
  )
}
