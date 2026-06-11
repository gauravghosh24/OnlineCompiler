package com.gaurav.compiler_api.service;

public class ExecutionResult {
    private String output;
    private String error;
    private int exitCode;

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
}