package com.roni.library.logger.handler;

import com.roni.library.contracts.api.IApiCallback;
import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.logger.NativeLogger;

import java.util.Collections;

/**
 * Command handler processing routed API requests under the prefix "logger/".
 */
public class LoggerCommandHandler implements IApiHandler {
    public static final String ROUTE_PREFIX = "logger/";
    private final NativeLogger logger;

    public LoggerCommandHandler(NativeLogger logger) {
        this.logger = logger != null ? logger : NativeLogger.getInstance();
    }

    public LoggerCommandHandler() {
        this(NativeLogger.getInstance());
    }

    @Override
    public String getRoutePrefix() {
        return ROUTE_PREFIX;
    }

    @Override
    public void handle(NativeApiRequest request, IApiCallback callback) {
        if (request == null) {
            if (callback != null) {
                callback.onError(new NativeApiError(NativeApiError.CODE_INVALID_PARAM, "Request cannot be null"));
            }
            return;
        }

        String action = request.getAction();
        // Route: logger/log
        if ("log".equalsIgnoreCase(action) || "logEntry".equalsIgnoreCase(action) || request.getPath().endsWith("/log")) {
            String levelStr = request.getStringParam("level", "INFO");
            String tag = request.getStringParam("tag", "WebWorker");
            String message = request.getStringParam("message", "");
            if (message.isEmpty()) {
                message = request.getStringParam("msg", "");
            }
            String correlationId = request.getStringParam("correlationId", request.getRequestId());

            LogLevel level = LogLevel.fromString(levelStr, LogLevel.INFO);
            LogEntry entry = new LogEntry(
                    request.getTimestamp(),
                    level,
                    tag,
                    message,
                    "WebThread-" + Thread.currentThread().getId(),
                    null,
                    correlationId
            );

            logger.log(entry);

            if (callback != null) {
                callback.onSuccess(NativeApiResponse.success(request.getRequestId(), Collections.singletonMap("status", "logged")));
            }
        } else if ("setLevel".equalsIgnoreCase(action)) {
            String newLevelStr = request.getStringParam("level", "INFO");
            LogLevel newLevel = LogLevel.fromString(newLevelStr, LogLevel.INFO);
            logger.setMinimumLevel(newLevel);
            if (callback != null) {
                callback.onSuccess(NativeApiResponse.success(request.getRequestId(), Collections.singletonMap("currentLevel", newLevel.name())));
            }
        } else {
            if (callback != null) {
                callback.onError(new NativeApiError(NativeApiError.CODE_NOT_FOUND, "Unsupported logger action: " + action));
            }
        }
    }
}
