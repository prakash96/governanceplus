package com.governanceplus.web.dto.rules;

/** "Test on sample" request for an XML rule — sampleXml should be a full, valid XML document with any xmlns: prefixes the xpath uses declared on its root element. */
public class XmlRuleTestRequest {

    private String xpath;
    private String usageAttribute;
    private String usagePattern;
    private String sampleXml;

    public String getXpath() { return xpath; }
    public void setXpath(String xpath) { this.xpath = xpath; }

    public String getUsageAttribute() { return usageAttribute; }
    public void setUsageAttribute(String usageAttribute) { this.usageAttribute = usageAttribute; }

    public String getUsagePattern() { return usagePattern; }
    public void setUsagePattern(String usagePattern) { this.usagePattern = usagePattern; }

    public String getSampleXml() { return sampleXml; }
    public void setSampleXml(String sampleXml) { this.sampleXml = sampleXml; }
}
