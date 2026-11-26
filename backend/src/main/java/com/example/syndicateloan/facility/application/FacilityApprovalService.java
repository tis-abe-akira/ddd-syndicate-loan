package com.example.syndicateloan.facility.application;

import com.example.syndicateloan.facility.domain.Facility;
import com.example.syndicateloan.facility.domain.FacilityRepository;
import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.Organizations;
import com.example.syndicateloan.shared.BusinessRuleException;
import com.example.syndicateloan.shared.ConcurrentChangeException;
import com.example.syndicateloan.shared.ForbiddenException;
import com.example.syndicateloan.shared.NotFoundException;
import com.example.syndicateloan.syndicate.DealForFormation;
import com.example.syndicateloan.syndicate.Deals;
import com.example.syndicateloan.workflow.ApprovalDraft;
import com.example.syndicateloan.workflow.ApprovalRequests;
import com.example.syndicateloan.workflow.ApprovalSubject;
import com.example.syndicateloan.workflow.SubmittedApproval;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-015 step 7、8: 組成中のファシリティを、組成の稟議にかける(UC-024 を含む)。A8、A10、BR-005、BR-010 の起案の部分。
 *
 * <p>稟議の中身はファシリティから組み立て、画面からは受け取らない(UC-024 BR-007、#41)。
 * 承認・否決の後の処理(BR-006、BR-010 の後半)と、否決後の再起案(A4)は、UC-026・UC-029 ができてから足す(#31)。
 */
@Service
@Transactional(readOnly = true)
public class FacilityApprovalService {

    /** 組成の稟議の種類。 */
    public static final String SUBJECT_TYPE = "FACILITY_FORMATION";

    private final Deals deals;
    private final FacilityRepository facilities;
    private final ApprovalRequests approvals;
    private final Organizations organizations;

    public FacilityApprovalService(
            Deals deals, FacilityRepository facilities, ApprovalRequests approvals, Organizations organizations) {
        this.deals = deals;
        this.facilities = facilities;
        this.approvals = approvals;
        this.organizations = organizations;
    }

    /** UC-015 step 7(UC-024 step 1〜4): 組成の稟議の起案を始め、承認経路の案を示す。 */
    public FormationApprovalProposal propose(long dealId, ActingUser user) {
        DealForFormation deal = visibleDeal(deals.findForFormation(dealId), dealId, user);
        if (deal.status().isClosed()) {
            throw new BusinessRuleException("融資案件が取下げか謝絶になったため、組成の稟議を起案できません。");
        }
        Facility facility = draftFacility(deal);
        return new FormationApprovalProposal(facility.id(), facility.version(),
                approvals.propose(subject(deal, facility), user));
    }

    /** UC-015 step 7、8: 組成の稟議を申請し、融資案件を組成中にする。 */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public SubmittedApproval submit(FormationApprovalCommand command, ActingUser user) {
        DealForFormation deal = visibleDeal(deals.lockForFormation(command.dealId()), command.dealId(), user);
        Optional<Facility> current = facilities.findByDealId(deal.id());
        if (deal.status().isClosed()) {
            // UC-015 A10 / BR-010: a withdrawn or declined deal keeps no facility under formation.
            current.filter(f -> f.approvalRequestId() == null).ifPresent(f -> facilities.delete(f.id()));
            throw new BusinessRuleException("融資案件が取下げか謝絶になったため、組成の稟議を起案できません。"
                    + "組成中のファシリティは破棄しました。");
        }
        Facility facility = draftFacility(deal);
        // NFR-003: the facility shown with the proposal must not have changed since.
        if (facility.version() != command.facilityVersion()) {
            throw new ConcurrentChangeException("ファシリティが別の操作で変わりました。起案をやり直してください。");
        }
        SubmittedApproval submitted = approvals.submit(new ApprovalDraft(subject(deal, facility),
                command.routeRuleId(), command.routeRuleVersion(), command.title(), command.body(), command.steps()),
                user);
        // UC-015 BR-007: once under approval, shares and period stay as they are.
        facilities.update(facility.underApproval(submitted.requestId()));
        // UC-015 BR-010: drafting the formation request turns the deal from preparing to forming.
        deals.startFormation(deal.id());
        return submitted;
    }

    // UC-015 BR-005: route attribute amount and the conditions compared on redraft (credit score: #31).
    private ApprovalSubject subject(DealForFormation deal, Facility facility) {
        Map<Long, BigDecimal> shares = facility.sharePie().shares();
        String borrower = organizations.namesOf(List.of(deal.borrowerOrganizationId()))
                .getOrDefault(deal.borrowerOrganizationId(), "");
        Map<String, String> conditions = new LinkedHashMap<>();
        conditions.put("currency", facility.currency());
        conditions.put("start_date", facility.period().startDate().toString());
        conditions.put("end_date", facility.period().endDate().toString());
        conditions.put("shares", shares.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";")));
        return new ApprovalSubject(SUBJECT_TYPE, "facility:" + facility.id(),
                (borrower + " " + deal.purpose() + " ファシリティ組成").strip(),
                Map.of("amount", facility.commitmentAmount()), conditions);
    }

    /** 稟議にかけられる組成中のファシリティ。 */
    private Facility draftFacility(DealForFormation deal) {
        Facility facility = facilities.findByDealId(deal.id())
                .orElseThrow(() -> new BusinessRuleException("組成中のファシリティがありません。先に組成を確定してください。"));
        if (!facility.isRevisable()) {
            throw new BusinessRuleException("ファシリティが確定しているか稟議にかかっているため、組成の稟議を起案できません。");
        }
        return facility;
    }

    private static DealForFormation visibleDeal(Optional<DealForFormation> found, long dealId, ActingUser user) {
        // NFR-007: only the lead bank of the deal sees its formation; others get "not found".
        DealForFormation deal = found
                .filter(d -> user.belongsTo(d.leadOrganizationId()))
                .orElseThrow(() -> new NotFoundException("融資案件が見つかりません: " + dealId));
        if (!user.hasRole(ActingUser.BUSINESS_USER)) {
            throw new ForbiddenException("組成の稟議の起案は、業務ユーザーだけができます。");
        }
        return deal;
    }
}
