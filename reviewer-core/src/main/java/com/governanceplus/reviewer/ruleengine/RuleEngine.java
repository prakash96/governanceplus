
package com.governanceplus.reviewer.ruleengine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.governanceplus.reviewer.ruleengine.model.PomRule;
import com.governanceplus.reviewer.ruleengine.model.ProjectScopedRule;
import com.governanceplus.reviewer.ruleengine.model.Rule;
import com.governanceplus.reviewer.ruleengine.model.RuleConfig;
import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;

public class RuleEngine {

    public Set<Violation> validate(File projectDir, String rulesPath) throws Exception {

        RuleLoader loader = new RuleLoader();
        RuleConfig config = loader.load(rulesPath);

        XPathEvaluator evaluator = new XPathEvaluator();

        Set<Violation> violations = new LinkedHashSet<>();

        File pomFile = new File(projectDir, "pom.xml");
        String projectArtifactId = null;
        if (pomFile.exists()) {
            try {
                projectArtifactId = PomDependencyValidator.readProjectArtifactId(pomFile);
            } catch (Exception e) {
                // Unknown project name — rules with a projectNamePattern are left applicable
                // rather than silently skipped (see applicableTo's null-artifactId handling).
            }
        }

        List<Rule> applicableXmlRules = applicableTo(config.getRules(), projectArtifactId);
        List<PomRule> applicablePomRules = applicableTo(config.getPomRules(), projectArtifactId);
        List<SwaggerRule> applicableSwaggerRules = applicableTo(config.getSwaggerRules(), projectArtifactId);

        Path muleDir = Paths.get(projectDir.getAbsolutePath(), "src/main/mule");

        if (!Files.exists(muleDir))
            return violations;

        List<File> allXmlFiles = new ArrayList<>();
        Files.walk(muleDir)
                .filter(p -> p.toString().endsWith(".xml"))
                .forEach(path -> {

                	allXmlFiles.add(path.toFile());
                });

        for(File xmlFile: allXmlFiles){
        	try {


                violations.addAll(
                        evaluator.evaluate(
                                xmlFile,
                                applicableXmlRules, allXmlFiles
                        )
                );

            } catch (Exception e) {

                e.printStackTrace();
            }

        }

        if(pomFile.exists()) {
        	violations.addAll(PomDependencyValidator.validate(pomFile, applicablePomRules));
        }

        Path apiDir = Paths.get(projectDir.getAbsolutePath(), "src/main/resources/api");
        if (Files.exists(apiDir) && !applicableSwaggerRules.isEmpty()) {
            SwaggerRuleEvaluator swaggerEvaluator = new SwaggerRuleEvaluator();
            List<File> specFiles = new ArrayList<>();
            Files.walk(apiDir)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
                    })
                    .forEach(p -> specFiles.add(p.toFile()));

            for (File specFile : specFiles) {
                try {
                    violations.addAll(swaggerEvaluator.evaluate(specFile, applicableSwaggerRules));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return violations;
    }

    /**
     * Filters out rules whose projectNamePattern doesn't match this project's
     * pom.xml artifactId. A blank/absent pattern always matches (applies to
     * every project). If the artifactId itself couldn't be determined, rules
     * are left in rather than silently dropped — an unknown project name
     * shouldn't be indistinguishable from "explicitly out of scope."
     */
    private <T extends ProjectScopedRule> List<T> applicableTo(List<T> rules, String projectArtifactId) {
        List<T> applicable = new ArrayList<>();
        for (T rule : rules) {
            String pattern = rule.getProjectNamePattern();
            if (pattern == null || pattern.isBlank() || projectArtifactId == null
                    || GlobMatcher.matches(pattern, projectArtifactId)) {
                applicable.add(rule);
            }
        }
        return applicable;
    }
}
