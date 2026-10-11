# Phase 4 Toast user_connected authority

## Verified target

- The pinned Toast checkout is clean at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The exact row creates an existing player, replaces `#0:do_login_command` to return it, replaces `#0:user_connected` to record call locals, opens a new connection, sends one login line, and expects `{this, player, caller, args, argstr}` to equal `{#0, login_player, #-1, {login_player}, ""}`.

## Accepted findings

- New connections begin under a fresh negative connection object with listener handler #0 for the stock row.
- The login input runs `do_login_command`; when it returns the pre-existing player object, the task queue changes from the negative connection ID to that player and any queue ownership is reconciled.
- The login task's current task state is explicitly cleaned before `player_connected` starts the notifier task.
- With no existing connection for that player and because the player predates this login task, Toast sets the server handle player and connection time, emits the ordinary connected message when enabled, then runs `user_connected` on the listener handler.
- `user_connected` is a new server task, not a fork. Its receiver/this is the handler, player is the logged-in player, caller is #-1, args is `{player}`, and argstr is empty.

## State and next action

- Read-only authority audit; Barn was not inspected and no production files were edited.
- Report exact YAML and pinned Toast source line authorities to the parent agent.
