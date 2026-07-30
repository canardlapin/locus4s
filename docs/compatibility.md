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
