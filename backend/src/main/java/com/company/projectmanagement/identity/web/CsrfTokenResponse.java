package com.company.projectmanagement.identity.web;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {

    public static CsrfTokenResponse from(CsrfToken token) {
        return new CsrfTokenResponse(
                token.getHeaderName(),
                token.getParameterName(),
                token.getToken());
    }
}
