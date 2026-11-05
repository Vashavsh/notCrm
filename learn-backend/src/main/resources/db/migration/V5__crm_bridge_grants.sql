-- What the CRM's database role may do with the bridge, granted here so it stays in step with the bridge itself.
-- The CRM may: read the progress view, insert and update accounts (issue access, reset a password, close it).
-- It may not read lessons' progress rows, attempts, answers, pictures or sessions directly, and may not delete accounts.
-- The role is named by the placeholder crm_role (LEARN_CRM_DB_ROLE, default pipeline_user); where the role does not
-- exist (a developer's laptop, the tests of this app) nothing is granted.

DO $bridge$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${crm_role}') THEN
        EXECUTE format('GRANT USAGE ON SCHEMA learn TO %I', '${crm_role}');
        EXECUTE format('GRANT SELECT, INSERT, UPDATE ON learn.account TO %I', '${crm_role}');
        EXECUTE format('GRANT SELECT ON learn.v_student_progress TO %I', '${crm_role}');
    END IF;
END
$bridge$;
