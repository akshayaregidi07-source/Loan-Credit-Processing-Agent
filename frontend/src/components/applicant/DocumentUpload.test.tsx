/**
 * Property 22: Frontend File Upload Client-Side Validation — Task 17.4.
 *
 * Tests the `validateFile` pure function from documentService using fast-check
 * property-based testing. The function is the exact guard called by the
 * DocumentUpload component before any API call is made (Requirement 11.4).
 *
 * Property: for any file object,
 *   - size > 10 MB  → validateFile returns a SIZE error (not null)
 *   - invalid MIME  → validateFile returns a MIME error (not null)
 *   - size ≤ 10 MB AND accepted MIME → validateFile returns null (allowed)
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { validateFile } from '../../services/documentService'

const MAX_BYTES = 10 * 1024 * 1024 // 10 MB
const ACCEPTED_MIMES = ['application/pdf', 'image/jpeg', 'image/png'] as const

/** Creates a minimal File-like object with the given size and MIME type. */
function makeFile(size: number, mimeType: string): File {
  // File constructor: (bits, filename, options)
  // We pass an empty Uint8Array but override size via a custom object to
  // avoid allocating gigabytes of memory during tests.
  const blob = new Blob([''], { type: mimeType })
  // Object.defineProperty lets us set the read-only `size` property on the File
  const file = new File([blob], 'test-file', { type: mimeType })
  Object.defineProperty(file, 'size', { value: size, writable: false })
  return file
}

describe('Property 22: validateFile — client-side file validation', () => {

  it('P22a: any file > 10 MB is rejected with a SIZE error (never reaches the API)', () => {
    fc.assert(
      fc.property(
        // sizes strictly above the 10 MB limit, up to 100 MB
        fc.integer({ min: MAX_BYTES + 1, max: 100 * 1024 * 1024 }),
        fc.constantFrom(...ACCEPTED_MIMES),
        (size, mime) => {
          const file = makeFile(size, mime)
          const result = validateFile(file)
          expect(result).not.toBeNull()
          expect(result!.type).toBe('SIZE')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('P22b: any unsupported MIME type is rejected with a MIME error (never reaches the API)', () => {
    const badMimes = [
      'text/plain', 'image/gif', 'image/webp', 'application/json',
      'application/octet-stream', 'video/mp4', 'audio/mpeg',
    ]
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: MAX_BYTES }),       // valid size
        fc.constantFrom(...badMimes),                  // invalid MIME
        (size, mime) => {
          const file = makeFile(size, mime)
          const result = validateFile(file)
          expect(result).not.toBeNull()
          expect(result!.type).toBe('MIME')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('P22c: any file ≤ 10 MB with an accepted MIME type is allowed (null returned)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: MAX_BYTES }),        // valid size (1 byte – 10 MB)
        fc.constantFrom(...ACCEPTED_MIMES),             // valid MIME
        (size, mime) => {
          const file = makeFile(size, mime)
          const result = validateFile(file)
          expect(result).toBeNull()
        },
      ),
      { numRuns: 100 },
    )
  })

  it('P22d: boundary — exactly 10 MB is allowed; exactly 10 MB + 1 byte is rejected', () => {
    const atBoundary = makeFile(MAX_BYTES, 'application/pdf')
    expect(validateFile(atBoundary)).toBeNull()

    const overBoundary = makeFile(MAX_BYTES + 1, 'application/pdf')
    expect(validateFile(overBoundary)).not.toBeNull()
    expect(validateFile(overBoundary)!.type).toBe('SIZE')
  })

})
