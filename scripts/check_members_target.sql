SELECT m.full_name, m.status
FROM public.members m
JOIN public.groups g ON m.group_id = g.id
WHERE g.name = 'Settlement-Test-1784228142922';
