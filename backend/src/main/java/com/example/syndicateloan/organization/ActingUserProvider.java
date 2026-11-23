package com.example.syndicateloan.organization;

/**
 * いま操作している利用者を返すポート。
 *
 * <p>ログイン(UC-006)と ID プロバイダ(Issue #7、#28)が決まるまでは、開発・テスト用のアダプタだけがある。
 * それ以外の設定では利用者を返さず、すべての操作を拒否する。
 */
public interface ActingUserProvider {

    /**
     * いま操作している利用者。
     *
     * @throws UnauthenticatedException 利用者が分からないか、無効なとき
     */
    ActingUser current();
}
