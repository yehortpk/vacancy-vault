package com.github.yehortpk.parser.controllers;

import com.github.yehortpk.parser.models.admin.AdminSessionDTO;
import com.github.yehortpk.parser.models.admin.LoginRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Controller responsible for the admin panel's session: logging in with a JSON body, and
 * telling the panel who is logged in. Logging out is the security chain's {@code POST /logout}.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    /**
     * Checks the credentials and binds the admin to the session.
     *
     * <p>A wrong password and an unknown username fail the same way, as a 401, see
     * {@code GlobalExceptionHandler}.
     */
    @PostMapping("/login")
    public AdminSessionDTO login(@RequestBody LoginRequestDTO request,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

        // A session that existed before the login must not survive it under the same id
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new AdminSessionDTO(authentication.getName());
    }

    /** Answers 401 without a session, like everything else under {@code /parser}. */
    @GetMapping("/parser/session")
    public AdminSessionDTO getSession(Principal principal) {
        return new AdminSessionDTO(principal.getName());
    }
}
