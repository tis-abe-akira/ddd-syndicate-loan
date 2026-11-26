import type { FormationApprovalProposal, FormationApprovalRequest, ProposedApprovalStep } from '../api/client'

/**
 * What the drafter decides in UC-024 step 5: the title, the body and who takes each step.
 * A removed consultee or report step is dropped; main-track steps cannot be removed (BR-003).
 */
export type ApprovalDraftForm = {
  title: string
  body: string
  steps: { step: ProposedApprovalStep; userId: number | null; removed: boolean }[]
}

export function initialDraft(proposal: FormationApprovalProposal): ApprovalDraftForm {
  return {
    title: proposal.title,
    body: '',
    steps: proposal.steps.map((step) => ({ step, userId: step.userId, removed: false })),
  }
}

/** The steps that will be sent, in the proposal's order (their index names the field steps[i]). */
export function keptSteps(form: ApprovalDraftForm) {
  return form.steps.filter((s) => !s.removed)
}

export function toApprovalRequest(proposal: FormationApprovalProposal, form: ApprovalDraftForm): FormationApprovalRequest {
  return {
    facilityVersion: proposal.facilityVersion,
    routeRuleId: proposal.routeRuleId,
    routeRuleVersion: proposal.routeRuleVersion,
    title: form.title,
    body: form.body === '' ? null : form.body,
    steps: keptSteps(form).map((s) => ({ kind: s.step.kind, order: s.step.order, userId: s.userId })),
  }
}

/** The step as the drafter reads it: 合議先, メイントラック n 段目(職位), 報告先. */
export function stepLabel(step: ProposedApprovalStep): string {
  switch (step.kind) {
    case 'CONSULTATION':
      return '合議先'
    case 'REPORT':
      return '報告先'
    default:
      return `承認 ${step.order} 段目${step.positionName ? `(${step.positionName}以上)` : ''}`
  }
}
