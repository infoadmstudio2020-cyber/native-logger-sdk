package com.roni.library.logger;

import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.logger.formatter.ILogFormatter;
import com.roni.library.logger.formatter.PatternLogFormatter;
import com.roni.library.logger.sink.ILogSink;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable configuration settings for the NativeLogger engine.
 */
public final class LoggerConfig {
    private final LogLevel minimumLevel;
    private final boolean fileLoggingEnabled;
    private final File logDirectory;
    private final boolean consoleMirroringEnabled;
    private final boolean piiMaskingEnabled;
    private final ILogFormatter customFormatter;
    private final List<ILogSink> additionalSinks;

    private LoggerConfig(Builder builder) {
        this.minimumLevel = builder.minimumLevel != null ? builder.minimumLevel : LogLevel.INFO;
        this.fileLoggingEnabled = builder.fileLoggingEnabled;
        this.logDirectory = builder.logDirectory;
        this.consoleMirroringEnabled = builder.consoleMirroringEnabled;
        this.piiMaskingEnabled = builder.piiMaskingEnabled;
        this.customFormatter = builder.customFormatter != null ? builder.customFormatter : new PatternLogFormatter(builder.piiMaskingEnabled);
        this.additionalSinks = new ArrayList<>(builder.additionalSinks);
    }

    public static Builder builder() {
        return new Builder();
    }

    public LogLevel getMinimumLevel() {
        return minimumLevel;
    }

    public boolean isFileLoggingEnabled() {
        return fileLoggingEnabled;
    }

    public File getLogDirectory() {
        return logDirectory;
    }

    public boolean isConsoleMirroringEnabled() {
        return consoleMirroringEnabled;
    }

    public boolean isPiiMaskingEnabled() {
        return piiMaskingEnabled;
    }

    public ILogFormatter getCustomFormatter() {
        return customFormatter;
    }

    public List<ILogSink> getAdditionalSinks() {
        return additionalSinks;
    }

    public static final class Builder {
        private LogLevel minimumLevel = LogLevel.INFO;
        private boolean fileLoggingEnabled = false;
        private File logDirectory = null;
        private boolean consoleMirroringEnabled = true;
        private boolean piiMaskingEnabled = true;
        private ILogFormatter customFormatter = null;
        private final List<ILogSink> additionalSinks = new ArrayList<>();

        public Builder minimumLevel(LogLevel level) {
            this.minimumLevel = level;
            return this;
        }

        public Builder enableFileLogging(boolean enable, File directory) {
            this.fileLoggingEnabled = enable;
            this.logDirectory = directory;
            return this;
        }

        public Builder enableConsoleMirroring(boolean enable) {
            this.consoleMirroringEnabled = enable;
            return this;
        }

        public Builder enablePiiMasking(boolean enable) {
            this.piiMaskingEnabled = enable;
            return this;
        }

        public Builder formatter(ILogFormatter formatter) {
            this.customFormatter = formatter;
            return this;
        }

        public Builder addSink(ILogSink sink) {
            if (sink != null) {
                this.additionalSinks.add(sink);
            }
            return this;
        }

        public LoggerConfig build() {
            return new LoggerConfig(this);
        }
    }
}
