package com.jd_s4nd_b0x.CountMe.drive;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

/**
 * Google sign-in via the Identity authorization API. Only the narrow "drive.file" scope is
 * requested: the app can see just the files it created itself, never the rest of the Drive. No
 * server / developer backend is involved; tokens come straight from Google Play services.
 */
public final class DriveAuth {

    public static final String SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file";

    private static final String PREFS = "DriveAuthPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";

    private DriveAuth() {}

    public static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE_DRIVE_FILE)))
                .build();
    }

    /** Blocking. Returns a fresh access token or null when user interaction is required. */
    public static String silentToken(Context context)
            throws ExecutionException, InterruptedException {
        AuthorizationResult result =
                Tasks.await(Identity.getAuthorizationClient(context).authorize(request()));
        return result.hasResolution() ? null : result.getAccessToken();
    }

    public static void saveAccount(Context context, String name, String email) {
        prefs(context).edit().putString(KEY_NAME, name).putString(KEY_EMAIL, email).apply();
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, null);
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, null);
    }

    public static boolean isSignedIn(Context context) {
        return getEmail(context) != null;
    }

    public static void signOut(Context context) {
        prefs(context).edit().clear().apply();
        // Best effort: revoke Play services' cached grant so the next sign-in re-prompts.
        Identity.getAuthorizationClient(context)
                .revokeAccess(
                        RevokeAccessRequest.builder()
                                .setScopes(Collections.singletonList(new Scope(SCOPE_DRIVE_FILE)))
                                .build())
                .addOnFailureListener(
                        e -> {
                            // Nothing to do: local sign-out already happened and the grant can be
                            // revoked in
                            // the Google Account settings.
                        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
