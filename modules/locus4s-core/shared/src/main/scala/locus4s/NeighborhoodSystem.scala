package locus4s

enum NeighborhoodSystemError:
  case MembershipCenterOwnerMismatch(mismatch: SpaceMismatch)
  case MembershipAmbientOwnerMismatch(mismatch: SpaceMismatch)
  case MissingEmbeddedCenter(centerOrdinal: Int, ambientOrdinal: Int)

  def message: String =
    this match
      case MembershipCenterOwnerMismatch(mismatch) =>
        s"neighborhood membership has the wrong center owner: ${mismatch.message}"
      case MembershipAmbientOwnerMismatch(mismatch) =>
        s"neighborhood membership has the wrong ambient owner: ${mismatch.message}"
      case MissingEmbeddedCenter(center, ambient) =>
        s"center $center embeds at ambient ordinal $ambient but is absent from its neighborhood"

/** Compact neighborhoods for an ordered center domain embedded in an ambient domain.
  *
  * Membership rows are indexed by `C`, so CSR row-offset storage is proportional to the
  * number of actual centers rather than the ambient-domain size.
  */
final class NeighborhoodSystem[C, S] private (
    val centerEmbedding: Injection[C, S],
    val membership: Relation[C, S]
):
  def centers: FiniteDomain[C] =
    centerEmbedding.from

  def ambient: FiniteDomain[S] =
    centerEmbedding.to

  def center(index: Index[C]): Index[S] =
    centerEmbedding(index)

  /** Materialize one neighborhood in O(row degree). */
  def neighborhood(index: Index[C]): Region[S] =
    membership.row(index)

  def foreachNeighbor(index: Index[C])(f: Index[S] => Unit): Unit =
    membership.foreachTarget(index)(f)

  /** O(1) center-owner transport sharing the embedding and CSR payloads. */
  def rebindCenters[A](
      alignment: DomainAlignment[C, A]
  ): NeighborhoodSystem[A, S] =
    new NeighborhoodSystem(
      centerEmbedding.rebindFrom(alignment),
      membership.rebindFrom(alignment)
    )

  /** O(1) ambient-owner transport sharing the embedding and CSR payloads. */
  def rebindAmbient[B](
      alignment: DomainAlignment[S, B]
  ): NeighborhoodSystem[C, B] =
    new NeighborhoodSystem(
      centerEmbedding.rebindTo(alignment),
      membership.rebindTo(alignment)
    )

  override def equals(other: Any): Boolean =
    other match
      case that: NeighborhoodSystem[?, ?] =>
        centerEmbedding == that.centerEmbedding && membership == that.membership
      case _ =>
        false

  override def hashCode(): Int =
    31 * centerEmbedding.hashCode() + membership.hashCode()

  override def toString: String =
    s"NeighborhoodSystem(${centers.name.value} -> ${ambient.name.value}, centers=${centers.size}, pairs=${membership.pairCount})"

object NeighborhoodSystem:
  /** Validate exact live endpoint owners at a dynamic boundary. */
  def from[C, S, A, B](
      centerEmbedding: Injection[C, S],
      membership: Relation[A, B]
  ): Either[NeighborhoodSystemError, NeighborhoodSystem[C, S]] =
    if !centerEmbedding.from.sameRuntimeOwnerAs(membership.from) then
      Left(
        NeighborhoodSystemError.MembershipCenterOwnerMismatch(
          centerEmbedding.from.mismatch(membership.from)
        )
      )
    else if !centerEmbedding.to.sameRuntimeOwnerAs(membership.to) then
      Left(
        NeighborhoodSystemError.MembershipAmbientOwnerMismatch(
          centerEmbedding.to.mismatch(membership.to)
        )
      )
    else
      centerEmbedding.from.align(membership.from) match
        case Left(_) =>
          Left(
            NeighborhoodSystemError.MembershipCenterOwnerMismatch(
              centerEmbedding.from.mismatch(membership.from)
            )
          )
        case Right(centerAlignment) =>
          centerEmbedding.to.align(membership.to) match
            case Left(_) =>
              Left(
                NeighborhoodSystemError.MembershipAmbientOwnerMismatch(
                  centerEmbedding.to.mismatch(membership.to)
                )
              )
            case Right(ambientAlignment) =>
              Right(
                new NeighborhoodSystem(
                  centerEmbedding,
                  membership
                    .rebindFrom(centerAlignment.reverse)
                    .rebindTo(ambientAlignment.reverse)
                )
              )

  /** Construct after the caller has explicitly aligned distinct persisted owners. */
  def fromAligned[C, S, A, B](
      centerEmbedding: Injection[C, S],
      membership: Relation[A, B],
      centerAlignment: DomainAlignment[C, A],
      ambientAlignment: DomainAlignment[S, B]
  ): NeighborhoodSystem[C, S] =
    new NeighborhoodSystem(
      centerEmbedding,
      membership
        .rebindFrom(centerAlignment.reverse)
        .rebindTo(ambientAlignment.reverse)
    )

  def fromIdentityCenters[S](
      membership: Relation[S, S]
  ): NeighborhoodSystem[S, S] =
    new NeighborhoodSystem(Injection.identity(membership.from), membership)

  def fromSelection[S](
      selection: Selection[S],
      membership: Relation[selection.I, S]
  ): NeighborhoodSystem[selection.I, S] =
    new NeighborhoodSystem(selection.embedding, membership)

/** A NeighborhoodSystem in which every embedded center belongs to its own row. */
final class CenteredNeighborhoodSystem[C, S] private (
    val toNeighborhoodSystem: NeighborhoodSystem[C, S]
):
  export toNeighborhoodSystem.{
    ambient,
    center,
    centerEmbedding,
    centers,
    foreachNeighbor,
    membership,
    neighborhood
  }

  def rebindCenters[A](
      alignment: DomainAlignment[C, A]
  ): CenteredNeighborhoodSystem[A, S] =
    new CenteredNeighborhoodSystem(
      toNeighborhoodSystem.rebindCenters(alignment)
    )

  def rebindAmbient[B](
      alignment: DomainAlignment[S, B]
  ): CenteredNeighborhoodSystem[C, B] =
    new CenteredNeighborhoodSystem(
      toNeighborhoodSystem.rebindAmbient(alignment)
    )

  override def equals(other: Any): Boolean =
    other match
      case that: CenteredNeighborhoodSystem[?, ?] =>
        toNeighborhoodSystem == that.toNeighborhoodSystem
      case _ =>
        false

  override def hashCode(): Int =
    toNeighborhoodSystem.hashCode()

  override def toString: String =
    s"Centered$toNeighborhoodSystem"

object CenteredNeighborhoodSystem:
  def from[C, S](
      neighborhoods: NeighborhoodSystem[C, S]
  ): Either[NeighborhoodSystemError, CenteredNeighborhoodSystem[C, S]] =
    var centerOrdinal = 0
    var missing = Option.empty[NeighborhoodSystemError]
    while centerOrdinal < neighborhoods.centers.size && missing.isEmpty do
      val center =
        neighborhoods.centers.indexAtValidatedOrdinal(centerOrdinal)
      val ambient = neighborhoods.center(center)
      if !neighborhoods.membership.isRelated(center, ambient) then
        missing = Some(
          NeighborhoodSystemError.MissingEmbeddedCenter(
            centerOrdinal,
            ambient.ordinal
          )
        )
      centerOrdinal += 1

    missing match
      case Some(error) => Left(error)
      case None        => Right(new CenteredNeighborhoodSystem(neighborhoods))

  def from[C, S, A, B](
      centerEmbedding: Injection[C, S],
      membership: Relation[A, B]
  ): Either[NeighborhoodSystemError, CenteredNeighborhoodSystem[C, S]] =
    NeighborhoodSystem.from(centerEmbedding, membership).flatMap(from)

  def fromAligned[C, S, A, B](
      centerEmbedding: Injection[C, S],
      membership: Relation[A, B],
      centerAlignment: DomainAlignment[C, A],
      ambientAlignment: DomainAlignment[S, B]
  ): Either[NeighborhoodSystemError, CenteredNeighborhoodSystem[C, S]] =
    from(
      NeighborhoodSystem.fromAligned(
        centerEmbedding,
        membership,
        centerAlignment,
        ambientAlignment
      )
    )

  def fromIdentityCenters[S](
      membership: Relation[S, S]
  ): Either[NeighborhoodSystemError, CenteredNeighborhoodSystem[S, S]] =
    from(NeighborhoodSystem.fromIdentityCenters(membership))

  def fromSelection[S](
      selection: Selection[S],
      membership: Relation[selection.I, S]
  ): Either[
    NeighborhoodSystemError,
    CenteredNeighborhoodSystem[selection.I, S]
  ] =
    from(NeighborhoodSystem.fromSelection(selection, membership))
