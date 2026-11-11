package com.example.syndicateloan.organization.web;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.ActingUserProvider;
import com.example.syndicateloan.organization.UnauthenticatedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 開発・テスト以外の設定: ログイン(UC-006)ができるまで、どの利用者も受け付けない(安全側に倒す)。
 */
@Component
@Profile("!dev & !test")
public class NoActingUserProvider implements ActingUserProvider {

    @Override
    public ActingUser current() {
        throw new UnauthenticatedException("ログインの仕組みがまだありません。");
    }
}
