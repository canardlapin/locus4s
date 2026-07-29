package locus4s

enum PointError:
  case OutOfBounds(pointOrdinal: Int, size: Int)
  case ForeignDomain(expected: DomainRecord, actual: DomainRecord)

  def message: String =
    this match
      case OutOfBounds(pointOrdinal, size) =>
        s"ordinal $pointOrdinal is outside [0, $size)"
      case ForeignDomain(expected, actual) =>
        s"point belongs to ${actual.id.value}, expected ${expected.id.value}"

/** A bounded ordinal carrying its live finite-space owner.
  *
  * The class is sealed so package-joining consumers cannot manufacture a
  * subtype through its package-visible constructor.
  */
sealed abstract class Ordinal[S] private[locus4s] (
    private val owner: FiniteSpace[S],
    val value: Int
):
  final def domain: DomainRecord =
    owner.record

  private[locus4s] final def belongsTo(space: FiniteSpace[?]): Boolean =
    owner eq space

  final override def equals(other: Any): Boolean =
    other match
      case that: Ordinal[?] =>
        owner.eq(that.owner) && value == that.value
      case _ =>
        false

  final override def hashCode(): Int =
    31 * owner.hashCode() + value

  final override def toString: String =
    s"Point(${domain.name.value}, $value)"

/** Domain points are the domain's owned ordinals, not a parallel
  * representation.
  */
type Point[S] = Ordinal[S]

/** A finite domain whose static owner `S` is created only by fresh or restore.
  *
  * This class is both the public space and its unforgeable runtime owner token.
  * Its sealed implementation is private to this source file's registry
  * companion; package membership alone grants no construction path.
  */
sealed abstract class FiniteSpace[S] private[locus4s] (
    val record: DomainRecord
):
  final def id: DomainId =
    record.id

  final def name: DomainName =
    record.name

  final def size: Int =
    record.size

  final def point(ordinal: Int): Either[PointError, Point[S]] =
    if containsOrdinal(ordinal) then Right(ownedPoint(ordinal))
    else Left(PointError.OutOfBounds(ordinal, size))

  final def pointOption(ordinal: Int): Option[Point[S]] =
    if containsOrdinal(ordinal) then Some(ownedPoint(ordinal))
    else None

  final def points: Iterator[Point[S]] =
    Iterator.range(0, size).map(ownedPoint)

  final def contains(point: Point[S]): Boolean =
    owns(point) && containsOrdinal(point.value)

  final def align[T](
      that: FiniteSpace[T]
  ): Either[DomainAlignmentError, DomainAlignment[S, T]] =
    DomainAlignment.check(this, that)

  final def samePersistentIdentityAs[T](that: FiniteSpace[T]): Boolean =
    record == that.record

  final def sameRuntimeOwnerAs[T](that: FiniteSpace[T]): Boolean =
    this eq that

  private[locus4s] final def validate(
      point: Point[S]
  ): Either[PointError, Int] =
    if !owns(point) then
      Left(PointError.ForeignDomain(record, point.domain))
    else if !containsOrdinal(point.value) then
      Left(PointError.OutOfBounds(point.value, size))
    else
      Right(point.value)

  private[locus4s] final def owns(point: Ordinal[?]): Boolean =
    point.belongsTo(this)

  private[locus4s] final def containsOrdinal(ordinal: Int): Boolean =
    ordinal >= 0 && ordinal < size

  private[locus4s] final def mismatch[T](
      actual: FiniteSpace[T]
  ): SpaceMismatch =
    SpaceMismatch(record, actual.record, record == actual.record)

  private final def ownedPoint(ordinal: Int): Point[S] =
    FiniteSpace.ownedPoint(this, ordinal)

  final override def equals(other: Any): Boolean =
    other match
      case that: FiniteSpace[?] =>
        this eq that
      case _ =>
        false

  final override def hashCode(): Int =
    System.identityHashCode(this)

  final override def toString: String =
    s"FiniteSpace(${record.name.value}, ${record.id.value}, $size)"

object FiniteSpace:
  private final class OwnedOrdinal[S](
      owner: FiniteSpace[S],
      value: Int
  ) extends Ordinal[S](owner, value)

  private def ownedPoint[S](
      owner: FiniteSpace[S],
      ordinal: Int
  ): Point[S] =
    new OwnedOrdinal(owner, ordinal)

/** Checked evidence that two live owners denote the same persistent domain. */
final class DomainAlignment[A, B] private (
    val left: FiniteSpace[A],
    val right: FiniteSpace[B]
):
  def toRight(point: Point[A]): Either[PointError, Point[B]] =
    left.validate(point).flatMap(right.point)

  def toLeft(point: Point[B]): Either[PointError, Point[A]] =
    right.validate(point).flatMap(left.point)

  def reverse: DomainAlignment[B, A] =
    new DomainAlignment(right, left)

  def regionToRight(region: Region[A]): Either[SpaceMismatch, Region[B]] =
    region.rebind(this)

  def regionToLeft(region: Region[B]): Either[SpaceMismatch, Region[A]] =
    region.rebind(reverse)

  def selectionToRight(
      selection: Selection[A]
  ): Either[SpaceMismatch, Selection[B]] =
    selection.rebind(this)

  def selectionToLeft(
      selection: Selection[B]
  ): Either[SpaceMismatch, Selection[A]] =
    selection.rebind(reverse)

object DomainAlignment:
  def check[A, B](
      left: FiniteSpace[A],
      right: FiniteSpace[B]
  ): Either[DomainAlignmentError, DomainAlignment[A, B]] =
    if left.record == right.record then
      Right(new DomainAlignment(left, right))
    else
      Left(DomainAlignmentError(left.record, right.record))

/** An existential finite domain used at serialization and other dynamic
  * boundaries.
  */
sealed trait SomeFiniteSpace:
  type S
  val value: FiniteSpace[S]

/** Result of fresh construction or checked restoration.
  *
  * The abstract `S` prevents callers from inventing static domain evidence.
  */
sealed trait DomainResolution:
  type S
  val registry: DomainRegistry
  val space: FiniteSpace[S]

/** Immutable registry for checked recovery of runtime domain owners.
  *
  * Callers must retain the returned registry. Re-restoring an identical record
  * through that value returns the same live owner; conflicting metadata for a
  * registered persistent id is rejected. Fresh construction is available only
  * when the registry was built with an explicit immutable `DomainIdSource`.
  */
final class DomainRegistry private (
    private val entries: Map[DomainId, SomeFiniteSpace],
    private val idSource: Option[DomainIdSource]
):
  def fresh(
      name: String,
      size: Int
  ): Either[DomainFreshError, DomainResolution] =
    idSource match
      case None =>
        Left(DomainFreshError.MissingIdSource)
      case Some(source) =>
        for
          step <- source.next.left.map(DomainFreshError.IdSourceFailure.apply)
          record <-
            DomainRecord
              .make(step.id, name, size)
              .left
              .map(DomainFreshError.InvalidRecord.apply)
          resolution <-
            if entries.contains(record.id) then
              Left(DomainFreshError.GeneratedIdCollision(record.id))
            else
              Right(registerNew(record, Some(step.nextSource)))
        yield resolution

  def restore(
      record: DomainRecord
  ): Either[DomainRestoreError, DomainResolution] =
    entries.get(record.id) match
      case Some(existing) =>
        if existing.value.record == record then
          Right(DomainRegistry.resolution(this, existing.value))
        else
          Left(
            DomainRestoreError.ConflictingRecord(
              existing.value.record,
              record
            )
          )
      case None =>
        Right(registerNew(record, idSource))

  def find(id: DomainId): Option[SomeFiniteSpace] =
    entries.get(id)

  def size: Int =
    entries.size

  private def registerNew(
      record: DomainRecord,
      nextSource: Option[DomainIdSource]
  ): DomainResolution =
    final class RestoredDomain
    val space =
      new DomainRegistry.LiveFiniteSpace[RestoredDomain](record)
    val packed = DomainRegistry.packed(space)
    DomainRegistry.resolution(
      new DomainRegistry(entries.updated(record.id, packed), nextSource),
      space
    )

object DomainRegistry:
  private final class LiveFiniteSpace[S](
      record: DomainRecord
  ) extends FiniteSpace[S](record)

  private final class PackedFiniteSpace[A](
      val value: FiniteSpace[A]
  ) extends SomeFiniteSpace:
    type S = A

  private final class ResolvedDomain[A](
      val registry: DomainRegistry,
      val space: FiniteSpace[A]
  ) extends DomainResolution:
    type S = A

  private def packed[A](
      space: FiniteSpace[A]
  ): SomeFiniteSpace { type S = A } =
    new PackedFiniteSpace(space)

  private def resolution[A](
      registry: DomainRegistry,
      space: FiniteSpace[A]
  ): DomainResolution { type S = A } =
    new ResolvedDomain(registry, space)

  val empty: DomainRegistry =
    new DomainRegistry(Map.empty, None)

  def withIdSource(source: DomainIdSource): DomainRegistry =
    new DomainRegistry(Map.empty, Some(source))

  def withSequentialIds(
      prefix: String,
      startAt: Long = 0L
  ): Either[DomainIdSourceConfigError, DomainRegistry] =
    DomainIdSource
      .sequential(prefix, startAt)
      .map(withIdSource)
