package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.Member
import java.time.LocalDate
import javax.inject.Inject

class CalculateRoscaRotationUseCase @Inject constructor() {
    operator fun invoke(members: List<Member>): List<Member> {
        return members.sortedBy { it.joinedAt ?: it.createdAt }
    }
}
