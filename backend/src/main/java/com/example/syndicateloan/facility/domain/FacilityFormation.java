package com.example.syndicateloan.facility.domain;

import com.example.syndicateloan.shared.InvalidInputException;
import com.example.syndicateloan.shared.InvalidInputException.FieldError;
import com.example.syndicateloan.shared.SharePie;
import com.example.syndicateloan.syndicate.AcceptedInvestor;
import com.example.syndicateloan.syndicate.DealForFormation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UC-015 step 5: 組成の内容が業務ルールを満たすことを確かめ、シェアパイを作る。 */
public final class FacilityFormation {

    private FacilityFormation() {
    }

    /**
     * 組成の内容から、ファシリティのシェアパイと引き出せる期間を作る。
     *
     * @throws InvalidInputException 直すべき項目があるとき(A5)。すべての項目をまとめて示す
     */
    public static Terms validate(DealForFormation deal, FormationInput input, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        Map<Long, BigDecimal> accepted = new LinkedHashMap<>();
        deal.acceptedInvestors().forEach(i -> accepted.put(i.organizationId(), i.commitmentAmount()));

        for (Long organizationId : input.investorShares().keySet()) {
            if (!accepted.containsKey(organizationId)) {
                errors.add(new FieldError("investorShares[" + organizationId + "]",
                        "参加を応諾した出資者ではありません。"));
            }
        }
        for (AcceptedInvestor investor : deal.acceptedInvestors()) {
            String field = "investorShares[" + investor.organizationId() + "]";
            BigDecimal share = input.investorShares().get(investor.organizationId());
            if (share == null) {
                errors.add(new FieldError(field, "持分を入力してください。"));
            } else if (share.signum() < 0) {
                errors.add(new FieldError(field, "持分は0円以上にしてください。"));
            }
            // UC-015 BR-009: An investor's share does not exceed its accepted commitment.
            else if (share.compareTo(investor.commitmentAmount()) > 0) {
                errors.add(new FieldError(field,
                        "持分は応諾したコミットメント額(" + investor.commitmentAmount().toPlainString()
                                + ")以下にしてください。"));
            }
            // UC-015 BR-001: An accepted investor with a share above zero enters the pie.
            else if (share.signum() > 0) {
                shares.put(investor.organizationId(), share);
            }
        }

        BigDecimal lead = input.leadCommitmentAmount();
        if (lead == null) {
            errors.add(new FieldError("leadCommitmentAmount", "リード銀行のコミットメント額を入力してください。"));
        } else if (lead.signum() < 0) {
            errors.add(new FieldError("leadCommitmentAmount", "リード銀行のコミットメント額は0円以上にしてください。"));
        }
        // UC-015 BR-001: The lead bank enters the pie with a commitment above zero.
        else if (lead.signum() > 0) {
            shares.put(deal.leadOrganizationId(), lead);
        }

        if (errors.isEmpty() && shares.isEmpty()) {
            errors.add(new FieldError("investorShares", "持分が0円より大きい参加金融機関がありません。"));
        }

        if (input.startDate() == null) {
            errors.add(new FieldError("startDate", "引き出せる期間の初日を入力してください。"));
        }
        // UC-015 BR-003: The first day of the drawdown period is today or later.
        else if (input.startDate().isBefore(today)) {
            errors.add(new FieldError("startDate", "引き出せる期間の初日は今日以降にしてください。"));
        }
        if (input.endDate() == null) {
            errors.add(new FieldError("endDate", "引き出せる期間の末日を入力してください。"));
        }
        // UC-015 BR-003: The last day of the drawdown period comes after the first day.
        else if (input.startDate() != null && !input.endDate().isAfter(input.startDate())) {
            errors.add(new FieldError("endDate", "引き出せる期間の末日は初日より後にしてください。"));
        }

        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
        return new Terms(new DrawdownPeriod(input.startDate(), input.endDate()), new SharePie(shares));
    }

    /**
     * 確かめた組成の内容。
     *
     * @param period   引き出せる期間
     * @param sharePie ファシリティのシェアパイ
     */
    public record Terms(DrawdownPeriod period, SharePie sharePie) {
    }
}
