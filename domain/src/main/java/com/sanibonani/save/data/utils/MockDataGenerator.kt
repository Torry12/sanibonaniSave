package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.PaymentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockDataGenerator @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val paymentRepo: PaymentRepository
) {
    suspend fun seedDatabase() {
        // 1. Create a Mock Group
        val groupId = UUID.randomUUID().toString()
        val mockGroup = Group(
            id = groupId,
            name = "Rainy Day Burial Society",
            type = GroupType.BURIAL_SOCIETY,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            description = "A community savings group for funeral expenses.",
            joiningFee = 200.0,
            monthlyContribution = 300.0,
            currentMembers = 0,
            balance = 0.0,
            feeStatus = AdminFeeState.PAID
        )
        
        groupRepo.createGroup(mockGroup).onSuccess {
            // Group seeded successfully
        }
    }
}
