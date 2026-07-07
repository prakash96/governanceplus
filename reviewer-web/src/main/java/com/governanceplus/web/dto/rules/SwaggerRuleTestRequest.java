package com.governanceplus.web.dto.rules;

/** "Test on sample" request for a swagger rule — sampleSpec is YAML or JSON OpenAPI content. */
public class SwaggerRuleTestRequest {

    private String jsonPath;
    private String sampleSpec;

    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }

    public String getSampleSpec() { return sampleSpec; }
    public void setSampleSpec(String sampleSpec) { this.sampleSpec = sampleSpec; }
}
