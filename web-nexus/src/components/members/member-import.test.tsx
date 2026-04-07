import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import { Permission } from '@/types/permissions'
import { PermissionGate } from '@/components/common/PermissionGate'
import { ImportMembersModal } from './ImportMembersModal'
import { ImportJobStatusPanel } from './ImportJobStatusPanel'

vi.mock('@/api/memberImport', () => ({
  uploadMemberCsv: vi.fn(),
  getImportJob: vi.fn().mockResolvedValue({
    jobId: 'test-job-id',
    status: 'QUEUED',
    fileName: 'test.csv',
    totalRows: null,
    importedCount: null,
    skippedCount: null,
    errorCount: null,
    errorDetail: null,
    startedAt: null,
    completedAt: null,
    createdAt: '2026-04-07T10:00:00Z',
  }),
  cancelImportJob: vi.fn(),
  rollbackImportJob: vi.fn(),
}))

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

describe('Member Import', () => {
  beforeEach(() => {
    useAuthStore.setState({
      permissions: new Set<string>(),
      isAuthenticated: false,
      accessToken: null,
      user: null,
    })
  })

  it('renders Import Members button for users with member:import permission', () => {
    useAuthStore.setState({
      permissions: new Set([Permission.MEMBER_IMPORT]),
    })

    render(
      <PermissionGate permission={Permission.MEMBER_IMPORT}>
        <button data-testid="import-members-btn">Import Members</button>
      </PermissionGate>,
    )

    expect(screen.getByTestId('import-members-btn')).toBeInTheDocument()
  })

  it('does not render Import Members button without permission', () => {
    useAuthStore.setState({
      permissions: new Set([Permission.MEMBER_READ]),
    })

    render(
      <PermissionGate permission={Permission.MEMBER_IMPORT}>
        <button data-testid="import-members-btn">Import Members</button>
      </PermissionGate>,
    )

    expect(screen.queryByTestId('import-members-btn')).not.toBeInTheDocument()
  })

  it('upload modal shows file picker and sample CSV link', () => {
    render(
      <ImportMembersModal
        clubPublicId="test-club-id"
        isOpen={true}
        onClose={() => {}}
        onJobCreated={() => {}}
      />,
      { wrapper },
    )

    expect(screen.getByTestId('csv-file-input')).toBeInTheDocument()
    expect(screen.getByTestId('sample-csv-link')).toBeInTheDocument()
  })

  it('job status panel shows cancel button when status is QUEUED', async () => {
    const mockGetImportJob = await import('@/api/memberImport')
    vi.mocked(mockGetImportJob.getImportJob).mockResolvedValue({
      jobId: 'test-job-id',
      status: 'QUEUED',
      fileName: 'test.csv',
      totalRows: null,
      importedCount: null,
      skippedCount: null,
      errorCount: null,
      errorDetail: null,
      startedAt: null,
      completedAt: null,
      createdAt: '2026-04-07T10:00:00Z',
    })

    render(
      <ImportJobStatusPanel
        jobId="test-job-id"
        clubPublicId="test-club-id"
        onClear={() => {}}
      />,
      { wrapper },
    )

    const cancelBtn = await screen.findByTestId('cancel-import-btn')
    expect(cancelBtn).toBeInTheDocument()
  })

  it('job status panel shows rollback button when status is COMPLETED', async () => {
    const mockGetImportJob = await import('@/api/memberImport')
    vi.mocked(mockGetImportJob.getImportJob).mockResolvedValue({
      jobId: 'test-job-id',
      status: 'COMPLETED',
      fileName: 'test.csv',
      totalRows: 10,
      importedCount: 8,
      skippedCount: 1,
      errorCount: 1,
      errorDetail: 'Row 3: invalid gender',
      startedAt: '2026-04-07T10:01:00Z',
      completedAt: '2026-04-07T10:01:15Z',
      createdAt: '2026-04-07T10:00:00Z',
    })

    render(
      <ImportJobStatusPanel
        jobId="test-job-id"
        clubPublicId="test-club-id"
        onClear={() => {}}
      />,
      { wrapper },
    )

    const rollbackBtn = await screen.findByTestId('rollback-import-btn')
    expect(rollbackBtn).toBeInTheDocument()
  })

  it('rollback confirmation dialog appears before calling rollback endpoint', async () => {
    const mockGetImportJob = await import('@/api/memberImport')
    vi.mocked(mockGetImportJob.getImportJob).mockResolvedValue({
      jobId: 'test-job-id',
      status: 'COMPLETED',
      fileName: 'test.csv',
      totalRows: 10,
      importedCount: 8,
      skippedCount: 1,
      errorCount: 1,
      errorDetail: null,
      startedAt: '2026-04-07T10:01:00Z',
      completedAt: '2026-04-07T10:01:15Z',
      createdAt: '2026-04-07T10:00:00Z',
    })

    render(
      <ImportJobStatusPanel
        jobId="test-job-id"
        clubPublicId="test-club-id"
        onClear={() => {}}
      />,
      { wrapper },
    )

    const rollbackBtn = await screen.findByTestId('rollback-import-btn')
    fireEvent.click(rollbackBtn)

    const confirmBtn = await screen.findByTestId('confirm-rollback-btn')
    expect(confirmBtn).toBeInTheDocument()
  })
})
