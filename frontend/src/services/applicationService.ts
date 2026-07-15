import type { AxiosInstance } from 'axios'

// ---------------------------------------------------------------------------
// Request types
// ---------------------------------------------------------------------------

export type EmploymentStatus =
  | 'EMPLOYED'
  | 'SELF_EMPLOYED'
  | 'UNEMPLOYED'
  | 'RETIRED'
  | 'STUDENT'

export interface ApplicationSubmitRequest {
  requestedAmount: number
  loanPurpose: string
  employmentStatus: EmploymentStatus
  grossMonthlyIncome: number
  totalMonthlyDebt: number
}

export type DecisionValue = 'APPROVED' | 'DECLINED' | 'REFERRED_FOR_FURTHER_REVIEW'

export interface UnderwriterDecisionRequest {
  decisionValue: DecisionValue
  justificationText: string
  overrideReason?: string
}

// ---------------------------------------------------------------------------
// Response types
// ---------------------------------------------------------------------------

export type ApplicationStatus =
  | 'SUBMITTED'
  | 'DOCUMENT_INCOMPLETE'
  | 'DOCUMENT_INVALID'
  | 'DOCUMENT_INCONSISTENT'
  | 'DOCUMENTS_VERIFIED'
  | 'PROCESSING'
  | 'SCORING_ERROR'
  | 'AWAITING_UNDERWRITER_REVIEW'
  | 'DECISION_RECORDED'

export interface ApplicationStatusResponse {
  applicationId: string
  status: ApplicationStatus
  lastUpdatedAt: string
  decisionValue: DecisionValue | null
  decisionTimestamp: string | null
}

export interface ApplicationSummaryResponse {
  applicationId: string
  status: ApplicationStatus
  requestedAmount: number
  loanPurpose: string
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface CreditScoreBreakdown {
  creditScore: number
  dtiRatio: number
  dtiSubScore: number
  incomeStabilityScore: number
  creditHistoryScore: number
  dtiWeight: number
  incomeStabilityWeight: number
  creditHistoryWeight: number
  policyThresholdId: string
  computedAt: string
}

export type RecommendationValue = 'APPROVE' | 'REFER' | 'DECLINE'

export interface RecommendationDetail {
  recommendationValue: RecommendationValue
  policyThresholdId: string
  explanation: string
  producedAt: string
}

export type FairnessOutcome = 'FAIRNESS_FLAG' | 'FAIRNESS_PASSED'

export interface FairnessResultResponse {
  originalCreditScore: number
  anonymisedCreditScore: number
  fairnessDelta: number
  fairnessOutcome: FairnessOutcome
  flagReason: string | null
  evaluatedAt: string
}

export type DocumentType = 'GOVERNMENT_ID' | 'INCOME_PROOF' | 'BANK_STATEMENT'
export type DocumentValidationStatus = 'PENDING' | 'PASSED' | 'FAILED'

export interface DocumentMetadata {
  id: string
  documentType: DocumentType
  originalFilename: string
  mimeType: string
  fileSizeBytes: number
  uploadedAt: string
  validationStatus: DocumentValidationStatus
}

export interface ApplicationFormData {
  requestedAmount: number
  loanPurpose: string
  employmentStatus: EmploymentStatus
  grossMonthlyIncome: number
  totalMonthlyDebt: number
}

export interface ApplicationReviewResponse {
  applicationId: string
  status: ApplicationStatus
  formData: ApplicationFormData
  documents: DocumentMetadata[]
  creditScore: CreditScoreBreakdown | null
  recommendation: RecommendationDetail | null
  fairnessResult: FairnessResultResponse | null
  hasFairnessFlag: boolean
  fairnessFlagReason: string | null
  createdAt: string
}

// ---------------------------------------------------------------------------
// Service functions
// ---------------------------------------------------------------------------

/** Submits a new application. Returns the Location header's application ID. */
export async function submitApplication(
  api: AxiosInstance,
  request: ApplicationSubmitRequest,
): Promise<string> {
  const response = await api.post<void>('/applications', request)
  // Extract UUID from Location: /api/v1/applications/{id}/status
  const location = response.headers['location'] as string
  const parts = location.split('/')
  return parts[parts.indexOf('applications') + 1]
}

/** Gets the current status of an applicant's own application. */
export async function getStatus(
  api: AxiosInstance,
  applicationId: string,
): Promise<ApplicationStatusResponse> {
  const response = await api.get<ApplicationStatusResponse>(
    `/applications/${applicationId}/status`,
  )
  return response.data
}

/** Gets the full review payload (Underwriter/Admin). */
export async function getReview(
  api: AxiosInstance,
  applicationId: string,
): Promise<ApplicationReviewResponse> {
  const response = await api.get<ApplicationReviewResponse>(
    `/applications/${applicationId}/review`,
  )
  return response.data
}

/** Returns a paginated worklist of applications awaiting underwriter review. */
export async function listApplications(
  api: AxiosInstance,
  page = 0,
  size = 20,
): Promise<PageResponse<ApplicationSummaryResponse>> {
  const response = await api.get<PageResponse<ApplicationSummaryResponse>>(
    `/applications?page=${page}&size=${size}`,
  )
  return response.data
}

/** Submits the underwriter's final decision. */
export async function submitDecision(
  api: AxiosInstance,
  applicationId: string,
  request: UnderwriterDecisionRequest,
): Promise<void> {
  await api.post<void>(`/applications/${applicationId}/decision`, request)
}
