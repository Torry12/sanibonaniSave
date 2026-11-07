-- Balance-vs-ledger probe. Reads nothing but public.groups, public.group_ledger,
-- public.contributions and public.members, and writes nothing.
--
-- Run on either tier (Local with psql, Cloud through the Dashboard SQL editor) when a
-- screen reports a balance that does not match the money people believe the group holds.
-- The three columns answer different questions and comparing them is the whole point:
--   balance_column        what the cached NUMERIC(12,2) column says, which is what the
--                         Admin Overview renders - the ledger is not down-synced to the
--                         device, so the column is the UI's only source.
--   ledger_sum/ledger_rows   the authoritative money trail, since balance is ledger-derived.
--   contributions_sum     principal recorded per member, which can be non-zero while the
--                         ledger is empty when rows were inserted outside record_contribution_v2.
--
-- Reading P.1:
--   ledger_sum <> balance_column      the column drifted; audit_and_fix_group_balance_v1
--                                     with p_fix => true recomputes it from the ledger.
--   ledger_rows = 0 AND contributions_sum > 0 AND balance_column = 0
--                                     the money was posted without ever reaching the ledger,
--                                     so a zero balance is arithmetically correct. That is a
--                                     seeding defect, not a sync or a rendering defect.
--   everything agrees and is non-zero the tier is fine and the complaint is client-side;
--                                     check which backend the handset is on and whether its
--                                     Room cache has refreshed since.
WITH ls AS (
    SELECT group_id, SUM(amount) AS s, COUNT(*) AS rows
      FROM public.group_ledger
     GROUP BY group_id
), cs AS (
    -- Group-side principal only. internal_post_contribution (02_FUNCTIONS_AND_TRIGGERS.sql:238)
    -- routes registration_contribution and member_fee to platform_ledger and never touches the
    -- group ledger, so counting them here invents a shortfall the tier does not have.
    SELECT group_id, SUM(amount) AS s
      FROM public.contributions
     WHERE status = 'paid'
       AND COALESCE(type, 'contribution') NOT IN ('registration_contribution', 'member_fee', 'member_fee_ledger')
     GROUP BY group_id
), ms AS (
    SELECT group_id, COUNT(*) AS members
      FROM public.members
     WHERE status = 'active'
     GROUP BY group_id
)
\echo '=== P.1 every group: balance column vs ledger vs contributions ==='
SELECT g.name,
       g.balance                                        AS balance_column,
       COALESCE(ls.s, 0)::numeric(12,2)                AS ledger_sum,
       COALESCE(ls.rows, 0)                            AS ledger_rows,
       COALESCE(cs.s, 0)::numeric(12,2)                AS contributions_sum,
       COALESCE(ms.members, 0)                         AS active_members,
       CASE
           WHEN COALESCE(ls.rows, 0) = 0 AND COALESCE(cs.s, 0) > 0
                THEN 'MONEY OUTSIDE LEDGER'
           WHEN COALESCE(ls.s, 0) <> g.balance
                THEN 'COLUMN DRIFTS'
           WHEN g.balance = 0 AND COALESCE(cs.s, 0) > 0
                THEN 'ZERO COLUMN, CONTRIBS PRESENT'
           ELSE 'consistent'
       END                                             AS verdict
  FROM public.groups g
  LEFT JOIN ls ON ls.group_id = g.id
  LEFT JOIN cs ON cs.group_id = g.id
  LEFT JOIN ms ON ms.group_id = g.id
 ORDER BY verdict DESC, g.name
  LIMIT 60;

\echo '=== P.2 platform-wide drift counts ==='
SELECT COUNT(*)                                                        AS groups_total,
       COUNT(*) FILTER (WHERE COALESCE(ls.s, 0) <> g.balance)          AS balance_drifts,
       COUNT(*) FILTER (WHERE g.balance = 0 AND COALESCE(ls.s, 0) <> 0) AS zero_column_with_ledger_money,
       COUNT(*) FILTER (WHERE g.balance = 0 AND COALESCE(cs.s, 0) > 0) AS zero_column_with_contribs,
       COUNT(*) FILTER (WHERE COALESCE(ls.rows, 0) = 0)                AS no_ledger_rows
  FROM public.groups g
  LEFT JOIN (SELECT group_id, SUM(amount) AS s, COUNT(*) AS rows
               FROM public.group_ledger GROUP BY group_id) ls ON ls.group_id = g.id
  LEFT JOIN (SELECT group_id, SUM(amount) AS s
               FROM public.contributions WHERE status = 'paid' AND COALESCE(type, 'contribution') NOT IN ('registration_contribution', 'member_fee', 'member_fee_ledger') GROUP BY group_id) cs ON cs.group_id = g.id;

\echo '=== P.3 the groups seen on the device ==='
SELECT g.name,
       g.balance                          AS balance_column,
       COALESCE(ls.s, 0)::numeric(12,2)   AS ledger_sum,
       COALESCE(ls.rows, 0)               AS ledger_rows,
       COALESCE(cs.s, 0)::numeric(12,2)   AS contributions_sum,
       g.updated_at
  FROM public.groups g
  LEFT JOIN (SELECT group_id, SUM(amount) AS s, COUNT(*) AS rows
               FROM public.group_ledger GROUP BY group_id) ls ON ls.group_id = g.id
  LEFT JOIN (SELECT group_id, SUM(amount) AS s
               FROM public.contributions WHERE status = 'paid' AND COALESCE(type, 'contribution') NOT IN ('registration_contribution', 'member_fee', 'member_fee_ledger') GROUP BY group_id) cs ON cs.group_id = g.id
 WHERE g.name LIKE 'Sim-%' OR g.name IN ('Sim-Investment-7252', 'Sim-ROSCA-4478')
 ORDER BY g.name;
