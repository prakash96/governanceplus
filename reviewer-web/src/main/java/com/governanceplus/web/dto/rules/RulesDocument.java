package com.governanceplus.web.dto.rules;

import java.util.List;

/** The full contents of rules.json: XML, pom.xml, and Swagger/OpenAPI rule categories. */
public class RulesDocument {

    private List<XmlRuleDto> rules;
    private List<PomRuleDto> pomRules;
    private List<SwaggerRuleDto> swaggerRules;

    public RulesDocument() {
    }

    public RulesDocument(List<XmlRuleDto> rules, List<PomRuleDto> pomRules, List<SwaggerRuleDto> swaggerRules) {
        this.rules = rules;
        this.pomRules = pomRules;
        this.swaggerRules = swaggerRules;
    }

    public List<XmlRuleDto> getRules() { return rules; }
    public void setRules(List<XmlRuleDto> rules) { this.rules = rules; }

    public List<PomRuleDto> getPomRules() { return pomRules; }
    public void setPomRules(List<PomRuleDto> pomRules) { this.pomRules = pomRules; }

    public List<SwaggerRuleDto> getSwaggerRules() { return swaggerRules; }
    public void setSwaggerRules(List<SwaggerRuleDto> swaggerRules) { this.swaggerRules = swaggerRules; }
}
