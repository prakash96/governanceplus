
CREATE TABLE IF NOT EXISTS XML_RULES (
    ID VARCHAR(100) PRIMARY KEY,
    CATEGORY VARCHAR(200),
    SEVERITY VARCHAR(50),
    DESCRIPTION VARCHAR(2000),
    XPATH CLOB,
    PROJECT_NAME_PATTERN VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS POM_RULES (
    ARTIFACT_ID VARCHAR(200) PRIMARY KEY,
    MIN_VERSION VARCHAR(50),
    PROJECT_NAME_PATTERN VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS SWAGGER_RULES (
    ID VARCHAR(100) PRIMARY KEY,
    CATEGORY VARCHAR(200),
    SEVERITY VARCHAR(50),
    DESCRIPTION VARCHAR(2000),
    JSON_PATH CLOB,
    PROJECT_NAME_PATTERN VARCHAR(200),
    SELECTION CLOB,
    OPERATOR VARCHAR(50),

    VALUE_TEXT VARCHAR(1000)
);


MERGE INTO XML_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, XPATH, PROJECT_NAME_PATTERN) KEY (ID) VALUES ('MULE039', 'LOGGING', 'WARN', 'Logger should define log message', '//logger[not(@message)]', NULL);
MERGE INTO XML_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, XPATH, PROJECT_NAME_PATTERN) KEY (ID) VALUES ('MULE040', 'HTTP', 'HIGH', 'HTTP listener path must come from properties', '//http:listener[@path and not(contains(@path,''${''))]', NULL);
MERGE INTO XML_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, XPATH, PROJECT_NAME_PATTERN) KEY (ID) VALUES ('MULE041', 'BEST_PRACTICE', 'ERROR', 'HTTP Request should be inside Until-Successful', '//*[local-name()=''request'' and namespace-uri()=''http://www.mulesoft.org/schema/mule/http''][not(ancestor::*[local-name()=''until-successful''])]', NULL);
MERGE INTO XML_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, XPATH, PROJECT_NAME_PATTERN) KEY (ID) VALUES ('R006', 'BEST_PRACTICE', 'WARNING', 'Until Successful Hardcoding for maxRetries', '//*[local-name()=''until-successful'' and (@maxRetries or @millisBetweenRetries) and (not(contains(@maxRetries,''${'')) or not(contains(@millisBetweenRetries,''${'')))]', NULL);
MERGE INTO XML_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, XPATH, PROJECT_NAME_PATTERN) KEY (ID) VALUES ('R007', 'BEST_PRACTICE', 'ERROR', 'Flow naming convention should be lower case with hyphen', '//flow[@name != translate(@name, ''ABCDEFGHIJKLMNOPQRSTUVWXYZ'', ''abcdefghijklmnopqrstuvwxyz'') or translate(@name, ''abcdefghijklmnopqrstuvwxyz-'', '''') != '''']', NULL);
MERGE INTO XML_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, XPATH, PROJECT_NAME_PATTERN) KEY (ID) VALUES ('UI-DEL-TEST', 'Test', 'MINOR', 'tmp', '//foo', NULL);

MERGE INTO POM_RULES (ARTIFACT_ID, MIN_VERSION, PROJECT_NAME_PATTERN) KEY (ARTIFACT_ID) VALUES ('mule-amqp-connector', '1.9.0', NULL);
MERGE INTO POM_RULES (ARTIFACT_ID, MIN_VERSION, PROJECT_NAME_PATTERN) KEY (ARTIFACT_ID) VALUES ('mule-http-connector', '1.3.0', NULL);

MERGE INTO SWAGGER_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, JSON_PATH, PROJECT_NAME_PATTERN, SELECTION, OPERATOR, VALUE_TEXT) KEY (ID) VALUES ('SWAGGER-001', 'API_DESIGN', 'WARNING', 'Every operation should document at least one 400 error response', '$.paths.*.*.responses[''400'']', '', NULL, NULL, NULL);
MERGE INTO SWAGGER_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, JSON_PATH, PROJECT_NAME_PATTERN, SELECTION, OPERATOR, VALUE_TEXT) KEY (ID) VALUES ('02', 'API', 'MAJOR', '', '$.components.schemas.Customer[?(@.type == ''object'')]', '', '$.components.schemas.Customer.type', 'EQUALS', 'object');
MERGE INTO SWAGGER_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, JSON_PATH, PROJECT_NAME_PATTERN, SELECTION, OPERATOR, VALUE_TEXT) KEY (ID) VALUES ('002', 'Request Schema Validation', 'MAJOR', 'max length should be defined for request string fields', '$.paths.*.*.requestBody.content.*.schema.properties.*[?(@.type == ''string'')][?(!@[''maxLength''])]', '', NULL, NULL, NULL);
MERGE INTO SWAGGER_RULES (ID, CATEGORY, SEVERITY, DESCRIPTION, JSON_PATH, PROJECT_NAME_PATTERN, SELECTION, OPERATOR, VALUE_TEXT) KEY (ID) VALUES ('04', 'Request Schema Validation', 'MAJOR', 'Request body schemas must set additionalProperties: false', '$.paths.*.*.requestBody.content.*.schema[?(!@.additionalProperties)]', '', '$.paths.*.*.requestBody.content.*.schema.additionalProperties', 'NOT_EXISTS', '');
