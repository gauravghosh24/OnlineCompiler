# 🚀 Remote Code Execution Engine
**🔥 Try it live:** [http://13.60.54.44:8080](http://13.60.54.44:8080)
A highly scalable, secure, and interactive online compiler designed to execute untrusted user code in isolated environments. Built with a Spring Boot backend and deployed on an AWS EC2 instance, the platform features a dark-themed, Monaco-powered frontend that mirrors an industry-standard IDE.

Currently supports dynamic compilation and execution for **C++ (GCC)**, **Python**, and **Java**, making it an ideal lightweight testing ground for competitive programming and algorithmic problem-solving.

---

## 🛠️ Tech Stack & Architecture

* **Frontend:** HTML/CSS/Vanilla JavaScript, Monaco Editor (for VS Code-like syntax highlighting)
* **Backend:** Java 21, Spring Boot 3.5.9
* **Containerization:** Docker (Isolated execution sandboxing)
* **Infrastructure:** AWS EC2 (Ubuntu Linux), Maven

---

## ✨ Key Features

* **Interactive I/O Piping:** Supports fully interactive command-line inputs, feeding custom test cases directly into the running code.
* **Strict Timeout Enforcement (TLE):** Custom process manager that assassinates infinite loops (`while(true)`) after a strict time limit, mimicking competitive programming platforms.
* **Secure Docker Sandboxing:** Every execution spins up an ephemeral, highly restricted Docker container with blocked network access and capped CPU/Memory usage.
* **Heavyweight Compilation Support:** Engineered with Linux Swap Memory to easily handle massive C++ header files (like `<bits/stdc++.h>`) without crashing the host server.

---

## 🧠 Engineering Deep Dive: Taming AWS & Docker

Building an execution engine fundamentally differs from standard web applications. The core requirement—executing untrusted user code securely—introduces massive complexity involving OS-level resource management, inter-process communication, and strict timeout enforcement. 

Here is how I solved the major architectural challenges while deploying this platform:

### 1. The `bits/stdc++.h` Memory Crash (OOM Killer)
**The Problem:** When compiling C++ code, the Linux Out Of Memory (OOM) Killer forcefully terminated the `g++` compiler, throwing a `Killed signal terminated program cc1plus` error. 
**The Root Cause:** `#include <bits/stdc++.h>` pre-compiles the entire C++ standard library. On an AWS Free Tier instance (1GB RAM), allocating memory to the OS, Java Virtual Machine, and Docker left insufficient space for this heavy compilation, causing immediate container crashes.
**The Fix:** Engineered a hard-drive-level memory buffer.
1. Carved out a **2GB Swap File** (`fallocate -l 2G /swapfile`) on the AWS instance to act as emergency virtual RAM.
2. Bumped the Docker container limits in the Java `ProcessBuilder` from `256m` to `512m`. This allowed the compiler to temporarily overflow into the swap space, successfully compiling massive header files.

### 2. Architecting the Execution Assassin (TLE)
**The Problem:** Initial timeout implementations (`process.waitFor()`) were bypassed by infinite loops, causing the Java thread to hang indefinitely while attempting to read frozen output streams.
**The Root Cause:** Java's `Process` API traps threads if they attempt to read an open stream from a frozen container.
**The Fix:** Order of operations inversion. Forced the Java thread to hit the stopwatch *before* reading streams. If the 15-second timer expires (accounting for `g++` compile times), Java forcefully assassinates the rogue container using `process.destroyForcibly()`, freeing up server CPU and returning a custom TLE error.

### 3. The "Missing Pipe" Input Freeze
**The Problem:** Valid code that required input (e.g., `std::cin >> name;`) falsely triggered the TLE timeout.
**The Root Cause:** The C++ program booted inside the container and waited indefinitely for an input stream that the Java backend never provided.
**The Fix:** Built an interactive data pipe. 
1. Added the `-i` (interactive) flag to the Docker execution command to keep `stdin` open.
2. Intercepted the process immediately after launch to inject the user's input stream via Java's `OutputStream`.

### 4. The EOF (End of File) Trap
**The Problem:** Even with input injection, competitive programming patterns like `while(cin >> x)` hung indefinitely.
**The Root Cause:** Java held the `OutputStream` open, meaning the C++ code never received an End of File (EOF) signal.
**The Fix:** Wrapped the input injection in a `try-with-resources` block. This guarantees that the exact millisecond the input text is flushed into the container, the pipe is severed. This simulates a true `EOF` signal, allowing the code to proceed and terminate normally.

### 💻 The Execution Engine Core
```java
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
