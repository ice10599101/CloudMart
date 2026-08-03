package com.cloudmart.risk.config;

import com.cloudmart.common.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class InternalCallAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_CALL_VALUE = "true";
    private static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String internalCallHeader = request.getHeader(SecurityConstants.INTERNAL_CALL_HEADER);

        if (INTERNAL_CALL_VALUE.equals(internalCallHeader)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String userIdHeader = request.getHeader(SecurityConstants.USER_ID_HEADER);
            String principal = userIdHeader != null ? userIdHeader : "INTERNAL_SERVICE";

            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null,
                    List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
