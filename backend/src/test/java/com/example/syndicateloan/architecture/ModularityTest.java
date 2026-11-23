package com.example.syndicateloan.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.syndicateloan.SyndicateLoanApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/** Spring Modulith でモジュールの境界を確かめる(循環しない、他モジュールの内部を参照しない)。 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(SyndicateLoanApplication.class);

    @Test
    void modulesAreTheOnesTheArchitectureNames() {
        assertThat(modules.stream().map(ApplicationModule::getDisplayName))
                .containsExactlyInAnyOrder(
                        "organization", "workflow", "party", "syndicate",
                        "facility", "loan", "fee", "transaction", "shared");
    }

    @Test
    void moduleBoundariesAreRespected() {
        modules.verify();
    }
}
