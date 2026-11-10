package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.local.ContributionDao
import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.PaymentDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import javax.inject.Provider

/**
 * A tier switch replaces the Room cache and closes the one it replaces, so a local data source is
 * only correct while it reads the cache that is live right now. These tests put a different
 * database under the provider between two calls and require the second call to answer from it.
 */
class RoomDataSourceLiveCacheTest {

    private val firstDao = mockk<GroupDao>(relaxed = true)
    private val rebuiltDao = mockk<GroupDao>(relaxed = true)
    private val firstDb = mockk<SanibonaniDatabase>()
    private val rebuiltDb = mockk<SanibonaniDatabase>()

    private var live: SanibonaniDatabase = firstDb

    @Test
    fun `a group read after the cache is replaced goes to the new database`() = runBlocking {
        every { firstDb.groupDao() } returns firstDao
        every { rebuiltDb.groupDao() } returns rebuiltDao
        // A relaxed mock answers a nullable read with a child mock, so "no such group" has to be
        // stated for both caches or the first read fails before the swap is reached.
        coEvery { firstDao.getGroupById("g1") } returns null
        coEvery { rebuiltDao.getGroupById("g1") } returns null

        val source = RoomGroupDataSourceImpl(Provider { live })

        assertNull(source.getGroupById("g1"))
        live = rebuiltDb
        assertNull(source.getGroupById("g1"))

        coVerify(exactly = 1) { firstDao.getGroupById("g1") }
        coVerify(exactly = 1) { rebuiltDao.getGroupById("g1") }
    }

    @Test
    fun `every dao a source uses follows the replaced cache`() = runBlocking {
        val firstContributions = mockk<ContributionDao>(relaxed = true)
        val firstPayments = mockk<PaymentDao>(relaxed = true)
        val rebuiltContributions = mockk<ContributionDao>(relaxed = true)
        val rebuiltPayments = mockk<PaymentDao>(relaxed = true)
        every { firstDb.contributionDao() } returns firstContributions
        every { firstDb.paymentDao() } returns firstPayments
        every { rebuiltDb.contributionDao() } returns rebuiltContributions
        every { rebuiltDb.paymentDao() } returns rebuiltPayments

        val source = RoomContributionDataSourceImpl(Provider { live })

        source.clearAll()
        live = rebuiltDb
        source.clearAll()

        coVerify(exactly = 1) { firstContributions.clearAll() }
        coVerify(exactly = 1) { firstPayments.clearAll() }
        coVerify(exactly = 1) { rebuiltContributions.clearAll() }
        coVerify(exactly = 1) { rebuiltPayments.clearAll() }
    }
}
