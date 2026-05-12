package ly.payhub.merchant

import kotlinx.coroutines.test.runTest
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

class RawMerchantApiOrgTest {

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

    private val orgJson = """
        {
          "id": "11111111-2222-3333-4444-555555555555",
          "code": "acme",
          "name": "Acme Co",
          "type": "company",
          "status": "active",
          "created_at": "2026-01-01T00:00:00Z",
          "legal_name": "Acme Holdings LLC",
          "tax_number": "TX-9988",
          "commercial_register_no": "CR-12345",
          "billing_email": "billing@acme.example",
          "support_email": null,
          "phone": "+218910000000",
          "website": "https://acme.example",
          "address_line_1": "1 Market St",
          "address_line_2": null,
          "city": "Tripoli",
          "country": "LY",
          "logo_url": null
        }
    """.trimIndent()

    @Test
    fun get_org_decodes_full_field_set() = runTest {
        server.enqueue(MockResponse().setBody(orgJson))
        val o = api.getOrg(baseUrl(), token).getOrThrow()
        assertEquals("acme", o.code)
        assertEquals("Acme Co", o.name)
        assertEquals("company", o.type)
        assertEquals("Acme Holdings LLC", o.legalName)
        assertEquals("TX-9988", o.taxNumber)
        assertEquals("CR-12345", o.commercialRegisterNo)
        assertEquals("billing@acme.example", o.billingEmail)
        assertEquals(null, o.supportEmail)
        assertEquals("https://acme.example", o.website)
        assertEquals("1 Market St", o.addressLine1)
        assertEquals("LY", o.country)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/merchant/org", req.path)
    }

    @Test
    fun update_org_sends_only_dirty_keys() = runTest {
        server.enqueue(MockResponse().setBody(orgJson))
        val o = api.updateOrg(
            baseUrl(), token,
            RawMerchantApi.OrgPatch(name = "Acme Co", website = "https://e.x"),
        ).getOrThrow()
        assertEquals("acme", o.code)

        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/merchant/org", req.path)
        val body = req.body.readUtf8()
        assertTrue(body, body.contains("\"name\":\"Acme Co\""))
        assertTrue(body, body.contains("\"website\":\"https://e.x\""))
        // explicitNulls=false ⇒ the untouched fields are not serialised at all.
        assertFalse(body, body.contains("legal_name"))
        assertFalse(body, body.contains("\"phone\""))
        assertFalse(body, body.contains("logo_url"))
    }

    @Test
    fun update_org_can_clear_a_field_with_empty_string() = runTest {
        server.enqueue(MockResponse().setBody(orgJson))
        api.updateOrg(baseUrl(), token, RawMerchantApi.OrgPatch(taxNumber = "")).getOrThrow()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"tax_number\":\"\""))
    }
}
