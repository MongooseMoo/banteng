# Huh Toast authority audit

## Current task

- Read-only Phase 4 audit of the four `command_parsing.yaml` `huh` rows against the pinned Toast source used by Banteng.
- Determine fixture validity and exact semantics/order for `$server_options.player_huh`, `player:huh`, `location:huh`, arguments, permissions, and fallback output.
- Report exact source paths and lines plus the smallest semantic requirement.

## Current state

- `profiles/toast/stock-wsl-testdb.json` is the exact operational authority for
  these stock command-parsing rows. It pins source path
  `/root/src/toaststunt` at commit
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`, and the disposable `Test.db`
  fixture.
- Live WSL `git -C /root/src/toaststunt rev-parse HEAD` returned that exact
  pinned commit during this audit.
- The exact four rows are
  `location_huh_works_without_player_huh_option`,
  `huh_calls_player_verb_with_player_huh_enabled`,
  `huh_calls_location_verb_with_player_huh_disabled`, and
  `location_huh_disabled_with_player_huh_enabled` in
  `../moo-conformance-tests/src/moo_conformance/_tests/server/command_parsing.yaml:17-141`.
- The rows respectively observe location dispatch with the option absent,
  player dispatch with the option true, location dispatch with the option
  false, and player-only output with the option true.
- No Gradle command has been run.
- No commit has been created.

## `do_command_task` dispatch (`src/tasks.cc:811-875`)

- `parse_command(command, tq->player)` runs first (`820`); a null parsed
  command returns without any `huh` handling (`822-823`).
- Intrinsic commands run before ordinary or `huh` verbs (`825`). For a
  non-intrinsic input, Toast obtains the player's current location (`826-828`)
  and tokenizes the entire command with `parse_into_wordlist` (`837`).
- The handler's `do_command` task runs before normal dispatch (`838-843`). A
  non-done outcome or true result suppresses everything below it.
- Ordinary command lookup is short-circuit ordered player, location, direct
  object, indirect object (`844-847`). `huh` is reached only if none match.
- Last-chance lookup is exclusive: a valid location plus false
  `server_int_option("player_huh", PLAYER_HUH)` looks up callable
  `location:huh` (`848-851`); otherwise a valid player plus true option looks
  up callable `player:huh` (`852-855`). It never tries one `huh` and then the
  other.
- A found ordinary or `huh` verb is invoked by the same
  `do_input_task(tq->player, pc, _this, vh)` path (`856`). If no candidate is
  found, Toast notifies the player with exact fallback text
  `I couldn't understand that.` and clears the last input task id (`857-859`).
- Output suffix notification follows dispatch/fallback (`862-863`).

## `server_int_option` and compiled default

- `src/server.cc:529-542` implements `get_server_option`: for the requested
  object (here `SYSTEM_OBJECT`), it resolves its `server_options` property,
  requires that value to be a valid object, then looks up the named property
  on that object. The system object's `server_options` is also the fallback
  source.
- `src/server.cc:1419-1428` implements `server_int_option`: if the named
  property exists, only an integer value is honored; a missing property or a
  non-integer value returns the supplied compiled default.
- `src/include/options.h:382-392` defines the meaning of `PLAYER_HUH`: false is
  original LambdaMOO location-based last-chance handling; true changes it to
  the player's `huh` verb. This pinned configuration leaves the opt-in define
  commented out (`392`). `src/include/options.h:561-563` therefore defines
  `PLAYER_HUH` as `0`.
- Consequently, in the pinned Toast build, an absent
  `$server_options.player_huh` is exactly false/location dispatch. Integer `0`
  is also location dispatch; any nonzero integer is player dispatch. A
  non-integer property does not use MOO truth and instead falls back to compiled
  false.

## Blocker

- None currently known.

## Next action

- Verify the live WSL checkout identity, then trace the command-dispatch source
  one function at a time and record each finding before moving on.
