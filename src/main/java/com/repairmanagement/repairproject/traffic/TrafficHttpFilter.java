package com.repairmanagement.repairproject.traffic;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TrafficHttpFilter extends OncePerRequestFilter {
    private final TrafficRecorder recorder;
    public TrafficHttpFilter(TrafficRecorder recorder) { this.recorder=recorder; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        try { chain.doFilter(request,response); }
        finally {
            if (request.getRequestURI().startsWith("/api/") &&
                !request.getRequestURI().startsWith("/api/platform/traffic")) {
                // Content-Length may be absent for compressed/streamed responses; this is only a known-byte estimate.
                long out = response.getHeader("Content-Length") == null ? 0 : parseSize(response.getHeader("Content-Length"));
                recorder.record(request.getRemoteAddr(),request.getHeader("X-Real-IP"),
                    Math.max(0,request.getContentLengthLong()),out);
            }
        }
    }
    private static long parseSize(String s) { try {return Math.max(0,Long.parseLong(s));} catch(Exception ex){return 0;} }
}
