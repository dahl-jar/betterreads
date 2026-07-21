package com.betterreads;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.betterreads", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureRules {

    private static final String CONTROLLER_PACKAGE = "..controller..";

    private static final String REPOSITORY_PACKAGE = "..repository..";

    private static final String SERVICE_PACKAGE = "..service..";

    private static final String ENTITY_PACKAGE = "..entity..";

    private static final String SOURCE_PORT_PACKAGE = "..service.source.port..";

    private static final String SOURCE_MODEL_PACKAGE = "..service.source.model..";

    private static final String CATALOG_PACKAGE = "..catalog..";

    private static final String MINIO_INTEGRATION_PACKAGE = "..integration.minio..";

    private static final String ROOT_CONFIG_PACKAGES = "com.betterreads.config..";

    private static final String ROOT_COMMON_PACKAGES = "com.betterreads.common..";

    private static final String PACKAGE_ROOT = "com.betterreads.";

    /** Self-contained slices whose entity and repository share one package. */
    private static final String[] SELF_CONTAINED_SLICES =
        {"..auth.refresh..", "..auth.token..", "..mail.outbox.."};

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION = noFields()
        .should()
        .beAnnotatedWith(Autowired.class);

    @ArchTest
    static final ArchRule CONTROLLERS_SHOULD_NOT_ACCESS_REPOSITORIES = noClasses()
        .that()
        .resideInAPackage(CONTROLLER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(REPOSITORY_PACKAGE);

    @ArchTest
    static final ArchRule SERVICES_SHOULD_NOT_DEPEND_ON_CONTROLLERS = noClasses()
        .that()
        .resideInAPackage(SERVICE_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(CONTROLLER_PACKAGE);

    @ArchTest
    static final ArchRule CONTROLLERS_SHOULD_RESIDE_IN_CONTROLLER_PACKAGE = classes()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .resideInAPackage(CONTROLLER_PACKAGE);

    @ArchTest
    static final ArchRule REPOSITORIES_SHOULD_RESIDE_IN_REPOSITORY_PACKAGE = classes()
        .that()
        .haveSimpleNameEndingWith("Repository")
        .and()
        .resideOutsideOfPackages(SELF_CONTAINED_SLICES)
        .should()
        .resideInAPackage(REPOSITORY_PACKAGE);

    @ArchTest
    static final ArchRule ENTITIES_SHOULD_RESIDE_IN_ENTITY_PACKAGE = classes()
        .that()
        .areAnnotatedWith(Entity.class)
        .and()
        .resideOutsideOfPackages(SELF_CONTAINED_SLICES)
        .should()
        .resideInAPackage(ENTITY_PACKAGE);

    @ArchTest
    static final ArchRule REPOSITORIES_SHOULD_NOT_DEPEND_ON_SERVICES_OR_CONTROLLERS = noClasses()
        .that()
        .resideInAPackage(REPOSITORY_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(SERVICE_PACKAGE, CONTROLLER_PACKAGE);

    @ArchTest
    static final ArchRule SOURCE_PORTS_SHOULD_BE_INTERFACES_OR_RECORDS = classes()
        .that()
        .resideInAPackage(SOURCE_PORT_PACKAGE)
        .and()
        .areTopLevelClasses()
        .should()
        .beInterfaces()
        .orShould()
        .beRecords();

    @ArchTest
    static final ArchRule SOURCE_MODELS_SHOULD_BE_RECORDS_OR_ENUMS = classes()
        .that()
        .resideInAPackage(SOURCE_MODEL_PACKAGE)
        .and()
        .areTopLevelClasses()
        .should()
        .beRecords()
        .orShould()
        .beEnums();

    @ArchTest
    static final ArchRule CATALOG_SHOULD_NOT_DEPEND_ON_MINIO_INTEGRATION = noClasses()
        .that()
        .resideInAPackage(CATALOG_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(MINIO_INTEGRATION_PACKAGE);

    /**
     * A feature's repository and entity packages are its private persistence layer; other features
     * read through its service and DTO types. config and common hold cross-cutting wiring and are
     * exempt. The self-contained slices (auth.refresh, auth.token, mail.outbox) hold their
     * persistence types in the feature root, outside any repository or entity package segment, so
     * the feature comparison ties them to their own feature.
     */
    @ArchTest
    static final ArchRule REPOSITORIES_AND_ENTITIES_ARE_ACCESSED_ONLY_WITHIN_THEIR_FEATURE = classes()
        .that()
        .resideOutsideOfPackages(ROOT_CONFIG_PACKAGES, ROOT_COMMON_PACKAGES)
        .should(accessPersistenceOfTheirOwnFeatureOnly());

    private static ArchCondition<JavaClass> accessPersistenceOfTheirOwnFeatureOnly() {
        return new ArchCondition<>("access repositories and entities of their own feature only") {
            @Override
            public void check(final JavaClass origin, final ConditionEvents events) {
                origin.getDirectDependenciesFromSelf().stream()
                    .filter(dependency -> isForeignFeaturePersistence(origin, dependency.getTargetClass()))
                    .forEach(dependency -> events.add(
                        SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static boolean isForeignFeaturePersistence(final JavaClass origin, final JavaClass target) {
        return isPersistence(target) && !feature(origin).equals(feature(target));
    }

    private static boolean isPersistence(final JavaClass type) {
        final String packageName = type.getPackageName();
        return packageName.startsWith(PACKAGE_ROOT)
            && (hasSegment(packageName, "repository") || hasSegment(packageName, "entity"));
    }

    private static String feature(final JavaClass type) {
        final String packageName = type.getPackageName();
        if (!packageName.startsWith(PACKAGE_ROOT)) {
            return packageName;
        }
        final String rest = packageName.substring(PACKAGE_ROOT.length());
        final int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static boolean hasSegment(final String packageName, final String segment) {
        return packageName.contains("." + segment + ".") || packageName.endsWith("." + segment);
    }

    private ArchitectureRules() {
    }
}
