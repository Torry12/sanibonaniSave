package com.sanibonani.save

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.data.local.MemberDao
import com.sanibonani.save.data.local.MemberEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.StorageRepository
import com.sanibonani.save.data.repository.MemberRepositoryImpl
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class MemberRepositoryTest {

    private lateinit var repository: MemberRepositoryImpl
    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>()
    private val db = mockk<SanibonaniDatabase>()
    private val memberDao = mockk<MemberDao>(relaxed = true)
    private val actuarialRepo = mockk<ActuarialRepository>()
    private val storageRepo = mockk<StorageRepository>()
    private val actuarialRepoProvider = Provider { actuarialRepo }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        every { db.memberDao() } returns memberDao
        repository = MemberRepositoryImpl(supabase, groupRepo, actuarialRepoProvider, db, storageRepo)
    }

    @Test
    fun `getGroupMembers emits cached data immediately`() = runTest {
        val groupId = "test_group"
        val cachedEntities = listOf(
            MemberEntity(
                id = "1", groupId = groupId, userId = "u1", memberKey = "k1",
                fullName = "John Doe", idNumber = "123", phone = "012", email = "a@b.com",
                street = "", suburb = "", city = "", province = "",
                status = com.sanibonani.save.domain.model.MemberStatus.ACTIVE,
                joinedAt = "", probationEndAt = "", profilePhotoUrl = null,
                document1Url = null, document1Type = null, 
                document1Status = com.sanibonani.save.domain.model.DocumentStatus.PENDING,
                document2Url = null, document2Type = null,
                document2Status = com.sanibonani.save.domain.model.DocumentStatus.PENDING,
                document3Url = null, document3Type = null,
                document3Status = com.sanibonani.save.domain.model.DocumentStatus.PENDING,
                document4Url = null, document4Type = null,
                document4Status = com.sanibonani.save.domain.model.DocumentStatus.PENDING,
                document5Url = null, document5Type = null,
                document5Status = com.sanibonani.save.domain.model.DocumentStatus.PENDING,
                beneficiaryCount = 0, beneficiaryOver65Count = 0,
                monthlyContributionOverride = null,
                totalContributions = 0, fcmToken = null, 
                notificationPref = com.sanibonani.save.domain.model.NotificationPref.BOTH,
                createdAt = ""
            )
        )

        every { memberDao.observeActiveMembers(groupId) } returns flowOf(cachedEntities)
        
        val result = repository.getGroupMembers(groupId).first()
        
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("John Doe", result.getOrThrow()[0].fullName)
    }
}
