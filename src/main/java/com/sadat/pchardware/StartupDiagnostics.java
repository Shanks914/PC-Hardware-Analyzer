package com.sadat.pchardware;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Writes launcher and JavaFX startup output to a file that can be shared for troubleshooting. */
public final class StartupDiagnostics {
    private static volatile PrintStream log;

    private StartupDiagnostics() { }

    public static synchronized void initialize() {
        if (log != null) return;
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = localAppData == null || localAppData.isBlank()
                    ? Path.of(System.getProperty("user.home"), ".pc-hardware-analyzer")
                    : Path.of(localAppData, "PC Hardware Analyzer");
            Path logFile = base.resolve("logs").resolve("startup.log");
            Files.createDirectories(logFile.getParent());
            OutputStream file = Files.newOutputStream(logFile, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            PrintStream originalErr = System.err;
            PrintStream originalOut = System.out;
            log = new PrintStream(file, true, StandardCharsets.UTF_8);
            System.setErr(new PrintStream(new TeeOutputStream(originalErr, log), true, StandardCharsets.UTF_8));
            System.setOut(new PrintStream(new TeeOutputStream(originalOut, log), true, StandardCharsets.UTF_8));
            log.println("\n=== Launch attempt " + Instant.now() + " ===");
            log.println("Java: " + System.getProperty("java.version"));
            log.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            log.println("App: " + System.getProperty("jpackage.app-path", "unknown"));
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                System.err.println("Uncaught exception on thread '" + thread.getName() + "':");
                error.printStackTrace(System.err);
            });
            mark("Diagnostic logging initialized: " + logFile);
        } catch (Exception error) {
            // Logging must never prevent the application from starting.
            error.printStackTrace();
        }
    }

    public static void mark(String message) {
        PrintStream current = log;
        if (current != null) {
            current.println("[" + Instant.now() + "] " + message);
            current.flush();
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override public synchronized void write(int value) throws IOException {
            first.write(value);
            second.write(value);
        }

        @Override public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        @Override public synchronized void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
