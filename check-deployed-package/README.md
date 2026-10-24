# Logdash Java SDK check

Checks the SDK as Maven Central serves it: installs it in a clean container, sends logs and metrics with it, and reads them back.
CI runs it after each release.

```
LOGDASH_API_KEY=<ingest-key> LOGDASH_PERSONAL_API_KEY=<personal-api-key> ./check-deployed-package/run.sh
```

`LOGDASH_PERSONAL_API_KEY` is an `ldp_` personal API key with `logs:read` and `metrics:read`, restricted to the project `LOGDASH_API_KEY` sends to.
`LOGDASH_VERSION` picks the version to check, the latest release by default.
`LOGDASH_API_URL` picks the API, `https://api.logdash.io` by default.
