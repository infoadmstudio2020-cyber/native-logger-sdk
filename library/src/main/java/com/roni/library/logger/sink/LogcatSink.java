package com.roni.library.logger.sink;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.logger.formatter.ILogFormatter;
import com.roni.library.logger.formatter.PatternLogFormatter;

import java.lang.reflect.Method;

/**
 * Pluggable sink dispatching logs to Android Studio Logcat or console.
 */
public class LogcatSink implements ILogSink {
    private final String name;
    private final ILogFormatter formatter;
    private volatile boolean enabled = true;
    private volatile boolean disposed = false;

    // Reflection handles for android.util.Log when running inside Android OS
    private static Method logV;
    private static Method logD;
    private static Method logI;
    private static Method logW;
    private static Method logE;
    private static boolean isAndroid = false;

    static {
        try {
            Class<?> logClass = Class.forName("android.util.Log");
            logV = logClass.getMethod("v", String.class, String.class);
            logD = logClass.getMethod("d", String.class, String.class);
            logI = logClass.getMethod("i", String.class, String.class);
            logW = logClass.getMethod("w", String.class, String.class, Throwable.class);
            logE = logClass.getMethod("e", String.class, String.class, Throwable.class);
            isAndroid = true;
        } catch (Throwable ignored) {
            isAndroid = false;
        }
    }

    public LogcatSink(String name, ILogFormatter formatter) {
        this.name = name != null ? name : "LogcatSink";
        this.formatter = formatter != null ? formatter : new PatternLogFormatter(true);
    }

    public LogcatSink() {
        this("LogcatSink", new PatternLogFormatter(true));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void write(LogEntry entry) {
        if (!enabled || disposed || entry == null) {
            return;
        }

        String tag = entry.getTag();
        String message = entry.getMessage();
        Throwable tr = entry.getThrowable();

        if (isAndroid) {
            try {
                switch (entry.getLevel()) {
                    case VERBOSE:
                        logV.invoke(null, tag, message);
                        break;
                    case DEBUG:
                        logD.invoke(null, tag, message);
                        break;
                    case INFO:
                        logI.invoke(null, tag, message);
                        break;
                    case WARN:
                        logW.invoke(null, tag, message, tr);
                        break;
                    case ERROR:
                    case ASSERT:
                        logE.invoke(null, tag, message, tr);
                        break;
                }
                return;
            } catch (Throwable ignored) {
                // Fallback to console output
            }
        }

        // JVM / Fallback console logger
        String formatted = formatter.format(entry);
        if (entry.getLevel() == LogLevel.ERROR || entry.getLevel() == LogLevel.ASSERT) {
            System.err.println(formatted);
        } else {
            System.out.println(formatted);
        }
    }

    @Override
    public void flush() {
        System.out.flush();
        System.err.flush();
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
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
