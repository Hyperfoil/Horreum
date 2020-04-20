-- Make sure restricted user can't see our secret
GRANT select, insert, delete, update ON ALL TABLES IN SCHEMA public TO appuser;
REVOKE ALL ON dbsecret FROM appuser;
GRANT ALL ON ALL sequences IN SCHEMA public TO appuser;
