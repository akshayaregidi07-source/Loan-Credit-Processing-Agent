interface ErrorMessageProps {
  /** HTTP status code or arbitrary error string. */
  readonly error: number | string | null | undefined
  /** Override the default message. */
  readonly message?: string
}

const HTTP_MESSAGES: Record<number, string> = {
  400: 'Your request could not be processed. Please check your input.',
  401: 'Your session has expired. Please log in again.',
  403: 'You do not have permission to perform this action.',
  404: 'The requested resource was not found.',
  413: 'The uploaded file exceeds the 10 MB limit.',
  415: 'The file type is not supported. Please upload a PDF, JPEG, or PNG.',
  422: 'Please check the form for errors and try again.',
  429: 'Too many requests. Please wait a moment before trying again.',
  500: 'An unexpected error occurred. Please try again later.',
  503: 'The service is temporarily unavailable. Please try again shortly.',
}

/**
 * Renders a user-readable error message without exposing raw HTTP status codes
 * or stack traces (Req 11.10).
 */
export default function ErrorMessage({ error, message }: ErrorMessageProps) {
  if (!error) return null

  const text =
    message ??
    (typeof error === 'number' ? HTTP_MESSAGES[error] : error) ??
    'An unexpected error occurred. Please try again later.'

  return (
    <div
      role="alert"
      className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {text}
    </div>
  )
}
