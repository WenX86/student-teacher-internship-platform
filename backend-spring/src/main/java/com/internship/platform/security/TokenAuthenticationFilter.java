package com.internship.platform.security;

import com.internship.platform.entity.UserAccountEntity;
import com.internship.platform.mapper.UserAccountMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenSessionService tokenSessionService;
    private final UserAccountMapper userAccountMapper;

    public TokenAuthenticationFilter(TokenSessionService tokenSessionService, UserAccountMapper userAccountMapper) {
        this.tokenSessionService = tokenSessionService;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String userId = tokenSessionService.resolveUserId(token);
            if (StringUtils.hasText(userId) && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserAccountEntity user = userAccountMapper.selectById(userId);
                if (user != null && "ACTIVE".equals(user.getStatus())) {
                    LoginUser loginUser = new LoginUser(user.getId(), user.getAccount(), user.getName(), user.getRole(), user.getCollegeId());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            loginUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
