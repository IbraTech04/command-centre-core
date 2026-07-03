package com.ibrasoft.commandcentre.security;

import com.ibrasoft.commandcentre.service.DiscordService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordGuildFilter extends OncePerRequestFilter {
    
    private final DiscordService discordService;
    private static final String GUILD_CHECK_ATTRIBUTE = "DISCORD_GUILD_VERIFIED";
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Only apply guild check to OAuth2-authenticated users
        // Skip JWT-authenticated users (they were verified during JWT issuance)
        if (authentication != null && authentication.isAuthenticated() 
                && authentication.getPrincipal() instanceof OAuth2User) {
            
            HttpSession session = request.getSession(false); // Don't create session if it doesn't exist
            
            // If no session exists or guild not verified, check guild membership
            Boolean guildVerified = session != null ? (Boolean) session.getAttribute(GUILD_CHECK_ATTRIBUTE) : null;
            
            // Only check guild membership once per session
            if (guildVerified == null || !guildVerified) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                String userId = oauth2User.getAttribute("id");
                
                log.info("Checking guild membership for OAuth2 user: {}", userId);
                
                if (!discordService.isUserInRequiredGuild(userId)) {
                    log.warn("OAuth2 user {} is not in required guild", userId);
                    if (session != null) {
                        session.invalidate();
                    }
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Access Denied\",\"code\":\"NOT_IN_GUILD\",\"message\":\"You must be a member of the required Discord server to access this API\"}"
                    );
                    return;
                }
                
                // Cache the result in session (create session only if check passed)
                HttpSession newSession = request.getSession(true);
                newSession.setAttribute(GUILD_CHECK_ATTRIBUTE, Boolean.TRUE);
                log.info("OAuth2 user {} verified as guild member", userId);
            }
        }
        // JWT-authenticated users bypass this filter entirely
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Don't filter login endpoints and public resources
        String path = request.getServletPath();
        
        // Skip filter for public endpoints
        if (path.startsWith("/login") || 
            path.startsWith("/oauth2") || 
            path.equals("/") ||
            path.startsWith("/error") ||
            path.startsWith("/api/auth/") ||
            path.startsWith("/v3/")
        
        ) {
            return true;
        }
        
        // Skip filter if JWT authentication is being used (no session-based OAuth)
        // This prevents Discord API calls for JWT-authenticated requests
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("Skipping Discord guild filter for JWT-authenticated request");
            return true;
        }
        
        return false;
    }
}
