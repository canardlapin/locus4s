# Complexity and allocation contracts

This document states the public cost model. It distinguishes guaranteed
behavior from the current reference representation.

`n` is a source-domain size, `m` is a target-domain size, `k` is a region
cardinality, `d` is one relation-row degree, and `e` is a relation pair count.

## Domains and indices

| Operation | Time | Material allocation |
|---|---:|---|
| `domain.index(ordinal)` | O(1) | the `Either` result; no index object |
| `domain.indexOption(ordinal)` | O(1) | the `Option` result; no index object |
| `domain.foreachIndex(f)` | O(n) | none per index |
| `domain.indices` | O(n) when consumed | iterator machinery; use `foreachIndex` in hot code |
| typed `Index.ordinal` | O(1) | none |
| alignment index transport | O(1) | none |
| registry `register` / `restore` | expected O(1) lookup | result wrapper; a new owner and persistent map path only for a new key |
| registry `find` | expected O(1) | `Option` result |
| alignment check | O(1) | `Either` and, on success, alignment evidence |
| alignment composition / reverse | O(1) | one alignment value |
| alignment transport / endpoint rebind | O(1) | one wrapper; immutable payload storage is shared |

`Index[S]` is represented by an opaque `Int` on the JVM and Scala.js. The
callback supplied to `foreachIndex` may allocate; the traversal does not create
an owned object for each ordinal.

## Regions

| Operation | Time | Material allocation |
|---|---:|---|
| `Region.empty`, `Region.whole` | O(1) | one small region value |
| empty/whole complement | O(1) | one small region value |
| sparse membership | O(log k) | none |
| sparse traversal | O(k) | iterator machinery only when an iterator is requested |
| sparse union/intersection/difference | O(k1 + k2) | output-sized primitive buffer |
| sparse xor | O(k1 + k2) | output-sized primitive buffer |
| complement | O(1) for empty/whole; O(n) for sparse | one small region or complement-sized primitive buffer |
| `Region.tabulate` | O(n) | output-sized sparse buffer |
| `fromOrdinals` | O(k log k) | copied, sorted, output-sized primitive buffers |
| `fromSortedDistinct` | O(k) validation | one copied primitive buffer |
| `fromIndices` / `fromPoints` | O(k log k) | copied, sorted, output-sized primitive buffers |
| `ordinalsInDomainOrder` | O(k), or O(n) for whole | defensive primitive array |
| checked Boolean operations | underlying operation plus O(1) owner check | `Either` plus underlying result |
| rebind | O(1) | one wrapper; ordinal storage is shared |

The current representation is `Empty`, `Whole`, or sorted distinct primitive
ordinals. A dense bitmap may be added later without changing the semantics if
benchmarks justify it.

## Maps

| Operation | Time | Material allocation |
|---|---:|---|
| `TotalMap.apply` | O(1) | none |
| `TotalMap.foreachMapping` | O(n) | none per mapping |
| `TotalMap.andThen` | O(n) | one primitive target buffer |
| total-map image | O(k log k) worst case with output canonicalization | output-sized region storage |
| total-map pullback | O(n) | output-sized region storage |
| map endpoint rebind | O(1) | one wrapper; target storage is shared |
| `fromTargetOrdinals` | O(n) validation | one copied primitive target buffer |
| `fromTargetIndices` | O(n) | one primitive target buffer |
| `tabulate` / `identity` | O(n) | one primitive target buffer |
| `targetOrdinals` | O(n) | defensive primitive array |
| checked composition/image/pullback | underlying operation plus O(1) owner check | `Either` plus underlying result |
| `PartialMap.apply` | O(1) | `Option`; no target index object |
| `PartialMap.isDefinedAt` | O(1) | none |
| `PartialMap.definedRegion` | O(n) | defined-region storage |
| `PartialMap.foreachDefined` | O(n) | `Option` result per source in the reference implementation |
| partial-map composition | O(n) | one primitive target buffer |
| partial-map image | O(k log k) worst case with output canonicalization | output-sized region storage |
| partial-map construction / tabulation | O(n) | one primitive target buffer |
| `optionalTargetOrdinals` | O(n) | defensive boxed `Vector[Option[Int]]` |
| partial-map endpoint rebind | O(1) | one wrapper; target storage is shared |
| certified-map validation | O(n) | property-specific validation storage |
| certified-map lookup / composition | O(1) / O(n) | none / one primitive target buffer |
| certified-map endpoint rebind | O(1) | one wrapper; target storage is shared |
| injection support | O(n log n) worst case with output canonicalization | image-region storage |
| bijection inverse | O(n) | inverse primitive target buffer |

`TotalMap` and its certified wrappers use one contiguous immutable primitive
target buffer. `PartialMap` uses the same shape with a private undefined
sentinel.

Injection validation uses expected O(n) hash-index work. Surjection validation
uses O(n + m) time and O(m) marker storage. Bijection validation combines the
equal-cardinality check with injection validation. `PartialSurjection` uses the
same O(n + m) coverage court over defined targets.

## Relations

| Operation | Time | Material allocation |
|---|---:|---|
| `foreachTarget(source)` | O(d) | none per target |
| `row(source)` | O(d) | O(d) |
| `hasTargets(source)` | O(1) | none |
| `isRelated(source, target)` | O(log d) | none |
| converse | O(m + e) | O(m + e) |
| union/intersection | O(n + e1 + e2) | output-sized CSR |
| subset | O(n + e1 + e2) worst case | none |
| relation image | visited source rows and edges, plus O(v log v) worst-case canonicalization for v visited targets | output-sized region storage |
| composition | O(n) plus visited two-hop edges and per-row sort/deduplication | reached-target buffers and output CSR |
| endpoint rebind | O(1) | one wrapper; CSR storage is shared |
| `fromOrdinalRows` | input rows plus per-row sort/deduplication | copied output CSR |
| `fromCsr` | O(n + e) validation | copied CSR |
| `tabulate` | O(n + e) plus callback work | output CSR |
| checked composition/lattice/image | underlying operation plus O(1) endpoint checks | `Either` plus underlying result |

Relations use compressed sparse rows: row offsets plus one contiguous sorted,
unique target buffer. Composition never initializes an `m`-sized Boolean
marker for each source row.

An empty relation has a special constant-size representation, including when a
domain declares `Int.MaxValue` elements. A non-empty CSR relation cannot have
`Int.MaxValue` source rows because an array cannot store the required
`sourceSize + 1` offsets; construction reports
`RelationError.RowOffsetCountOverflow`.

The public `csr` and `ordinalRows` methods make defensive copies. They are
dynamic-boundary and compatibility operations. Core algorithms traverse the
immutable representation directly and do not call these copying methods.

## Selections

| Operation | Time | Material allocation |
|---|---:|---|
| construct from `k` ordinals | expected O(k), plus injection validation | position owner and O(k) primitive map storage |
| construct from a region | O(k), plus injection validation | position owner and O(k) primitive map storage |
| position lookup | O(1) | none |
| ordered traversal | O(k) | none per index through `foreachIndex`; iterator machinery through `indices` |
| `ordinals` | O(k) | defensive primitive array |
| support | computed at construction | stored region |
| source rebind | O(1) | wrappers only; position and target storage are shared |
| gather a field | O(1) to create a view | one view |

The selection position domain is a real owner. Gathering preserves it in the
result type.

## Fields and aggregation

`Field` implementations state their own indexed-access and storage costs.
The supplied `VectorField` has O(1) lookup. Its rebind shares the `Vector`.
`Field.view`, `map`, `zipWith`, `pullback`, `gather`, and `restrict` create
O(1) views and evaluate on access. Their typed forms allocate one view;
checked forms add O(1) owner checks and an `Either`. `foreachValue` and
`foreachValueWithOrdinal` cost O(n) plus implementation lookup and callback
work. `valuesInDomainOrder` creates iterator machinery; `toVector` costs O(n)
and materializes n values.

`VectorField.fromValues` costs O(n) and defensively materializes one `Vector`.
`VectorField.tabulate` and `FieldBuilder.vector.tabulate` cost O(n) plus
callback work and materialize one `Vector`.

`SectionView.apply` is one region membership plus an `Either`. Section mapping,
restriction, and rebinding create O(1) views except that sparse support
intersection follows the Region cost above. Section traversal costs O(k).
Section gather validates all k selected indices and then creates an O(1)
field view.

Total-map aggregation costs O(n + m) plus reducer work. Relation aggregation
costs O(n + e + m) plus reducer work. Both allocate the destination
accumulators and the chosen output field. Neither clones its map or relation.
Source traversal is increasing ordinal order, so reducers observe deterministic
input order.

## Persistence records

Recording or restoring a sparse Region costs O(k); whole and empty Region
records are O(1). Selection records cost O(k). TotalMap and PartialMap records
cost O(n). Non-empty Relation records cost O(n + e); an empty Relation record
is O(1). Recording materializes neutral `Vector` payloads. Restoration
validates endpoint keys in O(1), then applies the corresponding constructor
cost and allocation described above. No record operation mutates or exposes a
live structure's private immutable storage.

## Evidence

`PerformanceContractsSuite` exercises sparse rows and composition against a
billion-element declared target and compact empty/whole values at
`Int.MaxValue`. `JvmAllocationCourtSuite` uses JVM per-thread allocation
accounting after warmup. `ScalaJsPerformanceCourtSuite` runs the corresponding
primitive traversal and sparse-row probes. CI runs the Scala.js suite under
both fast and full optimization.

These courts are regression gates, not universal latency promises. Hardware,
runtime, storage implementations, and callbacks determine elapsed time.
