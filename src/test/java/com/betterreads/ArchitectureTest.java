package com.betterreads;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// PMD.LooseCoupling, PMD.TestClassWithoutTestCases: check() takes JavaClasses, PMD ignores @ArchTest.
@SuppressWarnings({"PMD.LooseCoupling", "PMD.TestClassWithoutTestCases"})
@AnalyzeClasses(packages = "com.betterreads", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private static final String FEATURES = "com.betterreads.features..";

    private static final String CLIENTS = "com.betterreads.clients..";

    private static final String CONFIG = "com.betterreads.config..";

    @ArchTest
    static void shouldKeepFeaturesIndependent(final JavaClasses classes) {
        slices()
            .matching("com.betterreads.features.(*)..")
            .should()
            .notDependOnEachOther()
            .check(classes);
    }

    @ArchTest
    static void shouldKeepClientsOutOfFeatures(final JavaClasses classes) {
        noClasses()
            .that()
            .resideInAPackage(CLIENTS)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(FEATURES)
            .check(classes);
    }

    @ArchTest
    static void shouldKeepClientsIndependent(final JavaClasses classes) {
        slices()
            .matching("com.betterreads.clients.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(
                resideInAPackage("com.betterreads.clients.hardcover*"),
                resideInAPackage("com.betterreads.clients.hardcover"))
            .ignoreDependency(
                resideInAPackage("com.betterreads.clients.wikipedia"),
                resideInAPackage("com.betterreads.clients.wikidata"))
            .ignoreDependency(resideInAPackage(CLIENTS), resideInAPackage("com.betterreads.clients.http"))
            .check(classes);
    }

    @ArchTest
    static void shouldKeepSharedModulesOutOfFeatures(final JavaClasses classes) {
        noClasses()
            .that()
            .resideOutsideOfPackages(FEATURES, CONFIG)
            .and()
            .doNotHaveFullyQualifiedName(BetterReadsApplication.class.getName())
            .should()
            .dependOnClassesThat()
            .resideInAPackage(FEATURES)
            .check(classes);
    }

    @ArchTest
    static void shouldKeepFeaturesAndClientsFlat(final JavaClasses classes) {
        noClasses()
            .should()
            .resideInAnyPackage("com.betterreads.features.*.*..", "com.betterreads.clients.*.*..")
            .check(classes);
    }

    @ArchTest
    static void shouldInjectThroughConstructors(final JavaClasses classes) {
        noFields()
            .should()
            .beAnnotatedWith(Autowired.class)
            .check(classes);
    }

    private ArchitectureTest() {
    }
}
