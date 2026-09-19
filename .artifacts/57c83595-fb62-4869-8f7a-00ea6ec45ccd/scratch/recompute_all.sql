DO $$
DECLARE
    v_g RECORD;
BEGIN
    FOR v_g IN SELECT id, name FROM public.groups LOOP
        RAISE NOTICE 'Recalculating Group: %', v_g.name;
        PERFORM public.recalculate_all_group_financials_v1(v_g.id);
    END LOOP;
END $$;
