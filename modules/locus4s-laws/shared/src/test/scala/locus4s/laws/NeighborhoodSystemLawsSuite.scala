package locus4s.laws

import locus4s.CenteredNeighborhoodSystem
import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.Injection
import locus4s.NeighborhoodSystem
import locus4s.Relation
import munit.FunSuite

final class NeighborhoodSystemLawsSuite extends FunSuite:
  test("compact neighborhood endpoint, order, centered, and rebind laws"):
    val centerRecord =
      mustRight(DomainRecord.parse("law-centers", "centers", 2))
    val ambientRecord =
      mustRight(DomainRecord.parse("law-ambient", "ambient", 5))
    val leftCenters = mustRight(DomainRegistry.empty.restore(centerRecord)).space
    val rightCenters = mustRight(DomainRegistry.empty.restore(centerRecord)).space
    val leftAmbient = mustRight(DomainRegistry.empty.restore(ambientRecord)).space
    val rightAmbient = mustRight(DomainRegistry.empty.restore(ambientRecord)).space
    val embedding =
      mustRight(
        Injection.fromTargetOrdinals(leftCenters, leftAmbient, Vector(1, 4))
      )
    val membership =
      mustRight(
        Relation.fromOrdinalRows(
          leftCenters,
          leftAmbient,
          Vector(Vector(0, 1), Vector(3, 4))
        )
      )
    val neighborhoods = mustRight(NeighborhoodSystem.from(embedding, membership))
    val centered = mustRight(CenteredNeighborhoodSystem.from(neighborhoods))
    val centerAlignment = mustRight(leftCenters.align(rightCenters))
    val ambientAlignment = mustRight(leftAmbient.align(rightAmbient))

    assert(NeighborhoodSystemLaws.endpointOwners(neighborhoods))
    assert(NeighborhoodSystemLaws.centerOrder(neighborhoods))
    assert(NeighborhoodSystemLaws.centeredReflexivity(centered))
    assert(
      NeighborhoodSystemLaws.rebindRoundTrip(
        neighborhoods,
        centerAlignment,
        ambientAlignment
      )
    )

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
