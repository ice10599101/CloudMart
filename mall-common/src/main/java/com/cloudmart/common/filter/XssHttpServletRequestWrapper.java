package com.cloudmart.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.HtmlUtils;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String[]> sanitizedParameters;

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        sanitizedParameters = sanitizeParameters(request.getParameterMap());
    }

    @Override
    public String getParameter(String name) {
        String[] values = sanitizedParameters.get(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(sanitizedParameters);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(sanitizedParameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return sanitizedParameters.get(name);
    }

    private static Map<String, String[]> sanitizeParameters(Map<String, String[]> parameterMap) {
        Map<String, String[]> result = new HashMap<>(parameterMap.size());
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] originalValues = entry.getValue();
            String[] sanitizedValues = new String[originalValues.length];
            for (int i = 0; i < originalValues.length; i++) {
                sanitizedValues[i] = sanitize(originalValues[i]);
            }
            result.put(entry.getKey(), sanitizedValues);
        }
        return result;
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
