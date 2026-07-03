package com.ibrasoft.commandcentre.security;

import com.ibrasoft.commandcentre.model.Role;
import com.ibrasoft.commandcentre.service.DiscordService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final DiscordService discordService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String userId = oauth2User.getAttribute("id");
            if (userId != null) {
                boolean allowed = discordService.isUserInRequiredGuild(userId);
                if (!allowed) {
                    log.warn("OAuth login denied for user {} not in required guild", userId);
                    invalidateSession(request);
                    response.sendRedirect(unauthorizedRedirect("not_in_guild"));
                    return;
                }
                String token = jwtService.generateUserToken(userId, List.of(Role.ROLE_USER));
                String redirect = UriComponentsBuilder.fromUriString(frontendUrl)
                    .queryParam("token", token)
                    .build()
                    .toUriString();
                response.sendRedirect(redirect);
                return;
            }
        }

        invalidateSession(request);
        response.sendRedirect(unauthorizedRedirect("auth_failed"));
    }

    private void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private String unauthorizedRedirect(String reason) {
        return UriComponentsBuilder.fromUriString(frontendUrl)
            .path("/unauthorized")
            .queryParam("reason", reason)
            .build()
            .toUriString();
    }
}
