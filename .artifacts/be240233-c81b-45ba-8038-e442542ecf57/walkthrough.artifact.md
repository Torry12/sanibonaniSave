# Walkthrough - Parallel ROSCA Subgroups

I have refactored the ROSCA rotation logic to support parallel subgroups, ensuring that members in large groups don't have to wait an excessive amount of time for their payouts.

## Changes Made

### 1. Domain Logic Refinement
Updated the core ROSCA calculation engine to split members into subgroups.

#### [CalculateRoscaRotationUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/rosca/CalculateRoscaRotationUseCase.kt)
- **Automatic Splitting**: If a group's member count exceeds its target cycle length (now defaulting to 6 months), the system automatically creates parallel subgroups.
- **Dynamic Cycle Adjustment**: If a group has fewer members than the target cycle length, the cycle is automatically shortened to match the member count.
- **Subgroup Pots**: Each subgroup calculates its own pot total based on its specific number of members.

#### [ExecuteRoscaPayoutUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/rosca/ExecuteRoscaPayoutUseCase.kt)
- **Parallel Payout Processing**: The payout engine now identifies "Current" members across all subgroups and can process multiple payouts in the same month.

### 2. Registration Defaults
Updated the group creation flow to align with the new standard.

#### [GroupRegistrationViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/GroupRegistrationViewModel.kt)
- **Default Cycle**: Changed the default ROSCA cycle and period from 12 months to 6 months for a better user experience.

### 3. UI Enhancements
Improved how ROSCA schedules are presented to administrators.

#### [BusinessInsightsComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/BusinessInsightsComponents.kt)
- **Subgroup Visualization**: The "ROSCA Rotation Schedule" now displays clear headers for each subgroup, allowing admins to see parallel cycles at a glance.

## Verification Results

### Automated Tests
Updated `CalculateRoscaRotationUseCaseTest.kt` with a new test case:
- `invoke splits large group into subgroups based on periodMonths`: Verified that 14 members are correctly split into 6+6+2 subgroups with independent cycles and pots.

### Impact Analysis
> [!IMPORTANT]
> This change ensures that even if a ROSCA group grows to 50 members, the maximum wait time for any individual member remains capped at the group's target cycle length (default 6 months), significantly improving the perceived value and liquidity for members.
