package com.dyx.crossrow.interceptor;

import com.dyx.crossrow.utils.JwtUtils;
import com.dyx.crossrow.utils.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);
    @Resource
    private JwtUtils jwtUtils;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行跨域的 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = null;

        // 1. 从请求头中获取 TOKEN
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            token = authHeader.substring(BEARER_PREFIX.length());
        }

        if (token == null || token.trim().isEmpty()) {
            token = request.getParameter("token");
        }

        if (token == null || token.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No token found in Header or URL parameter");
            return false;
        }

        try {
            Claims claims = jwtUtils.parseToken(token);
            // 3. 拿到护照里的 userId，戴上“工作牌”
            Long userId = claims.get("userId", Long.class);
            if (userId == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "unknown user");
                return false;
            }
            UserContext.setUserId(userId);
            return true;
        } catch (ExpiredJwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expire");
            return false;
        } catch (SignatureException | MalformedJwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalid signature");
            return false;
        } catch (Exception e) {
            log.error("Token parse unknown error", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server error");
            return false;
        }
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束，防止内存泄漏
        UserContext.clear();
    }
}
