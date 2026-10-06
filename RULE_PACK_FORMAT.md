# YouTooBee rule-pack format — schema 1

A rule pack is declarative JSON. It cannot inject JavaScript.

```json
{
  "schemaVersion": 1,
  "ruleVersion": 301,
  "name": "YouTooBee Core 2026.10.1",
  "network": {
    "blockedHosts": ["example-ad-host.invalid"],
    "blockedPathFragments": ["/ad_endpoint/"],
    "trackerFragments": ["/telemetry/"]
  },
  "dom": {
    "adSelectors": [".ad-container"],
    "skipSelectors": ["button.skip-ad"],
    "annoyances": {
      "shorts": [".shorts-shelf"],
      "recommendations": ["#related"],
      "comments": ["#comments"],
      "endScreen": [".end-card"],
      "openInApp": [".open-in-app"]
    }
  }
}
```

## Constraints

- `schemaVersion` must currently equal `1`.
- `ruleVersion` must be a positive integer and must be greater than the active version for an update install.
- Maximum downloaded pack size: 256 KiB.
- Maximum entries per list: 256.
- Maximum length per rule: 320 characters.
- Update transport must be HTTPS.

## Safety model

Remote rule packs can change which requests are blocked and which page elements are selected/hidden or treated as Skip buttons. They cannot replace `AdBlockScript.kt` or execute arbitrary code. A previous pack is retained for rollback.
