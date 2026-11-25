package com.example.syndicateloan.organization.web;

import com.example.syndicateloan.organization.domain.ActiveUser;
import com.example.syndicateloan.organization.domain.AppUserRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 開発用: 画面で、操作する利用者({@value DevelopmentHeaderActingUserProvider#HEADER})を選ぶための一覧。
 * プロファイル dev でだけ有効にする。ログイン(UC-006)ができたら削除する。
 */
@RestController
@Profile("dev")
public class DevelopmentActingUsersController {

    private final AppUserRepository users;

    public DevelopmentActingUsersController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/dev/acting-users")
    public List<ActiveUser> actingUsers() {
        return users.findAllActive();
    }
}
