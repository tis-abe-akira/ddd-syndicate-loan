package com.example.archfixture.compliant.notification;

import com.example.archfixture.compliant.organization.OrganizationId;
import com.example.archfixture.compliant.workflow.ApprovalRequest;

/** 適合: notification は organization と workflow にだけ依存する。 */
public class NoticeListener {
    OrganizationId organization;
    ApprovalRequest request;
}
