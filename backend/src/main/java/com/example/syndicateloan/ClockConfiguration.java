package com.example.syndicateloan;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 業務の「今日」は日本時間で決める(Issue #32)。テストは固定した Clock に差し替える。 */
@Configuration
public class ClockConfiguration {

    /** 業務の時間帯。 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    @Bean
    Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
