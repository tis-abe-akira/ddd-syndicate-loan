package com.example.syndicateloan.facility.web;

import com.example.syndicateloan.facility.application.FacilityApprovalService;
import com.example.syndicateloan.facility.application.FormationApprovalCommand;
import com.example.syndicateloan.organization.ActingUserProvider;
import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import com.example.syndicateloan.workflow.StepKind;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-015 step 7、8: 組成の稟議を起案する(UC-024 を含む)。 */
@RestController
@RequestMapping("/api/deals/{dealId}/facility")
public class FacilityApprovalController {

    private final FacilityApprovalService service;
    private final ActingUserProvider actingUser;

    public FacilityApprovalController(FacilityApprovalService service, ActingUserProvider actingUser) {
        this.service = service;
        this.actingUser = actingUser;
    }

    /** UC-015 step 7(UC-024 step 1〜4): 組成の稟議の起案を始め、承認経路の案を受け取る。 */
    @GetMapping("/approval-proposal")
    public FormationApprovalProposalResponse propose(@PathVariable long dealId) {
        return FormationApprovalProposalResponse.of(service.propose(dealId, actingUser.current()));
    }

    /** UC-015 step 7、8: 組成の稟議を申請する。融資案件は組成中になる。 */
    @PostMapping("/approval-request")
    @ResponseStatus(HttpStatus.CREATED)
    public FormationApprovalResponse submit(
            @PathVariable long dealId, @Valid @RequestBody FormationApprovalRequest request) {
        var steps = request.steps().stream()
                .map(s -> new StepAssignment(StepKind.valueOf(s.kind()), s.order(), s.userId()))
                .toList();
        return FormationApprovalResponse.of(service.submit(new FormationApprovalCommand(dealId,
                request.facilityVersion(), request.routeRuleId(), request.routeRuleVersion(), request.title(),
                request.body(), steps), actingUser.current()));
    }
}
