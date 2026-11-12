package com.example.syndicateloan.facility.application;

import com.example.syndicateloan.facility.application.FormationView.BlockedReason;
import com.example.syndicateloan.facility.application.FormationView.FacilitySummary;
import com.example.syndicateloan.facility.application.FormationView.Investor;
import com.example.syndicateloan.facility.domain.Facility;
import com.example.syndicateloan.facility.domain.FacilityFormation;
import com.example.syndicateloan.facility.domain.FacilityFormation.Terms;
import com.example.syndicateloan.facility.domain.FacilityRepository;
import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.shared.BusinessRuleException;
import com.example.syndicateloan.shared.ConcurrentChangeException;
import com.example.syndicateloan.shared.ForbiddenException;
import com.example.syndicateloan.shared.NotFoundException;
import com.example.syndicateloan.syndicate.AcceptedInvestor;
import com.example.syndicateloan.syndicate.DealForFormation;
import com.example.syndicateloan.syndicate.DealStatus;
import com.example.syndicateloan.syndicate.Deals;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-015 ファシリティを組成する: step 1〜6 と A1、A2、A3、A5、A6、A9。
 *
 * <p>組成の稟議の起案(step 7、8、A4、A10、BR-005、BR-010)は、承認ワークフロー(UC-024)ができてから足す
 * (Issue #31)。
 */
@Service
@Transactional(readOnly = true)
public class FacilityFormationService {

    private final Deals deals;
    private final FacilityRepository facilities;
    private final Clock clock;

    public FacilityFormationService(Deals deals, FacilityRepository facilities, Clock clock) {
        this.deals = deals;
        this.facilities = facilities;
        this.clock = clock;
    }

    /** UC-015 step 1、2: 組成を始め、参加を応諾した出資者と今のファシリティを示す。 */
    public FormationView start(long dealId, ActingUser user) {
        DealForFormation deal = visibleDeal(deals.findForFormation(dealId), dealId, user);
        Optional<Facility> facility = facilities.findByDealId(dealId);

        BigDecimal totalAccepted = deal.acceptedInvestors().stream()
                .map(AcceptedInvestor::commitmentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var investors = deal.acceptedInvestors().stream()
                .map(i -> new Investor(i.organizationId(), i.commitmentAmount(), facility
                        // UC-015 A3: show the current shares of the facility under formation.
                        .map(f -> f.sharePie().shares().getOrDefault(i.organizationId(), BigDecimal.ZERO))
                        .orElse(i.commitmentAmount())))
                .toList();
        BigDecimal lead = facility
                .map(f -> f.sharePie().shares().getOrDefault(deal.leadOrganizationId(), BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
        FacilitySummary summary = facility
                .map(f -> new FacilitySummary(f.id(), f.status().name(), f.approvalRequestId() != null,
                        f.commitmentAmount(), f.period().startDate(), f.period().endDate(), f.version()))
                .orElse(null);

        return new FormationView(deal.id(), deal.currency(), blockedReason(deal, facility), investors,
                totalAccepted, lead, summary, deal.participationFingerprint());
    }

    /** UC-015 step 4〜6: 組成を確定し、ファシリティを組成中として登録するか、直す。 */
    @Transactional
    public Facility form(FormFacilityCommand command, ActingUser user) {
        // The deal row stays locked until commit, so its participants cannot change meanwhile.
        DealForFormation deal = visibleDeal(deals.lockForFormation(command.dealId()), command.dealId(), user);
        Optional<Facility> current = facilities.findByDealId(deal.id());

        BlockedReason blocked = blockedReason(deal, current);
        if (blocked == BlockedReason.DEAL_CLOSED) {
            // UC-015 A9: the deal was withdrawn or declined; register nothing.
            throw new BusinessRuleException("融資案件が取下げか謝絶になったため、組成できません。");
        }
        if (blocked != null) {
            throw new BusinessRuleException(message(blocked));
        }
        if (!deal.participationFingerprint().equals(command.participationFingerprint())) {
            // UC-015 A6: participation answers changed after they were shown; register nothing.
            throw new ConcurrentChangeException("参加の回答が変わりました。組成をやり直してください。");
        }

        // NFR-003: a facility changed by another operation since it was shown is not overwritten.
        Integer currentVersion = current.map(Facility::version).orElse(null);
        if (!java.util.Objects.equals(currentVersion, command.facilityVersion())) {
            throw new ConcurrentChangeException("ファシリティが別の操作で変わりました。組成をやり直してください。");
        }

        LocalDate today = LocalDate.now(clock);
        Terms terms = FacilityFormation.validate(deal, command.input(), today);

        if (current.isPresent()) {
            // UC-015 A3: revise the facility under formation.
            return facilities.update(current.get().revise(terms.period(), terms.sharePie()));
        }
        // UC-015 BR-008: The facility uses the deal's currency, without conversion.
        return facilities.insert(Facility.draft(deal.id(), deal.currency(), terms.period(), terms.sharePie()));
    }

    private static DealForFormation visibleDeal(Optional<DealForFormation> found, long dealId, ActingUser user) {
        // NFR-007: only the lead bank of the deal sees its formation; others get "not found".
        DealForFormation deal = found
                .filter(d -> user.belongsTo(d.leadOrganizationId()))
                .orElseThrow(() -> new NotFoundException("融資案件が見つかりません: " + dealId));
        if (!user.hasRole(ActingUser.BUSINESS_USER)) {
            throw new ForbiddenException("ファシリティの組成は、業務ユーザーだけができます。");
        }
        return deal;
    }

    private static BlockedReason blockedReason(DealForFormation deal, Optional<Facility> facility) {
        if (deal.status().isClosed()) {
            return BlockedReason.DEAL_CLOSED;
        }
        // UC-015 A2: a FIXED facility or one under approval cannot be changed.
        if (facility.isPresent() && !facility.get().isRevisable()) {
            return BlockedReason.FACILITY_LOCKED;
        }
        if (deal.status() != DealStatus.PREPARING) {
            return BlockedReason.DEAL_NOT_PREPARING;
        }
        // UC-015 A1: there is no investor who accepted to participate.
        if (deal.acceptedInvestors().isEmpty()) {
            return BlockedReason.NO_ACCEPTED_INVESTOR;
        }
        return null;
    }

    private static String message(BlockedReason reason) {
        return switch (reason) {
            case NO_ACCEPTED_INVESTOR -> "参加を応諾した出資者がいないため、組成できません。";
            case FACILITY_LOCKED -> "ファシリティが確定しているか稟議にかかっているため、今は組成を変えられません。";
            case DEAL_NOT_PREPARING -> "融資案件が組成準備中でないため、組成できません。";
            case DEAL_CLOSED -> "融資案件が取下げか謝絶になったため、組成できません。";
        };
    }
}
