package com.company.projectmanagement.identity.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 64) String username,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank
        @Size(max = 200)
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=[PROTECTED]]";
    }
}
