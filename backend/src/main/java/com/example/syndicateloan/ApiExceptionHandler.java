package com.example.syndicateloan;

import com.example.syndicateloan.organization.UnauthenticatedException;
import com.example.syndicateloan.shared.BusinessRuleException;
import com.example.syndicateloan.shared.ConcurrentChangeException;
import com.example.syndicateloan.shared.ForbiddenException;
import com.example.syndicateloan.shared.InvalidInputException;
import com.example.syndicateloan.shared.NotFoundException;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalForbiddenException;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException;
import com.example.syndicateloan.workflow.ApprovalNotFoundException;
import com.example.syndicateloan.workflow.DecisionNotAllowedException;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 例外を RFC 9457 の Problem Details にする(stack.md の API contract)。 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    ProblemDetail invalidInput(InvalidInputException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-input", e.getMessage());
        problem.setProperty("errors", e.errors());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", e.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ProblemDetail unauthenticated(UnauthenticatedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, "business-rule", e.getMessage());
    }

    @ExceptionHandler(ConcurrentChangeException.class)
    ProblemDetail concurrentChange(ConcurrentChangeException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-change", e.getMessage());
    }

    /** UC-024 A1・A2・A3: 理由(reason)と、進んでいるか否決された稟議(existingRequestId)を添える。 */
    @ExceptionHandler(ApprovalNotAllowedException.class)
    ProblemDetail approvalNotAllowed(ApprovalNotAllowedException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "approval-not-allowed", e.getMessage());
        problem.setProperty("reason", e.reason().name());
        problem.setProperty("existingRequestId", e.existingRequestId());
        return problem;
    }

    /** UC-024 A5: 直すべき項目(title、steps、steps[添字])を、入力の誤りと同じ形で返す。 */
    @ExceptionHandler(InvalidApprovalDraftException.class)
    ProblemDetail invalidApprovalDraft(InvalidApprovalDraftException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-input", e.getMessage());
        problem.setProperty("errors", e.errors());
        return problem;
    }

    @ExceptionHandler(ApprovalConcurrentChangeException.class)
    ProblemDetail approvalConcurrentChange(ApprovalConcurrentChangeException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-change", e.getMessage());
    }

    @ExceptionHandler(ApprovalForbiddenException.class)
    ProblemDetail approvalForbidden(ApprovalForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", e.getMessage());
    }

    /** UC-025・UC-026 A1: 他社の稟議は、存在するかどうかも分からない(NFR-007)。 */
    @ExceptionHandler(ApprovalNotFoundException.class)
    ProblemDetail approvalNotFound(ApprovalNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    /** UC-025・UC-026 A2: 今は判断できない理由(reason)を添える。 */
    @ExceptionHandler(DecisionNotAllowedException.class)
    ProblemDetail decisionNotAllowed(DecisionNotAllowedException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "decision-not-allowed", e.getMessage());
        problem.setProperty("reason", e.reason().name());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + type));
        return problem;
    }
}
