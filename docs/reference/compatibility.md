# Compatibility policy

locus4s is pre-1.0. The finite-domain contracts are still allowed to change
when a change is necessary to make ownership, identity, algebraic laws, or
performance guarantees correct.

Before 1.0:

- release notes identify source-breaking changes and provide a migration path;
- deprecated compatibility names may remain when they preserve the new
  invariants without restoring unsafe behavior;
- no compatibility shim will permit index forging, contradictory mismatch
  errors, or branching-unsafe persistent ID generation;
- image4s and neuroimaging libraries should isolate locus4s behind small bridge
  modules rather than expose its evolving concrete API deeply.

After the first public baseline is selected, the build will add binary
compatibility checking for JVM artifacts. That baseline should be chosen only
after the ownership, persistence, representation, and downstream adoption
gates are complete. Scala.js source and linker compatibility will continue to
be checked by compilation and optimized linking because JVM binary tools do not
cover JavaScript artifacts.

The repository currently treats the following as stable design constraints,
even before a stable binary baseline:

- persistent identity and live runtime ownership are distinct;
- independently restored owners require explicit alignment;
- same-owner typed operations are total;
- indices are bounded, zero-cost ordinals;
- fields do not prescribe storage;
- geometry and imaging policy remain downstream.

## Canonical names and compatibility spellings

New APIs and documentation use `Index[S]` for a bounded typed ordinal and
`Field[S, A]` for representation-neutral indexed values. `VectorField` names
the supplied strict vector-backed implementation. Compatibility names do not
define a second representation or a second ownership model.

The following spellings are retained for the pre-1.0 series, deprecated by
documentation now, and scheduled for removal at 1.0:

| Compatibility spelling | Canonical spelling | Source consequence at 1.0 |
|---|---|---|
| `Point[S]` | `Index[S]` | change the type name; values keep the same opaque-`Int` representation |
| `PointError` | `IndexError` | change the error type and constructor qualifier |
| `domain.point` | `domain.index` | change the method name; the `Either` result is unchanged |
| `domain.pointOption` | `domain.indexOption` | change the method name; the `Option` result is unchanged |
| `domain.points` | `domain.indices` | change the iterator method name |
| `region.pointsInDomainOrder` | `region.indicesInDomainOrder` | change the iterator method name |
| `Region.fromPoints` and `Selection.fromPoints` | `fromIndices` | change the constructor name |
| `IndexedField[S, A]` | `VectorField[S, A]`, or `Field[S, A]` at abstraction boundaries | choose whether the caller requires vector storage |
| `IndexedFieldError` | `FieldConstructionError` | change the error type and constructor qualifier |
| `at(index)` | `apply(index)` | replace `value.at(index)` with `value(index)` |
| `Section.valuesIn` | `SectionView.gather` | change the method name; the compact position-domain result remains |

These names carry Scala `@deprecated` annotations in the current snapshot, so
downstream compilation identifies each migration site. No new locus4s API will
use them, and their removal at 1.0 will be source-breaking. The aliases can
remain until then without blocking ScalaFIM or another downstream library from
adopting the canonical API.

## Ownership checks and alignment

A typed method whose arguments share one owner type is total. A method ending
in `Checked` compares exact live owners and returns `SpaceMismatch` when they
differ, even if their persistent keys match. A method ending in `Aligned`
requires `DomainAlignment` evidence and permits independently restored owners
whose persistent structural keys agree.

Target-relabeling comparisons are a deliberate exception to target-owner
equality: they compare the partition of an aligned source domain and derive a
bijection between observed target fibers. They do not interpret equal target
ordinals from distinct owners as equal labels.
