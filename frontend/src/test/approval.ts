import type { FormationApprovalProposal } from '../api/client'

/** UC-024 step 4 for a formation request: a consultee, two main-track steps (the second without a default) and a report. */
export function approvalProposal(): FormationApprovalProposal {
  return {
    facilityId: 1301,
    facilityVersion: 2,
    routeRuleId: 1502,
    routeRuleVersion: 0,
    title: '東都電機工業 研究開発センターの建設 ファシリティ組成',
    steps: [
      { kind: 'CONSULTATION', order: null, positionName: null, userId: 1108, candidates: [
        { userId: 1108, name: '中村 由紀', positionName: '課長' },
        { userId: 1104, name: '田中 誠', positionName: '課長' },
      ] },
      { kind: 'MAIN', order: 1, positionName: '課長', userId: 1104, candidates: [
        { userId: 1104, name: '田中 誠', positionName: '課長' },
        { userId: 1108, name: '中村 由紀', positionName: '課長' },
      ] },
      { kind: 'MAIN', order: 2, positionName: '部長', userId: null, candidates: [
        { userId: 1105, name: '伊藤 美咲', positionName: '部長' },
        { userId: 1107, name: '山本 直樹', positionName: '部長' },
      ] },
      { kind: 'REPORT', order: null, positionName: null, userId: 1102, candidates: [
        { userId: 1102, name: '鈴木 一郎', positionName: null },
      ] },
    ],
  }
}
