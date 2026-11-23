package com.example.syndicateloan.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 規則そのものが効いていることを確かめる(run-buddy のミューテーションチェックに当たる)。
 * 各規則を、わざと違反させたフィクスチャに当てて失敗すること、適合したフィクスチャに当てて通ることを見る。
 * 規則を変えたら、この2つの両方が成り立つことを保つ。
 */
class ArchitectureRulesSelfTest {

    private static final String VIOLATING = "com.example.archfixture.violating";
    private static final String COMPLIANT = "com.example.archfixture.compliant";

    private static final JavaClasses VIOLATING_CLASSES = new ClassFileImporter().importPackages(VIOLATING);
    private static final JavaClasses COMPLIANT_CLASSES = new ClassFileImporter().importPackages(COMPLIANT);

    @TestFactory
    Stream<DynamicTest> everyRuleRejectsItsViolation() {
        return rules().map(rule -> DynamicTest.dynamicTest("rejects: " + rule.name(), () ->
                assertThatThrownBy(() -> rule.of(VIOLATING).check(VIOLATING_CLASSES))
                        .isInstanceOf(AssertionError.class)));
    }

    @TestFactory
    Stream<DynamicTest> everyRuleAcceptsCompliantCode() {
        return rules().map(rule -> DynamicTest.dynamicTest("accepts: " + rule.name(), () ->
                assertThatCode(() -> rule.of(COMPLIANT).check(COMPLIANT_CLASSES))
                        .doesNotThrowAnyException()));
    }

    private static Stream<NamedRule> rules() {
        return Stream.of(
                new NamedRule("workflow depends only on organization", ArchitectureRules::workflowDependsOnlyOnOrganization),
                new NamedRule("organization depends on no other module", ArchitectureRules::organizationDependsOnNoOtherModule),
                new NamedRule("domain does not depend on outer layers", ArchitectureRules::domainDoesNotDependOnOuterLayers),
                new NamedRule("domain does not use frameworks", ArchitectureRules::domainDoesNotUseFrameworks),
                new NamedRule("domain types are immutable", ArchitectureRules::domainTypesAreImmutable),
                new NamedRule("transactional ports have no default methods", ArchitectureRules::transactionalPortsHaveNoDefaultMethods));
    }

    private record NamedRule(String name, Function<ArchitectureRules, ArchRule> rule) {
        ArchRule of(String root) {
            return rule.apply(new ArchitectureRules(root));
        }
    }
}
