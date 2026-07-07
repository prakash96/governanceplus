
package com.governanceplus.reviewer.ruleengine.model;

public class Violation {

    private String ruleId;
    private String message;
    private String file;
    private int line;

    public Violation(String ruleId, String message, String file, int line) {
        this.ruleId = ruleId;
        this.message = message;
        this.file = file;
        this.line = line;
    }

    public String getRuleId() { return ruleId; }
    public String getMessage() { return message; }
    public String getFile() { return file; }

	public int getLine() {
		return line;
	}

	public void setLine(int line) {
		this.line = line;
	}

	public void setRuleId(String ruleId) {
		this.ruleId = ruleId;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setFile(String file) {
		this.file = file;
	}
    
	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return (ruleId + message + file + line).hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		// TODO Auto-generated method stub
		return obj.hashCode() == this.hashCode();
	}
}
