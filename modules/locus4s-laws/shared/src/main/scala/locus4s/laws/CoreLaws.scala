package locus4s.laws

import locus4s.Bijection
import locus4s.DomainAlignment
import locus4s.Index
import locus4s.Injection
import locus4s.PartialMap
import locus4s.PartialSurjection
import locus4s.Region
import locus4s.Relation
import locus4s.Selection
import locus4s.Surjection
import locus4s.TotalMap

object DomainAlignmentLaws:
  def identity[S](
      alignment: DomainAlignment[S, S],
      index: Index[S]
  ): Boolean =
    alignment.toRight(index) == index

  def reverseRoundTrip[A, B](
      alignment: DomainAlignment[A, B],
      index: Index[A]
  ): Boolean =
    alignment.reverse.toRight(alignment.toRight(index)) == index

  def compositionAssociativity[A, B, C, D](
      first: DomainAlignment[A, B],
      second: DomainAlignment[B, C],
      third: DomainAlignment[C, D],
      index: Index[A]
  ): Boolean =
    first
      .andThen(second)
      .andThen(third)
      .toRight(index) ==
      first
        .andThen(second.andThen(third))
        .toRight(index)

  def regionRoundTrip[A, B](
      alignment: DomainAlignment[A, B],
      region: Region[A]
  ): Boolean =
    alignment.reverse.transport(alignment.transport(region)) == region

  def selectionRoundTrip[A, B](
      alignment: DomainAlignment[A, B],
      selection: Selection[A]
  ): Boolean =
    alignment.reverse
      .transport(alignment.transport(selection)) == selection

  def regionBooleanNaturality[A, B](
      alignment: DomainAlignment[A, B],
      left: Region[A],
      right: Region[A]
  ): Boolean =
    alignment.transport(left.union(right)) ==
      alignment.transport(left).union(alignment.transport(right)) &&
      alignment.transport(left.intersect(right)) ==
      alignment.transport(left).intersect(alignment.transport(right)) &&
      alignment.transport(left.complement) ==
      alignment.transport(left).complement

  def selectionSupportNaturality[A, B](
      alignment: DomainAlignment[A, B],
      selection: Selection[A]
  ): Boolean =
    alignment.transport(selection).support ==
      alignment.transport(selection.support)

  def totalMapSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      mapping: TotalMap[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(mapping)) == mapping

  def totalMapTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      mapping: TotalMap[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(mapping)) == mapping

  def totalMapSourceImageNaturality[A, B, Y](
      alignment: DomainAlignment[A, B],
      mapping: TotalMap[A, Y],
      region: Region[A]
  ): Boolean =
    alignment.transportFrom(mapping).image(alignment.transport(region)) ==
      mapping.image(region)

  def totalMapTargetImageNaturality[X, A, B](
      alignment: DomainAlignment[A, B],
      mapping: TotalMap[X, A],
      region: Region[X]
  ): Boolean =
    alignment.transportTo(mapping).image(region) ==
      alignment.transport(mapping.image(region))

  def totalMapTargetPullbackNaturality[X, A, B](
      alignment: DomainAlignment[A, B],
      mapping: TotalMap[X, A],
      region: Region[A]
  ): Boolean =
    alignment
      .transportTo(mapping)
      .pullback(alignment.transport(region)) ==
      mapping.pullback(region)

  def partialMapSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      mapping: PartialMap[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(mapping)) == mapping

  def partialMapTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      mapping: PartialMap[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(mapping)) == mapping

  def injectionSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      injection: Injection[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(injection)) == injection

  def injectionTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      injection: Injection[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(injection)) == injection

  def surjectionSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      surjection: Surjection[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(surjection)) == surjection

  def surjectionTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      surjection: Surjection[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(surjection)) == surjection

  def bijectionSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      bijection: Bijection[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(bijection)) == bijection

  def bijectionTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      bijection: Bijection[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(bijection)) == bijection

  def partialSurjectionSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      surjection: PartialSurjection[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(surjection)) == surjection

  def partialSurjectionTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      surjection: PartialSurjection[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(surjection)) == surjection

  def relationSourceRoundTrip[A, B, Y](
      alignment: DomainAlignment[A, B],
      relation: Relation[A, Y]
  ): Boolean =
    alignment.reverse
      .transportFrom(alignment.transportFrom(relation)) == relation

  def relationTargetRoundTrip[X, A, B](
      alignment: DomainAlignment[A, B],
      relation: Relation[X, A]
  ): Boolean =
    alignment.reverse
      .transportTo(alignment.transportTo(relation)) == relation

  def relationSourceImageNaturality[A, B, Y](
      alignment: DomainAlignment[A, B],
      relation: Relation[A, Y],
      region: Region[A]
  ): Boolean =
    alignment
      .transportFrom(relation)
      .image(alignment.transport(region)) ==
      relation.image(region)

  def relationTargetImageNaturality[X, A, B](
      alignment: DomainAlignment[A, B],
      relation: Relation[X, A],
      region: Region[X]
  ): Boolean =
    alignment.transportTo(relation).image(region) ==
      alignment.transport(relation.image(region))

object RegionLaws:
  def booleanAlgebra[S](
      left: Region[S],
      middle: Region[S],
      right: Region[S]
  ): Boolean =
    val empty = Region.empty(left.space)
    val whole = Region.whole(left.space)
    left.union(middle) == middle.union(left) &&
    left.intersect(middle) == middle.intersect(left) &&
    left.union(middle).union(right) ==
      left.union(middle.union(right)) &&
      left.intersect(middle).intersect(right) ==
      left.intersect(middle.intersect(right)) &&
      left.intersect(middle.union(right)) ==
      left.intersect(middle).union(left.intersect(right)) &&
      left.union(middle.intersect(right)) ==
      left.union(middle).intersect(left.union(right)) &&
      left.union(empty) == left &&
      left.intersect(whole) == left &&
      left.union(whole) == whole &&
      left.intersect(empty) == empty &&
      left.complement.complement == left &&
      left.union(left.complement) == whole &&
      left.intersect(left.complement) == empty &&
      left.union(middle).complement ==
      left.complement.intersect(middle.complement) &&
      left.intersect(middle).complement ==
      left.complement.union(middle.complement) &&
      left.xor(middle) == left.diff(middle).union(middle.diff(left))

  def subsetPartialOrder[S](
      left: Region[S],
      middle: Region[S],
      right: Region[S]
  ): Boolean =
    val reflexive = left.subsetOf(left)
    val antisymmetric =
      !(left.subsetOf(middle) && middle.subsetOf(left)) || left == middle
    val transitive =
      !(left.subsetOf(middle) && middle.subsetOf(right)) ||
        left.subsetOf(right)
    reflexive && antisymmetric && transitive

object TotalMapLaws:
  def categoryIdentity[X, Y](mapping: TotalMap[X, Y]): Boolean =
    TotalMap.identity(mapping.from).andThen(mapping) == mapping &&
      mapping.andThen(TotalMap.identity(mapping.to)) == mapping

  def categoryAssociativity[W, X, Y, Z](
      first: TotalMap[W, X],
      second: TotalMap[X, Y],
      third: TotalMap[Y, Z]
  ): Boolean =
    first.andThen(second).andThen(third) ==
      first.andThen(second.andThen(third))

  def imagePreservesUnion[X, Y](
      mapping: TotalMap[X, Y],
      left: Region[X],
      right: Region[X]
  ): Boolean =
    mapping.image(left.union(right)) ==
      mapping.image(left).union(mapping.image(right))

  def pullbackBooleanAlgebra[X, Y](
      mapping: TotalMap[X, Y],
      left: Region[Y],
      right: Region[Y]
  ): Boolean =
    mapping.pullback(left.union(right)) ==
      mapping.pullback(left).union(mapping.pullback(right)) &&
      mapping.pullback(left.intersect(right)) ==
      mapping.pullback(left).intersect(mapping.pullback(right)) &&
      mapping.pullback(left.complement) ==
      mapping.pullback(left).complement

  def imagePullbackAdjunction[X, Y](
      mapping: TotalMap[X, Y],
      source: Region[X],
      target: Region[Y]
  ): Boolean =
    mapping.image(source).subsetOf(target) ==
      source.subsetOf(mapping.pullback(target))

object RelationLaws:
  def categoryIdentity[X, Y](relation: Relation[X, Y]): Boolean =
    Relation.identity(relation.from).andThen(relation) == relation &&
      relation.andThen(Relation.identity(relation.to)) == relation

  def categoryAssociativity[W, X, Y, Z](
      first: Relation[W, X],
      second: Relation[X, Y],
      third: Relation[Y, Z]
  ): Boolean =
    first.andThen(second).andThen(third) ==
      first.andThen(second.andThen(third))

  def converseLaws[X, Y, Z](
      first: Relation[X, Y],
      second: Relation[Y, Z]
  ): Boolean =
    first.converse.converse == first &&
      first.andThen(second).converse ==
      second.converse.andThen(first.converse)

  def latticeLaws[X, Y](
      left: Relation[X, Y],
      middle: Relation[X, Y],
      right: Relation[X, Y]
  ): Boolean =
    left.union(middle) == middle.union(left) &&
      left.intersect(middle) == middle.intersect(left) &&
      left.union(left) == left &&
      left.intersect(left) == left &&
      left.intersect(middle.union(right)) ==
      left.intersect(middle).union(left.intersect(right))

  def imageComposition[X, Y, Z](
      first: Relation[X, Y],
      second: Relation[Y, Z],
      region: Region[X]
  ): Boolean =
    first.andThen(second).image(region) ==
      second.image(first.image(region))

object SelectionLaws:
  def injectionAndSupport[S](selection: Selection[S]): Boolean =
    val ordinals = selection.ordinals
    ordinals.distinct.length == ordinals.length &&
    selection.support ==
      selection.embedding.toTotalMap.image(
        Region.whole(selection.positions)
      ) &&
      selection.positions.indices.forall: position =>
        selection(position).ordinal == ordinals(position.ordinal)

object CertifiedMapLaws:
  def injectionIsUnique[X, Y](injection: Injection[X, Y]): Boolean =
    val targets =
      injection.toTotalMap.targetOrdinals
    targets.distinct.length == targets.length

  def surjectionCovers[X, Y](surjection: Surjection[X, Y]): Boolean =
    surjection.toTotalMap
      .image(Region.whole(surjection.from))
      .isWhole

  def bijectionInverse[X, Y](bijection: Bijection[X, Y]): Boolean =
    bijection.andThen(bijection.inverse).toTotalMap ==
      TotalMap.identity(bijection.from) &&
      bijection.inverse.andThen(bijection).toTotalMap ==
      TotalMap.identity(bijection.to)

object PartialMapLaws:
  def compositionAssociativity[W, X, Y, Z](
      first: PartialMap[W, X],
      second: PartialMap[X, Y],
      third: PartialMap[Y, Z]
  ): Boolean =
    first.andThen(second).andThen(third) ==
      first.andThen(second.andThen(third))
