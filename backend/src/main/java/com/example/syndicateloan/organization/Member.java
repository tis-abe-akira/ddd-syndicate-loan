package com.example.syndicateloan.organization;

/**
 * 利用組織に属する利用者と、その職位。承認者を決める(workflow)のに使う。
 *
 * @param userId         利用者
 * @param organizationId 所属する利用組織
 * @param name           氏名
 * @param active         有効か
 * @param position       職位。持たなければ null
 */
public record Member(long userId, long organizationId, String name, boolean active, Position position) {

    /** 職位の順位。職位を持たなければ 0(どの職位よりも弱い)。 */
    public int rank() {
        return position == null ? 0 : position.rank();
    }
}
