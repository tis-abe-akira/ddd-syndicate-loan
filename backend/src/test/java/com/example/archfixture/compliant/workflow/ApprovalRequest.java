package com.example.archfixture.compliant.workflow;

import com.example.archfixture.compliant.organization.OrganizationId;

/** 適合: workflow は organization だけに依存する。 */
public record ApprovalRequest(OrganizationId tenant, String subjectRef) {
}
