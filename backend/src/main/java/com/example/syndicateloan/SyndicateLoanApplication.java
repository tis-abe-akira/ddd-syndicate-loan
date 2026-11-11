package com.example.syndicateloan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * シンジケートローン管理システムのエントリポイント。
 *
 * <p>直下のパッケージが Spring Modulith のアプリケーションモジュールになる
 * (docs/architecture/stack.md の Module layout)。
 */
@SpringBootApplication
public class SyndicateLoanApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyndicateLoanApplication.class, args);
    }
}
