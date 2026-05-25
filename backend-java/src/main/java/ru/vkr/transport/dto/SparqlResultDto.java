package ru.vkr.transport.dto;

import java.util.List;
import java.util.Map;

public class SparqlResultDto {
    private List<String> variables;
    private List<Map<String, String>> results;
    private Boolean booleanResult; // for ASK queries
    private String queryType; // SELECT or ASK
    private String error;

    public SparqlResultDto() {}

    public List<String> getVariables() { return variables; }
    public void setVariables(List<String> variables) { this.variables = variables; }

    public List<Map<String, String>> getResults() { return results; }
    public void setResults(List<Map<String, String>> results) { this.results = results; }

    public Boolean getBooleanResult() { return booleanResult; }
    public void setBooleanResult(Boolean booleanResult) { this.booleanResult = booleanResult; }

    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
