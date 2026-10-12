DO $$
DECLARE
    v_group_id UUID;
    v_admin_id UUID;
    v_payout_id UUID;
    v_initial_balance NUMERIC;
    v_success_count INT := 0;
    v_reject_count INT := 0;
    v_bug_count INT := 0;
    v_final_balance NUMERIC;
BEGIN
    RAISE NOTICE 'Starting Payout and Ledger Simulation...';

    -- Get a group with an admin
    SELECT id, admin_user_id INTO v_group_id, v_admin_id
    FROM public.groups LIMIT 1;
    
    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'No groups found for simulation.';
    END IF;

    -- Inject some artificial liquidity so we can test payouts
    PERFORM public.increment_group_balance(v_group_id, 10000.00, 'Test Liquidity Injection', 'adjustment');

    -- Get the balance
    SELECT balance INTO v_initial_balance FROM public.groups WHERE id = v_group_id;

    -- Scenario 1: Valid Payout Request
    BEGIN
        INSERT INTO public.payouts (
            id, group_id, amount, bank_name, account_no, branch_code, status, processed_by
        ) VALUES (
            gen_random_uuid(), v_group_id, 1000.00, 'Test Bank', '1234567890', '123456', 'pending', v_admin_id
        ) RETURNING id INTO v_payout_id;
        
        -- Admin processes the payout (calls increment_group_balance with negative)
        PERFORM public.increment_group_balance(v_group_id, -1000.00, 'Payout Processed', 'payout', v_payout_id::text);
        
        -- Update payout status
        UPDATE public.payouts SET status = 'completed', processed_at = NOW() WHERE id = v_payout_id;
        v_success_count := v_success_count + 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'BUG|Valid Payout Failed|%|%', SQLSTATE, SQLERRM;
        v_bug_count := v_bug_count + 1;
    END;

    -- Scenario 2: Overdraft Payout Request
    BEGIN
        INSERT INTO public.payouts (
            id, group_id, amount, bank_name, account_no, branch_code, status, processed_by
        ) VALUES (
            gen_random_uuid(), v_group_id, 50000.00, 'Test Bank', '1234567890', '123456', 'pending', v_admin_id
        ) RETURNING id INTO v_payout_id;
        
        -- Admin processes the payout (calls increment_group_balance with negative)
        PERFORM public.increment_group_balance(v_group_id, -50000.00, 'Overdraft Payout', 'payout', v_payout_id::text);
        
        -- Update payout status
        UPDATE public.payouts SET status = 'completed', processed_at = NOW() WHERE id = v_payout_id;
        
        -- Let's check the balance
        SELECT balance INTO v_final_balance FROM public.groups WHERE id = v_group_id;
        IF v_final_balance < 0 THEN
            RAISE NOTICE 'BUG|Overdraft Allowed|Balance went negative: %', v_final_balance;
            v_bug_count := v_bug_count + 1;
            
            -- Revert the damage for the sake of the database state
            PERFORM public.increment_group_balance(v_group_id, 50000.00, 'Revert Overdraft Payout', 'adjustment');
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'REJECT|Overdraft Blocked|%|%', SQLSTATE, SQLERRM;
        v_reject_count := v_reject_count + 1;
    END;

    RAISE NOTICE 'PAYOUT_SIM_DONE|Success:%|Reject:%|Bug:%', v_success_count, v_reject_count, v_bug_count;
END;
$$;
