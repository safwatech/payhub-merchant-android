package ly.payhub.merchant

import kotlinx.coroutines.test.runTest
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.AppErrorException
import ly.payhub.merchant.data.RawMerchantApi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for the raw `/merchant/auth/{change-password,mfa/*}` endpoints — request
 * shape and the envelope-aware error mapping, especially that a 401 from these
 * surfaces is **not** treated as a session expiry.
 */
class RawMerchantApiAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RawMerchantApi
    private val token = "test-access-token"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = RawMerchantApi(OkHttpClient())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')
    private fun err(r: Result<*>): AppError? = (r.exceptionOrNull() as? AppErrorException)?.error

    @Test
    fun change_password_posts_old_and_new_and_code() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val r = api.changePassword(baseUrl(), token, oldPassword = "old-secret-1234", newPassword = "new-secret-5678", code = "123456")
        assertTrue(r.isSuccess)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/merchant/auth/change-password", req.path)
        val body = req.body.readUtf8()
        assertTrue(body, body.contains("\"old_password\":\"old-secret-1234\""))
        assertTrue(body, body.contains("\"new_password\":\"new-secret-5678\""))
        assertTrue(body, body.contains("\"code\":\"123456\""))
        assertEquals("Bearer $token", req.getHeader("Authorization"))
    }

    @Test
    fun change_password_omits_code_when_null() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api.changePassword(baseUrl(), token, "old", "newlongpassword12", code = null).getOrThrow()
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body, body.contains("\"code\""))
    }

    @Test
    fun mfa_required_401_maps_to_mfa_required_not_unauthorized() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"code":"hub.merchant.mfa_required","message":"Authenticator code required."}}"""),
        )
        val r = api.changePassword(baseUrl(), token, "old", "newlongpassword12", null)
        val e = err(r)
        assertTrue("got: $e", e is AppError.MfaRequired)
        assertFalse(e is AppError.Unauthorized)
        assertEquals("Authenticator code required.", e?.message)
    }

    @Test
    fun bad_credentials_401_maps_to_invalid_not_unauthorized() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"code":"hub.merchant.bad_credentials","message":"That password does not match."}}"""),
        )
        val r = api.changePassword(baseUrl(), token, "wrong", "newlongpassword12", null)
        val e = err(r)
        assertTrue("got: $e", e is AppError.Invalid)
        assertFalse(e is AppError.Unauthorized)
        assertEquals("That password does not match.", (e as AppError.Invalid).message)
    }

    @Test
    fun mfa_enrol_decodes_secret_and_uri() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"secret":"JBSWY3DPEHPK3PXP","otpauth_uri":"otpauth://totp/PayHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=PayHub","issuer":"PayHub","account":"alice"}""",
            ),
        )
        val e = api.mfaEnrol(baseUrl(), token).getOrThrow()
        assertEquals("JBSWY3DPEHPK3PXP", e.secret)
        assertTrue(e.otpauthUri.startsWith("otpauth://"))
        assertEquals("PayHub", e.issuer)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/merchant/auth/mfa/enrol", req.path)
    }

    @Test
    fun mfa_confirm_posts_code() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api.mfaConfirm(baseUrl(), token, "654321").getOrThrow()
        val req = server.takeRequest()
        assertEquals("/merchant/auth/mfa/confirm", req.path)
        assertTrue(req.body.readUtf8().contains("\"code\":\"654321\""))
    }

    @Test
    fun mfa_disable_posts_password() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api.mfaDisable(baseUrl(), token, "account-secret-1234").getOrThrow()
        val req = server.takeRequest()
        assertEquals("/merchant/auth/mfa/disable", req.path)
        assertTrue(req.body.readUtf8().contains("\"password\":\"account-secret-1234\""))
    }

    @Test
    fun conflict_409_envelope_message_flows_into_invalid() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error":{"code":"hub.merchant.mfa_not_enabled","message":"MFA is not currently enabled."}}"""),
        )
        val r = api.mfaDisable(baseUrl(), token, "pw")
        val e = err(r)
        assertTrue("got: $e", e is AppError.Invalid)
        assertEquals("MFA is not currently enabled.", (e as AppError.Invalid).message)
    }
}
