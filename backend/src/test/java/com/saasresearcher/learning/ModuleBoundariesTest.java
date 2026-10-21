package com.saasresearcher.learning;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModuleBoundariesTest {

    private static final String ROOT = "com.saasresearcher.learning.";

    private final JavaClasses production = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.saasresearcher.learning");

    @Test
    void noCrossModuleInternalImports() {
        classes().should(notDependOnOtherModuleInternals()).allowEmptyShould(true).check(production);
    }

    @Test
    void commonDoesNotDependOnFeatureModules() {
        noClasses().that().resideInAPackage(ROOT + "common..")
                .should().dependOnClassesThat(resideInFeatureModule())
                .allowEmptyShould(true)
                .check(production);
    }

    @Test
    void layersInsideModulesOnlyDependDownwards() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy(layer("controller"))
                .layer("Service").definedBy(layer("service"))
                .layer("Repository").definedBy(layer("repository"))
                .layer("Dto").definedBy(layer("dto"))
                .layer("Entity").definedBy(layer("entity"))
                .layer("Config").definedBy(layer("config"))
                .layer("Infra").definedBy(layer("infra"))
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Config", "Infra")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Infra", "Config")
                .whereLayer("Dto").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository", "Infra")
                .whereLayer("Infra").mayOnlyBeAccessedByLayers("Service", "Config")
                .allowEmptyShould(true)
                .check(production);
    }

    private static String layer(String name) {
        return ROOT + "*." + name + "..";
    }

    private static ArchCondition<JavaClass> notDependOnOtherModuleInternals() {
        return new ArchCondition<>("not depend on another module's classes outside its api package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Optional<String> originModule = module(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    Optional<String> targetModule = module(target);
                    boolean internal = !target.getPackageName().endsWith(".api")
                            && !target.getPackageName().endsWith("." + targetModule.orElse(""));
                    if (internal && targetModule.isPresent() && !targetModule.equals(originModule)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> resideInFeatureModule() {
        return com.tngtech.archunit.base.DescribedPredicate.describe("reside in a feature module",
                javaClass -> module(javaClass).filter(name -> !name.equals("common")).isPresent());
    }

    private static Optional<String> module(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT)) {
            return Optional.empty();
        }
        String rest = packageName.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
    }
}
