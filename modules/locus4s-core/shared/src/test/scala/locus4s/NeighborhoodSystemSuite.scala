package locus4s

import munit.FunSuite

final class NeighborhoodSystemSuite extends FunSuite:
  test("sparse centers retain compact order and ambient neighborhoods"):
    val ambient = ephemeral("neighborhood-ambient", 8)
    val selection =
      mustRight(Selection.fromOrdinals(ambient, Vector(6, 1, 4)))
    val membership =
      mustRight(
        Relation.fromOrdinalRows(
          selection.positions,
          ambient,
          Vector(Vector(5, 6, 7), Vector(0, 1), Vector(3, 4, 5))
        )
      )
    val neighborhoods =
      NeighborhoodSystem.fromSelection(selection, membership)

    assertEquals(neighborhoods.centers.size, 3)
    assertEquals(neighborhoods.ambient.size, 8)
    assertEquals(neighborhoods.membership.csr.rowOffsets.length, 4)
    assertEquals(
      neighborhoods.center(mustRight(neighborhoods.centers.index(0))).ordinal,
      6
    )
    assertEquals(
      neighborhoods
        .neighborhood(mustRight(neighborhoods.centers.index(1)))
        .ordinalsInDomainOrder
        .toSeq,
      Seq(0, 1)
    )

  test("dynamic construction rejects foreign center and ambient owners"):
    val centers = ephemeral("neighborhood-centers", 2)
    val ambient = ephemeral("neighborhood-ambient-owner", 4)
    val embedding =
      mustRight(Injection.fromTargetOrdinals(centers, ambient, Vector(0, 2)))
    val foreignCenters = ephemeral("foreign-centers", 2)
    val foreignAmbient = ephemeral("foreign-ambient", 4)
    val wrongCenters =
      mustRight(
        Relation.fromOrdinalRows(
          foreignCenters,
          ambient,
          Vector(Vector(0), Vector(2))
        )
      )
    val wrongAmbient =
      mustRight(
        Relation.fromOrdinalRows(
          centers,
          foreignAmbient,
          Vector(Vector(0), Vector(2))
        )
      )

    NeighborhoodSystem.from(embedding, wrongCenters) match
      case Left(_: NeighborhoodSystemError.MembershipCenterOwnerMismatch) => ()
      case result => fail(s"expected center owner mismatch, found $result")
    NeighborhoodSystem.from(embedding, wrongAmbient) match
      case Left(_: NeighborhoodSystemError.MembershipAmbientOwnerMismatch) => ()
      case result => fail(s"expected ambient owner mismatch, found $result")

  test("centered construction proves every embedded center is a member"):
    val centers = ephemeral("centered-centers", 2)
    val ambient = ephemeral("centered-ambient", 5)
    val embedding =
      mustRight(Injection.fromTargetOrdinals(centers, ambient, Vector(1, 4)))
    val valid =
      mustRight(
        Relation.fromOrdinalRows(
          centers,
          ambient,
          Vector(Vector(0, 1), Vector(3, 4))
        )
      )
    val invalid =
      mustRight(
        Relation.fromOrdinalRows(
          centers,
          ambient,
          Vector(Vector(0), Vector(3, 4))
        )
      )

    assert(CenteredNeighborhoodSystem.from(embedding, valid).isRight)
    assertEquals(
      CenteredNeighborhoodSystem.from(embedding, invalid),
      Left(NeighborhoodSystemError.MissingEmbeddedCenter(0, 1))
    )

  test("empty, singleton, and identity-center systems are lawful"):
    val empty = ephemeral("empty-neighborhoods", 0)
    val emptySystem =
      mustRight(
        CenteredNeighborhoodSystem.fromIdentityCenters(
          Relation.empty(empty, empty)
        )
      )
    assertEquals(emptySystem.centers.size, 0)
    assertEquals(emptySystem.membership.csr.rowOffsets.length, 0)

    val singleton = ephemeral("singleton-neighborhoods", 1)
    val singletonSystem =
      mustRight(
        CenteredNeighborhoodSystem.fromIdentityCenters(
          Relation.identity(singleton)
        )
      )
    val only = mustRight(singleton.index(0))
    assertEquals(singletonSystem.center(only), only)
    assert(singletonSystem.neighborhood(only).contains(only))

  test("explicit alignment and endpoint rebinding preserve neighborhoods"):
    val centerRecord =
      mustRight(DomainRecord.parse("aligned-centers", "centers", 2))
    val ambientRecord =
      mustRight(DomainRecord.parse("aligned-ambient", "ambient", 5))
    val leftCenters = mustRight(DomainRegistry.empty.restore(centerRecord)).space
    val rightCenters = mustRight(DomainRegistry.empty.restore(centerRecord)).space
    val leftAmbient = mustRight(DomainRegistry.empty.restore(ambientRecord)).space
    val rightAmbient = mustRight(DomainRegistry.empty.restore(ambientRecord)).space
    val embedding =
      mustRight(
        Injection.fromTargetOrdinals(leftCenters, leftAmbient, Vector(1, 4))
      )
    val rightMembership =
      mustRight(
        Relation.fromOrdinalRows(
          rightCenters,
          rightAmbient,
          Vector(Vector(0, 1), Vector(3, 4))
        )
      )
    val centerAlignment = mustRight(leftCenters.align(rightCenters))
    val ambientAlignment = mustRight(leftAmbient.align(rightAmbient))

    assert(NeighborhoodSystem.from(embedding, rightMembership).isLeft)
    val aligned =
      NeighborhoodSystem.fromAligned(
        embedding,
        rightMembership,
        centerAlignment,
        ambientAlignment
      )
    assert(aligned.centers.sameRuntimeOwnerAs(leftCenters))
    assert(aligned.ambient.sameRuntimeOwnerAs(leftAmbient))

    val rebound =
      aligned
        .rebindCenters(centerAlignment)
        .rebindAmbient(ambientAlignment)
    assert(rebound.centers.sameRuntimeOwnerAs(rightCenters))
    assert(rebound.ambient.sameRuntimeOwnerAs(rightAmbient))
    assertEquals(
      rebound.membership.ordinalRows.map(_.toSeq).toSeq,
      rightMembership.ordinalRows.map(_.toSeq).toSeq
    )

  private def ephemeral(name: String, size: Int): FiniteDomain[?] =
    mustRight(FiniteDomain.ephemeral(name, size)).value

  private def mustRight[E, A](value: Either[E, A]): A =
    value match
      case Right(result) => result
      case Left(error)   => fail(s"expected Right, found Left($error)")
