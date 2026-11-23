package com.example.syndicateloan.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.modfixture.cycle.CycleFixture;
import com.example.modfixture.internal.InternalFixture;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/** Spring Modulith の検査が、わざと壊したモジュール構成を見つけることを確かめる。 */
class ModularityRulesSelfTest {

    /** フィクスチャはテストのクラスなので、テストのクラスも読み込む(本体の ModularityTest は既定のまま読み込まない)。 */
    private static final ImportOption INCLUDE_TEST_CLASSES = location -> true;

    @Test
    void rejectsCyclesBetweenModules() {
        assertThatThrownBy(() -> ApplicationModules.of(CycleFixture.class, INCLUDE_TEST_CLASSES).verify())
                .isInstanceOf(Violations.class);
    }

    @Test
    void rejectsAccessToAnotherModulesInternals() {
        assertThatThrownBy(() -> ApplicationModules.of(InternalFixture.class, INCLUDE_TEST_CLASSES).verify())
                .isInstanceOf(Violations.class);
    }
}
