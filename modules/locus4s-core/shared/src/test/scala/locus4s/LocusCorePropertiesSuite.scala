package locus4s

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

final class LocusCorePropertiesSuite extends ScalaCheckSuite:
  property("Region is a Boolean algebra and matches immutable Set"):
    forAll(regionCase): (size, leftInput, middleInput, rightInput) =>
      val space = restored(s"region-$size", size).space
      val left = mustRight(Region.fromOrdinals(space, leftInput))
      val middle = mustRight(Region.fromOrdinals(space, middleInput))
      val right = mustRight(Region.fromOrdinals(space, rightInput))
      val empty = Region.empty(space)
      val whole = Region.whole(space)
      val universe = (0 until size).toSet
      val leftSet = leftInput.toSet
      val middleSet = middleInput.toSet

      assertEquals(
        left.union(middle).ordinalsInDomainOrder.toSet,
        leftSet union middleSet
      )
      assertEquals(
        left.intersect(middle).ordinalsInDomainOrder.toSet,
        leftSet intersect middleSet
      )
      assertEquals(
        left.diff(middle).ordinalsInDomainOrder.toSet,
        leftSet diff middleSet
      )
      assertEquals(left.complement.ordinalsInDomainOrder.toSet, universe diff leftSet)
      assertEquals(left.union(middle), middle.union(left))
      assertEquals(left.intersect(middle), middle.intersect(left))
      assertEquals(left.union(middle).union(right), left.union(middle.union(right)))
      assertEquals(
        left.intersect(middle).intersect(right),
        left.intersect(middle.intersect(right))
      )
      assertEquals(
        left.intersect(middle.union(right)),
        left.intersect(middle).union(left.intersect(right))
      )
      assertEquals(left.union(empty), left)
      assertEquals(left.intersect(whole), left)
      assertEquals(left.union(whole), whole)
      assertEquals(left.intersect(empty), empty)
      assertEquals(left.complement.complement, left)
      assertEquals(left.union(left.complement), whole)
      assertEquals(left.intersect(left.complement), empty)
      assertEquals(
        left.union(middle).complement,
        left.complement.intersect(middle.complement)
      )
      assertEquals(
        left.xor(middle),
        left.diff(middle).union(middle.diff(left))
      )
      assert(left.subsetOf(left))

  property("TotalMap forms a category and satisfies image-pullback laws"):
    forAll(totalMapCase):
      (size, firstTargets, secondTargets, thirdTargets, sourceInput, targetInput) =>
        val space = restored(s"map-$size", size).space
        val first =
          mustRight(TotalMap.fromTargetOrdinals(space, space, firstTargets))
        val second =
          mustRight(TotalMap.fromTargetOrdinals(space, space, secondTargets))
        val third =
          mustRight(TotalMap.fromTargetOrdinals(space, space, thirdTargets))
        val source = mustRight(Region.fromOrdinals(space, sourceInput))
        val target = mustRight(Region.fromOrdinals(space, targetInput))

        assertEquals(
          TotalMap.identity(space).andThen(first),
          first
        )
        assertEquals(first.andThen(TotalMap.identity(space)), first)
        assertEquals(
          first.andThen(second).andThen(third),
          first.andThen(second.andThen(third))
        )
        assertEquals(
          first.pullback(target.complement),
          first.pullback(target).complement
        )
        assertEquals(
          first.image(source).subsetOf(target),
          source.subsetOf(first.pullback(target))
        )

  property("Relation CSR operations match a reference relation model"):
    forAll(relationCase): (size, firstRows, secondRows, thirdRows, sourceInput) =>
      val space = restored(s"relation-$size", size).space
      val first =
        mustRight(Relation.fromOrdinalRows(space, space, firstRows))
      val second =
        mustRight(Relation.fromOrdinalRows(space, space, secondRows))
      val third =
        mustRight(Relation.fromOrdinalRows(space, space, thirdRows))
      val region = mustRight(Region.fromOrdinals(space, sourceInput))
      val firstModel = canonicalRows(firstRows)
      val secondModel = canonicalRows(secondRows)

      assertEquals(
        first.ordinalRows.map(_.toVector).toVector,
        firstModel
      )
      assertEquals(
        first.andThen(second).ordinalRows.map(_.toVector).toVector,
        compose(firstModel, secondModel)
      )
      assertEquals(first.converse.converse, first)
      assertEquals(
        first.andThen(second).andThen(third),
        first.andThen(second.andThen(third))
      )
      assertEquals(Relation.identity(space).andThen(first), first)
      assertEquals(first.andThen(Relation.identity(space)), first)
      assertEquals(
        first.union(second).ordinalRows.map(_.toSet).toVector,
        firstModel.zip(secondModel).map((left, right) => left.toSet union right.toSet)
      )
      assertEquals(
        first.intersect(second).ordinalRows.map(_.toSet).toVector,
        firstModel
          .zip(secondModel)
          .map((left, right) => left.toSet intersect right.toSet)
      )
      assertEquals(
        first.andThen(second).image(region),
        second.image(first.image(region))
      )

  property("Selection is an injection with an owned position domain"):
    forAll(selectionCase): (size, ordinals) =>
      val space = restored(s"selection-$size", size).space
      val selection =
        mustRight(Selection.fromOrdinals(space, ordinals))

      assertEquals(selection.ordinals.toVector, ordinals)
      assertEquals(selection.size, ordinals.size)
      assert(selection.positions.size == ordinals.size)
      assert(!selection.positions.isPersistable)
      assertEquals(
        selection.support,
        selection.embedding.toTotalMap.image(
          Region.whole(selection.positions)
        )
      )
      selection.positions.indices.foreach: position =>
        assertEquals(
          selection(position).ordinal,
          ordinals(position.ordinal)
        )

  test("empty and whole Region use canonical edge semantics"):
    val emptySpace = restored("empty", 0).space
    val singleton = restored("singleton", 1).space

    assert(Region.empty(emptySpace).isEmpty)
    assert(Region.whole(emptySpace).isEmpty)
    assert(Region.whole(singleton).isWhole)
    assertEquals(
      Region.whole(singleton).complement,
      Region.empty(singleton)
    )

  test("validated fast constructors reject malformed structures"):
    val space = restored("malformed", 4).space
    assertEquals(
      Region.fromSortedDistinct(space, Vector(0, 2, 2)),
      Left(RegionError.NotStrictlyIncreasing(2, 2, 2))
    )
    assertEquals(
      TotalMap.fromTargetOrdinals(space, space, Vector(0, 1)),
      Left(TotalMapError.WrongTargetCount(4, 2))
    )
    assertEquals(
      Selection.fromOrdinals(space, Vector(1, 2, 1)),
      Left(SelectionError.DuplicateOrdinal(1))
    )
    assertEquals(
      Relation.fromCsr(
        space,
        space,
        Vector(0, 1, 1, 1, 1),
        Vector(4)
      ),
      Left(RelationError.TargetOutOfBounds(0, 0, 4, 4))
    )
    assertEquals(
      Relation.fromCsr(
        space,
        space,
        Vector(0, 2, 2, 2, 2),
        Vector(2, 1)
      ),
      Left(RelationError.RowNotStrictlyIncreasing(0, 1, 2, 1))
    )

  private val regionCase =
    for
      size <- Gen.choose(0, 20)
      left <- boundedList(size)
      middle <- boundedList(size)
      right <- boundedList(size)
    yield (size, left, middle, right)

  private val totalMapCase =
    for
      size <- Gen.choose(0, 16)
      first <- targets(size)
      second <- targets(size)
      third <- targets(size)
      source <- boundedList(size)
      target <- boundedList(size)
    yield (size, first, second, third, source, target)

  private val relationCase =
    for
      size <- Gen.choose(0, 12)
      first <- rows(size)
      second <- rows(size)
      third <- rows(size)
      source <- boundedList(size)
    yield (size, first, second, third, source)

  private val selectionCase =
    Gen
      .choose(0, 24)
      .flatMap: size =>
        Gen
          .choose(0, size)
          .flatMap: selected =>
            Gen
              .pick(selected, (0 until size).toVector)
              .map(values => (size, values.toVector))

  private def boundedList(size: Int): Gen[Vector[Int]] =
    if size == 0 then Gen.const(Vector.empty)
    else
      Gen
        .listOf(Gen.choose(0, size - 1))
        .map(_.toVector)

  private def targets(size: Int): Gen[Vector[Int]] =
    if size == 0 then Gen.const(Vector.empty)
    else
      Gen
        .listOfN(size, Gen.choose(0, size - 1))
        .map(_.toVector)

  private def rows(size: Int): Gen[Vector[Vector[Int]]] =
    if size == 0 then Gen.const(Vector.empty)
    else
      Gen
        .listOfN(
          size,
          Gen
            .listOf(Gen.choose(0, size - 1))
            .map(_.toVector)
        )
        .map(_.toVector)

  private def canonicalRows(
      rows: Vector[Vector[Int]]
  ): Vector[Vector[Int]] =
    rows.map(_.distinct.sorted)

  private def compose(
      first: Vector[Vector[Int]],
      second: Vector[Vector[Int]]
  ): Vector[Vector[Int]] =
    first.map(row => row.flatMap(second).distinct.sorted)

  private def restored(id: String, size: Int): DomainResolution =
    val record = mustRight(DomainRecord.parse(id, id, size))
    mustRight(DomainRegistry.empty.restore(record))

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
