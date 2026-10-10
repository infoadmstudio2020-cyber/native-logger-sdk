package com.roni.library.logger;

import com.roni.library.contracts.api.IApiCallback;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.logger.formatter.JsonLogFormatter;
import com.roni.library.logger.formatter.PatternLogFormatter;
import com.roni.library.logger.handler.LoggerCommandHandler;
import com.roni.library.logger.sink.FileLogSink;
import com.roni.library.logger.sink.ILogSink;
import com.roni.library.logger.sink.RemoteDiagnosticSink;
import com.roni.library.logger.sink.WebConsoleMirrorSink;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NativeLoggerTest {
    private static int passed = 0;
    private static int failed = 0;

    private static void assertTrue(String name, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + name);
            passed++;
        } else {
            System.err.println("  [FAIL] " + name);
            failed++;
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            System.out.println("  [PASS] " + name);
            passed++;
        } else {
            System.err.println("  [FAIL] " + name + " - Expected: " + expected + ", Actual: " + actual);
            failed++;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("NativeLoggerSDK (Phase 02) Execution Tests");
        System.out.println("=================================================");

        // 1. Test PII Masking
        PatternLogFormatter formatter = new PatternLogFormatter(true);
        String sensitive = "User login with password='SuperSecretPassword123' and cc=4111-2222-3333-4444 and token=Bearer eyJhbGciOi";
        String masked = formatter.maskPII(sensitive);
        assertTrue("Password redacted", masked.contains("[REDACTED_SECRET]") && !masked.contains("SuperSecretPassword123"));
        assertTrue("Credit card redacted", masked.contains("[REDACTED_PAN]") && !masked.contains("4111-2222-3333-4444"));
        assertTrue("Bearer token redacted", masked.contains("[REDACTED_AUTH_TOKEN]"));

        // 2. Test JsonLogFormatter
        JsonLogFormatter jsonFormatter = new JsonLogFormatter(true);
        LogEntry entry = new LogEntry(1700000000000L, LogLevel.WARN, "AuthTag", "Attempted with pin='1234'", "Worker-1", null, "corr-xyz");
        String json = jsonFormatter.format(entry);
        assertTrue("JSON has level", json.contains("\"level\":\"WARN\""));
        assertTrue("JSON has masked PIN", json.contains("[REDACTED_SECRET]"));
        assertTrue("JSON has correlationId", json.contains("\"correlationId\":\"corr-xyz\""));

        // 3. Test FileLogSink Rolling & Compression
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "logger_test_" + System.currentTimeMillis());
        tempDir.mkdirs();
        FileLogSink fileSink = new FileLogSink(tempDir, "test.log", 2048L, 3, formatter); // low 2KB limit for test roll
        for (int i = 0; i < 200; i++) {
            fileSink.write(new LogEntry(LogLevel.INFO, "RollTest", "Message line #" + i + " padding out file content to trigger rapid rollover"));
        }
        fileSink.flush();
        Thread.sleep(200); // allow async gzip compressor
        File[] files = tempDir.listFiles();
        assertTrue("Log directory contains files", files != null && files.length >= 2);
        boolean foundGz = false;
        for (File f : files) {
            if (f.getName().endsWith(".gz")) {
                foundGz = true;
                break;
            }
        }
        assertTrue("GZIP compressed archive created upon rollover", foundGz);
        fileSink.dispose();

        // 4. Test WebConsoleMirrorSink
        WebConsoleMirrorSink mirrorSink = new WebConsoleMirrorSink();
        AtomicReference<LogEntry> captured = new AtomicReference<>();
        mirrorSink.addListener(captured::set);
        LogEntry consoleLog = new LogEntry(LogLevel.INFO, "Console", "web app ready");
        mirrorSink.write(consoleLog);
        assertEquals("Web log captured", "web app ready", captured.get() != null ? captured.get().getMessage() : null);

        // 5. Test RemoteDiagnosticSink
        AtomicInteger batchCount = new AtomicInteger(0);
        RemoteDiagnosticSink diagSink = new RemoteDiagnosticSink("DiagTest", 5, entries -> batchCount.addAndGet(entries.size()));
        diagSink.write(new LogEntry(LogLevel.DEBUG, "Diag", "low severity - ignored"));
        assertEquals("Low severity ignored in diag sink", 0, diagSink.getQueueSize());
        for (int i = 0; i < 5; i++) {
            diagSink.write(new LogEntry(LogLevel.ERROR, "Diag", "Critical error #" + i));
        }
        assertEquals("Diagnostic batch flushed on threshold", 5, batchCount.get());

        // 6. Test NativeLogger Asynchronous Queue Throughput
        AtomicInteger sinkCounter = new AtomicInteger(0);
        ILogSink countingSink = new ILogSink() {
            @Override public String getName() { return "Counter"; }
            @Override public void write(LogEntry e) { sinkCounter.incrementAndGet(); }
            @Override public void flush() {}
            @Override public boolean isEnabled() { return true; }
            @Override public void setEnabled(boolean e) {}
            @Override public void dispose() {}
            @Override public boolean isDisposed() { return false; }
        };

        LoggerConfig config = LoggerConfig.builder()
                .minimumLevel(LogLevel.DEBUG)
                .addSink(countingSink)
                .build();

        NativeLogger logger = NativeLogger.init(config);
        long startTime = System.currentTimeMillis();
        int totalLogs = 10000;
        for (int i = 0; i < totalLogs; i++) {
            logger.d("PerfTag", "High throughput event " + i);
        }
        long enqueueDuration = System.currentTimeMillis() - startTime;
        System.out.println("  10,000 logs enqueued in " + enqueueDuration + " ms (Non-blocking UI guarantee)");
        assertTrue("Enqueue 10,000 logs under 200ms", enqueueDuration < 200);

        logger.flush();
        assertEquals("Counting sink processed all 10,000 entries", totalLogs, sinkCounter.get());

        // 7. Test LoggerCommandHandler API Integration
        LoggerCommandHandler handler = new LoggerCommandHandler(logger);
        assertEquals("Route prefix matches", "logger/", handler.getRoutePrefix());

        Map<String, Object> params = new HashMap<>();
        params.put("level", "WARN");
        params.put("tag", "BridgeUI");
        params.put("message", "Token expired: Bearer abcdef12345");
        NativeApiRequest req = new NativeApiRequest("req_101", "logger/log", "log", params, System.currentTimeMillis());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NativeApiResponse> responseRef = new AtomicReference<>();
        handler.handle(req, new IApiCallback() {
            @Override
            public void onSuccess(NativeApiResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(NativeApiError error) {
                latch.countDown();
            }
        });

        assertTrue("Handler completed callback", latch.await(1, TimeUnit.SECONDS));
        assertTrue("Handler response success", responseRef.get() != null && responseRef.get().isSuccess());

        logger.dispose();

        System.out.println("=================================================");
        System.out.println("Logger Results: " + passed + " passed, " + failed + " failed.");
        System.out.println("=================================================");

        // Cleanup
        if (tempDir.exists()) {
            for (File f : tempDir.listFiles()) f.delete();
            tempDir.delete();
        }

        if (failed > 0) {
            System.exit(1);
        }
    }
}
