import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  getClassInstance,
  getClassBookings,
  getClassAttendance,
  submitAttendance,
  gxKeys,
} from '@/api/gx'
import { GXAttendanceForm } from '@/components/gx/GXAttendanceForm'
import type { AttendanceEntry, GXAttendanceStatus } from '@/types/domain'

interface AttendanceSearchParams {
  classId: string
}

export const Route = createFileRoute('/gx/attendance')({
  validateSearch: (search: Record<string, unknown>): AttendanceSearchParams => ({
    classId: String(search.classId ?? ''),
  }),
  component: GXAttendancePage,
})

function GXAttendancePage() {
  const { classId } = Route.useSearch()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const queryClient = useQueryClient()

  const { data: instance } = useQuery({
    queryKey: gxKeys.instanceDetail(classId),
    queryFn: () => getClassInstance(classId),
    enabled: !!classId,
  })

  const { data: bookingsData } = useQuery({
    queryKey: gxKeys.bookings(classId),
    queryFn: () => getClassBookings(classId, { size: 50 }),
    enabled: !!classId,
  })

  const { data: existingAttendance } = useQuery({
    queryKey: gxKeys.attendance(classId),
    queryFn: () => getClassAttendance(classId),
    enabled: !!classId,
  })

  const mutation = useMutation({
    mutationFn: (entries: AttendanceEntry[]) =>
      submitAttendance(classId, { attendance: entries }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gxKeys.attendance(classId) })
      queryClient.invalidateQueries({ queryKey: gxKeys.instanceDetail(classId) })
    },
  })

  if (!classId) {
    return <p className="text-gray-500">{t('gx.attendance.noClass')}</p>
  }

  const className = instance
    ? isAr
      ? instance.classType.nameAr
      : instance.classType.nameEn
    : ''

  const existingMap: Record<string, GXAttendanceStatus> = {}
  if (existingAttendance) {
    for (const att of existingAttendance) {
      existingMap[att.member.id] = att.status
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">
        {t('gx.attendance.title')} — {className}
      </h1>

      {bookingsData ? (
        <GXAttendanceForm
          bookings={bookingsData.items}
          existingAttendance={existingMap}
          onSubmit={(entries) => mutation.mutate(entries)}
          isSubmitting={mutation.isPending}
        />
      ) : (
        <p className="text-gray-500">{t('common.loading')}</p>
      )}
    </div>
  )
}
