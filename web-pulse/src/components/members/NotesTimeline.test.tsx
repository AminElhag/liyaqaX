import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { NotesTimeline } from './NotesTimeline'
import i18n from '@/i18n'

vi.mock('@/api/memberNotes', () => ({
  getTimeline: vi.fn(),
  deleteNote: vi.fn(),
  memberNoteKeys: {
    all: ['member-notes'],
    timeline: (id: string) => ['member-notes', 'timeline', id],
    followUps: () => ['member-notes', 'follow-ups'],
  },
}))

describe('NotesTimeline', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders note events in timeline', async () => {
    const { getTimeline } = await import('@/api/memberNotes')
    vi.mocked(getTimeline).mockResolvedValue({
      events: [
        {
          eventAt: '2026-04-07T09:30:00Z',
          eventType: 'NOTE_GENERAL',
          noteId: 'note-1',
          content: 'Test note content',
          noteType: 'GENERAL',
          followUpAt: null,
          createdByName: 'Staff User',
          canDelete: true,
        },
      ],
      nextCursor: null,
    })

    renderWithProviders(<NotesTimeline memberId="member-1" />)

    expect(await screen.findByText('Test note content')).toBeDefined()
  })

  it('renders membership and payment events in timeline', async () => {
    const { getTimeline } = await import('@/api/memberNotes')
    vi.mocked(getTimeline).mockResolvedValue({
      events: [
        {
          eventAt: '2026-04-01T10:00:00Z',
          eventType: 'PAYMENT_COLLECTED',
          paymentId: 'pay-1',
          amountSar: '150.00',
          method: 'cash',
        },
        {
          eventAt: '2026-03-01T08:00:00Z',
          eventType: 'MEMBERSHIP_JOINED',
          membershipId: 'ms-1',
          planName: 'Basic Monthly',
          detail: 'Joined on Basic Monthly plan',
        },
      ],
      nextCursor: null,
    })

    renderWithProviders(<NotesTimeline memberId="member-1" />)

    expect(await screen.findByText(/150.00 SAR/)).toBeDefined()
    expect(await screen.findByText(/Joined on Basic Monthly plan/)).toBeDefined()
  })

  it('delete icon visible on own notes', async () => {
    const { getTimeline } = await import('@/api/memberNotes')
    vi.mocked(getTimeline).mockResolvedValue({
      events: [
        {
          eventAt: '2026-04-07T09:30:00Z',
          eventType: 'NOTE_GENERAL',
          noteId: 'note-1',
          content: 'My note',
          noteType: 'GENERAL',
          followUpAt: null,
          createdByName: 'Me',
          canDelete: true,
        },
      ],
      nextCursor: null,
    })

    renderWithProviders(<NotesTimeline memberId="member-1" />)
    await screen.findByText('My note')

    const deleteButton = screen.getByTitle(i18n.t('common.delete'))
    expect(deleteButton).toBeDefined()
  })

  it('delete icon hidden on others notes', async () => {
    const { getTimeline } = await import('@/api/memberNotes')
    vi.mocked(getTimeline).mockResolvedValue({
      events: [
        {
          eventAt: '2026-04-07T09:30:00Z',
          eventType: 'NOTE_GENERAL',
          noteId: 'note-2',
          content: 'Other note',
          noteType: 'GENERAL',
          followUpAt: null,
          createdByName: 'Other',
          canDelete: false,
        },
      ],
      nextCursor: null,
    })

    renderWithProviders(<NotesTimeline memberId="member-1" />)
    await screen.findByText('Other note')

    const deleteButtons = screen.queryAllByTitle(i18n.t('common.delete'))
    expect(deleteButtons).toHaveLength(0)
  })

  it('shows empty state when no events', async () => {
    const { getTimeline } = await import('@/api/memberNotes')
    vi.mocked(getTimeline).mockResolvedValue({
      events: [],
      nextCursor: null,
    })

    renderWithProviders(<NotesTimeline memberId="member-1" />)

    expect(await screen.findByText(i18n.t('notes.empty'))).toBeDefined()
  })

  it('shows add note button', () => {
    renderWithProviders(<NotesTimeline memberId="member-1" />)
    expect(screen.getByText(i18n.t('notes.add_button'))).toBeDefined()
  })
})
