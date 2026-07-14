# First server value authority

## Scope

This record freezes only the value behavior required by the first production
server path and already exposed by durable conformance rows. It does not
authorize a Java `NoneValue` or `UnboundValue`: the suite exposes a missing
variable as `E_VARNF`, but has no row that exposes or compares internal none and
unbound sentinels.

## Normative Barn specification

- `../barn/spec/types.md`: value tags, truth, equality, ordering, and literal
  forms.
- `../barn/spec/operators.md`: arithmetic, logical operators, and MOO equality.
- `../barn/spec/errors.md`: the canonical error-code family.
- `../barn/spec/builtins/lists.md`: one-based list indexing and list mutation
  semantics.

## Current Barn implementation reference

- `../barn/types/value.go`: truth and cross-value equality dispatch.
- `../barn/types/str.go`: case-insensitive MOO string equality.
- `../barn/types/list.go`: immutable list values and one-based indexing.
- `../barn/types/objid.go`: object-reference identity and literal form.
- `../barn/types/errorcode.go`: canonical error names and literal form.

## Verified Toast authority

Source identity is `/root/src/toaststunt` at
`aecc51e9449c6e7c95272f0f044b5ba38948459e`.

- `src/db_io.cc:163-221,363-383`: tagged database value forms, including
  `TYPE_NONE` and clear/unbound storage markers.
- `src/list.cc:449-510`: observable literal formatting; string literals copy
  Latin-1 bytes directly and prefix only `"` and `\` with a backslash.
- `src/utils.cc:381-399`: MOO truth by concrete value type.
- `src/execute.cc:1302-1305,1339-1358,1542-1557`: equality, comparison, and
  logical negation.
- `src/execute.cc:1216-1256,1624-1653`: one-based indexing behavior.

Toast's internal none and clear markers are implementation state, not authority
for public Java value types without an observable conformance contract.

## Frozen contracts and durable rows

| Family | Contract | Durable conformance row |
| --- | --- | --- |
| integer | construction/tag | `basic/types.yaml`, `types::typeof_int_value` |
| integer | addition | `basic/arithmetic.yaml`, `arithmetic::addition` |
| integer | equality | `language/equality.yaml`, `equality::int_equality` |
| integer | zero false, nonzero true | `basic/types.yaml`, `types::truthy_zero_int` and `types::truthy_nonzero_int` |
| integer | literal `17` | `basic/value.yaml`, `value::toliteral_int` |
| string | construction/tag | `basic/types.yaml`, `types::typeof_str_value` |
| string | ASCII case-insensitive equality | `language/string_comparison_case.yaml`, `string_comparison_case::equality_operator_is_also_case_insensitive` |
| string | equality/hash consistency in maps | `builtins/map.yaml`, `map::maphaskey_default_case_insensitive` |
| string | empty false, nonempty true | `basic/types.yaml`, `types::truthy_empty_string` and `types::truthy_nonempty_string` |
| string | escaped literal | `basic/value.yaml`, `value::toliteral_string` |
| object | construction/tag | `basic/types.yaml`, `types::typeof_obj_value` |
| object | identity equality | `language/equality.yaml`, `equality::object_equality` |
| object | `#0` is false | `basic/types.yaml`, `types::truthy_object_ref` |
| object | literal `#0` | `basic/value.yaml`, `value::toliteral_object` |
| error | construction/tag | `basic/types.yaml`, `types::typeof_err_value` |
| error | identity equality | `language/equality.yaml`, `equality::error_equality` |
| error | false in conditions | `basic/types.yaml`, `types::truthy_error_value` |
| error | canonical literal | `basic/value.yaml`, `value::toliteral_error` |
| list | construction/tag | `basic/types.yaml`, `types::typeof_list_value` |
| list | one-based indexing | `builtins/pcre.yaml`, `pcre::pcre_match_simple` |
| list | structural equality | `language/equality.yaml`, `equality::list_equality_with_splicing` |
| list | empty false, nonempty true | `basic/types.yaml`, `types::truthy_empty_list` and `types::truthy_nonempty_list` |
| list | literal `{1, 2}` | `basic/value.yaml`, `value::toliteral_list` |
| missing variable | raise `E_VARNF` | `language/scatter.yaml`, `scatter::scatter_optional_no_default` |

## First-slice decision

Implement a closed immutable family for integer, string, object, error, and list
values with the contracts above. Represent an absent Java result with Java
control flow rather than inventing a public MOO sentinel. Defer a distinct
none/unbound value until a durable Toast-proven row requires it.
