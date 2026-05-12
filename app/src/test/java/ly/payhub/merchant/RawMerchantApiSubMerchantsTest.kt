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

class RawMerchantApiSubMerchantsTest {

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
    fun list_sub_merchants_decodes_rows_with_payments_count() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[
                  {
                    "id": "sub-1", "merchant_id": "m-1", "code": "acme-east", "code_prefix": "AE",
                    "name": "Acme East", "status": "active", "external_ref": "ext-1",
                    "metadata": {"region":"east"},
                    "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-02T00:00:00Z",
                    "payments_count": 7
                  }
                ]""".trimIndent(),
            ),
        )
        val list = api.listSubMerchants(baseUrl(), token).getOrThrow()
        assertEquals(1, list.size)
        val sm = list.first()
        assertEquals("acme-east", sm.code)
        assertEquals("AE", sm.codePrefix)
        assertEquals("active", sm.status)
        assertEquals(7, sm.paymentsCount)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/merchant/sub-merchants", req.path)
    }

    @Test
    fun create_sub_merchant_posts_code_prefix_name_status() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"sub-2","merchant_id":"m-1","code":"acme-west","code_prefix":"AW","name":"Acme West","status":"active","metadata":{},"created_at":"x","updated_at":"x","payments_count":0}""",
            ),
        )
        val sm = api.createSubMerchant(
            baseUrl(), token,
            RawMerchantApi.SubMerchantCreate(code = "acme-west", codePrefix = "AW", name = "Acme West"),
        ).getOrThrow()
        assertEquals("acme-west", sm.code)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/merchant/sub-merchants", req.path)
        val body = req.body.readUtf8()
        assertTrue(body, body.contains("\"code\":\"acme-west\""))
        assertTrue(body, body.contains("\"code_prefix\":\"AW\""))
        assertTrue(body, body.contains("\"name\":\"Acme West\""))
        assertTrue(body, body.contains("\"status\":\"active\""))
    }

    @Test
    fun delete_sub_merchant_204_succeeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val r = api.deleteSubMerchant(baseUrl(), token, "sub-1")
        assertTrue(r.isSuccess)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/merchant/sub-merchants/sub-1", req.path)
    }

    @Test
    fun delete_sub_merchant_409_plaintext_detail_surfaces_invalid() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"detail":"Sub-merchant has 3 payment(s); cannot delete. Disable it instead — ..."}"""),
        )
        val r = api.deleteSubMerchant(baseUrl(), token, "sub-1")
        val e = err(r)
        assertTrue("got: $e", e is AppError.Invalid)
        val msg = (e as AppError.Invalid).message
        assertFalse(msg.isBlank())
        assertTrue(msg, msg.contains("payment"))
    }

    @Test
    fun list_sub_users_decodes_role_and_mfa() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[
                  {"id":"u-1","sub_merchant_id":"sub-1","username":"cashier1","role":"sub_operator","status":"active","full_name":"Cashier One","email":"c1@x.y","mobile":null,"phone":null,"mfa_enabled":true,"last_login_at":null,"created_at":"2026-01-01T00:00:00Z"}
                ]""".trimIndent(),
            ),
        )
        val list = api.listSubUsers(baseUrl(), token, "sub-1").getOrThrow()
        assertEquals(1, list.size)
        val u = list.first()
        assertEquals("cashier1", u.username)
        assertEquals("sub_operator", u.role)
        assertTrue(u.mfaEnabled)

        val req = server.takeRequest()
        assertEquals("/merchant/sub-merchants/sub-1/users", req.path)
    }

    @Test
    fun create_sub_user_decodes_invite_url() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"u-2","sub_merchant_id":"sub-1","username":"cashier2","role":"sub_operator","status":"active","full_name":"Cashier Two","mfa_enabled":false,"created_at":"x","invite_url":"https://app.payhub.ly/m/accept-invite?token=abc","invite_sent_to_channel":"email","invite_expires_at":"2026-01-04T00:00:00Z"}""",
            ),
        )
        val created = api.createSubUser(
            baseUrl(), token, "sub-1",
            RawMerchantApi.SubUserCreate(username = "cashier2", fullName = "Cashier Two", email = "c2@x.y"),
        ).getOrThrow()
        assertEquals("https://app.payhub.ly/m/accept-invite?token=abc", created.inviteUrl)
        assertEquals("email", created.inviteSentToChannel)
        assertEquals("cashier2", created.toRow().username)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/merchant/sub-merchants/sub-1/users", req.path)
        assertTrue(req.body.readUtf8().contains("\"username\":\"cashier2\""))
    }

    @Test
    fun update_sub_user_409_last_owner_surfaces_message() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error":{"code":"hub.merchant.last_sub_owner","message":"That would leave zero active owners."}}"""),
        )
        val r = api.updateSubUser(baseUrl(), token, "sub-1", "u-1", RawMerchantApi.SubUserPatch(role = "sub_viewer"))
        val e = err(r)
        assertTrue("got: $e", e is AppError.Invalid)
        assertEquals("That would leave zero active owners.", (e as AppError.Invalid).message)
    }

    @Test
    fun clear_sub_user_mfa_posts_code() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api.clearSubUserMfa(baseUrl(), token, "sub-1", "u-1", "112233").getOrThrow()
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/merchant/sub-merchants/sub-1/users/u-1/clear-mfa", req.path)
        assertTrue(req.body.readUtf8().contains("\"code\":\"112233\""))
    }

    @Test
    fun reissue_invite_decodes_url_and_channel() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"sent_to_channel":"sms","invite_url":"https://app.payhub.ly/m/accept-invite?token=def","expires_at":"2026-01-05T00:00:00Z"}""",
            ),
        )
        val ri = api.reissueSubUserInvite(baseUrl(), token, "sub-1", "u-1").getOrThrow()
        assertEquals("sms", ri.sentToChannel)
        assertTrue(ri.inviteUrl.contains("token=def"))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/merchant/sub-merchants/sub-1/users/u-1/reissue-invite", req.path)
    }
}
