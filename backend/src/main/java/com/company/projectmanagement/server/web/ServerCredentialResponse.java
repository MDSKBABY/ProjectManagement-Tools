package com.company.projectmanagement.server.web;

/** 只由显式查看凭据接口返回，调用方必须在使用后立即清除。 */
public record ServerCredentialResponse(String username, String password) { }
