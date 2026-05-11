package ly.payhub.merchant

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.AppErrorException
import ly.payhub.merchant.data.RawMerchantApi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MockWebServer-backed coverage for the raw-HTTP `/merchant/*` endpoints
 * [RawMerchantApi] wraps for the merchant app (payments + settlements +
 * dashboard-by-sub + devices). The SDK's own auth / pay-links / dashboard paths
 * are covered upstream in `sdks/android`; here we only assert what we own
 * locally — request shape (path + query + bearer header) and the JSON →
 * Kotlin deserialization stays in sync with the Pydantic response models in
 * `app/api/merchant/*.py`.
 */
class RawMerchantApiPaymentsTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RawMerchantApi
    private val token = "test-access-token"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = RawMerchantApi(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun assertAuthHeader(req: RecordedRequest) {
        assertEquals("Bearer $token", req.getHeader("Authorization"))
        assertEquals("application/json", req.getHeader("Accept"))
    }

    // ---------------------------------------------------------------- payments

    @Test
    fun list_payments_serializes_filters_and_paging() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val r = api.listPayments(baseUrl(), token, psp = "sadad", status = "succeeded", limit = 25, offset = 50)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().isEmpty())

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/merchant/payments?"))
        assertTrue(recorded.path!!.contains("psp=sadad"))
        assertTrue(recorded.path!!.contains("status=succeeded"))
        assertTrue(recorded.path!!.contains("limit=25"))
        assertTrue(recorded.path!!.contains("offset=50"))
        assertAuthHeader(recorded)
    }

    @Test
    fun list_payments_caps_limit_at_200() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        api.listPayments(baseUrl(), token, limit = 999)
        val recorded = server.takeRequest()
        assertTrue("got: ${recorded.path}", recorded.path!!.contains("limit=200"))
    }

    @Test
    fun list_payments_decodes_a_row() = runTest {
        // Field names must match the server's PaymentRowOut Pydantic model 1:1.
        server.enqueue(
            MockResponse().setBody(
                """[
                  {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "psp_code": "moamalat",
                    "psp_ref": "abc-123",
                    "merchant_order_ref": "shop-987",
                    "amount_minor": 25500,
                    "currency": "LYD",
                    "status": "succeeded",
                    "created_at": "2026-05-10T12:34:56Z",
                    "updated_at": "2026-05-10T12:35:01Z"
                  }
                ]""".trimIndent(),
            ),
        )
        val r = api.listPayments(baseUrl(), token)
        val rows = r.getOrThrow()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("moamalat", row.pspCode)
        assertEquals("abc-123", row.pspRef)
        assertEquals("shop-987", row.merchantOrderRef)
        assertEquals(25_500L, row.amountMinor)
        assertEquals("LYD", row.currency)
        assertEquals("succeeded", row.status)
    }

    @Test
    fun get_payment_decodes_events_and_metadata() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "id": "11111111-2222-3333-4444-555555555555",
                  "psp_code": "sadad",
                  "psp_ref": null,
                  "merchant_order_ref": "order-1",
                  "amount_minor": 5000,
                  "currency": "LYD",
                  "status": "succeeded",
                  "created_at": "2026-05-10T12:00:00Z",
                  "updated_at": "2026-05-10T12:05:00Z",
                  "events": [
                    {
                      "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                      "event_type": "payment.initiated",
                      "prev_status": null,
                      "new_status": "pending",
                      "source": "payhub",
                      "created_at": "2026-05-10T12:00:00Z"
                    },
                    {
                      "id": "ffffffff-0000-1111-2222-333333333333",
                      "event_type": "payment.succeeded",
                      "prev_status": "pending",
                      "new_status": "succeeded",
                      "source": "psp",
                      "created_at": "2026-05-10T12:05:00Z"
                    }
                  ],
                  "metadata": {
                    "pay_link_id": "link-42",
                    "customer_msisdn": "+218910000000"
                  }
                }""".trimIndent(),
            ),
        )
        val detail = api.getPayment(baseUrl(), token, "11111111-2222-3333-4444-555555555555").getOrThrow()
        assertEquals("order-1", detail.merchantOrderRef)
        assertNull(detail.pspRef)
        assertEquals(2, detail.events.size)
        assertEquals("payment.initiated", detail.events.first().eventType)
        assertEquals("payhub", detail.events.first().source)
        assertEquals("succeeded", detail.events.last().newStatus)
        val payLinkId = (detail.metadata["pay_link_id"] as? JsonPrimitive)?.contentOrNull
        val msisdn = (detail.metadata["customer_msisdn"] as? JsonPrimitive)?.contentOrNull
        assertEquals("link-42", payLinkId)
        assertEquals("+218910000000", msisdn)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/merchant/payments/11111111-2222-3333-4444-555555555555", recorded.path)
        assertAuthHeader(recorded)
    }

    // ---------------------------------------------------------------- settlements

    @Test
    fun list_settlements_decodes_counters_and_dates() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[
                  {
                    "id": "f1f1f1f1-0000-1111-2222-333333333333",
                    "psp_code": "moamalat",
                    "filename": "moamalat_2026-05-10.csv",
                    "file_sha256": "deadbeef",
                    "period_from": "2026-05-10T00:00:00Z",
                    "period_to": "2026-05-10T23:59:59Z",
                    "row_count": 120,
                    "matched_count": 118,
                    "mismatch_count": 2,
                    "missing_in_hub_count": 0,
                    "missing_in_psp_count": 0,
                    "created_at": "2026-05-11T01:15:00Z"
                  }
                ]""".trimIndent(),
            ),
        )
        val list = api.listSettlements(baseUrl(), token).getOrThrow()
        assertEquals(1, list.size)
        val f = list.first()
        assertEquals("moamalat", f.pspCode)
        assertEquals("moamalat_2026-05-10.csv", f.filename)
        assertEquals(120, f.rowCount)
        assertEquals(118, f.matchedCount)
        assertEquals(2, f.mismatchCount)
    }

    @Test
    fun get_settlement_hits_singular_path() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "id": "f1f1f1f1-0000-1111-2222-333333333333",
                  "psp_code": "sadad",
                  "filename": "sadad.csv",
                  "file_sha256": "abc",
                  "period_from": null,
                  "period_to": null,
                  "row_count": 0,
                  "matched_count": 0,
                  "mismatch_count": 0,
                  "missing_in_hub_count": 0,
                  "missing_in_psp_count": 0,
                  "created_at": "2026-05-11T01:15:00Z"
                }""".trimIndent(),
            ),
        )
        val f = api.getSettlement(baseUrl(), token, "f1f1f1f1-0000-1111-2222-333333333333").getOrThrow()
        assertEquals("sadad", f.pspCode)
        assertNull(f.periodFrom)

        val recorded = server.takeRequest()
        assertEquals("/merchant/settlements/f1f1f1f1-0000-1111-2222-333333333333", recorded.path)
    }

    @Test
    fun list_settlement_rows_passes_filter_and_decodes_diff() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[
                  {
                    "id": "row-1",
                    "merchant_order_ref": "order-99",
                    "psp_ref": "psp-99",
                    "psp_status": "settled",
                    "amount_minor": 12500,
                    "currency": "LYD",
                    "payment_id": "pay-99",
                    "status": "mismatch",
                    "diff": { "amount_minor": { "hub": 12500, "psp": 12000 } },
                    "created_at": "2026-05-11T01:00:00Z"
                  }
                ]""".trimIndent(),
            ),
        )
        val rows = api.listSettlementRows(
            baseUrl(), token, "file-1", statusFilter = "mismatch", limit = 10, offset = 0,
        ).getOrThrow()
        assertEquals(1, rows.size)
        val r = rows.first()
        assertEquals("mismatch", r.status)
        assertEquals("pay-99", r.paymentId)
        assertNotNull(r.diff["amount_minor"])

        val recorded = server.takeRequest()
        assertTrue("path: ${recorded.path}", recorded.path!!.startsWith("/merchant/settlements/file-1/rows?"))
        assertTrue(recorded.path!!.contains("status_filter=mismatch"))
        assertTrue(recorded.path!!.contains("limit=10"))
    }

    // ---------------------------------------------------------------- error mapping

    @Test
    fun http_401_maps_to_unauthorized_apperror() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"unauthenticated"}"""))
        val r = api.listPayments(baseUrl(), token)
        assertTrue(r.isFailure)
        val err = (r.exceptionOrNull() as? AppErrorException)?.error
        assertTrue("got: $err", err is AppError.Unauthorized)
    }

    @Test
    fun http_404_maps_to_not_found_apperror() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"not found"}"""))
        val r = api.getPayment(baseUrl(), token, "missing")
        assertTrue(r.isFailure)
        val err = (r.exceptionOrNull() as? AppErrorException)?.error
        assertTrue("got: $err", err is AppError.NotFound)
    }

    @Test
    fun http_500_maps_to_unexpected_apperror() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = api.listSettlements(baseUrl(), token)
        assertTrue(r.isFailure)
        val err = (r.exceptionOrNull() as? AppErrorException)?.error
        assertTrue("got: $err", err is AppError.Unexpected)
    }

    // ---------------------------------------------------------------- dashboard-by-sub

    @Test
    fun dashboard_by_sub_decodes_breakdown() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "window_hours": 72,
                  "sub_breakdown": [
                    {
                      "sub_merchant_id": "sub-1",
                      "code": "S1",
                      "name": "Shop One",
                      "paid_count": 4,
                      "paid_volume_minor": 12500,
                      "inflight": 1,
                      "active_pay_links": 2,
                      "needs_followup": 0
                    }
                  ]
                }""".trimIndent(),
            ),
        )
        val resp = api.dashboardBySub(baseUrl(), token, windowHours = 72).getOrThrow()
        assertEquals(72, resp.windowHours)
        assertEquals(1, resp.subBreakdown.size)
        val row = resp.subBreakdown.first()
        assertEquals("Shop One", row.name)
        assertEquals(4, row.paidCount)
        assertEquals(12_500L, row.paidVolumeMinor)
    }

    // ---------------------------------------------------------------- devices (token-in-body)

    @Test
    fun register_device_posts_token_in_json_body() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val r = api.registerDevice(baseUrl(), token, fcmToken = "fcm-abc")
        assertTrue(r.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/merchant/devices", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body should carry the fcm token: $body", body.contains("fcm-abc"))
        assertTrue("body should declare platform=android: $body", body.contains("\"platform\":\"android\""))
        assertAuthHeader(recorded)
    }

    @Test
    fun unregister_device_deletes_with_token_in_body() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        api.unregisterDevice(baseUrl(), token, fcmToken = "fcm-abc").getOrThrow()

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/merchant/devices", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("body should carry the fcm token: $body", body.contains("fcm-abc"))
    }
}
