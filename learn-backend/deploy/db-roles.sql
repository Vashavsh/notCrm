-- One-time setup of the course site's database role, run on the server as the postgres superuser, in the CRM's database:
--
--   sudo -u postgres psql -d pipeline_crm -v ON_ERROR_STOP=1 -v learn_password='<a long random password>' -f db-roles.sql
--
-- Safe to run again (it also resets the password). What it sets up:
--   * the role learn_app, the only login the course site uses;
--   * the schema `learn`, owned by learn_app, so its migrations can create their tables there;
--   * search_path = learn for that role, so the site cannot even name a table of the CRM by accident.
-- The CRM's tables live in `public`, are owned by the CRM's role and are granted to nobody else, so learn_app
-- gets "permission denied" on all of them. What the CRM may touch in `learn` is granted by learn-backend's own
-- migration (V5), to the role named in LEARN_CRM_DB_ROLE (default pipeline_user).
-- Roles belong to the whole Postgres server, not to one database, so a pg_dump of pipeline_crm does not contain them:
-- when the server is rebuilt from a dump, run this script again before the first start of the course site.

SELECT 'CREATE ROLE learn_app LOGIN'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'learn_app')
\gexec

ALTER ROLE learn_app WITH LOGIN PASSWORD :'learn_password';

CREATE SCHEMA IF NOT EXISTS learn AUTHORIZATION learn_app;
ALTER ROLE learn_app SET search_path = learn;

REVOKE ALL ON SCHEMA public FROM learn_app;
