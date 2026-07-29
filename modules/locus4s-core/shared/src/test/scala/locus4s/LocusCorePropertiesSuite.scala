package locus4s

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

final class LocusCorePropertiesSuite extends ScalaCheckSuite:
  private val domainResolution =
    mustRight(
      mustRight(DomainRegistry.withSequentialIds("properties"))
        .fresh("property-domain", 12)
    )
  private val space = domainResolution.space

  property("region construction is sorted, duplicate-free, and bounded"):
    forAll(Gen.listOf(Gen.choose(-36, 36))): input =>
      val bounded = input.map(value => Math.floorMod(value, space.size))
      val region = mustRight(Region.fromOrdinals(space, bounded))
      val expected = bounded.distinct.sorted
      assertEquals(region.ordinalsInDomainOrder.toSeq, expected)
      assertEquals(region.cardinality, expected.size)
      assert(region.pointsInDomainOrder.forall(space.contains))

  property("region union, intersection, difference, and complement obey set laws"):
    forAll(
      Gen.listOf(Gen.choose(0, space.size - 1)),
      Gen.listOf(Gen.choose(0, space.size - 1))
    ): (leftInput, rightInput) =>
      val left = mustRight(Region.fromOrdinals(space, leftInput))
      val right = mustRight(Region.fromOrdinals(space, rightInput))
      val union = mustRight(left.union(right))
      val reverseUnion = mustRight(right.union(left))
      val intersection = mustRight(left.intersect(right))
      val difference = mustRight(left.diff(right))

      assertEquals(union, reverseUnion)
      assertEquals(
        union.ordinalsInDomainOrder.toSet,
        leftInput.toSet union rightInput.toSet
      )
      assertEquals(
        intersection.ordinalsInDomainOrder.toSet,
        leftInput.toSet intersect rightInput.toSet
      )
      assertEquals(
        difference.ordinalsInDomainOrder.toSet,
        leftInput.toSet diff rightInput.toSet
      )
      assertEquals(
        mustRight(left.union(left.complement)),
        Region.whole(space)
      )
      assertEquals(
        mustRight(left.intersect(left.complement)),
        Region.empty(space)
      )

  property("selection preserves order while enforcing uniqueness"):
    forAll(Gen.someOf((0 until space.size).toList)): input =>
      val selection = mustRight(Selection.fromOrdinals(space, input))
      assertEquals(selection.ordinals.toSeq, input)
      assertEquals(selection.region.ordinalsInDomainOrder.toSeq, input.sorted)
      assertEquals(selection.get(-1), None)
      assertEquals(selection.get(selection.size), None)

  test("selection rejects duplicate and out-of-bounds ordinals"):
    assertEquals(
      Selection.fromOrdinals(space, List(1, 2, 1)),
      Left(SelectionError.DuplicateOrdinal(1))
    )
    assertEquals(
      Selection.fromOrdinals(space, List(1, space.size)),
      Left(SelectionError.OutOfBounds(1, space.size, space.size))
    )

  property("total maps are total and compose extensionally"):
    forAll(
      Gen.listOfN(space.size, Gen.choose(0, space.size - 1)),
      Gen.listOfN(space.size, Gen.choose(0, space.size - 1))
    ): (firstTargets, secondTargets) =>
      val first =
        mustRight(TotalMap.fromTargetOrdinals(space, space, firstTargets))
      val second =
        mustRight(TotalMap.fromTargetOrdinals(space, space, secondTargets))
      val composed = mustRight(first.andThen(second))

      space.points.foreach: point =>
        val firstResult = mustRight(first.at(point))
        val expected = mustRight(second.at(firstResult))
        assertEquals(mustRight(composed.at(point)), expected)

      assertEquals(
        mustRight(TotalMap.identity(space).andThen(first)),
        first
      )
      assertEquals(
        mustRight(first.andThen(TotalMap.identity(space))),
        first
      )

  test("total map validates target count and bounds and owns its input"):
    val wrongCount =
      TotalMap.fromTargetOrdinals(space, space, List.fill(space.size - 1)(0))
    assertEquals(
      wrongCount,
      Left(TotalMapError.WrongTargetCount(space.size, space.size - 1))
    )

    val outOfBounds = Vector.fill(space.size)(0).updated(3, space.size)
    assertEquals(
      TotalMap.fromTargetOrdinals(space, space, outOfBounds),
      Left(TotalMapError.TargetOutOfBounds(3, space.size, space.size))
    )

    val mutableTargets = Array.tabulate(space.size)(identity)
    val mapping =
      mustRight(TotalMap.fromTargetOrdinals(space, space, mutableTargets))
    mutableTargets(0) = space.size - 1
    assertEquals(mustRight(mapping.at(mustRight(space.point(0)))).value, 0)

  property("relation rows are canonical and converse is involutive"):
    val rowGen =
      Gen.listOfN(
        space.size,
        Gen.listOf(Gen.choose(0, space.size - 1))
      )
    forAll(rowGen): rows =>
      val relation =
        mustRight(Relation.fromOrdinalRows(space, space, rows))
      relation.ordinalRows.zip(rows).foreach: (actual, input) =>
        assertEquals(actual.toSeq, input.distinct.sorted)
      assertEquals(relation.converse.converse, relation)
      assertEquals(
        mustRight(Relation.identity(space).andThen(relation)),
        relation
      )
      assertEquals(
        mustRight(relation.andThen(Relation.identity(space))),
        relation
      )

  test("relation validates shape, bounds, and defensively copies rows"):
    assertEquals(
      Relation.fromOrdinalRows(
        space,
        space,
        List.fill(space.size - 1)(List.empty[Int])
      ),
      Left(RelationError.WrongRowCount(space.size, space.size - 1))
    )

    val invalidRows =
      Vector.fill(space.size)(Vector.empty[Int]).updated(2, Vector(space.size))
    assertEquals(
      Relation.fromOrdinalRows(space, space, invalidRows),
      Left(RelationError.TargetOutOfBounds(2, 0, space.size, space.size))
    )

    val mutableRow = Array(1, 2)
    val mutableRows =
      Array.tabulate(space.size): source =>
        if source == 0 then mutableRow else Array.emptyIntArray
    val relation =
      mustRight(
        Relation.fromOrdinalRows(
          space,
          space,
          mutableRows.map(_.toIndexedSeq).toIndexedSeq
        )
      )
    mutableRow(0) = 8
    assertEquals(relation.ordinalRows(0).toSeq, Seq(1, 2))

  test("map and relation images agree for functional relations"):
    val targets = Vector.tabulate(space.size)(index => (index * 3) % space.size)
    val mapping =
      mustRight(TotalMap.fromTargetOrdinals(space, space, targets))
    val relation =
      mustRight(
        Relation.fromOrdinalRows(
          space,
          space,
          targets.map(target => Vector(target))
        )
      )
    val region =
      mustRight(Region.fromOrdinals(space, Vector(1, 3, 7, 9)))

    assertEquals(
      mustRight(mapping.image(region)),
      mustRight(relation.image(region))
    )
    assertEquals(
      mustRight(mapping.pullback(mustRight(mapping.image(region)))),
      mustRight(relation.converse.image(mustRight(relation.image(region))))
    )

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) =>
        result
      case Left(error) =>
        fail(s"expected Right, found Left($error)")
