package com.example.syndicateloan.organization.domain;

import com.example.syndicateloan.organization.ActingUser;
import java.util.Optional;

/** 利用者のポート。 */
public interface AppUserRepository {

    /** 有効な利用者を、所属とロールとともに返す。無効か存在しなければ空。 */
    Optional<ActingUser> findActiveUser(long userId);
}
