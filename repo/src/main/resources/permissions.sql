-- Make sure restricted user can't see our secret
GRANT select, insert, delete, update ON ALL TABLES IN SCHEMA public TO repo_restricted;
REVOKE ALL ON dbsecret FROM repo_restricted;
GRANT ALL ON ALL sequences IN SCHEMA public TO repo_restricted;
