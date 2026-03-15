package com.betterreads;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.betterreads", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureRules {

    private static final String CONTROLLER_PACKAGE = "..controller..";

    private static final String REPOSITORY_PACKAGE = "..repository..";

    private static final String SERVICE_PACKAGE = "..service..";

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION = noFields()
        .should()
        .beAnnotatedWith(Autowired.class)
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule CONTROLLERS_SHOULD_NOT_ACCESS_REPOSITORIES = noClasses()
        .that()
        .resideInAPackage(CONTROLLER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(REPOSITORY_PACKAGE).allowEmptyShould(true);

    @ArchTest
    static final ArchRule SERVICES_SHOULD_NOT_DEPEND_ON_CONTROLLERS = noClasses()
        .that()
        .resideInAPackage(SERVICE_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(CONTROLLER_PACKAGE).allowEmptyShould(true);

    private ArchitectureRules() {
    }
}
