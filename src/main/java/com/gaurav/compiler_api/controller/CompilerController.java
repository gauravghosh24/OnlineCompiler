package com.gaurav.compiler_api.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gaurav.compiler_api.service.CodeExecutionService;
import com.gaurav.compiler_api.service.ExecutionResult;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class CompilerController {

    private final CodeExecutionService executionService;

    public CompilerController(CodeExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/run")
    public ExecutionResult runCode(@RequestBody CodeRequest request) {
        return executionService.executeCppCode(request.getCode(),request.getLanguage(),request.getInput());
    }
}
class CodeRequest {
    private String code;
    private String language;
    private String input;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
}