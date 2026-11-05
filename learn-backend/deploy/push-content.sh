#!/usr/bin/env bash
# Copies the course material and the shared Amplitude logins from this machine to the server, outside the release
# folders, then restarts the course site so it re-reads (and re-validates) them. Nothing here goes through git.
#
#   LEARN_SSH=deploy@<server> ./push-content.sh <content dir> <secrets dir>
#
# <content dir> has lessons/, images/, stages.json, resources.json; <secrets dir> has one <resource id>.json per dataset.
# If the new content is invalid the app refuses to start and the deploy's health check says so: fix it and run again.
set -euo pipefail

: "${LEARN_SSH:?set LEARN_SSH to the ssh target, e.g. deploy@203.0.113.10}"
content="${1:?content dir}"
secrets="${2:?secrets dir}"
[ -f "$content/stages.json" ] && [ -d "$content/lessons" ] && [ -d "$content/images" ] || { echo "not a content dir: $content" >&2; exit 1; }

rsync -az --delete "$content"/ "$LEARN_SSH:/var/lib/pipeline-learn/content/"
rsync -az --delete --chmod=D700,F600 "$secrets"/ "$LEARN_SSH:/var/lib/pipeline-learn/secrets/"
ssh "$LEARN_SSH" 'sudo systemctl restart pipeline-learn'
echo "pushed; check: curl -s https://learn.shabash-hub.ru/api/health"
