# Security

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's private vulnerability reporting on
this repository (Security → Report a vulnerability), which reaches the maintainer
without disclosing the problem first.

Useful to include: what you did, what happened, and which build flavour and
version you were on. A proof of concept helps and is welcome.

This is a personal project, not a funded one. Expect an acknowledgement within a
week, and please allow reasonable time for a fix before disclosing publicly.

## What matters most here

RepForth has no server, so the usual backend surface does not exist. The
sensitive material is **on the device**, and it is of two kinds.

### API keys (Phase 2, not yet built)

The coach calls a provider with a key the user supplies. The requirement, which
has tests attached rather than being an aspiration, is that key material never
appears in:

Room · ordinary DataStore · logs · exported data · Android backups · CI output ·
test reports · screenshots · any message sent to the watch.

Keys are encrypted with the Android Keystore. **If you find a path where a key
becomes readable — including in a stack trace or a bug report — that is a real
vulnerability and worth reporting even if it looks minor.**

### Workout history

The only copy is on the device. There is no backup service to restore from, so a
bug that corrupts or deletes it is a data-loss bug, not a cosmetic one. Room is
configured to refuse a schema it does not recognise rather than migrate
destructively.

## What is not a vulnerability

- **The app sends your request to an AI provider you configured.** That is the
  documented design of bring-your-own-key, stated in `PRIVACY.md`. The provider's
  handling of it is governed by their terms.
- **Media downloads in the `licensed` flavour.** Fetched from a pinned immutable
  commit and verified against a SHA-256 in the manifest before caching.
- **The absence of a licence file.** That is an open project decision, not a
  security issue.

## Scope

This repository's source and the app built from it. The upstream exercise dataset
and Gym visual's media are third-party — see `NOTICE.md`.
