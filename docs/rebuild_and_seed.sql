-- SanibonaniSave: Rebuild and Seed Database SQL
-- Drop tables if they exist (order matters for FKs)
DROP TABLE IF EXISTS behavior_analytics_summary;
DROP TABLE IF EXISTS fraud_detection_events;
DROP TABLE IF EXISTS member_behavior_track;
DROP TABLE IF EXISTS group_ledger;
DROP TABLE IF EXISTS burial_claims;
DROP TABLE IF EXISTS group_health_scores;
DROP TABLE IF EXISTS loan_repayments;
DROP TABLE IF EXISTS loans;
DROP TABLE IF EXISTS member_documents;
DROP TABLE IF EXISTS payouts;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS beneficiaries;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS contributions;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS groups;

-- Table: groups
CREATE TABLE groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    province TEXT NOT NULL,
    city TEXT NOT NULL,
    township TEXT NOT NULL,
    description TEXT NOT NULL,
    logo_emoji TEXT NOT NULL,
    joining_fee REAL NOT NULL,
    monthly_contribution REAL NOT NULL,
    late_fee REAL NOT NULL,
    late_fee_grace_days INTEGER NOT NULL,
    probation_months INTEGER NOT NULL,
    payment_due_day INTEGER NOT NULL,
    max_members INTEGER NOT NULL,
    current_members INTEGER NOT NULL,
    is_public INTEGER NOT NULL,
    allow_partial_payment INTEGER NOT NULL,
    auto_suspend_after INTEGER NOT NULL,
    bank_name TEXT,
    account_number TEXT,
    branch_code TEXT,
    account_type TEXT NOT NULL,
    gateway_public_key TEXT,
    balance REAL NOT NULL,
    admin_user_id TEXT,
    fee_status TEXT NOT NULL,
    registration_paid INTEGER NOT NULL,
    is_platform_suspended INTEGER NOT NULL,
    goal_amount REAL NOT NULL,
    period_months INTEGER NOT NULL,
    constitution_url TEXT,
    constitution_status TEXT NOT NULL,
    max_beneficiaries INTEGER NOT NULL,
    beneficiary_increase_pct REAL NOT NULL,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    latitude REAL,
    longitude REAL,
    geohash TEXT,
    rosca_rotation_method TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_groups_is_public ON groups(is_public);
CREATE INDEX idx_groups_admin_user_id ON groups(admin_user_id);
CREATE INDEX idx_groups_fee_status ON groups(fee_status);

-- Table: members
CREATE TABLE members (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    user_id TEXT,
    member_key TEXT,
    full_name TEXT NOT NULL,
    id_number TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT NOT NULL,
    street TEXT NOT NULL,
    suburb TEXT NOT NULL,
    city TEXT NOT NULL,
    province TEXT NOT NULL,
    status TEXT NOT NULL,
    joined_at TEXT NOT NULL,
    probation_end_at TEXT NOT NULL,
    profile_photo_url TEXT,
    document_1_url TEXT,
    document_1_type TEXT,
    document_1_status TEXT NOT NULL,
    document_2_url TEXT,
    document_2_type TEXT,
    document_2_status TEXT NOT NULL,
    document_3_url TEXT,
    document_3_type TEXT,
    document_3_status TEXT NOT NULL,
    document_4_url TEXT,
    document_4_type TEXT,
    document_4_status TEXT NOT NULL,
    document_5_url TEXT,
    document_5_type TEXT,
    document_5_status TEXT NOT NULL,
    beneficiary_count INTEGER,
    beneficiary_over_65_count INTEGER,
    monthly_contribution_override REAL,
    total_contributions INTEGER,
    total_paid REAL NOT NULL,
    fcm_token TEXT,
    notification_pref TEXT NOT NULL,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_members_group_id ON members(group_id);
CREATE INDEX idx_members_user_id ON members(user_id);
CREATE INDEX idx_members_status ON members(status);
CREATE UNIQUE INDEX idx_members_member_key ON members(member_key);

-- Table: contributions
CREATE TABLE contributions (
    id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    policy_id TEXT,
    amount REAL NOT NULL,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    due_date TEXT NOT NULL,
    paid_at TEXT,
    status TEXT NOT NULL,
    type TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    late_fees_applied INTEGER NOT NULL,
    transaction_id TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_contributions_member_id ON contributions(member_id);
CREATE INDEX idx_contributions_group_id ON contributions(group_id);
CREATE INDEX idx_contributions_status ON contributions(status);
CREATE INDEX idx_contributions_due_date ON contributions(due_date);

-- Table: payments
CREATE TABLE payments (
    id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    amount REAL NOT NULL,
    payment_type TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    transaction_id TEXT,
    status TEXT NOT NULL,
    processed_at TEXT,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_payments_member_id ON payments(member_id);
CREATE INDEX idx_payments_group_id ON payments(group_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_payment_type ON payments(payment_type);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- Table: beneficiaries
CREATE TABLE beneficiaries (
    id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    full_name TEXT NOT NULL,
    id_number TEXT,
    relationship TEXT,
    date_of_birth TEXT,
    is_over_65 INTEGER NOT NULL,
    document_url TEXT,
    face_photo_url TEXT,
    document_status TEXT NOT NULL,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (id, group_id, member_id)
);
CREATE INDEX idx_beneficiaries_group_id ON beneficiaries(group_id);
CREATE INDEX idx_beneficiaries_member_id ON beneficiaries(member_id);

-- Table: notifications
CREATE TABLE notifications (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    member_id TEXT,
    message TEXT NOT NULL,
    channel TEXT NOT NULL,
    trigger_event TEXT NOT NULL,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_notifications_group_id ON notifications(group_id);
CREATE INDEX idx_notifications_member_id ON notifications(member_id);
CREATE INDEX idx_notifications_trigger_event ON notifications(trigger_event);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- Table: member_documents
CREATE TABLE member_documents (
    id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    label TEXT NOT NULL,
    document_url TEXT NOT NULL,
    document_type TEXT,
    status TEXT NOT NULL,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_member_documents_member_id ON member_documents(member_id);
CREATE INDEX idx_member_documents_group_id ON member_documents(group_id);

-- Table: payouts
CREATE TABLE payouts (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    amount REAL NOT NULL,
    bank_name TEXT NOT NULL,
    account_no TEXT NOT NULL,
    branch_code TEXT NOT NULL,
    status TEXT NOT NULL,
    processed_by TEXT,
    processed_at TEXT,
    payout_reference TEXT,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_payouts_group_id ON payouts(group_id);
CREATE INDEX idx_payouts_status ON payouts(status);

-- Table: loans
CREATE TABLE loans (
    id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    amount REAL NOT NULL,
    interest_rate REAL NOT NULL,
    total_to_repay REAL NOT NULL,
    total_repaid REAL NOT NULL,
    monthly_repayment REAL NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    next_payment_date TEXT,
    status TEXT NOT NULL,
    purpose TEXT,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_loans_member_id ON loans(member_id);
CREATE INDEX idx_loans_group_id ON loans(group_id);
CREATE INDEX idx_loans_status ON loans(status);

-- Table: loan_repayments
CREATE TABLE loan_repayments (
    id TEXT PRIMARY KEY,
    loan_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    amount REAL NOT NULL,
    paid_at TEXT,
    payment_method TEXT NOT NULL,
    transaction_id TEXT,
    loan_interest_rate REAL,
    loan_max_amount REAL,
    loan_max_months INTEGER,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_loan_repayments_loan_id ON loan_repayments(loan_id);
CREATE INDEX idx_loan_repayments_member_id ON loan_repayments(member_id);
CREATE INDEX idx_loan_repayments_group_id ON loan_repayments(group_id);

-- Table: group_health_scores
CREATE TABLE group_health_scores (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    overall_score INTEGER NOT NULL,
    zone TEXT NOT NULL,
    components_json TEXT NOT NULL,
    recommendations_json TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    expires_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_group_health_scores_group_id ON group_health_scores(group_id);
CREATE INDEX idx_group_health_scores_generated_at ON group_health_scores(generated_at);

-- Table: burial_claims
CREATE TABLE burial_claims (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    beneficiary_id TEXT NOT NULL,
    beneficiary_name TEXT NOT NULL,
    face_photo_url TEXT,
    cause_of_death TEXT NOT NULL,
    date_of_death TEXT NOT NULL,
    claim_amount REAL NOT NULL,
    bank_name TEXT NOT NULL,
    account_no TEXT NOT NULL,
    branch_code TEXT NOT NULL,
    account_holder TEXT NOT NULL,
    notes TEXT,
    status TEXT NOT NULL,
    reviewed_by TEXT,
    reviewed_at TEXT,
    admin_notes TEXT,
    rejection_reason TEXT,
    created_at TEXT,
    updated_at INTEGER NOT NULL
);
CREATE INDEX idx_burial_claims_group_id ON burial_claims(group_id);
CREATE INDEX idx_burial_claims_member_id ON burial_claims(member_id);
CREATE INDEX idx_burial_claims_beneficiary_id ON burial_claims(beneficiary_id);
CREATE INDEX idx_burial_claims_status ON burial_claims(status);

-- Table: group_ledger
CREATE TABLE group_ledger (
    id TEXT PRIMARY KEY,
    groupId TEXT NOT NULL,
    transactionId TEXT,
    amount REAL NOT NULL,
    balanceAfter REAL NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL,
    createdAt TEXT,
    updatedAt INTEGER NOT NULL
);

-- Table: member_behavior_track
CREATE TABLE member_behavior_track (
    id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL,
    member_id_number TEXT NOT NULL,
    group_id TEXT NOT NULL,
    total_contributions INTEGER NOT NULL,
    on_time_contributions INTEGER NOT NULL,
    late_contributions INTEGER NOT NULL,
    overdue_count INTEGER NOT NULL,
    missed_contributions INTEGER NOT NULL,
    payment_consistency_score REAL NOT NULL,
    average_days_late REAL NOT NULL,
    current_payment_streak INTEGER NOT NULL,
    longest_payment_streak INTEGER NOT NULL,
    has_broken_streak_recently INTEGER NOT NULL,
    total_amount_contributed REAL NOT NULL,
    total_late_fees_paid REAL NOT NULL,
    pending_late_fees REAL NOT NULL,
    total_outstanding_amount REAL NOT NULL,
    total_loans_requested INTEGER NOT NULL,
    total_loans_approved INTEGER NOT NULL,
    total_loans_completed INTEGER NOT NULL,
    active_loans INTEGER NOT NULL,
    overdue_loans INTEGER NOT NULL,
    loan_default_count INTEGER NOT NULL,
    loan_completion_rate REAL NOT NULL,
    duplicate_transaction_count INTEGER NOT NULL,
    suspicious_activity_count INTEGER NOT NULL,
    unusual_payment_patterns INTEGER NOT NULL,
    multiple_accounts_detected INTEGER NOT NULL,
    velocity_check_failed INTEGER NOT NULL,
    rapid_disbursement_attempts INTEGER NOT NULL,
    member_status TEXT NOT NULL,
    fraud_risk_level TEXT NOT NULL,
    fraud_score REAL NOT NULL,
    behavior_score REAL NOT NULL,
    is_flagged_for_review INTEGER NOT NULL,
    is_suspended INTEGER NOT NULL,
    suspension_reason TEXT,
    review_notes TEXT,
    months_in_group INTEGER NOT NULL,
    joined_at TEXT,
    last_activity_at TEXT,
    last_contribution_at TEXT,
    admin_notes TEXT,
    last_reviewed_at TEXT,
    reviewed_by TEXT,
    created_at TEXT,
    updated_at TEXT
);
CREATE UNIQUE INDEX idx_member_behavior_track_member_id ON member_behavior_track(member_id);
CREATE INDEX idx_member_behavior_track_member_id_number ON member_behavior_track(member_id_number);
CREATE INDEX idx_member_behavior_track_group_id ON member_behavior_track(group_id);
CREATE INDEX idx_member_behavior_track_fraud_risk_level ON member_behavior_track(fraud_risk_level);
CREATE INDEX idx_member_behavior_track_is_flagged_for_review ON member_behavior_track(is_flagged_for_review);

-- Table: fraud_detection_events
CREATE TABLE fraud_detection_events (
    id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    details_json TEXT NOT NULL,
    action_taken TEXT,
    resolved INTEGER NOT NULL,
    created_at TEXT,
    updated_at TEXT
);
CREATE INDEX idx_fraud_detection_events_member_id ON fraud_detection_events(member_id);
CREATE INDEX idx_fraud_detection_events_group_id ON fraud_detection_events(group_id);
CREATE INDEX idx_fraud_detection_events_severity ON fraud_detection_events(severity);
CREATE INDEX idx_fraud_detection_events_resolved ON fraud_detection_events(resolved);

-- Table: behavior_analytics_summary
CREATE TABLE behavior_analytics_summary (
    group_id TEXT PRIMARY KEY,
    total_members_tracked INTEGER NOT NULL,
    excellent_members INTEGER NOT NULL,
    good_members INTEGER NOT NULL,
    fair_members INTEGER NOT NULL,
    poor_members INTEGER NOT NULL,
    suspended_members INTEGER NOT NULL,
    high_fraud_risk_count INTEGER NOT NULL,
    flagged_members_count INTEGER NOT NULL,
    average_behavior_score REAL NOT NULL,
    average_fraud_score REAL NOT NULL,
    on_time_payment_rate REAL NOT NULL,
    loan_default_rate REAL NOT NULL,
    calculated_at TEXT
);
CREATE UNIQUE INDEX idx_behavior_analytics_summary_group_id ON behavior_analytics_summary(group_id);

-- Example seed data (edit as needed)
INSERT INTO groups (id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution, late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, current_members, is_public, allow_partial_payment, auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance, admin_user_id, fee_status, registration_paid, is_platform_suspended, goal_amount, period_months, constitution_url, constitution_status, max_beneficiaries, beneficiary_increase_pct, loan_interest_rate, loan_max_amount, loan_max_months, created_at, latitude, longitude, geohash, rosca_rotation_method, updated_at) VALUES ('group1', 'Test Group', 'SAVINGS', 'Gauteng', 'Johannesburg', 'Soweto', 'A test group', '💰', 100.0, 200.0, 10.0, 5, 3, 1, 50, 10, 1, 1, 2, 'FNB', '123456789', '250655', 'Cheque', NULL, 1000.0, 'admin1', 'DUE', 1, 0, 5000.0, 12, NULL, 'PENDING', 5, 10.0, 0.0, 0.0, 12, '2026-05-20', -26.2041, 28.0473, NULL, 'FIXED', 1716182400000);
INSERT INTO members (id, group_id, user_id, member_key, full_name, id_number, phone, email, street, suburb, city, province, status, joined_at, probation_end_at, profile_photo_url, document_1_url, document_1_type, document_1_status, document_2_url, document_2_type, document_2_status, document_3_url, document_3_type, document_3_status, document_4_url, document_4_type, document_4_status, document_5_url, document_5_type, document_5_status, beneficiary_count, beneficiary_over_65_count, monthly_contribution_override, total_contributions, total_paid, fcm_token, notification_pref, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at) VALUES ('member1', 'group1', 'user1', 'key1', 'John Doe', '8001015009087', '0712345678', 'john@example.com', '123 Main St', 'Orlando', 'Johannesburg', 'Gauteng', 'ACTIVE', '2026-01-01', '2026-04-01', NULL, NULL, NULL, 'PENDING', NULL, NULL, 'PENDING', NULL, NULL, 'PENDING', NULL, NULL, 'PENDING', NULL, NULL, 'PENDING', 2, 0, NULL, 12, 2400.0, NULL, 'BOTH', 0.0, 0.0, 12, '2026-01-01', 1716182400000);
INSERT INTO contributions (id, member_id, group_id, policy_id, amount, loan_interest_rate, loan_max_amount, loan_max_months, created_at, due_date, paid_at, status, type, payment_method, late_fees_applied, transaction_id, updated_at) VALUES ('contrib1', 'member1', 'group1', NULL, 200.0, 0.0, 0.0, 12, '2026-01-01', '2026-01-31', '2026-01-31', 'PAID', 'contribution', 'bank', 0, NULL, 1716182400000);

