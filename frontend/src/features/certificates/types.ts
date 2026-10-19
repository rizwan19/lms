export type CertificateStatus = 'PENDING_RENDER' | 'ISSUED' | 'RENDER_FAILED' | 'REVOKED'

export type Certificate = {
  id: string
  studentName: string
  courseTitle: string
  edition: number
  completedOn: string
  status: CertificateStatus
  issuedAt: string | null
  renderError: string | null
  revokedReason: string | null
}

export const STATUS_LABELS: Record<CertificateStatus, string> = {
  PENDING_RENDER: 'Being prepared',
  ISSUED: 'Issued',
  RENDER_FAILED: 'Could not be created',
  REVOKED: 'Revoked',
}
