package com.example.syndicateloan.facility.web;

import com.example.syndicateloan.facility.application.FacilityFormationService;
import com.example.syndicateloan.facility.application.FormFacilityCommand;
import com.example.syndicateloan.facility.application.FormationView;
import com.example.syndicateloan.facility.domain.Facility;
import com.example.syndicateloan.facility.domain.FormationInput;
import com.example.syndicateloan.organization.ActingUserProvider;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-015 ファシリティを組成する。 */
@RestController
@RequestMapping("/api/deals/{dealId}")
public class FacilityFormationController {

    private final FacilityFormationService service;
    private final ActingUserProvider actingUser;

    public FacilityFormationController(FacilityFormationService service, ActingUserProvider actingUser) {
        this.service = service;
        this.actingUser = actingUser;
    }

    /** UC-015 step 1、2: 組成を始める。 */
    @GetMapping("/facility-formation")
    public FormationView start(@PathVariable long dealId) {
        return service.start(dealId, actingUser.current());
    }

    /** UC-015 step 4〜6: 組成を確定する。ファシリティがなければ登録し、組成中なら直す。 */
    @PutMapping("/facility")
    public FacilityResponse form(@PathVariable long dealId, @Valid @RequestBody FormFacilityRequest request) {
        Map<Long, java.math.BigDecimal> shares = new LinkedHashMap<>();
        for (FormFacilityRequest.InvestorShareRequest share : request.investorShares()) {
            if (shares.containsKey(share.organizationId())) {
                throw new IllegalArgumentException("duplicate organizationId: " + share.organizationId());
            }
            shares.put(share.organizationId(), share.amount());
        }
        FormationInput input = new FormationInput(shares, request.leadCommitmentAmount(),
                request.startDate(), request.endDate());
        Facility facility = service.form(
                new FormFacilityCommand(dealId, request.participationFingerprint(), request.facilityVersion(), input), actingUser.current());
        return FacilityResponse.of(facility);
    }
}
