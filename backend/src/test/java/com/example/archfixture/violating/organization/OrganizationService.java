package com.example.archfixture.violating.organization;

import com.example.archfixture.violating.workflow.WorkflowService;

/** 違反: organization が他のモジュール(workflow)に依存する。 */
public class OrganizationService {
    WorkflowService workflow;
}
