package com.tzevabest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Ensures module boundaries are respected.
 * <p>
 * Cross-module access is allowed ONLY to {@code service} and {@code dto} packages.
 * Direct access to another module's {@code repository} or {@code domain} is forbidden.
 */
class ModuleBoundaryTest {

    private static final String BASE = "com.tzevabest";

    private static final String[] MODULES = {
            "common", "iam", "catalog", "inventory", "cart",
            "order", "payment", "shipping", "service", "notification", "admin"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void modules_should_not_access_foreign_repository_packages() {
        for (String module : MODULES) {
            String ownRepo = BASE + "." + module + ".repository..";
            for (String other : MODULES) {
                if (other.equals(module)) continue;
                // common — общий фундамент (BaseEntity, DTO, web-инфраструктура): доступен всем.
                if (other.equals("common")) continue;
                String foreignRepo = BASE + "." + other + ".repository..";

                ArchRule rule = noClasses()
                        .that().resideInAPackage(BASE + "." + module + "..")
                        .and().resideOutsideOfPackage(ownRepo)
                        .should().accessClassesThat().resideInAPackage(foreignRepo)
                        .allowEmptyShould(true)
                        .because("Module '" + module + "' must not access repository of '" + other + "'. Use its service/dto instead.");

                rule.check(classes);
            }
        }
    }

    @Test
    void modules_should_not_access_foreign_domain_packages() {
        for (String module : MODULES) {
            String ownDomain = BASE + "." + module + ".domain..";
            for (String other : MODULES) {
                if (other.equals(module)) continue;
                // common.domain (BaseEntity) — общий фундамент, наследуется всеми модулями.
                if (other.equals("common")) continue;
                String foreignDomain = BASE + "." + other + ".domain..";

                ArchRule rule = noClasses()
                        .that().resideInAPackage(BASE + "." + module + "..")
                        .and().resideOutsideOfPackage(ownDomain)
                        .should().accessClassesThat().resideInAPackage(foreignDomain)
                        .allowEmptyShould(true)
                        .because("Module '" + module + "' must not access domain of '" + other + "'. Use its service/dto instead.");

                rule.check(classes);
            }
        }
    }
}
