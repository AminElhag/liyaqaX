import { createFileRoute } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import {
  checkInMember,
  getTodayCount,
  getRecentCheckIns,
  checkInKeys,
  type CheckInResponse,
  type RecentCheckInItem,
} from '@/api/checkIn'
import { getMemberList, memberKeys, type MemberListParams } from '@/api/members'
import { cn } from '@/lib/cn'
import { formatDateTime } from '@/lib/formatDate'

export const Route = createFileRoute('/check-in/')({
  component: CheckInPage,
})

function CheckInPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()

  const [searchQuery, setSearchQuery] = useState('')
  const [qrInput, setQrInput] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [debouncedSearch, setDebouncedSearch] = useState('')

  const { data: todayData } = useQuery({
    queryKey: checkInKeys.todayCount(),
    queryFn: getTodayCount,
    refetchInterval: 60_000,
  })

  const { data: recentData } = useQuery({
    queryKey: checkInKeys.recent(),
    queryFn: getRecentCheckIns,
  })

  const searchParams: MemberListParams = { page: 0, size: 10 }
  const { data: searchResults, isLoading: isSearching } = useQuery({
    queryKey: [...memberKeys.list(searchParams), debouncedSearch],
    queryFn: () => getMemberList({ ...searchParams, search: debouncedSearch } as MemberListParams),
    enabled: debouncedSearch.length >= 2,
  })

  const checkInMutation = useMutation({
    mutationFn: checkInMember,
    onSuccess: (data: CheckInResponse) => {
      setErrorMessage(null)
      setSuccessMessage(t('checkin.success_toast', { name: data.memberName }))
      setSearchQuery('')
      setQrInput('')
      setDebouncedSearch('')
      queryClient.invalidateQueries({ queryKey: checkInKeys.todayCount() })
      queryClient.invalidateQueries({ queryKey: checkInKeys.recent() })
      setTimeout(() => setSuccessMessage(null), 5000)
    },
    onError: (error: { status?: number; detail?: string; errorCode?: string }) => {
      setSuccessMessage(null)
      if (error.status === 409) {
        if (error.errorCode === 'MEMBERSHIP_LAPSED') {
          setErrorMessage(t('checkin.membership_lapsed'))
        } else {
          setErrorMessage(error.detail ?? t('checkin.already_checked_in'))
        }
      } else {
        setErrorMessage(error.detail ?? t('common.error'))
      }
    },
  })

  const handleSearch = useCallback(
    (value: string) => {
      setSearchQuery(value)
      setErrorMessage(null)
      setSuccessMessage(null)
      const timeout = setTimeout(() => {
        setDebouncedSearch(value.trim())
      }, 300)
      return () => clearTimeout(timeout)
    },
    [],
  )

  const handleCheckIn = useCallback(
    (memberPublicId: string, method: 'staff_phone' | 'staff_name' | 'qr_scan') => {
      setErrorMessage(null)
      checkInMutation.mutate({ memberPublicId, method })
    },
    [checkInMutation],
  )

  const handleQrSubmit = useCallback(() => {
    const uuid = qrInput.trim()
    if (uuid.length > 0) {
      handleCheckIn(uuid, 'qr_scan')
    }
  }, [qrInput, handleCheckIn])

  const locale = i18n.language

  const methodLabel = (method: string) => {
    switch (method) {
      case 'staff_phone': return t('checkin.method.staff_phone')
      case 'staff_name': return t('checkin.method.staff_name')
      case 'qr_scan': return t('checkin.method.qr_scan')
      default: return method
    }
  }

  return (
    <PageShell title={t('checkin.page_title')}>
      {/* ── Header with today count ────────────────────────────────── */}
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900">
          {todayData?.branchName}
        </h2>
        <div className="rounded-lg bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700">
          {t('checkin.today_count', { count: todayData?.count ?? 0 })}
        </div>
      </div>

      {/* ── Search input ────────────────────────────────────────────── */}
      <div className="mb-4">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => handleSearch(e.target.value)}
          placeholder={t('checkin.search_placeholder')}
          className="w-full rounded-md border border-gray-300 px-4 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
      </div>

      {/* ── QR input ────────────────────────────────────────────────── */}
      <div className="mb-6 flex gap-2">
        <input
          type="text"
          value={qrInput}
          onChange={(e) => {
            setQrInput(e.target.value)
            setErrorMessage(null)
          }}
          onKeyDown={(e) => { if (e.key === 'Enter') handleQrSubmit() }}
          placeholder={t('checkin.qr_placeholder')}
          className="flex-1 rounded-md border border-gray-300 px-4 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <button
          type="button"
          onClick={handleQrSubmit}
          disabled={qrInput.trim().length === 0 || checkInMutation.isPending}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {t('checkin.check_in_button')}
        </button>
      </div>

      {/* ── Messages ────────────────────────────────────────────────── */}
      {errorMessage && (
        <div className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
          {errorMessage}
        </div>
      )}
      {successMessage && (
        <div className="mb-4 rounded-md bg-green-50 p-3 text-sm text-green-700">
          {successMessage}
        </div>
      )}

      {/* ── Search results ──────────────────────────────────────────── */}
      {debouncedSearch.length >= 2 && (
        <div className="mb-6">
          {isSearching && <LoadingSkeleton rows={3} />}
          {searchResults && searchResults.items.length > 0 && (
            <div className="divide-y divide-gray-100 rounded-md border border-gray-200">
              {searchResults.items.map((member) => (
                <div key={member.id} className="flex items-center justify-between px-4 py-3">
                  <div className="flex items-center gap-3">
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {locale === 'ar'
                          ? `${member.firstNameAr} ${member.lastNameAr}`
                          : `${member.firstNameEn} ${member.lastNameEn}`}
                      </p>
                      <p className="text-xs text-gray-500">{member.phone}</p>
                    </div>
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-xs font-medium',
                        member.membershipStatus === 'active' && 'bg-green-100 text-green-700',
                        member.membershipStatus === 'lapsed' && 'bg-red-100 text-red-700',
                        member.membershipStatus === 'frozen' && 'bg-blue-100 text-blue-700',
                        member.membershipStatus === 'expired' && 'bg-amber-100 text-amber-700',
                        member.membershipStatus === 'pending' && 'bg-gray-100 text-gray-700',
                        member.membershipStatus === 'terminated' && 'bg-red-100 text-red-700',
                      )}
                    >
                      {member.membershipStatus}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleCheckIn(member.id, 'staff_name')}
                    disabled={checkInMutation.isPending}
                    className="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {t('checkin.check_in_button')}
                  </button>
                </div>
              ))}
            </div>
          )}
          {searchResults && searchResults.items.length === 0 && (
            <p className="text-sm text-gray-500">{t('common.noResults')}</p>
          )}
        </div>
      )}

      {/* ── Recent check-ins ────────────────────────────────────────── */}
      <div>
        <h3 className="mb-3 text-sm font-semibold text-gray-700">
          {t('checkin.recent_title')}
        </h3>
        {recentData && recentData.checkIns.length > 0 ? (
          <div className="divide-y divide-gray-100 rounded-md border border-gray-200">
            {recentData.checkIns.map((ci: RecentCheckInItem) => (
              <div key={ci.checkInId} className="flex items-center justify-between px-4 py-2.5">
                <div>
                  <p className="text-sm font-medium text-gray-900">{ci.memberName}</p>
                  <p className="text-xs text-gray-500">{ci.memberPhone}</p>
                </div>
                <div className="text-end">
                  <p className="text-xs font-medium text-gray-600">{methodLabel(ci.method)}</p>
                  <p className="text-xs text-gray-400">{formatDateTime(ci.checkedInAt, locale)}</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-400">{t('checkin.empty_recent')}</p>
        )}
      </div>
    </PageShell>
  )
}
