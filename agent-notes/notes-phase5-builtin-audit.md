# Phase 5 builtin audit checkpoint

- Findings: the corrected complete missing `src/numbers.cc` family is 29 names; `anon` is absent and forbidden.
- Observations: exact WSL verification returned stock `aecc51e9449c6e7c95272f0f044b5ba38948459e` and mongoose `72e3c7f96ce7a41fdeba793aef8818dc4408072e`. Both registrations and implementations agree for this family. Matching generated rows and custom `builtins/math.yaml` plus `floatstr_call_shapes.yaml` are in the sibling conformance checkout.
- State: the two authorized Java paths remain clean. Contracts, domain/overflow error mappings, random/time behavior, formatting, distance, and relative-heading semantics have been inspected; patching has not begun.
- Blocker: none.
- Next action: patch only the catalog and existing catalog test, then run the exact focused Java test and relevant numeric YAML paths in WSL.
