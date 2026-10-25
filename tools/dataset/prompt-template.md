# Synthetic notification generation

Create entirely fictional English (EN) and romanized Hindi-English (Hinglish) notification examples for development testing. No real person, account, contact, phone number, email address, URL, credential, actual OTP, user log or collected notification may be included. Use fictional package identifiers beginning with com.dhyan.synthetic.

Return a JSON object with a rows array following the provided strict schema. Each row has id, lang, appCat, pkg, senderType, label, hasReplyAction, title, body, synthetic=true. All content is invented; never describe it as observed user data.

Distribute examples across both languages and all five labels (urgent, important, normal, engagement_bait, promo) and all seven sender types (family, colleague, friend, app, unknown, bank_or_otp, delivery). Use natural language, not trivial prefixes naming the correct label. Include easy and ambiguous cases; labels remain proposed synthetic annotations for later human review. Never include an actual code for bank_or_otp examples.

Use distinct title/body pairs and unique IDs within each batch. Package categories must use the supplied canonical appCat enum. Do not add fields or fenced Markdown. Follow the requested count and batch index; do not reproduce previous examples. Do not claim accuracy, calibrated probabilities, measured outcomes or causal evidence.
