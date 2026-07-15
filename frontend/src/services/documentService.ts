import type { AxiosInstance } from 'axios'
import type { DocumentMetadata, DocumentType } from './applicationService'

const MAX_FILE_BYTES = 10 * 1024 * 1024 // 10 MB
const ACCEPTED_MIME_TYPES = new Set(['application/pdf', 'image/jpeg', 'image/png'])

export interface FileValidationError {
  type: 'SIZE' | 'MIME'
  message: string
}

/**
 * Client-side file validation (Req 11.4).
 * Returns an error object if the file is invalid, or null if it's fine.
 */
export function validateFile(file: File): FileValidationError | null {
  if (file.size > MAX_FILE_BYTES) {
    return { type: 'SIZE', message: 'File exceeds the 10 MB limit.' }
  }
  if (!ACCEPTED_MIME_TYPES.has(file.type)) {
    return { type: 'MIME', message: 'Only PDF, JPEG, and PNG files are accepted.' }
  }
  return null
}

/** Uploads a document for the given application. */
export async function uploadDocument(
  api: AxiosInstance,
  applicationId: string,
  documentType: DocumentType,
  file: File,
): Promise<DocumentMetadata> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('documentType', documentType)

  const response = await api.post<DocumentMetadata>(
    `/applications/${applicationId}/documents`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return response.data
}
