package locus4s.laws

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.PartialMap
import locus4s.Persistence
import locus4s.Region
import locus4s.Relation
import locus4s.Selection
import locus4s.TotalMap

object PersistenceLaws:
  def domainRestorationIdempotent(
      registry: DomainRegistry,
      record: DomainRecord
  ): Boolean =
    (for
      first <- registry.restore(record)
      second <- first.registry.restore(record)
    yield first.space.sameRuntimeOwnerAs(second.space) &&
      second.registry.size == first.registry.size).getOrElse(false)

  def regionRoundTrip[S](region: Region[S]): Boolean =
    (for
      record <- Persistence.record(region)
      restored <- Persistence.restore(region.space, record)
    yield restored == region).getOrElse(false)

  def selectionRoundTrip[S](
      selection: Selection[S],
      positionRecord: DomainRecord,
      registry: DomainRegistry
  ): Boolean =
    (for
      record <- Persistence.record(selection, positionRecord)
      restored <-
        Persistence.restore(selection.space, record, registry)
    yield restored.selection == selection).getOrElse(false)

  def totalMapRoundTrip[X, Y](mapping: TotalMap[X, Y]): Boolean =
    (for
      record <- Persistence.record(mapping)
      restored <- Persistence.restore(mapping.from, mapping.to, record)
    yield restored == mapping).getOrElse(false)

  def partialMapRoundTrip[X, Y](mapping: PartialMap[X, Y]): Boolean =
    (for
      record <- Persistence.record(mapping)
      restored <- Persistence.restore(mapping.from, mapping.to, record)
    yield restored == mapping).getOrElse(false)

  def relationRoundTrip[X, Y](relation: Relation[X, Y]): Boolean =
    (for
      record <- Persistence.record(relation)
      restored <-
        Persistence.restore(relation.from, relation.to, record)
    yield restored == relation).getOrElse(false)
