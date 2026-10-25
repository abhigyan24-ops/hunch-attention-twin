# Development-only synthetic notifications

These rows are fictional training/evaluation examples, not telemetry events or live user measurements. Only this development dataset format has title/body fields; the event validator and relay continue to reject them. Never import this data into runtime notification logs, relay frames, or crash reports. Do not place real notification text or contact details in this directory, prompts, API requests or terminal output.

Offline, from the repository root:

```sh
node tools/dataset/generate_notifications.mjs --dry-run
node tools/dataset/validate-dataset.mjs tools/dataset/out/notifications.sample.jsonl
```

Dry run makes zero network calls, requires no key/model, and deterministically writes 20 hand-crafted rows (10 EN, 10 Hinglish; four per label; all seven sender types). It deduplicates by ID and normalized language/title/body content, validates, then writes only the curated JSONL. Conflicting duplicates fail. Console output contains provenance, counts and fixed error codes, never titles, bodies, credentials or provider response bodies.

Live mode reads OPENAI_API_KEY and OPENAI_MODEL, with --model taking precedence. Set credentials outside source control. No default model is guessed. Example invocation after setting those environment variables:

```sh
node tools/dataset/generate_notifications.mjs --count 2000
```

Live requests use only the fixed synthetic prompt and generation count, never external files/user logs. They use the Responses API with strict JSON-schema output and store=false; see [official OpenAI documentation](https://developers.openai.com/api/docs/guides/structured-outputs). The selected model must support that format. Output is notifications.synthetic.jsonl; every row must have synthetic=true. Structural validation and all-label/language/sender coverage apply before writing. IDs are assigned by the tool to avoid provider collisions. Duplicate content causing an underfilled dataset fails instead of silently reducing requested size or making extra paid requests.

The requested total is 20..2000, default 2000, in batches of at most 40. No live API call was made during Stage 1E verification; provider/model compatibility is not verified on target. Local mocked-response tests verify request construction, validation and error handling only.

Schema checks cannot establish label correctness. These balanced synthetic samples are not a population distribution, held-out real-data benchmark, calibrated urgency classifier or evidence of saved focus time. Review generated labels independently and use a temporal holdout for later real-data evaluation. The history/replay generator remains separate future Stage 4 work. Production modules must not import hidden persona ground truth.
