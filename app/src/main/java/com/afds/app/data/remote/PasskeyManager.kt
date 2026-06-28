package com.afds.app.data.remote

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential

class PasskeyManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    /** Pass the creation-options JSON string; returns registrationResponseJson. */
    suspend fun register(optionsJson: String): String {
        val request = CreatePublicKeyCredentialRequest(requestJson = optionsJson)
        val result = credentialManager.createCredential(context, request)
                as CreatePublicKeyCredentialResponse
        return result.registrationResponseJson
    }

    /** Pass the request-options JSON string; returns authenticationResponseJson. */
    suspend fun authenticate(optionsJson: String): String {
        val option = GetPublicKeyCredentialOption(requestJson = optionsJson)
        val request = GetCredentialRequest(listOf(option))
        val result = credentialManager.getCredential(context, request)
        val cred = result.credential as PublicKeyCredential
        return cred.authenticationResponseJson
    }
}
