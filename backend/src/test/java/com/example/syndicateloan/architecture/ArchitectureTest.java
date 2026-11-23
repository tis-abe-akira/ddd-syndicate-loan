package com.example.syndicateloan.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** 本物のアプリケーションに、構造の規則(ArchUnit)をすべて当てる。 */
class ArchitectureTest {

    private static final String ROOT = "com.example.syndicateloan";

    private final JavaClasses application = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @TestFactory
    Stream<DynamicTest> applicationFollowsTheArchitectureRules() {
        return new ArchitectureRules(ROOT).all().stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(application)));
    }
}
