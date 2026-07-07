
package com.governanceplus.reviewer.ruleengine;
import java.io.File;
import java.util.Set;

import com.governanceplus.reviewer.ruleengine.model.Violation;

public class MuleRuleValidatorMojo {

    public Set<Violation> execute(File projectBaseDir, String rulesFilePath) throws Exception {

            RuleEngine engine = new RuleEngine();

            Set<Violation> violations =
                    engine.validate(projectBaseDir, rulesFilePath);

            //HtmlReportGenerator report = new HtmlReportGenerator();
            //report.generate(violations);

            return violations;                

    }
}
