package locus4s.laws

import locus4s.Bijection
import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.DomainResolution
import locus4s.Injection
import locus4s.PartialMap
import locus4s.PartialSurjection
import locus4s.Region
import locus4s.Relation
import locus4s.Selection
import locus4s.Surjection
import locus4s.TotalMap
import locus4s.data.VectorField
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

final class IndexedFieldLawsSuite extends ScalaCheckSuite:
  property("Field map and pullback laws are reusable across domain sizes"):
    forAll(fieldCase): (size, values, firstTargets, secondTargets) =>
      val space = restored(s"field-law-$size", size).space
      val field = mustRight(VectorField.fromValues(space, values))
      val first =
        mustRight(TotalMap.fromTargetOrdinals(space, space, firstTargets))
      val second =
        mustRight(TotalMap.fromTargetOrdinals(space, space, secondTargets))

      assert(FieldLaws.mapIdentity(field)(_ == _))
      assert(
        FieldLaws.mapComposition(
          field,
          (value: Int) => value + 7,
          (value: Int) => value * 3
        )(_ == _)
      )
      assert(FieldLaws.pullbackIdentity(field)(_ == _))
      assert(
        FieldLaws.pullbackComposition(
          field,
          first,
          second
        )(_ == _)
      )

  property("Region reusable laws cover Boolean algebra and subset order"):
    forAll(regionCase): (size, firstInput, secondInput, thirdInput) =>
      val space = restored(s"region-law-$size", size).space
      val first = mustRight(Region.fromOrdinals(space, firstInput))
      val second = mustRight(Region.fromOrdinals(space, secondInput))
      val third = mustRight(Region.fromOrdinals(space, thirdInput))

      assert(RegionLaws.booleanAlgebra(first, second, third))
      assert(RegionLaws.subsetPartialOrder(first, second, third))

  property("map and relation category laws cover empty and ordinary domains"):
    forAll(morphismCase):
      (
          size,
          firstTargets,
          secondTargets,
          thirdTargets,
          rowsA,
          rowsB,
          rowsC,
          regionInput
      ) =>
        val space = restored(s"morphism-law-$size", size).space
        val first =
          mustRight(TotalMap.fromTargetOrdinals(space, space, firstTargets))
        val second =
          mustRight(TotalMap.fromTargetOrdinals(space, space, secondTargets))
        val third =
          mustRight(TotalMap.fromTargetOrdinals(space, space, thirdTargets))
        val relationA =
          mustRight(Relation.fromOrdinalRows(space, space, rowsA))
        val relationB =
          mustRight(Relation.fromOrdinalRows(space, space, rowsB))
        val relationC =
          mustRight(Relation.fromOrdinalRows(space, space, rowsC))
        val region =
          mustRight(Region.fromOrdinals(space, regionInput))

        assert(TotalMapLaws.categoryIdentity(first))
        assert(TotalMapLaws.categoryAssociativity(first, second, third))
        assert(TotalMapLaws.imagePreservesUnion(first, region, region.complement))
        assert(
          TotalMapLaws.pullbackBooleanAlgebra(
            first,
            region,
            region.complement
          )
        )
        assert(TotalMapLaws.imagePullbackAdjunction(first, region, region))
        assert(RelationLaws.categoryIdentity(relationA))
        assert(
          RelationLaws.categoryAssociativity(
            relationA,
            relationB,
            relationC
          )
        )
        assert(RelationLaws.converseLaws(relationA, relationB))
        assert(
          RelationLaws.latticeLaws(
            relationA,
            relationB,
            relationC
          )
        )
        assert(RelationLaws.imageComposition(relationA, relationB, region))

  property("Selection and certified map laws retain their proofs"):
    forAll(permutationCase): (size, permutation) =>
      val source = restored(s"cert-law-source-$size", size).space
      val target = restored(s"cert-law-target-$size", size).space
      val mapping =
        mustRight(
          TotalMap.fromTargetOrdinals(source, target, permutation)
        )
      val injection =
        mustRight(Injection.fromTotalMap(mapping))
      val surjection =
        mustRight(Surjection.fromTotalMap(mapping))
      val bijection =
        mustRight(Bijection.fromTotalMap(mapping))
      val selection =
        mustRight(Selection.fromOrdinals(target, permutation))
      val field =
        VectorField.tabulate(target)(_.ordinal)

      assert(CertifiedMapLaws.injectionIsUnique(injection))
      assert(CertifiedMapLaws.surjectionCovers(surjection))
      assert(CertifiedMapLaws.bijectionInverse(bijection))
      assert(SelectionLaws.injectionAndSupport(selection))
      assert(FieldLaws.gatherUsesPositionDomain(field, selection)(_ == _))

  property("partial-map composition is associative"):
    forAll(partialCase): (size, firstValues, secondValues, thirdValues) =>
      val space = restored(s"partial-law-$size", size).space
      val first =
        mustRight(
          PartialMap.fromOptionalTargetOrdinals(
            space,
            space,
            firstValues
          )
        )
      val second =
        mustRight(
          PartialMap.fromOptionalTargetOrdinals(
            space,
            space,
            secondValues
          )
        )
      val third =
        mustRight(
          PartialMap.fromOptionalTargetOrdinals(
            space,
            space,
            thirdValues
          )
        )

      assert(PartialMapLaws.compositionAssociativity(first, second, third))

  test("alignment and persistence reusable laws round-trip"):
    val record =
      mustRight(DomainRecord.parse("law-persistence", "first", 5))
    val renamed =
      mustRight(DomainRecord.parse("law-persistence", "second", 5))
    val left = mustRight(DomainRegistry.empty.restore(record))
    val right = mustRight(DomainRegistry.empty.restore(renamed))
    val alignment = mustRight(left.space.align(right.space))
    val region =
      mustRight(Region.fromOrdinals(left.space, Vector(0, 2, 4)))
    val selection =
      mustRight(Selection.fromOrdinals(left.space, Vector(4, 0, 2)))
    val mapping =
      mustRight(
        TotalMap.fromTargetOrdinals(
          left.space,
          left.space,
          Vector(1, 2, 3, 4, 0)
        )
      )
    val relation =
      mustRight(
        Relation.fromOrdinalRows(
          left.space,
          left.space,
          Vector(
            Vector(0, 1),
            Vector(2),
            Vector.empty,
            Vector(3, 4),
            Vector(0)
          )
        )
      )
    val field =
      VectorField.tabulate(left.space)(_.ordinal * 2)
    val positionRecord =
      mustRight(DomainRecord.parse("law-positions", "positions", 3))
    val bijection = mustRight(Bijection.fromTotalMap(mapping))
    val injection = bijection.toInjection
    val surjection = bijection.toSurjection
    val partial =
      PartialMap.tabulate(left.space, left.space)(index => Some(index))
    val partialSurjection =
      mustRight(PartialSurjection.fromPartialMap(partial))

    left.space.indices.foreach: index =>
      assert(DomainAlignmentLaws.reverseRoundTrip(alignment, index))
    assert(DomainAlignmentLaws.regionRoundTrip(alignment, region))
    assert(DomainAlignmentLaws.selectionRoundTrip(alignment, selection))
    assert(
      DomainAlignmentLaws.regionBooleanNaturality(
        alignment,
        region,
        selection.support
      )
    )
    assert(
      DomainAlignmentLaws.selectionSupportNaturality(
        alignment,
        selection
      )
    )
    assert(
      DomainAlignmentLaws.totalMapSourceRoundTrip(alignment, mapping)
    )
    assert(
      DomainAlignmentLaws.totalMapTargetRoundTrip(alignment, mapping)
    )
    assert(
      DomainAlignmentLaws.totalMapSourceImageNaturality(
        alignment,
        mapping,
        region
      )
    )
    assert(
      DomainAlignmentLaws.totalMapTargetImageNaturality(
        alignment,
        mapping,
        region
      )
    )
    assert(
      DomainAlignmentLaws.totalMapTargetPullbackNaturality(
        alignment,
        mapping,
        region
      )
    )
    assert(
      DomainAlignmentLaws.partialMapSourceRoundTrip(
        alignment,
        partial
      )
    )
    assert(
      DomainAlignmentLaws.partialMapTargetRoundTrip(
        alignment,
        partial
      )
    )
    assert(
      DomainAlignmentLaws.injectionSourceRoundTrip(
        alignment,
        injection
      )
    )
    assert(
      DomainAlignmentLaws.injectionTargetRoundTrip(
        alignment,
        injection
      )
    )
    assert(
      DomainAlignmentLaws.surjectionSourceRoundTrip(
        alignment,
        surjection
      )
    )
    assert(
      DomainAlignmentLaws.surjectionTargetRoundTrip(
        alignment,
        surjection
      )
    )
    assert(
      DomainAlignmentLaws.bijectionSourceRoundTrip(
        alignment,
        bijection
      )
    )
    assert(
      DomainAlignmentLaws.bijectionTargetRoundTrip(
        alignment,
        bijection
      )
    )
    assert(
      DomainAlignmentLaws.partialSurjectionSourceRoundTrip(
        alignment,
        partialSurjection
      )
    )
    assert(
      DomainAlignmentLaws.partialSurjectionTargetRoundTrip(
        alignment,
        partialSurjection
      )
    )
    assert(
      DomainAlignmentLaws.relationSourceRoundTrip(alignment, relation)
    )
    assert(
      DomainAlignmentLaws.relationTargetRoundTrip(alignment, relation)
    )
    assert(
      DomainAlignmentLaws.relationSourceImageNaturality(
        alignment,
        relation,
        region
      )
    )
    assert(
      DomainAlignmentLaws.relationTargetImageNaturality(
        alignment,
        relation,
        region
      )
    )
    assert(FieldLaws.alignmentRoundTrip(field, alignment)(_ == _))
    assert(
      FieldLaws.transportPullbackNaturality(
        field,
        mapping,
        alignment
      )(_ == _)
    )
    assert(
      PersistenceLaws.domainRestorationIdempotent(
        DomainRegistry.empty,
        record
      )
    )
    assert(PersistenceLaws.regionRoundTrip(region))
    assert(
      PersistenceLaws.selectionRoundTrip(
        selection,
        positionRecord,
        DomainRegistry.empty
      )
    )
    assert(PersistenceLaws.totalMapRoundTrip(mapping))
    val persistedPartial =
      mustRight(
        PartialMap.fromOptionalTargetOrdinals(
          left.space,
          left.space,
          Vector(Some(0), None, Some(2), Some(3), None)
        )
      )
    assert(PersistenceLaws.partialMapRoundTrip(persistedPartial))
    assert(PersistenceLaws.relationRoundTrip(relation))

  test("lawful sum aggregation fuses through composition"):
    val source = restored("aggregation-law-source", 6).space
    val middle = restored("aggregation-law-middle", 3).space
    val target = restored("aggregation-law-target", 2).space
    val field = VectorField.tabulate(source)(_.ordinal + 1)
    val first =
      mustRight(
        TotalMap.fromTargetOrdinals(
          source,
          middle,
          Vector(0, 0, 1, 1, 2, 2)
        )
      )
    val second =
      mustRight(
        TotalMap.fromTargetOrdinals(
          middle,
          target,
          Vector(0, 0, 1)
        )
      )

    assert(
      AggregationLaws.totalMapFusion(first, second, field)(0)(identity)(
        _ + _
      )(_ == _)
    )

  private val fieldCase =
    Gen
      .choose(0, 16)
      .flatMap: size =>
        for
          values <- Gen.listOfN(size, Gen.choose(-1000, 1000))
          first <- targets(size)
          second <- targets(size)
        yield (size, values.toVector, first, second)

  private val regionCase =
    for
      size <- Gen.choose(0, 16)
      first <- boundedList(size)
      second <- boundedList(size)
      third <- boundedList(size)
    yield (size, first, second, third)

  private val morphismCase =
    for
      size <- Gen.choose(0, 10)
      first <- targets(size)
      second <- targets(size)
      third <- targets(size)
      rowsA <- rows(size)
      rowsB <- rows(size)
      rowsC <- rows(size)
      region <- boundedList(size)
    yield (size, first, second, third, rowsA, rowsB, rowsC, region)

  private val permutationCase =
    Gen
      .choose(0, 16)
      .flatMap: size =>
        Gen
          .pick(size, (0 until size).toVector)
          .map(values => (size, values.toVector))

  private val partialCase =
    Gen
      .choose(0, 12)
      .flatMap: size =>
        for
          first <- partialTargets(size)
          second <- partialTargets(size)
          third <- partialTargets(size)
        yield (size, first, second, third)

  private def boundedList(size: Int): Gen[Vector[Int]] =
    if size == 0 then Gen.const(Vector.empty)
    else Gen.listOf(Gen.choose(0, size - 1)).map(_.toVector)

  private def targets(size: Int): Gen[Vector[Int]] =
    if size == 0 then Gen.const(Vector.empty)
    else
      Gen
        .listOfN(size, Gen.choose(0, size - 1))
        .map(_.toVector)

  private def partialTargets(
      size: Int
  ): Gen[Vector[Option[Int]]] =
    if size == 0 then Gen.const(Vector.empty)
    else
      Gen
        .listOfN(
          size,
          Gen.option(Gen.choose(0, size - 1))
        )
        .map(_.toVector)

  private def rows(size: Int): Gen[Vector[Vector[Int]]] =
    if size == 0 then Gen.const(Vector.empty)
    else
      Gen
        .listOfN(
          size,
          Gen.listOf(Gen.choose(0, size - 1)).map(_.toVector)
        )
        .map(_.toVector)

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
