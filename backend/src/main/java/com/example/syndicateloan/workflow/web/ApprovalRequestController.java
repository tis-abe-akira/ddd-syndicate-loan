package com.example.syndicateloan.workflow.web;

import com.example.syndicateloan.organization.ActingUserProvider;
import com.example.syndicateloan.workflow.ApprovalDecisions;
import com.example.syndicateloan.workflow.ApprovalView;
import com.example.syndicateloan.workflow.AssignedApproval;
import com.example.syndicateloan.workflow.DecisionCommand;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-025 稟議に合議する、UC-026 稟議を承認する。判断は業務の値を含まないので、workflow が REST を持つ。 */
@RestController
@RequestMapping("/api/approval-requests")
public class ApprovalRequestController {

    private final ApprovalDecisions decisions;
    private final ActingUserProvider actingUser;

    public ApprovalRequestController(ApprovalDecisions decisions, ActingUserProvider actingUser) {
        this.decisions = decisions;
        this.actingUser = actingUser;
    }

    /** UC-025・UC-026 step 1: 自分が今判断する番の稟議。 */
    @GetMapping("/assigned")
    public List<AssignedApproval> assigned() {
        return decisions.assigned(actingUser.current());
    }

    /** UC-025・UC-026 step 2: 稟議を開く。 */
    @GetMapping("/{requestId}")
    public ApprovalView view(@PathVariable long requestId) {
        return decisions.view(requestId, actingUser.current());
    }

    /** UC-025・UC-026 step 3〜8: 判断する。 */
    @PostMapping("/{requestId}/decisions")
    public ApprovalView decide(@PathVariable long requestId, @RequestBody DecisionRequest request) {
        return decisions.decide(requestId,
                new DecisionCommand(request.decision(), request.comment(), request.viewedVersion()),
                actingUser.current());
    }
}
