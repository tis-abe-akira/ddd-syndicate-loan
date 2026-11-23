package com.example.syndicateloan.facility.application;

import com.example.syndicateloan.facility.domain.FormationInput;

/**
 * UC-015 step 4: 組成の確定。
 *
 * @param dealId                   融資案件
 * @param participationFingerprint 組成を始めたときに示した、参加の回答の指紋(A6)
 * @param facilityVersion          組成を始めたときに示したファシリティのバージョン。まだなければ null
 * @param input                    担当者が決めた組成の内容
 */
public record FormFacilityCommand(
        long dealId, String participationFingerprint, Integer facilityVersion, FormationInput input) {
}
