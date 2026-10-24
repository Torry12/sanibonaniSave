package com.sanibonani.save.data.remote

/**
 * Explicit PostgREST column projections aligned with [supabase/rebuild_kit_v4/01_TABLES_AND_INDEXES.sql].
 * Keep in sync with domain models and brownfield migration
 * [supabase/migrations/20260529120000_align_members_app_columns.sql].
 */
object PostgrestColumns {
    const val GROUPS_SAFE =
        "id,name,type,province,city,township,description,logo_emoji,joining_fee,monthly_contribution," +
            "late_fee,late_fee_grace_days,probation_months,payment_due_day,max_members,current_members," +
            "is_public,allow_partial_payment,auto_suspend_after,bank_name,account_number,branch_code," +
            "account_type,balance,admin_user_id,fee_status,registration_paid,latitude," +
            "longitude,geohash,created_at,is_platform_suspended,goal_amount,period_months,max_beneficiaries," +
            "beneficiary_increase_pct,constitution_url,constitution_status,rosca_rotation_method," +
            "loan_interest_rate,loan_max_amount,loan_max_months"

    const val MEMBERS_SAFE =
        "id,group_id,user_id,full_name,id_number,phone,email,street,suburb,city,province,notification_pref," +
            "status,joined_at,probation_end_at,profile_photo_url,document_1_url,document_1_type,document_1_status," +
            "document_2_url,document_2_type,document_2_status,document_3_url,document_3_type,document_3_status," +
            "document_4_url,document_4_type,document_4_status,document_5_url,document_5_type,document_5_status," +
            "fcm_token,member_key,total_contributions,total_paid,beneficiary_count,beneficiary_over_65_count," +
            "monthly_contribution_override,created_at"

    /** Fallback when legacy DB is missing extended member columns. */
    const val MEMBERS_MINIMAL =
        "id,group_id,user_id,full_name,id_number,phone,email,notification_pref,status,joined_at," +
            "probation_end_at,fcm_token,member_key,total_contributions,total_paid,beneficiary_count," +
            "beneficiary_over_65_count,monthly_contribution_override,created_at"
}
