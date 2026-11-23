package com.example.syndicateloan.organization.web;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.ActingUserProvider;
import com.example.syndicateloan.organization.UnauthenticatedException;
import com.example.syndicateloan.organization.domain.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 開発・テスト用: リクエストのヘッダー {@value #HEADER} の利用者 id を、操作している利用者とする。
 *
 * <p>誰でも他人になりすませるので、プロファイル {@code dev} と {@code test} でだけ有効にする。
 * ログイン(UC-006)を作るときに、本物の認証のアダプタに差し替える(Issue #28)。
 */
@Component
@Profile({"dev", "test"})
public class DevelopmentHeaderActingUserProvider implements ActingUserProvider {

    /** 操作している利用者の id を運ぶヘッダー。 */
    public static final String HEADER = "X-Acting-User";

    private final AppUserRepository users;

    public DevelopmentHeaderActingUserProvider(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public ActingUser current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new UnauthenticatedException("操作している利用者が分かりません。");
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HEADER);
        long userId;
        try {
            userId = Long.parseLong(header);
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException("操作している利用者が分かりません。");
        }
        return users.findActiveUser(userId)
                .orElseThrow(() -> new UnauthenticatedException("操作している利用者が分かりません。"));
    }
}
