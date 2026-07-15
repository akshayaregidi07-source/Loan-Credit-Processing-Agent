import { useRef, useState } from 'react'
import type { DocumentType } from '../../services/applicationService'
import { validateFile } from '../../services/documentService'
import { useApi } from '../../contexts/ApiContext'
import { uploadDocument } from '../../services/documentService'
import type { DocumentMetadata } from '../../services/applicationService'

interface DocumentUploadProps {
  readonly applicationId: string
  /** Called when all three documents have been uploaded successfully. */
  readonly onAllUploaded?: (docs: DocumentMetadata[]) => void
}

const DOCUMENT_SLOTS: { type: DocumentType; label: string }[] = [
  { type: 'GOVERNMENT_ID', label: 'Government-issued ID' },
  { type: 'INCOME_PROOF', label: 'Income Proof' },
  { type: 'BANK_STATEMENT', label: 'Bank Statement' },
]

/**
 * Document upload panel — Task 17.3.
 *
 * Enforces client-side validation (size ≤ 10 MB, MIME = pdf/jpeg/png) before
 * sending to the API (Requirement 11.4). Displays inline errors per slot.
 * ARIA labels on all inputs and error messages (Requirement 11.9).
 */
export default function DocumentUpload({ applicationId, onAllUploaded }: DocumentUploadProps) {
  const api = useApi()
  const inputRefs = useRef<Record<DocumentType, HTMLInputElement | null>>({
    GOVERNMENT_ID: null,
    INCOME_PROOF: null,
    BANK_STATEMENT: null,
  })

  const [uploaded, setUploaded] = useState<Record<DocumentType, DocumentMetadata | null>>({
    GOVERNMENT_ID: null,
    INCOME_PROOF: null,
    BANK_STATEMENT: null,
  })
  const [errors, setErrors] = useState<Record<DocumentType, string | null>>({
    GOVERNMENT_ID: null,
    INCOME_PROOF: null,
    BANK_STATEMENT: null,
  })
  const [uploading, setUploading] = useState<Record<DocumentType, boolean>>({
    GOVERNMENT_ID: false,
    INCOME_PROOF: false,
    BANK_STATEMENT: false,
  })

  async function handleFileChange(docType: DocumentType, file: File | undefined) {
    if (!file) return

    // Client-side validation before touching the API (Req 11.4)
    const validationError = validateFile(file)
    if (validationError) {
      setErrors((prev) => ({ ...prev, [docType]: validationError.message }))
      return
    }

    setErrors((prev) => ({ ...prev, [docType]: null }))
    setUploading((prev) => ({ ...prev, [docType]: true }))

    try {
      const meta = await uploadDocument(api, applicationId, docType, file)
      const next = { ...uploaded, [docType]: meta }
      setUploaded(next)

      // Notify parent if all three are uploaded
      const all = Object.values(next).filter(Boolean) as DocumentMetadata[]
      if (all.length === 3) {
        onAllUploaded?.(all)
      }
    } catch {
      setErrors((prev) => ({
        ...prev,
        [docType]: 'Upload failed. Please try again.',
      }))
    } finally {
      setUploading((prev) => ({ ...prev, [docType]: false }))
    }
  }

  return (
    <div aria-label="Document upload section">
      <h2 className="mb-4 text-lg font-semibold text-gray-800">Upload Documents</h2>
      <div className="space-y-4">
        {DOCUMENT_SLOTS.map(({ type, label }) => {
          const done = !!uploaded[type]
          const error = errors[type]
          const busy = uploading[type]
          const inputId = `doc-${type}`
          const errId = `doc-${type}-err`

          return (
            <div key={type} className="rounded-md border border-gray-200 p-4">
              <label
                htmlFor={inputId}
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                {label}
                {done && (
                  <span className="ml-2 text-xs font-semibold text-green-600">✓ Uploaded</span>
                )}
              </label>

              <input
                id={inputId}
                ref={(el) => { inputRefs.current[type] = el }}
                type="file"
                accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                aria-required="true"
                aria-invalid={!!error}
                aria-describedby={error ? errId : undefined}
                disabled={busy || done}
                onChange={(e) => handleFileChange(type, e.target.files?.[0])}
                className="block w-full text-sm text-gray-500 file:mr-3 file:rounded-md file:border-0 file:bg-purple-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-purple-700 hover:file:bg-purple-100 disabled:opacity-50"
              />

              {busy && (
                <p className="mt-1 text-xs text-gray-500" aria-live="polite">
                  Uploading…
                </p>
              )}

              {error && (
                <p id={errId} role="alert" className="mt-1 text-xs text-red-600">
                  {error}
                </p>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
