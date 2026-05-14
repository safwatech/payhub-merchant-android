package ly.payhub.merchant.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Refresh-token at-rest persistence with optional biometric / device-credential
 * binding.
 *
 * Two slots live side by side in the same prefs file:
 *  - [KEY_PLAIN]  — the legacy unwrapped form. The prefs file is itself
 *    [androidx.security.crypto.EncryptedSharedPreferences] MasterKey-wrapped
 *    (see [TokenStore]), so this is not "plain on disk" — it just isn't
 *    user-auth-gated.
 *  - [KEY_CIPHER] + [KEY_IV] — AES/GCM/NoPadding ciphertext under an
 *    AndroidKeyStore key minted with
 *    `setUserAuthenticationRequired(true)` and a 30-second validity window.
 *    The SDK's first refresh after a successful app-lock unlock falls inside
 *    that window so the user isn't double-prompted; a stale window forces the
 *    next refresh to re-prompt.
 *
 * Toggling app lock on calls [rewrap]`(true)` — the live plaintext value is
 * re-encrypted into the cipher slot and the plain slot is wiped. Toggling
 * off does the inverse. The vault is dependency-light by design: a `() -> Boolean`
 * lambda asks the host (typically [ly.payhub.merchant.ui.lock.AppLockController])
 * whether lock is currently on, so unit tests can swap that in trivially.
 *
 * Edge: [KeyPermanentlyInvalidatedException] — fired when the user changes
 * their biometric enrolment — is caught, the slots are wiped, and `read()`
 * returns null. The host's auth-loss path then re-routes to login. No PII
 * is logged.
 *
 * See `docs/superpowers/specs/2026-05-14-d6-d7-sdk-1.2-and-app-followups-design.md` §3.5.
 */
class RefreshTokenVault(
    private val prefs: SharedPreferences,
    private val isLockEnabled: () -> Boolean,
) {
    companion object {
        const val KEY_PLAIN = "refresh_token_plain"
        const val KEY_CIPHER = "refresh_token_cipher"
        const val KEY_IV = "refresh_token_iv"

        private const val TAG = "RefreshTokenVault"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "payhub_refresh_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val VALIDITY_S = 30
    }

    /** Persist [refreshToken]. Picks the slot based on [isLockEnabled] **at write time**. */
    fun write(refreshToken: String) {
        if (isLockEnabled()) writeEncrypted(refreshToken) else writePlain(refreshToken)
    }

    /**
     * Read whichever slot is populated. The cipher slot wins when both exist
     * — a rewrap migration writes the new slot before deleting the old, so a
     * crash between the two leaves both, and the cipher one is the truth.
     */
    fun read(): String? =
        if (prefs.contains(KEY_CIPHER)) readEncrypted() else prefs.getString(KEY_PLAIN, null)

    /**
     * Migrate the current value between slots. No-op when there's nothing
     * stored. Called by `AppLockController.setEnabled(...)` after persisting
     * the toggle's new value.
     */
    fun rewrap(enabled: Boolean) {
        val current = read() ?: return
        clear()
        if (enabled) writeEncrypted(current) else writePlain(current)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_PLAIN)
            .remove(KEY_CIPHER)
            .remove(KEY_IV)
            .apply()
    }

    // --------------------------------------------------------------- internals

    private fun writePlain(value: String) {
        prefs.edit()
            .putString(KEY_PLAIN, value)
            .remove(KEY_CIPHER).remove(KEY_IV)
            .apply()
    }

    private fun writeEncrypted(value: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHER, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .remove(KEY_PLAIN)
            .apply()
    }

    private fun readEncrypted(): String? {
        val ctB64 = prefs.getString(KEY_CIPHER, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val key = getKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)),
            )
            val pt = cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP))
            String(pt, Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometric enrolment changed — the key is dead. Wipe the slot;
            // the host's auth-loss path takes the user back to login.
            Log.w(TAG, "refresh-token key invalidated; clearing slot")
            clear()
            null
        }
    }

    private fun getOrCreateKey(): SecretKey = getKey() ?: createKey()

    private fun getKey(): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun createKey(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                VALIDITY_S,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(VALIDITY_S)
        }
        gen.init(builder.build())
        return gen.generateKey()
    }
}
