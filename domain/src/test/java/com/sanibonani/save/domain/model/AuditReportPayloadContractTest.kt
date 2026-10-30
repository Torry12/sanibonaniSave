package com.sanibonani.save.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three `generate_*_audit_report_v1` RPCs answer with a JSONB object that the app decodes
 * straight into these models. The keys below are copied from the payloads in
 * `supabase/02_FUNCTIONS_AND_TRIGGERS.sql`, so this test fails the moment a master and a model
 * disagree on a key name or a value type — which `ignoreUnknownKeys` otherwise hides as a
 * silently-defaulted field on the admin's screen.
 */
class AuditReportPayloadContractTest {

    // Mirrors AppModule.provideJson(): the Supabase client decodes with this configuration.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true
        encodeDefaults = true
    }

    @Test
    fun `the group payload carries the group name under the key the server emits`() {
        val report = json.decodeFromString<GroupAuditReport>(
            """
            {"group_id":"g1","name":"Thokoze Stokvel","type":"stokvel","balance":"1250.00",
             "membership":{"total":12,"active":10,"probation":1,"suspended":1},
             "finances":{"total_inflow":"9000.00","total_outflow":"7750.00"},
             "reconciliation":{"last_check":"2026-10-20 09:00:00+02","status":"matched","discrepancy":"0.00"},
             "actuarial":{"risk_score":18,"solvency_ratio":1.34,"reserve_adequacy":1.02}}
            """.trimIndent()
        )

        assertEquals("Thokoze Stokvel", report.name)
        assertEquals(Money("1250.00"), report.balance)
        assertEquals(12, report.membership?.total)
        assertEquals("matched", report.reconciliation?.status)
        assertEquals(18, report.actuarial?.riskScore)
    }

    @Test
    fun `the platform payload decodes even though the server mixes numbers and text in top_level_stats`() {
        val report = json.decodeFromString<PlatformAuditReport>(
            """
            {"generated_at":"2026-10-26 11:04:52.318772+02","liquidity":"48200.00","revenue":"1910.50",
             "risk":{"high_risk_members":7,"critical_risk_members":2},
             "groups":{"total_groups":38,"suspended_groups":3},
             "top_level_stats":{"total_groups":38,"total_members":412,
                               "total_liquidity":"48200.00","total_revenue":"1910.50"}}
            """.trimIndent()
        )

        assertEquals(Money("48200.00"), report.liquidity)
        assertEquals(Money("1910.50"), report.revenue)
        assertEquals(7, report.risk?.highRiskMembers)
        assertEquals(3, report.groups?.suspendedGroups)
        assertEquals("48200.00", report.topLevelStats?.get("total_liquidity"))
    }

    @Test
    fun `the member payload decodes its money stats and recent logs`() {
        val report = json.decodeFromString<MemberAuditReport>(
            """
            {"member_id":"m1","full_name":"Naledi Dlamini","status":"active",
             "joined_at":"2026-01-04 08:00:00+02",
             "contributions":{"total_count":9,"total_paid":"2700.00","total_outstanding":"300.00",
                              "last_payment":"2026-10-01 07:30:00+02"},
             "loans":{"count":2,"total_borrowed":"1500.00","total_repaid":"900.00","outstanding":"600.00"},
             "behavior":{"score":44,"risk_level":"medium","consistency":0.66},
             "recent_audit_logs":[{"id":"a1","action":"MEMBER_STATUS_CHANGED","created_at":"2026-10-02 10:00:00+02"}]}
            """.trimIndent()
        )

        assertEquals(Money("2700.00"), report.contributions?.totalPaid)
        assertEquals(Money("600.00"), report.loans?.outstanding)
        assertEquals(44.0, report.behavior?.score ?: 0.0, 0.001)
        assertEquals(1, report.recentAuditLogs.size)
        assertEquals("MEMBER_STATUS_CHANGED", report.recentAuditLogs.first().action)
    }

    @Test
    fun `a balance audit result decodes the numeric amounts the RPC returns`() {
        val result = json.decodeFromString<GroupAuditResult>(
            """{"group_id":"g1","ledger_sum":1250.00,"current_balance":1238.00,"discrepancy":12.00,"fixed":false}"""
        )

        assertEquals(Money("1250.00"), result.ledgerSum)
        assertEquals(Money("12.00"), result.discrepancy)
        assertTrue(!result.fixed)
    }
}
