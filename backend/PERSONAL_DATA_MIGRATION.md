# Personal data encryption rollout

The application writes booking names, surnames, email addresses, and optional phone numbers as versioned AES-GCM ciphertext. Marketing subscriber names, email addresses, and surnames are encrypted as well. Both use a separate versioned HMAC-SHA-256 value for exact email lookup. Legacy plaintext columns remain temporarily so existing rows can be read while the backfill is pending.

## Before starting the application

- Provision independent 32-byte random keys for `PII_ENCRYPTION_KEY` and `PII_LOOKUP_KEY` through a secrets manager. The application expects Base64-encoded key bytes.
- Keep the same keys available to every application instance and preserve them with protected backups. Losing the encryption key makes encrypted booking data unreadable; losing the lookup key prevents email searches and duplicate detection for encrypted rows.
- Never copy production keys into the repository, test resources, logs, or ordinary configuration files. The committed values in `src/test/resources/application.properties` are test-only.

## Existing database rollout

1. Take and verify a database backup. Confirm that restoring it is possible before proceeding.
2. Schedule maintenance and stop application instances so bookings and marketing consent changes cannot happen while the one-time backfill runs.
3. Deploy the code with both keys configured and allow the schema update to add the ciphertext and lookup-hash columns and relax the legacy PII columns to nullable.
4. Run the application with `PII_MIGRATION_ENABLED=true` and bind its HTTP listener to loopback (`--server.address=127.0.0.1`). Do not route public traffic to this process. The runner processes at most 500 rows per transaction. Each batch stores all encrypted values and the email lookup hash before clearing that row's legacy plaintext fields. If a batch fails, its transaction rolls back.
5. Check the row counts without selecting personal values:

   ```sql
   SELECT COUNT(*)
   FROM booking
   WHERE email_lookup_hash IS NULL AND email IS NOT NULL;

   SELECT COUNT(*)
   FROM booking
   WHERE name IS NOT NULL OR surname IS NOT NULL OR email IS NOT NULL OR phone IS NOT NULL;

   SELECT COUNT(*)
   FROM marketing_subscriber
   WHERE email_lookup_hash IS NULL AND email IS NOT NULL;

   SELECT COUNT(*)
   FROM marketing_subscriber
   WHERE name IS NOT NULL OR surname IS NOT NULL OR email IS NOT NULL;
   ```

   All four counts should be zero before reopening traffic. Active booking and marketing records should have their ciphertext and lookup hashes populated; closed bookings can have their identifying fields and hashes removed.
6. Restart with `PII_MIGRATION_ENABLED=false` (or unset) and verify email search, duplicate-booking prevention, booking confirmation email, admin booking details, and check-in.
7. Retain the protected pre-migration backup only for the documented backup-retention period. It still contains plaintext personal data and must be access-restricted and deleted when that period ends.

Do not enable the migration while the service is handling live traffic. The migration runner is deliberately disabled by default. This rollout does not delete backups or old log files; those need separate retention decisions.

Marketing consent has a tokenized unsubscribe link in the booking confirmation email. The token is stored only as a SHA-256 hash, placed in the URL fragment (not the HTTP request), and a deliberate confirmation POST deletes matching marketing records. Expired marketing records are purged daily. The default retention is 24 months from consent and can be configured with `MARKETING_RETENTION_MONTHS`; match it with frontend `VITE_MARKETING_RETENTION_MONTHS`. Set `FRONTEND_BASE_URL` to the public HTTPS origin of the deployed frontend so the unsubscribe link reaches the app. The event owner must validate the period and publish it in the applicable notice before production use. Existing marketing rows receive expiry based on their stored consent timestamp during backfill.

The backfill and new indexes have been verified with H2 integration tests. They have not been run against PostgreSQL. Before production, rehearse the schema update and backfill in an isolated PostgreSQL staging database with a restored copy of production data; do not point test runs at the live application database. A PostgreSQL service is available locally, but no isolated test database credentials were provided; the read-only connectivity check did not complete, and no application database was queried or changed.
