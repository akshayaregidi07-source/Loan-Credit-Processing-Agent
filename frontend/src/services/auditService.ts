import type { AxiosInstance } from 'axios'

export interface AuditEventResponse {
  eventId: string
  applicationId: string
  eventType: string
  eventPayload: Record<string, unknown>
  actor: string
  createdAt: string
}

/** Returns all audit events for an application, ordered by created_at ASC. */
export async function getAuditTrail(
  api: AxiosInstance,
  applicationId: string,
): Promise<AuditEventResponse[]> {
  const response = await api.get<AuditEventResponse[]>(`/audit/${applicationId}`)
  return response.data
}

/** Exports all audit events within a date range (ADMIN only). */
export async function exportAuditEvents(
  api: AxiosInstance,
  from: string,
  to: string,
): Promise<AuditEventResponse[]> {
  const response = await api.get<AuditEventResponse[]>('/audit/export', {
    params: { from, to },
  })
  return response.data
}
