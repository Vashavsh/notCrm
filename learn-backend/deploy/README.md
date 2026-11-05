# Putting the course site on a server

The course site runs next to the CRM on the same machine and the same Postgres, but as its own program: own jar, own
systemd service (`pipeline-learn`, port 8081), own nginx site (`learn.shabash-hub.ru`), own deploy workflow. A deploy of
one never restarts the other. Nothing here has been run on a real server yet (the old one is gone); the pieces were
tested separately: the database script and the grants by `DbRolesScriptTest` / `BridgePermissionsIntegrationTest`, the
nginx file with `nginx -t`, the course against a real Postgres 18 in the tests.

```
browser ── https ──> nginx (learn.shabash-hub.ru)
                        ├─ /                 static files  /opt/pipeline-learn/frontend/current
                        ├─ /api/, /assets/course/ ──> 127.0.0.1:8081  learn-backend (systemd: pipeline-learn)
                                                          └─ Postgres, role learn_app, schema `learn`
CRM (crm.shabash-hub.ru, its own service) ── role pipeline_user ──> the same Postgres: schema `public`
                                              + the bridge in `learn` (account, v_student_progress), nothing else
```

What lives where on the server:

| Path | What | Written by |
|---|---|---|
| `/opt/pipeline-learn/releases/`, `current.jar` | jars, newest linked as `current.jar` | deploy workflow |
| `/opt/pipeline-learn/frontend/releases/`, `current` | built front end | deploy workflow |
| `/var/lib/pipeline-learn/content/` | lessons, images, `stages.json`, `resources.json` | `push-content.sh` (from your Mac) |
| `/var/lib/pipeline-learn/secrets/` | the shared Amplitude logins, one file per dataset (mode 600) | `push-content.sh` |
| `/etc/pipeline-learn.env` | settings, incl. the database password | by hand once |

## One-time setup (as root on the server)

Memory: the course site needs about 400 to 500 MB of its own (the service is capped at 640 MB), on top of Postgres, the
CRM and nginx. A server with less than 4 GB will be tight.

1. **DNS.** An `A` (and `AAAA`) record `learn.shabash-hub.ru` to the server's address.
2. **Packages.** Java 21 (`openjdk-21-jre-headless`), nginx, certbot (`python3-certbot-nginx`), rsync. PostgreSQL 18 is already there for the CRM.
3. **System user and folders.**
   ```bash
   useradd --system --home /var/lib/pipeline-learn --shell /usr/sbin/nologin pipeline-learn
   install -d -o deploy -g deploy /opt/pipeline-learn /opt/pipeline-learn/releases /opt/pipeline-learn/frontend /opt/pipeline-learn/frontend/releases
   install -d -m 750 -o deploy -g pipeline-learn /var/lib/pipeline-learn /var/lib/pipeline-learn/content /var/lib/pipeline-learn/secrets
   ```
   (`deploy` is the login the CRM's workflow already uses; `push-content.sh` and the workflow write as that user.)
4. **Database role and schema** (run from the repo checkout or copy `db-roles.sql`):
   ```bash
   sudo -u postgres psql -d pipeline_crm -v ON_ERROR_STOP=1 -v learn_password='<long random>' -f db-roles.sql
   ```
   Put the same password in `/etc/pipeline-learn.env`. If the CRM does not log in as `pipeline_user`, set `LEARN_CRM_DB_ROLE` to its role there.
5. **Settings.** `cp learn.env.example /etc/pipeline-learn.env`, fill it in, `chown root:pipeline-learn`, `chmod 640`.
6. **Service.** `cp pipeline-learn.service /etc/systemd/system/ && systemctl daemon-reload && systemctl enable pipeline-learn`.
   Let `deploy` restart it: `deploy ALL=(root) NOPASSWD: /bin/systemctl restart pipeline-learn` in `/etc/sudoers.d/pipeline-learn`.
7. **nginx.** `cp nginx-learn.conf /etc/nginx/sites-available/learn.shabash-hub.ru`, `cp nginx-learn-proxy.conf /etc/nginx/snippets/learn-proxy.conf`.
   Get the certificate first (`certbot certonly --nginx -d learn.shabash-hub.ru`, which needs only the port-80 block and the DNS record), then
   `ln -s ../sites-available/learn.shabash-hub.ru /etc/nginx/sites-enabled/ && nginx -t && systemctl reload nginx`.
8. **Content and logins.** From your Mac: `LEARN_SSH=deploy@<server> ./push-content.sh <content dir> <secrets dir>`
   (the real course lives in `notCrm-local-data/learn-content`, the logins are written to `.../learn-secrets` by `scripts/import-amplitude.py`).
   Do this before the first start: the app refuses to start without valid content.

## Switching the deploy workflow on

In the GitHub repository settings add the **variables** `LEARN_DEPLOY_ENABLED=true`, `LEARN_SSH_HOST`, `LEARN_SSH_USER` (`deploy`) and
`LEARN_SSH_HOST_KEY` (the line `ssh-keyscan -t ed25519 <host>` prints). The secret `SSH_PRIVATE_KEY` is the one the CRM's workflow already uses.
Then run "Deploy course site" by hand once (Actions, Run workflow). After that every merge to `main` that touches `learn-backend/` or
`learn-frontend/` deploys the course site only, and every merge that touches only the CRM deploys the CRM only.

## After a deploy

```bash
curl -s https://learn.shabash-hub.ru/api/health        # {"status":"ok"}
journalctl -u pipeline-learn -n 50                       # "Started LearnApplication", and the content check if it refused
sudo -u postgres psql -d pipeline_crm -c "select count(*) from learn.lesson_catalog"   # 38
```
Then in the CRM open a student, tab "Учебный сайт", issue access and log in with it.

## Rolling back, and what it does not undo

The workflow swaps `current.jar` and the front end back to the previous release if the new one does not become healthy. It does **not**
undo database migrations: the old program has to live with the newer schema. So migrations only ever add things (tables, columns, views),
never rename or drop, and the bridge (`account`, `v_student_progress`) is only extended. The same rule protects the CRM, which reads the bridge.

## Changing the course material or the Amplitude logins

Edit the files on your Mac, run `scripts/import-amplitude.py` if the logins changed, then `push-content.sh` again. The service restarts
and checks everything; if something is wrong it stays down with the reason in the journal, and the previous files can be pushed again.
Lesson and question ids must never change (progress is stored against them).

## Backups and rebuilding a server

* The nightly `pg_dump` of `pipeline_crm` contains the schema `learn` too: accounts, progress, answers, pictures, sessions.
* It does **not** contain database roles. On a rebuilt server restore the dump, then run `db-roles.sql` again (it recreates `learn_app`
  and takes the schema over), then start the course site; V5 re-grants the CRM its rights on the bridge.
* It does not contain `/var/lib/pipeline-learn` either: keep the content and logins on your Mac (and the logins in your password manager) and push them again.
