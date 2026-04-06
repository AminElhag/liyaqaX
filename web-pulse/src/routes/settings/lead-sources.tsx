import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getLeadSources,
  createLeadSource,
  toggleLeadSource,
  leadSourceKeys,
} from '@/api/leadSources'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/settings/lead-sources')({
  component: LeadSourcesSettingsPage,
})

function LeadSourcesSettingsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [newName, setNewName] = useState('')
  const [newNameAr, setNewNameAr] = useState('')
  const [newColor, setNewColor] = useState('#6B7280')

  const { data: sources, isLoading } = useQuery({
    queryKey: leadSourceKeys.list(),
    queryFn: getLeadSources,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createLeadSource({ name: newName, nameAr: newNameAr, color: newColor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: leadSourceKeys.all })
      setNewName('')
      setNewNameAr('')
      setNewColor('#6B7280')
    },
  })

  const toggleMutation = useMutation({
    mutationFn: (id: string) => toggleLeadSource(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: leadSourceKeys.all })
    },
  })

  return (
    <PageShell title={t('lead_sources.title')}>
      {isLoading && <LoadingSkeleton rows={4} />}

      {sources && (
        <div className="space-y-4">
          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lead_sources.color')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lead_sources.nameEn')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lead_sources.nameAr')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lead_sources.leads')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lead_sources.status')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('common.actions')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {sources.map((source) => (
                  <tr key={source.id}>
                    <td className="px-4 py-3">
                      <span
                        className="inline-block h-4 w-4 rounded-full"
                        style={{ backgroundColor: source.color }}
                      />
                    </td>
                    <td className="px-4 py-3 text-sm">{source.name}</td>
                    <td className="px-4 py-3 text-sm" dir="rtl">{source.nameAr}</td>
                    <td className="px-4 py-3 text-sm text-gray-500">{source.leadCount}</td>
                    <td className="px-4 py-3 text-sm">
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                          source.isActive
                            ? 'bg-green-100 text-green-700'
                            : 'bg-gray-100 text-gray-500'
                        }`}
                      >
                        {source.isActive ? t('common.active') : t('common.inactive')}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <PermissionGate permission={Permission.LEAD_SOURCE_UPDATE}>
                        <button
                          type="button"
                          onClick={() => toggleMutation.mutate(source.id)}
                          className="text-sm text-blue-600 hover:text-blue-800"
                        >
                          {source.isActive
                            ? t('lead_sources.deactivate')
                            : t('lead_sources.activate')}
                        </button>
                      </PermissionGate>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <PermissionGate permission={Permission.LEAD_SOURCE_CREATE}>
            <div className="rounded-lg border border-gray-200 p-4">
              <h3 className="mb-3 text-sm font-semibold">{t('lead_sources.add')}</h3>
              <div className="flex flex-wrap items-end gap-3">
                <div>
                  <label className="mb-1 block text-xs text-gray-500">
                    {t('lead_sources.nameEn')}
                  </label>
                  <input
                    type="text"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    className="rounded-md border border-gray-300 px-3 py-1.5 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs text-gray-500">
                    {t('lead_sources.nameAr')}
                  </label>
                  <input
                    type="text"
                    value={newNameAr}
                    onChange={(e) => setNewNameAr(e.target.value)}
                    className="rounded-md border border-gray-300 px-3 py-1.5 text-sm"
                    dir="rtl"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs text-gray-500">
                    {t('lead_sources.color')}
                  </label>
                  <input
                    type="color"
                    value={newColor}
                    onChange={(e) => setNewColor(e.target.value)}
                    className="h-8 w-10 cursor-pointer rounded border border-gray-300"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => createMutation.mutate()}
                  disabled={!newName.trim() || !newNameAr.trim() || createMutation.isPending}
                  className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {t('lead_sources.add')}
                </button>
              </div>
            </div>
          </PermissionGate>
        </div>
      )}
    </PageShell>
  )
}
