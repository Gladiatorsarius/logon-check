# Bug fixes in 1.0.1

## Activity was skipped in other dimensions
- Bug: only players in the first server level had periodic session validation.
- Cause: validation stopped after the first `level.players()` list.
- Reproduction: spend the qualifying session in the Nether or End without returning to the first level.
- Fix: validate the server-wide player list using the shared game rule, with one persistence flush per sweep.
- Tested: compilation against Minecraft 1.21.11; batch activity unit test. Dimension travel still needs an in-game test.

## Long online sessions became inactive
- Bug: a player could stay online, disconnect and immediately be banned for inactivity.
- Cause: once a session counted, its activity timestamp never advanced again.
- Reproduction: qualify a session, stay online longer than the inactivity threshold, then reconnect.
- Fix: refresh already-qualified sessions at each periodic check and on disconnect.
- Tested: deterministic-clock test advances an online session past a one-hour threshold and confirms it remains active.

## Disconnect could lose a qualified session
- Bug: reaching the minimum between periodic checks and disconnecting did not record activity.
- Cause: disconnect removed the session and only logged its duration.
- Reproduction: reach 30 minutes just after the last check, then leave before the next check.
- Fix: validate and persist before removing session state.
- Tested: 31-minute session counts on disconnect; 29-minute session does not.

## Session state survived reinitialization
- Bug: a reused manager retained another server lifecycle's timestamps and sessions.
- Cause: initialize loaded data without clearing existing maps.
- Reproduction: initialize in one directory, record activity, then initialize in a new empty directory.
- Fix: reset all activity/session maps and pending-save state before loading.
- Tested: fresh initialization has neither old activity nor an active session.

## Persistence repeatedly rewrote and truncated the live history
- Bug: each qualifying player rewrote the entire history on the server thread, directly truncating the destination.
- Cause: save ran inside each player's validation, with a copied map and FileWriter on the live file.
- Reproduction: multiple players qualify during the same validation sweep; interrupt a direct write to expose the old integrity risk.
- Fix: one save per sweep, serialize the map directly to a temporary sibling file, then replace the destination atomically where supported. Failed saves remain pending for retry; temporary files are cleaned up. A non-atomic replacement fallback is used where the filesystem does not support atomic moves.
- Tested: multiple player records survive reload; a blocked replacement keeps the original backup intact, retries after the obstruction is removed, and leaves no temporary file. No real power-loss test or populated-server benchmark.

## Game rule setup and session logs were misleading
- Bug: docs named gamerules that are not registered; claimed numeric ranges were not applied; duration logs printed literal formatting text and shifted arguments.
- Cause: old camelCase IDs, omitted range builders, and Python-style placeholders in SLF4J messages.
- Reproduction: follow the former README commands or inspect a qualifying session log.
- Fix: docs/metadata use registered namespaced IDs, integer builders enforce the documented ranges, and duration arguments are formatted before passing to SLF4J `{}` placeholders.
- Tested: compile and unit-test build pass; log output checked during tests. In-game command completion and boundary rejection remain manual checks.

## Manual checks still required
- Load the built mod on a Fabric 1.21.11 server and confirm all three mixins apply.
- Test qualification and disconnect in Overworld, Nether and End.
- Test threshold-based login enforcement on a disposable test player, including ban/pardon, without risking real player data.
- Confirm namespaced gamerules and their range boundaries in-game.

## Reported but intentionally not changed: corrupt history
- Malformed JSON or a null timestamp can still stop startup, as on main.
- Recovery policy remains undecided: no automatic reset, backup-and-fresh-start, or new startup policy is included in this branch. Repairing/recovering damaged activity history needs an explicit operator decision.
- Temporary-file replacement reduces the chance of new partial writes; it cannot reconstruct an already damaged file.

## Compatibility note
- Integer game rules now enforce their documented ranges (inactivity hours 1-8760, minimum session minutes 1-1440). Servers with previously out-of-range saved values should correct those settings before upgrading; world-load behavior for such existing values has not been tested in-game.
