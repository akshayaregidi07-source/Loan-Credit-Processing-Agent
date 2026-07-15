/**
 * Property 23: Frontend Decision Form Submission Guard — Task 18.4.
 *
 * Tests that the submit button in DecisionForm is:
 *   - disabled  when justificationText.length < 20
 *   - enabled   when justificationText.length ≥ 20
 *
 * Uses fast-check with Vitest + React Testing Library.
 * Requirements: 11.8
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup, within } from '@testing-library/react'
import * as fc from 'fast-check'
import DecisionForm from './DecisionForm'

afterEach(() => cleanup())

/**
 * Renders a fresh DecisionForm, selects APPROVED as decision value,
 * and returns references to the justification textarea and submit button.
 * Each call runs in its own container via cleanup.
 */
function renderForm() {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const { container } = render(
    <DecisionForm
      systemRecommendation="APPROVE"
      onSubmit={onSubmit}
      submitting={false}
    />,
  )

  // Use within(container) so queries are scoped to this render only
  const scope = within(container)

  // Select a decision so only justification length controls canSubmit
  const select = scope.getByRole('combobox', { name: /decision/i })
  fireEvent.change(select, { target: { value: 'APPROVED' } })

  const textarea = scope.getByRole('textbox', { name: /justification/i })
  // The button's accessible name is set via aria-label="Submit underwriter decision"
  const button = scope.getByRole('button', { name: /submit underwriter decision/i })
  return { container, textarea, button, onSubmit }
}

describe('Property 23: DecisionForm — submit guard (justification ≥ 20 chars)', () => {

  it('P23a: submit button is disabled for any justification shorter than 20 characters', () => {
    fc.assert(
      fc.property(
        fc.string({ maxLength: 19 }),
        (text) => {
          cleanup()
          const { textarea, button } = renderForm()
          fireEvent.change(textarea, { target: { value: text } })
          expect(button).toBeDisabled()
        },
      ),
      { numRuns: 100 },
    )
  })

  it('P23b: submit button is enabled for any justification of 20 or more characters', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 20, maxLength: 200 }),
        (text) => {
          cleanup()
          const { textarea, button } = renderForm()
          fireEvent.change(textarea, { target: { value: text } })
          expect(button).not.toBeDisabled()
        },
      ),
      { numRuns: 100 },
    )
  })

  it('P23c: boundary — exactly 19 chars disabled, exactly 20 chars enabled', () => {
    const exactly19 = 'x'.repeat(19)
    const exactly20 = 'x'.repeat(20)

    cleanup()
    const { textarea: ta19, button: btn19 } = renderForm()
    fireEvent.change(ta19, { target: { value: exactly19 } })
    expect(btn19).toBeDisabled()

    cleanup()
    const { textarea: ta20, button: btn20 } = renderForm()
    fireEvent.change(ta20, { target: { value: exactly20 } })
    expect(btn20).not.toBeDisabled()
  })

})
