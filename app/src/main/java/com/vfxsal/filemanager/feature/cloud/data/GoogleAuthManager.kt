package com.vfxsal.filemanager.feature.cloud.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Wraps the classic GoogleSignInClient flow, which authenticates the user and
 * grants the Drive scope in a single consent screen (no separate Credential
 * Manager authorization step needed).
 */
class GoogleAuthManager(context: Context) {

    private val appContext = context.applicationContext

    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

    private val signInClient: GoogleSignInClient =
        GoogleSignIn.getClient(appContext, signInOptions)

    val signInIntent: Intent
        get() = signInClient.signInIntent

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            null
        }
    }

    fun lastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun signOut(onComplete: () -> Unit = {}) {
        signInClient.signOut().addOnCompleteListener { onComplete() }
    }
}
