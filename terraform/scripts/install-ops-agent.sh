#!/bin/bash
# Installs the Cloud Ops Agent and forwards the systemd journal to Cloud Logging, so that
# application logs (unit familyexpenses-webapi) are visible in Logs Explorer
# and not only in journalctl on the VM.
#
# Appended to the VM startup script in main.tf, so it runs when a machine is created.
# Safe to re-run: both the installer and the config write are idempotent. Errors are tolerated so
# that a hiccup here never aborts the rest of the startup script.
set +e

# The startup script may have left the working directory elsewhere; the installer downloads a file.
cd /tmp

if ! systemctl is-active --quiet google-cloud-ops-agent; then
  curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
  bash add-google-cloud-ops-agent-repo.sh --also-install
fi

mkdir -p /etc/google-cloud-ops-agent
cat > /etc/google-cloud-ops-agent/config.yaml <<'OPSCFG'
logging:
  receivers:
    journald:
      type: systemd_journald
  service:
    pipelines:
      journald_pipeline:
        receivers: [journald]
OPSCFG

systemctl restart google-cloud-ops-agent
set -e
