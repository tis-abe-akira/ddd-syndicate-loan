package com.example.syndicateloan.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.stream.Stream;

/**
 * このプロジェクトの構造の規則(docs/architecture/design-principles.md「構造テスト」)。
 *
 * <p>規則はルートのパッケージを引数にとる。本物のアプリケーション({@code com.example.syndicateloan})に当てるほか、
 * わざと違反させたフィクスチャに当てて、規則が違反を見つけることを確かめる({@link ArchitectureRulesSelfTest})。
 *
 * <p>規則を弱めて(例外を足して)ビルドを通さない。規則を変えるなら、先に ADR で理由を残す。
 */
final class ArchitectureRules {

    static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";

    private final String root;

    ArchitectureRules(String root) {
        this.root = root;
    }

    List<ArchRule> all() {
        return List.of(
                workflowDependsOnlyOnOrganization(),
                organizationDependsOnNoOtherModule(),
                domainDoesNotDependOnOuterLayers(),
                domainDoesNotUseFrameworks(),
                domainTypesAreImmutable(),
                transactionalPortsHaveNoDefaultMethods());
    }

    /** ADR-002: workflow が依存してよいモジュールは organization だけ。 */
    ArchRule workflowDependsOnlyOnOrganization() {
        return noClasses().that().resideInAPackage(root + ".workflow..")
                .should().dependOnClassesThat(insideApplicationExcept("workflow", "organization"))
                .because("ADR-002: workflow は汎用のワークフロー SaaS として切り出すため、organization 以外のモジュールに依存しない")
                .allowEmptyShould(true);
    }

    /** ADR-003: organization はどのモジュールにも依存しない。 */
    ArchRule organizationDependsOnNoOtherModule() {
        return noClasses().that().resideInAPackage(root + ".organization..")
                .should().dependOnClassesThat(insideApplicationExcept("organization"))
                .because("ADR-003: organization はシンジケートローンの知識を持たず、どのモジュールにも依存しない")
                .allowEmptyShould(true);
    }

    /** design-principles 第1節: ドメインは application・persistence・client・web に依存しない。 */
    ArchRule domainDoesNotDependOnOuterLayers() {
        return noClasses().that().resideInAPackage(root + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + "..application..", root + "..persistence..", root + "..client..", root + "..web..")
                .because("ドメインは外側の層を知らない(依存の向きは web → ドメイン ← persistence)")
                .allowEmptyShould(true);
    }

    /** design-principles 第1節: ドメイン型に Spring・永続化・外部連携のライブラリを持ち込まない。 */
    ArchRule domainDoesNotUseFrameworks() {
        return noClasses().that().resideInAPackage(root + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.jooq..", "io.github.resilience4j..")
                .because("ドメイン型は永続化・Spring のアノテーションを持たず、外部のライブラリに依存しない")
                .allowEmptyShould(true);
    }

    /** design-principles 第1節: ドメイン型は不変(record か final クラス)。インターフェースと enum は除く。 */
    ArchRule domainTypesAreImmutable() {
        return classes().that().resideInAPackage(root + "..domain..")
                .and().areTopLevelClasses()
                .and().areNotInterfaces()
                .and().areNotEnums()
                .and().doNotHaveSimpleName("package-info")
                .should().haveModifier(JavaModifier.FINAL)
                .because("ドメイン型は不変な record か final クラスとし、自分で不変条件を守る")
                .allowEmptyShould(true);
    }

    /**
     * design-principles 第2節: {@code @Transactional} なクラスが実装するポートに {@code default} メソッドを置かない。
     * default メソッドはトランザクションのプロキシの外で動く(run-buddy ADR-0007 の実際のバグ)。
     */
    ArchRule transactionalPortsHaveNoDefaultMethods() {
        return classes().that().resideInAPackage(root + "..")
                .and(areTransactional())
                .should(implementOnlyInterfacesWithoutDefaultMethods())
                .because("default メソッドは @Transactional のプロキシの外で実行される")
                .allowEmptyShould(true);
    }

    private DescribedPredicate<JavaClass> insideApplicationExcept(String... allowedModules) {
        return new DescribedPredicate<>("reside in a module of " + root + " other than " + List.of(allowedModules)) {
            @Override
            public boolean test(JavaClass target) {
                String pkg = target.getPackageName();
                if (!pkg.startsWith(root + ".")) {
                    return false;
                }
                return Stream.of(allowedModules)
                        .map(module -> root + "." + module)
                        .noneMatch(allowed -> pkg.equals(allowed) || pkg.startsWith(allowed + "."));
            }
        };
    }

    private static DescribedPredicate<JavaClass> areTransactional() {
        return new DescribedPredicate<>("are @Transactional or have @Transactional methods") {
            @Override
            public boolean test(JavaClass javaClass) {
                return isTransactional(javaClass)
                        || javaClass.getMethods().stream().anyMatch(method ->
                                method.isAnnotatedWith(TRANSACTIONAL) || method.isAnnotatedWith(JAKARTA_TRANSACTIONAL));
            }
        };
    }

    private static boolean isTransactional(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(TRANSACTIONAL)
                || javaClass.isAnnotatedWith(JAKARTA_TRANSACTIONAL)
                || javaClass.isMetaAnnotatedWith(TRANSACTIONAL);
    }

    private static ArchCondition<JavaClass> implementOnlyInterfacesWithoutDefaultMethods() {
        return new ArchCondition<>("implement only interfaces without default methods") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaClass anInterface : javaClass.getAllRawInterfaces()) {
                    for (JavaMethod method : anInterface.getMethods()) {
                        boolean isDefault = !method.getModifiers().contains(JavaModifier.ABSTRACT)
                                && !method.getModifiers().contains(JavaModifier.STATIC)
                                && !method.getModifiers().contains(JavaModifier.SYNTHETIC);
                        if (isDefault) {
                            events.add(SimpleConditionEvent.violated(javaClass,
                                    javaClass.getName() + " implements " + anInterface.getName()
                                            + ", which has the default method " + method.getName()));
                        }
                    }
                }
            }
        };
    }
}
