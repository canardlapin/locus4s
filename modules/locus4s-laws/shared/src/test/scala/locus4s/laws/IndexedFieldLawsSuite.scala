package locus4s.laws

import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.TotalMap
import locus4s.data.IndexedField
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

final class IndexedFieldLawsSuite extends ScalaCheckSuite:
  private val record =
    mustRight(DomainRecord.parse("field-laws", "field-laws", 12))
  private val resolution =
    mustRight(DomainRegistry.empty.restore(record))
  private val space =
    resolution.space

  property("IndexedField map obeys identity and composition"):
    forAll(Gen.listOfN(space.size, Gen.choose(-1000, 1000))): values =>
      val field =
        mustRight(IndexedField.fromValues(space, values))

      assert(IndexedFieldLaws.mapIdentity(field)(_ == _))
      assert(
        IndexedFieldLaws.mapComposition(
          field,
          (value: Int) => value + 7,
          (value: Int) => value * 3
        )(_ == _)
      )

  property("lawful sum aggregation fuses through total-map composition"):
    val middleRecord =
      mustRight(DomainRecord.parse("field-laws-middle", "middle", 5))
    val targetRecord =
      mustRight(DomainRecord.parse("field-laws-target", "target", 3))
    val middle =
      mustRight(DomainRegistry.empty.restore(middleRecord)).space
    val target =
      mustRight(DomainRegistry.empty.restore(targetRecord)).space
    forAll(
      Gen.listOfN(space.size, Gen.choose(-1000, 1000)),
      Gen.listOfN(space.size, Gen.choose(0, middle.size - 1)),
      Gen.listOfN(middle.size, Gen.choose(0, target.size - 1))
    ): (values, firstTargets, secondTargets) =>
      val field =
        mustRight(IndexedField.fromValues(space, values))
      val first =
        mustRight(
          TotalMap.fromTargetOrdinals(space, middle, firstTargets)
        )
      val second =
        mustRight(
          TotalMap.fromTargetOrdinals(middle, target, secondTargets)
        )

      assert(
        mustRight(
          AggregationLaws.totalMapFusion(first, second, field)(0)(
            identity
          )(_ + _)(_ == _)
        )
      )

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
