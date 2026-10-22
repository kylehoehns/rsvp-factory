#!/usr/bin/env bash
# One-time setup for the local factory: store its two credentials in the macOS keychain, never in files.
#   1. a Claude subscription token, from `claude setup-token` (the sandbox can't use your Mac's login)
#   2. the factory GitHub App's private key (.pem), so night-shift.ts can open PRs as the app
set -euo pipefail

echo "1/2  Claude token: run 'claude setup-token' in another terminal, then paste the token when asked (twice)."
security add-generic-password -U -a "$USER" -s rsvp-factory-claude-token -w

read -rp "2/2  Path to the factory app's private key (.pem): " pem
pem=${pem/#\~/$HOME}
security add-generic-password -U -a "$USER" -s rsvp-factory-app-key -w "$(base64 <"$pem" | tr -d '\n')"

echo "Stored both in the login keychain. Delete $pem now; GitHub can generate a new key if you ever need one."
