package com.ai.interview.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonLayout extends LayoutBase<ILoggingEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        map.put("level", event.getLevel().toString());
        map.put("thread", event.getThreadName());
        map.put("logger", event.getLoggerName());
        map.put("message", event.getFormattedMessage());
        
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null && !mdc.isEmpty()) {
            map.put("context", mdc);
        }

        if (event.getThrowableProxy() != null) {
            map.put("exception", ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        
        try {
            return objectMapper.writeValueAsString(map) + "\n";
        } catch (Exception e) {
            return "{\"error\":\"Failed to format log: " + e.getMessage() + "\"}\n";
        }
    }
}
