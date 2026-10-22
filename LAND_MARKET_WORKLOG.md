# Land market integration worklog

Changes made in KingdomLandMapMenu.java:
- Corrected map/menu slot overlap: 7x4 map cells are limited to rows 0-3; control rows 4-5 are clickable.
- Added independent price selection for land sale and lease listing (25 to 10000 copper).
- Added face-to-face land purchase in completed town hall, seller within 8 blocks, using physical wallet transfer.
- Kept cash-at-bank land sale flow; no buyer bank-balance payment.

Validation: deterministic UI slot collision check passed (28 cells, 19 controls).
NOT VALIDATED: Forge compilation, in-game behavior, full transaction persistence under crash conditions.
The ZIP is a private development snapshot, not a release.
