package com.governanceplus.reviewermule.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Deliberately simple admin authentication: credentials live in Mule global properties
 * (global.xml's admin.username/admin.password — see auth.xml), not a database or user-management
 * system. There's no server-side session either — the "token" a successful login hands back is
 * just the standard HTTP Basic credential encoding (base64 of "username:password"), re-verified
 * against those same global properties on every mutating rules request. Stateless by design: no
 * session store to expire, invalidate, or leak.
 *
 * Called from Mule flows via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::auth::AdminAuth`), never the Mule Java
 * Module.
 */
public final class AdminAuth {

    private AdminAuth() {
    }

    /** Used by the login flow (auth.xml) to check a submitted username/password pair. */
    public static boolean checkCredentials(String username, String password, String expectedUsername, String expectedPassword) {
        return expectedUsername != null && expectedPassword != null
                && expectedUsername.equals(username) && expectedPassword.equals(password);
    }

    /** Used by the login flow (auth.xml) to hand back a token — literally the Basic-Auth encoding of the credentials just verified. */
    public static String encodeToken(String username, String password) {
        return Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Used by the require-admin sub-flow (auth.xml) guarding every rule add/update/delete endpoint:
     * checks an incoming `Authorization: Basic &lt;token&gt;` header value against the configured
     * admin credentials. Returns false (never throws) for a missing header, wrong scheme, malformed
     * base64, or wrong credentials — every failure mode maps to the same 401.
     */
    public static boolean isValidAuthorizationHeader(String authorizationHeaderValue, String expectedUsername, String expectedPassword) {
        if (authorizationHeaderValue == null || !authorizationHeaderValue.startsWith("Basic ")) {
            return false;
        }
        try {
            String token = authorizationHeaderValue.substring("Basic ".length()).trim();
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                return false;
            }
            String username = decoded.substring(0, separator);
            String password = decoded.substring(separator + 1);
            return checkCredentials(username, password, expectedUsername, expectedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}
