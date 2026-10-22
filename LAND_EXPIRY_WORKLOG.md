# Land lease maintenance (unreleased)

- The server now expires lease contracts every 1200 server ticks (about 60 seconds at 20 TPS), even if nobody opens the land map.
- Expiration clears tenant and worker permissions and assesses debt only once through the existing lease debt ledger.
- No automatic wallet or bank deduction is performed.
- Missing plot records are cleaned from the active lease registry.

Validation: `python 03_verify_project.py` passed. This is static validation, not a Forge compile or in-game test.
