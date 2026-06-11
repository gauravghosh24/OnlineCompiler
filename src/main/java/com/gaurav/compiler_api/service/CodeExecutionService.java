package com.gaurav.compiler_api.service;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CodeExecutionService {

    private static final String BASE_CODE_DIR = "/tmp/online-compiler/";

    public ExecutionResult executeCppCode(String sourceCode, String language, String userInput) {
        String requestId = UUID.randomUUID().toString();
        Path workingDir = Paths.get(BASE_CODE_DIR, requestId);
        ExecutionResult result = new ExecutionResult();

        try {
            Files.createDirectories(workingDir);
            
            // Write the user input to a file (optional fallback, but good to have)
            File inputFile = new File(workingDir.toFile(), "input.txt");
            Files.writeString(inputFile.toPath(), userInput != null ? userInput : "");
            
            String dockerImage = "";
            String dockerCmd = "";
            String fileName = "";
             
            switch(language.toLowerCase()) {
                case "cpp":
                    fileName = "main.cpp";
                    dockerImage = "gcc:latest";
                    dockerCmd = "g++ main.cpp -o main && ./main";
                    break;
                case "python":
                    fileName = "script.py";
                    dockerImage = "python:3.9-slim";
                    dockerCmd = "python script.py";
                    break;
                case "java":
                    fileName = "Main.java";
                    dockerImage = "eclipse-temurin:17-jdk-alpine";
                    dockerCmd = "javac Main.java && java Main";
                    break;
                default:
                    result.setError("Unsupported language: " + language);
                    result.setExitCode(400);
                    return result;
            }
            
            // Write the actual source code to a file
            File codeFile = new File(workingDir.toFile(), fileName);
            Files.writeString(codeFile.toPath(), sourceCode);
            
            // Build the Docker command
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "--rm", "-i",                  // Interactive mode enabled
                "--network", "none",                            // Block internet access
                "--memory", "512m",                             // 512MB RAM for heavy imports
                "--cpus", "1.0",                                // Limit CPU usage
                "-v", workingDir.toAbsolutePath() + ":/app",    // Mount folder
                "-w", "/app",                                   // Set working directory
                dockerImage,                                    // Secure official image
                "sh", "-c", dockerCmd
            );

            Process process = processBuilder.start();

         // Feed input
         if (userInput != null && !userInput.isEmpty()) {
             try (OutputStream os = process.getOutputStream()) {
                 os.write(userInput.getBytes());
                 os.write("\n".getBytes());
                 os.flush();
             }
         }

         // ✅ Read output CONCURRENTLY to prevent buffer deadlock
         StringBuilder output = new StringBuilder();
         StringBuilder error = new StringBuilder();

         Thread stdoutThread = new Thread(() -> {
             try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(process.getInputStream()))) {
                 String line;
                 while ((line = reader.readLine()) != null) {
                     output.append(line).append("\n");
                 }
             } catch (IOException ignored) {}
         });

         Thread stderrThread = new Thread(() -> {
             try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(process.getErrorStream()))) {
                 String line;
                 while ((line = reader.readLine()) != null) {
                     error.append(line).append("\n");
                 }
             } catch (IOException ignored) {}
         });

         stdoutThread.start();
         stderrThread.start();

         boolean finished = process.waitFor(5, TimeUnit.SECONDS);

         if (!finished) {
             process.destroyForcibly();
             stdoutThread.interrupt();
             stderrThread.interrupt();
             result.setOutput("");
             result.setError("Execution Timeout: Code took longer than 5 seconds to execute.");
             result.setExitCode(-1);
             return result;
         }

         stdoutThread.join();
         stderrThread.join();

         result.setOutput(output.toString().trim());
         result.setError(error.toString().trim());
         result.setExitCode(process.exitValue());

        } catch (IOException | InterruptedException e) {
            result.setError("System Error: " + e.getMessage());
            result.setExitCode(500);
        } finally {
            cleanupDirectory(workingDir.toFile());
        }

        return result;
    }

    private void cleanupDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            directory.delete();
        }
    }
}