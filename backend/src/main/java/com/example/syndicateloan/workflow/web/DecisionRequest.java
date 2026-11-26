package com.example.syndicateloan.workflow.web;

import com.example.syndicateloan.workflow.Decision;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-025・UC-026 step 3、4: 判断。
 *
 * @param decision      APPROVE、SEND_BACK、REJECT
 * @param comment       コメント。差し戻しと否決では必須
 * @param viewedVersion 稟議を開いたときのバージョン
 */
public record DecisionRequest(
        Decision decision, @Schema(types = {"string", "null"}) String comment, int viewedVersion) {
}
