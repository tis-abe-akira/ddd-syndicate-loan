package com.example.syndicateloan.organization;

/**
 * 利用組織の中の職位。弱い順に並ぶ階層の一段。
 *
 * @param id             職位
 * @param organizationId 職位を定義した利用組織
 * @param name           呼び方(部長、本部長など)
 * @param rank           階層の順位。1が最も弱い
 */
public record Position(long id, long organizationId, String name, int rank) {
}
