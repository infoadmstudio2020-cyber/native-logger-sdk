package com.roni.library.logger.sink;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.logger.formatter.ILogFormatter;
import com.roni.library.logger.formatter.PatternLogFormatter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * High-performance file logging sink with automated rolling rotation and background GZIP compression.
 * Enforces:
 * - 10MB max file threshold
 * - 5 historical archives (50MB hard storage cap)
 * - Automated GZIP compression on rollover
 */
public class FileLogSink implements ILogSink {
    public static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MB
    public static final int DEFAULT_MAX_ARCHIVE_COUNT = 5;

    private final String name;
    private final File logDirectory;
    private final String baseFileName;
    private final long maxFileSizeBytes;
    private final int maxArchiveFiles;
    private final ILogFormatter formatter;
    private final ExecutorService compressionExecutor;

    private File currentLogFile;
    private FileOutputStream fileOutputStream;
    private OutputStreamWriter outputStreamWriter;
    private BufferedWriter bufferedWriter;
    private long currentFileBytes = 0;

    private volatile boolean enabled = true;
    private volatile boolean disposed = false;
    private final Object writeLock = new Object();

    public FileLogSink(File logDirectory, String baseFileName, long maxFileSizeBytes, int maxArchiveFiles, ILogFormatter formatter) {
        this.name = "FileLogSink";
        this.logDirectory = logDirectory;
        this.baseFileName = baseFileName != null ? baseFileName : "app_log.log";
        this.maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE;
        this.maxArchiveFiles = maxArchiveFiles > 0 ? maxArchiveFiles : DEFAULT_MAX_ARCHIVE_COUNT;
        this.formatter = formatter != null ? formatter : new PatternLogFormatter(true);
        this.compressionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NativeLog-Compressor");
            t.setDaemon(true);
            return t;
        });

        initCurrentFile();
    }

    public FileLogSink(File logDirectory) {
        this(logDirectory, "app_log.log", DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_ARCHIVE_COUNT, new PatternLogFormatter(true));
    }

    private void initCurrentFile() {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs();
        }
        this.currentLogFile = new File(logDirectory, baseFileName);
        this.currentFileBytes = currentLogFile.exists() ? currentLogFile.length() : 0;
        try {
            this.fileOutputStream = new FileOutputStream(currentLogFile, true);
            this.outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
            this.bufferedWriter = new BufferedWriter(outputStreamWriter, 8192);
        } catch (IOException e) {
            System.err.println("FileLogSink failed to initialize file writer: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void write(LogEntry entry) {
        if (!enabled || disposed || entry == null || bufferedWriter == null) {
            return;
        }

        String formatted = formatter.format(entry);
        byte[] bytes = (formatted + "\n").getBytes(StandardCharsets.UTF_8);

        synchronized (writeLock) {
            try {
                if (currentFileBytes + bytes.length > maxFileSizeBytes) {
                    rollOver();
                }
                bufferedWriter.write(formatted);
                bufferedWriter.newLine();
                currentFileBytes += bytes.length;
            } catch (IOException e) {
                System.err.println("FileLogSink write failed: " + e.getMessage());
            }
        }
    }

    private void rollOver() throws IOException {
        flush();
        closeCurrentWriter();

        // Rotate existing archives: app_log.4.log.gz -> delete, app_log.3.log.gz -> app_log.4.log.gz, etc.
        File oldestArchive = new File(logDirectory, baseFileName + "." + maxArchiveFiles + ".gz");
        if (oldestArchive.exists()) {
            oldestArchive.delete();
        }

        for (int i = maxArchiveFiles - 1; i >= 1; i--) {
            File prev = new File(logDirectory, baseFileName + "." + i + ".gz");
            if (prev.exists()) {
                File target = new File(logDirectory, baseFileName + "." + (i + 1) + ".gz");
                prev.renameTo(target);
            }
        }

        // Rename current file to temporary before compression
        File sourceForCompression = new File(logDirectory, baseFileName + ".rolling");
        if (currentLogFile.exists()) {
            currentLogFile.renameTo(sourceForCompression);
        }

        File targetGz = new File(logDirectory, baseFileName + ".1.gz");

        // Compress asynchronously in background daemon thread
        compressionExecutor.submit(() -> compressFile(sourceForCompression, targetGz));

        // Re-open current log file fresh
        initCurrentFile();
    }

    private void compressFile(File source, File targetGz) {
        if (!source.exists()) return;
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(targetGz);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
            gzos.finish();
        } catch (IOException e) {
            System.err.println("FileLogSink compression failed: " + e.getMessage());
        } finally {
            source.delete();
        }
    }

    private void closeCurrentWriter() {
        try {
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void flush() {
        synchronized (writeLock) {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.flush();
                }
            } catch (IOException ignored) {}
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void dispose() {
        disposed = true;
        synchronized (writeLock) {
            closeCurrentWriter();
        }
        compressionExecutor.shutdown();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public File getCurrentLogFile() {
        return currentLogFile;
    }
}
