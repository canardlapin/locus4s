# Concepts

Read these pages when naked ordinals, runtime identity, and persisted identity
begin to interact.

- [Ownership and alignment](ownership.md) explains what the owner type proves
  and where runtime checks still belong.
- [Regions and selections](regions-and-selections.md) separates unordered
  support from an ordered compact position domain.
- [Maps and relations](maps-and-relations.md) presents the finite-domain
  morphisms used for reindexing, grouping, partial assignment, and sparse
  neighborhoods.

The concepts deliberately stop short of geometry or imaging policy. A
downstream library decides what its finite domains mean; locus4s ensures that
their indices and transformations compose safely.
