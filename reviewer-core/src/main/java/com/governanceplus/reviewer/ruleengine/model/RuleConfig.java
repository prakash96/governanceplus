
package com.governanceplus.reviewer.ruleengine.model;

import java.util.List;

public class RuleConfig {

    public RuleConfig(List<Rule> rules, List<PomRule> pomRules, List<SwaggerRule> swaggerRules) {
		super();
		this.rules = rules;
		this.pomRules = pomRules;
		this.swaggerRules = swaggerRules;
	}

	private List<Rule> rules;

	private List<PomRule> pomRules;

	private List<SwaggerRule> swaggerRules;

    public List<PomRule> getPomRules() {
		return pomRules;
	}

	public void setPomRules(List<PomRule> pomRules) {
		this.pomRules = pomRules;
	}

	public List<Rule> getRules() { return rules; }

    public void setRules(List<Rule> rules) { this.rules = rules; }

    public List<SwaggerRule> getSwaggerRules() { return swaggerRules; }

    public void setSwaggerRules(List<SwaggerRule> swaggerRules) { this.swaggerRules = swaggerRules; }
}
