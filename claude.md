# Project: SanibonaniSave

## Stack
- Kotlin + Jetpack Compose
- MVVM + Hilt (DI)
- Room (local DB)
- Supabase (backend)
- Coroutines + Flow

## Rules
- Never use LiveData, use Flow only
- All DI via Hilt — no manual instantiation
- ViewModels must not reference Android Context
- Repository pattern for all data access
- No business logic in Composables