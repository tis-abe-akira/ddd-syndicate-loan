/**
 * 承認ワークフロー(稟議、ADR-002)。業務に依存せず、依存してよいのは organization だけ。
 */
@ApplicationModule(displayName = "workflow")
package com.example.syndicateloan.workflow;

import org.springframework.modulith.ApplicationModule;
