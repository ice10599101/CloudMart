package com.cloudmart.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

public class XssFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws java.io.IOException, ServletException {
        chain.doFilter(new XssHttpServletRequestWrapper((HttpServletRequest) request), response);
    }

    @Override
    public void destroy() {
    }

    static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return value != null ? HtmlUtils.htmlEscape(value, "UTF-8") : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            String[] escaped = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escaped[i] = values[i] != null ? HtmlUtils.htmlEscape(values[i], "UTF-8") : null;
            }
            return escaped;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> original = super.getParameterMap();
            Map<String, String[]> escaped = new java.util.HashMap<>(original.size());
            for (Map.Entry<String, String[]> entry : original.entrySet()) {
                String[] values = entry.getValue();
                String[] escapedValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    escapedValues[i] = values[i] != null ? HtmlUtils.htmlEscape(values[i], "UTF-8") : null;
                }
                escaped.put(entry.getKey(), escapedValues);
            }
            return escaped;
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return value != null ? HtmlUtils.htmlEscape(value, "UTF-8") : null;
        }
    }
}
