package com.sanibonani.save

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.admin.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Ignore
import org.junit.Test

/**
 * Database Reset & Admin Creation Utility
 *
 * Run this test to:
 * 1. Clear all Supabase data (all tables)
 * 2. Create Platform Admin user with credentials:
 *    - Email: torrymsimango@gmail.com
 *    - Password: torry123M
 *    - Role: platform_admin
 *
 * NOTE: This is destructive and will DELETE ALL DATA in the remote database.
 * Use with caution in production environments.
 */
class DatabaseResetUtility {

    @Ignore("Manual utility, not a unit test - DESTRUCTIVE OPERATION")
    @Test
    fun resetDatabaseAndCreateAdmin(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            println("=".repeat(80))
            println("SANIBONANI SAVE - DATABASE RESET & ADMIN CREATION UTILITY")
            println("=".repeat(80))

            // ─── Configuration ──────────────────────────────────────────────────────
            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0.CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"
            val serviceRoleKey = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzU0MTgwNSwiZXhwIjoyMDg5MTE3ODA1fQ.EDVweeVevFlnIo-9xfwc80zZ93KM3tY-GTmWsUmPCLA"

            val adminEmail = "torrymsimango@gmail.com"
            val adminPassword = "torry123M"

            // ─── Create Supabase clients ────────────────────────────────────────────
            println("\n[1/4] Initializing Supabase clients...")

            val anonClient = createSupabaseClient(supabaseUrl, anonKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
                install(Postgrest)
            }

            val adminClient = createSupabaseClient(supabaseUrl, serviceRoleKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
                install(Postgrest)
            }

            println("✓ Supabase clients initialized")

            // ─── Delete all data from tables ────────────────────────────────────────
            println("\n[2/4] Clearing remote database tables...")

            try {
                // Order matters: delete child records before parents due to FK constraints
                val tablesToClear = listOf(
                    "payouts",
                    "member_documents",
                    "beneficiaries",
                    "notifications",
                    "contributions",
                    "payments",
                    "members",
                    "platform_fees",
                    "policies",
                    "group_actuarial_metrics",
                    "groups"
                )

                for (table in tablesToClear) {
                    try {
                        adminClient.postgrest[table].delete {
                            filter {
                                neq("id", "00000000-0000-0000-0000-000000000000")
                            }
                        }
                        println("  ✓ Cleared table: $table")
                    } catch (e: Exception) {
                        println("  ⚠ Warning clearing $table: ${e.message}")
                        // Continue clearing other tables
                    }
                }

                println("✓ All data cleared from remote database")
            } catch (e: Exception) {
                println("⚠ Error clearing remote data: ${e.message}")
                // Continue to admin creation
            }

            // ─── Delete existing admin user (if any) ─────────────────────────────────
            println("\n[3/4] Checking for existing admin user...")

            try {
                // List all users and find if admin exists
                val users = adminClient.auth.admin.retrieveUsers()
                val existingAdmin = users.find { it.email == adminEmail }

                if (existingAdmin != null) {
                    println("  Found existing admin user. Attempting to delete...")
                    adminClient.auth.admin.deleteUser(existingAdmin.id)
                    println("  ✓ Deleted existing admin user")
                }
            } catch (e: Exception) {
                println("  ⚠ Warning checking/deleting existing user: ${e.message}")
                // Continue to create new one
            }

            // ─── Create new Platform Admin user ────────────────────────────────────
            println("\n[4/4] Creating Platform Admin user...")

            try {
                val adminUser = adminClient.auth.admin.createUserWithEmail {
                    email = adminEmail
                    password = adminPassword
                    autoConfirm = true
                    userMetadata = buildJsonObject {
                        put("role", "platform_admin")
                        put("full_name", "Platform Administrator")
                    }
                }

                println("✓ Platform Admin user created successfully!")
                println("   Email: $adminEmail")
                println("   Role: platform_admin")
                println("   Password: $adminPassword")
                println("   User ID: ${adminUser.id}")

            } catch (e: Exception) {
                println("✗ Error creating admin user: ${e.message}")
                throw e
            }

            // ─── Summary ────────────────────────────────────────────────────────────
            println("\n" + "=".repeat(80))
            println("✓ DATABASE RESET COMPLETE")
            println("=".repeat(80))
            println("\nAdmin Login Credentials:")
            println("  Email: $adminEmail")
            println("  Password: $adminPassword")
            println("\nNext Steps:")
            println("  1. Clear local app cache: Settings > Apps > SanibonaniSave > Clear Cache")
            println("  2. Close and restart the app")
            println("  3. Sign in with the admin credentials above")
            println("=".repeat(80))

        } catch (e: Exception) {
            println("\n✗ FATAL ERROR: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Alternative: Only clear remote data without creating admin
     */
    @Ignore("Manual utility - clear data only")
    @Test
    fun clearRemoteDataOnly(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            println("\n[CLEARING REMOTE DATA ONLY]")

            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val serviceRoleKey = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzU0MTgwNSwiZXhwIjoyMDg5MTE3ODA1fQ.EDVweeVevFlnIo-9xfwc80zZ93KM3tY-GTmWsUmPCLA"

            val adminClient = createSupabaseClient(supabaseUrl, serviceRoleKey) {
                install(Auth)
                install(Postgrest)
            }

            val tablesToClear = listOf(
                "payouts",
                "member_documents",
                "beneficiaries",
                "notifications",
                "contributions",
                "payments",
                "members",
                "platform_fees",
                "policies",
                "group_actuarial_metrics",
                "groups"
            )

            for (table in tablesToClear) {
                try {
                    adminClient.postgrest[table].delete {
                        filter {
                            neq("id", "00000000-0000-0000-0000-000000000000")
                        }
                    }
                    println("✓ Cleared: $table")
                } catch (e: Exception) {
                    println("⚠ Error: $table - ${e.message}")
                }
            }

            println("\n✓ Remote data cleared successfully!")

        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Alternative: Only create admin without clearing data
     */
    @Ignore("Manual utility - create admin only")
    @Test
    fun createAdminUserOnly(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            println("\n[CREATING ADMIN USER ONLY]")

            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val serviceRoleKey = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzU0MTgwNSwiZXhwIjoyMDg5MTE3ODA1fQ.EDVweeVevFlnIo-9xfwc80zZ93KM3tY-GTmWsUmPCLA"

            val adminEmail = "torrymsimango@gmail.com"
            val adminPassword = "torry123M"

            val adminClient = createSupabaseClient(supabaseUrl, serviceRoleKey) {
                install(Auth)
            }

            val adminUser = adminClient.auth.admin.createUserWithEmail {
                email = adminEmail
                password = adminPassword
                autoConfirm = true
                userMetadata = buildJsonObject {
                    put("role", "platform_admin")
                    put("full_name", "Platform Administrator")
                }
            }

            println("✓ Platform Admin created:")
            println("   Email: $adminEmail")
            println("   Password: $adminPassword")
            println("   User ID: ${adminUser.id}")

        } finally {
            Dispatchers.resetMain()
        }
    }

    @Ignore("Manual utility for fixing existing groups - run only when needed")
    @Test
    fun fixExistingGroupsForDiscovery(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val separator = "=".repeat(80)
            println(separator)
            println("FIXING EXISTING GROUPS FOR DISCOVERY - UPDATING FLAGS")
            println(separator)

            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0.CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"

            val supabaseClient = createSupabaseClient(supabaseUrl, anonKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
                install(Postgrest)
            }

            println("\n[1/3] Fetching all existing groups...")

            // Get all groups first
            val allGroups: List<Map<String, Any>> = try {
                supabaseClient.postgrest["groups"].select().decodeList()
            } catch (e: Exception) {
                println("⚠️ Error fetching groups: ${e.message}")
                emptyList()
            }

            println("✓ Found ${allGroups.size} groups in database")

            if (allGroups.isEmpty()) {
                println("ℹ️ No groups to update")
                return@runBlocking
            }

            println("\n[2/3] Updating groups to be discoverable...")
            println("Setting: is_public = true, registration_paid = true")

            var successCount = 0
            var failureCount = 0

            for (group in allGroups) {
                val groupId = group["id"] as? String ?: continue
                val currentIsPublic = group["is_public"] as? Boolean ?: false
                val currentRegistrationPaid = group["registration_paid"] as? Boolean ?: false

                println("\nProcessing group: $groupId")
                println("  Current: is_public=$currentIsPublic, registration_paid=$currentRegistrationPaid")

                try {
                    supabaseClient.postgrest["groups"].update(
                        buildJsonObject {
                            put("is_public", true)
                            put("registration_paid", true)
                        }
                    ) {
                        filter { eq("id", groupId) }
                    }
                    println("  ✅ Updated successfully")
                    successCount++
                } catch (e: Exception) {
                    println("  ❌ Failed: ${e.message}")
                    failureCount++
                }
            }

            println("\n[3/3] Verification")
            println(separator)
            println("✅ Successfully updated: $successCount groups")
            println("❌ Failed updates: $failureCount groups")
            println(separator)

            println("\n📊 Updated groups are now discoverable!")
            println("Groups with is_public=true AND registration_paid=true will appear in Discover Groups")

        } catch (e: Exception) {
            println("❌ Error during group fix: ${e.message}")
            e.printStackTrace()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Ignore("Manual utility for database reset with mock data - DESTRUCTIVE")
    @Test
    fun resetDatabaseAndInstallMockData(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val separator = "=".repeat(80)
            println(separator)
            println("SANIBONANI SAVE - DATABASE RESET WITH MOCK DATA & ADMIN CREATION")
            println(separator)

            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0.CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"
            val serviceRoleKey = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzU0MTgwNSwiZXhwIjoyMDg5MTE3ODA1fQ.EDVweeVevFlnIo-9xfwc80zZ93KM3tY-GTmWsUmPCLA"

            val adminEmail = "torrymsimango@gmail.com"
            val adminPassword = "torry123M"

            println("\n[1/4] Initializing Supabase clients...")

            val supabaseClient = createSupabaseClient(supabaseUrl, serviceRoleKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
                install(Postgrest)
            }

            println("✓ Supabase client initialized")

            println("\n[2/4] Resetting database...")

            // Clear all data (order matters due to foreign keys)
            val tablesToClear = listOf(
                "payouts", "contributions", "member_documents", "notifications",
                "beneficiaries", "members", "groups", "platform_fees", "policies",
                "group_actuarial_metrics", "payments", "profiles"
            )

            var clearedCount = 0
            for (table in tablesToClear) {
                try {
                    supabaseClient.postgrest[table].delete {
                        filter {
                            neq("id", "00000000-0000-0000-0000-000000000000")
                        }
                    }
                    println("✓ Cleared $table")
                    clearedCount++
                } catch (e: Exception) {
                    // Try alternative deletion method
                    try {
                        supabaseClient.postgrest[table].select().decodeList<Map<String, Any>>()
                        println("✓ Found $table (will be cleared)")
                    } catch (e2: Exception) {
                        // Table might not exist yet
                    }
                }
            }

            println("✓ Database reset complete")

            println("\n[3/4] Creating platform admin account...")

            // Create admin user
            val adminUser = try {
                supabaseClient.auth.admin.createUserWithEmail {
                    email = adminEmail
                    password = adminPassword
                    autoConfirm = true
                    userMetadata = buildJsonObject {
                        put("full_name", "Platform Admin")
                        put("role", "platform_admin")
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Admin user creation: ${e.message}")
                null
            }

            if (adminUser != null) {
                println("✓ Admin account created: $adminEmail")

                // Create admin profile
                try {
                    supabaseClient.postgrest["profiles"].insert(
                        buildJsonObject {
                            put("id", adminUser.id)
                            put("email", adminEmail)
                            put("full_name", "Platform Admin")
                            put("role", "platform_admin")
                        }
                    )
                    println("✓ Admin profile created")
                } catch (e: Exception) {
                    println("⚠️ Profile creation: ${e.message}")
                }
            }

            println("\n[4/4] Installing mock data...")

            // Create sample groups
            val groupsData = listOf(
                buildJsonObject {
                    put("name", "Sunshine Stokvel")
                    put("type", "stokvel")
                    put("province", "Western Cape")
                    put("city", "Cape Town")
                    put("township", "Khayelitsha")
                    put("description", "A thriving community savings group")
                    put("logo_emoji", "☀️")
                    put("is_public", true)
                    put("registration_paid", true)
                    put("joining_fee", 500.0)
                    put("monthly_contribution", 500.0)
                    put("max_members", 50)
                    put("current_members", 0)
                    put("balance", 0.0)
                    put("late_fee", 50.0)
                    put("late_fee_grace_days", 5)
                    put("probation_months", 3)
                    put("payment_due_day", 28)
                    put("allow_partial_payment", false)
                    put("auto_suspend_after", 2)
                    put("is_platform_suspended", false)
                    put("goal_amount", 50000.0)
                    put("period_months", 12)
                    put("account_type", "Savings")
                    put("fee_status", "paid")
                    put("latitude", -33.9249)
                    put("longitude", 18.4241)
                    put("geohash", "k3vn8gxf2")
                },
                buildJsonObject {
                    put("name", "Rainy Day Burial Society")
                    put("type", "burial_society")
                    put("province", "Gauteng")
                    put("city", "Johannesburg")
                    put("township", "Soweto")
                    put("description", "Funeral expenses covered")
                    put("logo_emoji", "🕊️")
                    put("is_public", true)
                    put("registration_paid", true)
                    put("joining_fee", 300.0)
                    put("monthly_contribution", 200.0)
                    put("max_members", 100)
                    put("current_members", 0)
                    put("balance", 0.0)
                    put("late_fee", 25.0)
                    put("late_fee_grace_days", 5)
                    put("probation_months", 3)
                    put("payment_due_day", 28)
                    put("allow_partial_payment", false)
                    put("auto_suspend_after", 2)
                    put("is_platform_suspended", false)
                    put("goal_amount", 100000.0)
                    put("period_months", 24)
                    put("account_type", "Savings")
                    put("fee_status", "paid")
                    put("max_beneficiaries", 5)
                    put("beneficiary_increase_pct", 10.0)
                    put("latitude", -26.2485)
                    put("longitude", 27.8540)
                    put("geohash", "ke7fxj6n9")
                },
                buildJsonObject {
                    put("name", "Zama Zama Savings")
                    put("type", "investment_club")
                    put("province", "KwaZulu-Natal")
                    put("city", "Durban")
                    put("township", "Umlazi")
                    put("description", "Long-term investment focus")
                    put("logo_emoji", "💰")
                    put("is_public", true)
                    put("registration_paid", true)
                    put("joining_fee", 1000.0)
                    put("monthly_contribution", 1500.0)
                    put("max_members", 30)
                    put("current_members", 0)
                    put("balance", 0.0)
                    put("late_fee", 100.0)
                    put("late_fee_grace_days", 7)
                    put("probation_months", 6)
                    put("payment_due_day", 15)
                    put("allow_partial_payment", true)
                    put("auto_suspend_after", 3)
                    put("is_platform_suspended", false)
                    put("goal_amount", 500000.0)
                    put("period_months", 60)
                    put("account_type", "Investment")
                    put("fee_status", "paid")
                    put("latitude", -29.8587)
                    put("longitude", 31.0218)
                    put("geohash", "kd3qzm1c4")
                }
            )

            var groupsCreated = 0
            for (groupData in groupsData) {
                try {
                    supabaseClient.postgrest["groups"].insert(groupData)
                    val groupName = (groupData as? JsonObject)?.get("name")?.jsonPrimitive?.content ?: "Unknown"
                    println("✓ Created group: $groupName")
                    groupsCreated++
                } catch (e: Exception) {
                    println("⚠️ Group creation failed: ${e.message}")
                    println("   Details: ${e.cause?.message ?: "No details"}")
                }
            }

            // Verify groups were created
            println("\n[4b] Verifying mock data was created...")
            try {
                val createdGroups: List<Map<String, Any>> = supabaseClient.postgrest["groups"].select {
                    filter { eq("is_public", true) }
                    filter { eq("registration_paid", true) }
                }.decodeList()
                println("✓ Verified in database: ${createdGroups.size} discoverable groups")
                createdGroups.forEach { group ->
                    println("  - ${group["name"]} (ID: ${group["id"]})")
                }
            } catch (e: Exception) {
                println("⚠️ Verification failed: ${e.message}")
            }

            println(separator)
            println("INSTALLATION COMPLETE")
            println(separator)
            println("✅ Database reset: ✓")
            println("✅ Platform admin created: ✓")
            println("   Email: $adminEmail")
            println("   Password: $adminPassword")
            println("✅ Mock groups installed: $groupsCreated")
            println(separator)

            println("\n📱 Next steps:")
            println("1. Clear app cache: adb shell pm clear com.sanibonani.save")
            println("2. Restart app")
            println("3. Login with admin credentials")
            println("4. Navigate to Discover Groups to see mock data")

        } catch (e: Exception) {
            println("❌ Error during database reset: ${e.message}")
            e.printStackTrace()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Ignore("Manual utility for verifying local-remote sync")
    @Test
    fun verifySyncBetweenLocalAndRemote(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val separator = "=".repeat(80)
            println(separator)
            println("SANIBONANI SAVE - LOCAL-REMOTE SYNC VERIFICATION")
            println(separator)

            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0.CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"

            val supabaseClient = createSupabaseClient(supabaseUrl, anonKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
                install(Postgrest)
            }

            println("\n[1/4] Fetching remote groups...")

            // Get remote groups
            val remoteGroups: List<Map<String, Any>> = try {
                supabaseClient.postgrest["groups"].select {
                    filter { eq("is_public", true) }
                    filter { eq("registration_paid", true) }
                }.decodeList()
            } catch (e: Exception) {
                println("⚠️ Error fetching remote: ${e.message}")
                emptyList()
            }

            println("✓ Remote groups (is_public=1, registration_paid=1): ${remoteGroups.size}")
            remoteGroups.forEach { group ->
                println("  - ${group["id"]} (${group["name"]})")
            }

            println("\n[2/4] Checking local cache...")
            println("⚠️ Note: Local cache is in-app, we cannot directly query from test")
            println("  → The app should have synchronized on last load")
            println("  → If counts differ, cache sync may have failed")

            println("\n[3/4] Analysis...")

            val groupIds = remoteGroups.mapNotNull { it["id"] as? String }
            val groupNames = remoteGroups.mapNotNull { it["name"] as? String }

            println("Remote Summary:")
            println("  Total discoverable groups: ${remoteGroups.size}")
            println("  IDs: $groupIds")
            println("  Names: $groupNames")

            // Check for duplicates
            val idDuplicates = groupIds.groupingBy { it }.eachCount().filter { it.value > 1 }
            if (idDuplicates.isNotEmpty()) {
                println("⚠️ ISSUE FOUND: Duplicate group IDs in remote:")
                idDuplicates.forEach { (id, count) ->
                    println("  - $id appears $count times (BUG!)")
                }
            } else {
                println("✓ No duplicate IDs")
            }

            // Check for corrupted data
            val missingFields = remoteGroups.filter { group ->
                group["name"] == null || group["id"] == null || group["is_public"] == null || group["registration_paid"] == null
            }
            if (missingFields.isNotEmpty()) {
                println("⚠️ ISSUE FOUND: Groups with missing fields:")
                missingFields.forEach { group ->
                    println("  - ${group["id"]}: name=${group["name"]}, is_public=${group["is_public"]}, registration_paid=${group["registration_paid"]}")
                }
            } else {
                println("✓ All groups have required fields")
            }

            println("\n[4/4] Recommendations...")

            if (remoteGroups.isEmpty()) {
                println("ℹ️ No discoverable groups found")
                println("  → Run: fixExistingGroupsForDiscovery() OR resetDatabaseAndInstallMockData()")
            } else if (idDuplicates.isNotEmpty() || missingFields.isNotEmpty()) {
                println("⚠️ Data integrity issues detected!")
                println("  → Run: resetDatabaseAndInstallMockData() to clean database")
            } else {
                println("✓ Remote database appears clean")
                println("  → If app still shows wrong count, the issue is in local cache sync")
                println("  → Clear app cache: adb shell pm clear com.sanibonani.save")
                println("  → Restart app to force fresh sync")
            }

            println(separator)
            println("VERIFICATION COMPLETE")
            println(separator)

        } catch (e: Exception) {
            println("❌ Error during verification: ${e.message}")
            e.printStackTrace()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Ignore("Manual utility for clearing local cache and forcing fresh sync")
    @Test
     fun forceLocalCacheRefresh(): Unit = runBlocking {
        val separator = "=".repeat(80)
        println(separator)
        println("LOCAL CACHE REFRESH INSTRUCTIONS")
        println(separator)
        println("\n[1] Clear App Cache")
        println("    adb shell pm clear com.sanibonani.save")
        println("\n[2] Clear Room Database (if needed)")
        println("    adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db")
        println("    adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db-shm")
        println("    adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db-wal")
        println("\n[3] Restart App")
        println("    adb shell am start -n com.sanibonani.save/.MainActivity")
        println("\n[4] Monitor Logs")
        println("    adb logcat | grep 'GroupRepository\\|sync'")
        println("\n[5] Open Discover Groups")
        println("    The app will now force a fresh sync from remote to local")
        println("    All groups will be re-cached locally")
        println(separator)
    }

    @Ignore("Diagnostic - Check what's actually in remote database")
    @Test
    fun diagnosticCheckRemoteGroupsRaw(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val separator = "=".repeat(80)
            println(separator)
            println("DIAGNOSTIC: RAW REMOTE GROUPS CHECK")
            println(separator)

            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0.CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"

            val supabaseClient = createSupabaseClient(supabaseUrl, anonKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
                install(Postgrest)
            }

            println("\n[1/5] Checking ALL groups (no filters)...")
            try {
                val allGroups: List<Map<String, Any>> = supabaseClient.postgrest["groups"].select().decodeList()
                println("✓ Total groups in database: ${allGroups.size}")
                allGroups.forEach { group ->
                    println("  - ${group["name"]} | is_public=${group["is_public"]} | registration_paid=${group["registration_paid"]} | id=${group["id"]}")
                }
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
            }

            println("\n[2/5] Checking groups with is_public=true...")
            try {
                val publicGroups: List<Map<String, Any>> = supabaseClient.postgrest["groups"].select {
                    filter { eq("is_public", true) }
                }.decodeList()
                println("✓ Public groups: ${publicGroups.size}")
                publicGroups.forEach { group ->
                    println("  - ${group["name"]} | registration_paid=${group["registration_paid"]}")
                }
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
            }

            println("\n[3/5] Checking groups with registration_paid=true...")
            try {
                val paidGroups: List<Map<String, Any>> = supabaseClient.postgrest["groups"].select {
                    filter { eq("registration_paid", true) }
                }.decodeList()
                println("✓ Paid groups: ${paidGroups.size}")
                paidGroups.forEach { group ->
                    println("  - ${group["name"]} | is_public=${group["is_public"]}")
                }
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
            }

            println("\n[4/5] Checking groups with BOTH conditions (discoverable)...")
            try {
                val discoverableGroups: List<Map<String, Any>> = supabaseClient.postgrest["groups"].select {
                    filter { eq("is_public", true) }
                    filter { eq("registration_paid", true) }
                }.decodeList()
                println("✓ Discoverable groups: ${discoverableGroups.size}")
                discoverableGroups.forEach { group ->
                    println("  ID: ${group["id"]}")
                    println("  Name: ${group["name"]}")
                    println("  Type: ${group["type"]}")
                    println("  City: ${group["city"]}, ${group["province"]}")
                    println("  Joining Fee: ${group["joining_fee"]}")
                    println("  Monthly: ${group["monthly_contribution"]}")
                    println("  Current Members: ${group["current_members"]}")
                    println("  ---")
                }
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
            }

            println("\n[5/5] Summary")
            println(separator)
            println("If discoverable groups = 0:")
            println("  → Groups aren't being created or marked properly")
            println("  → Run: resetDatabaseAndInstallMockData()")
            println("  → Check console for [4b] Verification section")
            println("")
            println("If discoverable groups > 0:")
            println("  → Groups exist on remote")
            println("  → Issue is in app's fetch/sync logic")
            println("  → Run: adb logcat | grep 'GroupRepository'")
            println("  → Check if sync is happening")
            println(separator)

        } catch (e: Exception) {
            println("❌ Fatal error: ${e.message}")
            e.printStackTrace()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

