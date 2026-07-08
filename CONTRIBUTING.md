# Contributing

Thanks for your interest in CareCopilot.

## Development

1. Use Java 17 and Maven 3.9 or newer.
2. Run tests before submitting changes:

```bash
mvn -q test
```

3. Keep medical safety behavior explicit and test-covered. Changes that affect triage, red-flag routing, report interpretation, or medication boundaries should include focused tests.
4. Do not commit generated build output, local environment files, private health data, credentials, or model checkpoints.

## Pull Request Checklist

- Explain the user-facing or infrastructure behavior changed.
- Include tests or explain why the change is documentation-only.
- Update README or deployment docs when API, configuration, or operations behavior changes.
- Confirm that no private medical information is included in examples, tests, or fixtures.
