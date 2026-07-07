
package com.governanceplus.reviewer.ruleengine;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.governanceplus.reviewer.ruleengine.model.PomRule;
import com.governanceplus.reviewer.ruleengine.model.Rule;
import com.governanceplus.reviewer.ruleengine.model.RuleConfig;
import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;

public class RuleLoader {

	public RuleConfig load(String filePath) throws Exception {

		// Read JSON file
		String content = new String(Files.readAllBytes(Paths.get(filePath)));
		JSONObject json = new JSONObject(content);

		// Get rules array
		JSONArray rulesArray = json.getJSONArray("rules");

		JSONArray pomRulesArray = json.getJSONArray("pomRules");

		// Optional: not every rules.json predates the swagger rule category.
		JSONArray swaggerRulesArray = json.optJSONArray("swaggerRules");
		if (swaggerRulesArray == null) {
			swaggerRulesArray = new JSONArray();
		}

		List<Rule> ruleList = new ArrayList<>();

		List<PomRule> pomRuleList = new ArrayList<>();
		for (int i = 0; i < rulesArray.length(); i++) {
			JSONObject obj = rulesArray.getJSONObject(i);

			Rule rule = new Rule(obj.optString("id"), obj.optString("category"), obj.optString("severity", "INFO"),
					obj.optString("description"), obj.optString("xpath"), obj.optString("usageAttribute", null),
					obj.optString("usagePattern"), obj.optString("projectNamePattern", null));

			ruleList.add(rule);
		}

		for (int i = 0; i < pomRulesArray.length(); i++) {
			JSONObject obj = pomRulesArray.getJSONObject(i);

			PomRule rule = new PomRule(obj.optString("artifactId"), obj.optString("minVersion"),
					obj.optString("projectNamePattern", null));

			pomRuleList.add(rule);
		}

		List<SwaggerRule> swaggerRuleList = new ArrayList<>();
		for (int i = 0; i < swaggerRulesArray.length(); i++) {
			JSONObject obj = swaggerRulesArray.getJSONObject(i);

			SwaggerRule rule = new SwaggerRule(obj.optString("id"), obj.optString("category"),
					obj.optString("severity", "INFO"), obj.optString("description"), obj.optString("jsonPath"),
					obj.optString("projectNamePattern", null));

			swaggerRuleList.add(rule);
		}

		return new RuleConfig(ruleList, pomRuleList, swaggerRuleList);
	}
}
