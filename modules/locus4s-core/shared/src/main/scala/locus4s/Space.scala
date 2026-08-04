package locus4s

enum IndexError:
  case OutOfBounds(pointOrdinal: Int, size: Int)

  def message: String =
    this match
      case OutOfBounds(pointOrdinal, size) =>
        s"ordinal $pointOrdinal is outside [0, $size)"

/** Compatibility name for checked point/index construction failures. */
@deprecated("Use IndexError; scheduled for removal in 1.0.", "0.1.0")
type PointError = IndexError

@deprecated("Use IndexError; scheduled for removal in 1.0.", "0.1.0")
object PointError:
  val OutOfBounds = IndexError.OutOfBounds

/** A bounded ordinal owned statically by one live finite domain. */
type Index[S] = FiniteDomain.IndexValue[S]

object Index:
  export FiniteDomain.IndexValue.*

/** Compatibility name: domain points are zero-cost typed indices. */
@deprecated("Use Index; scheduled for removal in 1.0.", "0.1.0")
type Point[S] = Index[S]

/** A live finite owner.
  *
  * `S` is generative: public creation returns an existential owner type and there is no
  * public constructor that can create a second owner for an existing `S`. Consequently,
  * a value already typed as `Index[S]` belongs to this owner and typed operations are
  * total.
  */
sealed abstract class FiniteDomain[S] private[locus4s] (
    val name: DomainName,
    val size: Int,
    val persistentRecord: Option[DomainRecord]
):
  final def descriptor: DomainDescriptor =
    persistentRecord match
      case Some(record) =>
        DomainDescriptor.persistent(record)
      case None =>
        DomainDescriptor.ephemeral(name, size)

  final def persistentKey: Option[DomainKey] =
    persistentRecord.map(_.key)

  final def isPersistable: Boolean =
    persistentRecord.nonEmpty

  /** Checked dynamic-boundary construction. O(1), allocating no index object. */
  final def index(ordinal: Int): Either[IndexError, Index[S]] =
    if containsOrdinal(ordinal) then Right(FiniteDomain.unsafeIndex(ordinal))
    else Left(IndexError.OutOfBounds(ordinal, size))

  final def indexOption(ordinal: Int): Option[Index[S]] =
    if containsOrdinal(ordinal) then Some(FiniteDomain.unsafeIndex(ordinal))
    else None

  /** Compatibility spelling for `index`. */
  @deprecated("Use index; scheduled for removal in 1.0.", "0.1.0")
  final def point(ordinal: Int): Either[IndexError, Index[S]] =
    index(ordinal)

  /** Compatibility spelling for `indexOption`. */
  @deprecated("Use indexOption; scheduled for removal in 1.0.", "0.1.0")
  final def pointOption(ordinal: Int): Option[Index[S]] =
    indexOption(ordinal)

  /** Non-allocating traversal in increasing ordinal order.
    *
    * The callback itself may allocate; the domain does not allocate one object per
    * ordinal.
    */
  final def foreachIndex(f: Index[S] => Unit): Unit =
    var ordinal = 0
    while ordinal < size do
      f(FiniteDomain.unsafeIndex(ordinal))
      ordinal += 1

  /** Convenience iterator. Prefer `foreachIndex` in allocation-sensitive code. */
  final def indices: Iterator[Index[S]] =
    Iterator.range(0, size).map(FiniteDomain.unsafeIndex)

  /** Compatibility spelling for `indices`. */
  @deprecated("Use indices; scheduled for removal in 1.0.", "0.1.0")
  final def points: Iterator[Index[S]] =
    indices

  /** A typed index is owned by this domain by construction. */
  final def contains(index: Index[S]): Boolean =
    containsOrdinal(index.ordinal)

  final def align[T](
      that: FiniteDomain[T]
  ): Either[DomainAlignmentError, DomainAlignment[S, T]] =
    DomainAlignment.check(this, that)

  final def samePersistentIdentityAs[T](that: FiniteDomain[T]): Boolean =
    persistentKey.nonEmpty && persistentKey == that.persistentKey

  final def sameRuntimeOwnerAs[T](that: FiniteDomain[T]): Boolean =
    this eq that

  private[locus4s] final def containsOrdinal(ordinal: Int): Boolean =
    ordinal >= 0 && ordinal < size

  /** Allocation-free conversion used after a structure has validated bounds. */
  private[locus4s] final inline def indexAtOrdinal(
      ordinal: Int
  ): Index[S] =
    if containsOrdinal(ordinal) then FiniteDomain.unsafeIndex(ordinal)
    else
      throw new IndexOutOfBoundsException(
        s"validated structure contained ordinal $ordinal outside [0, $size)"
      )

  /** Allocation-free ordinal conversion for a downstream compiled structure.
    *
    * Use `index` at an untrusted dynamic boundary. This method is for an immutable
    * structure that already proved the ordinal lies in this exact domain during its own
    * construction. An invariant violation throws instead of returning a checked
    * wrapper, so hot traversal does not allocate one wrapper per emitted index.
    */
  final inline def indexAtValidatedOrdinal(
      ordinal: Int
  ): Index[S] =
    indexAtOrdinal(ordinal)

  private[locus4s] final def mismatch[T](
      actual: FiniteDomain[T]
  ): SpaceMismatch =
    SpaceMismatch.between(this, actual)

  final override def equals(other: Any): Boolean =
    other match
      case that: FiniteDomain[?] =>
        this eq that
      case _ =>
        false

  final override def hashCode(): Int =
    System.identityHashCode(this)

  final override def toString: String =
    persistentRecord match
      case Some(record) =>
        s"FiniteSpace(${name.value}, ${record.id.value}, $size)"
      case None =>
        s"FiniteDomain(${name.value}, ephemeral, $size)"

/** A persistable finite domain reconstructed by `DomainRegistry`. */
sealed abstract class FiniteSpace[S] private[locus4s] (
    val record: DomainRecord
) extends FiniteDomain[S](
      record.name,
      record.size,
      Some(record)
    ):
  final def id: DomainId =
    record.id

  final def key: DomainKey =
    record.key

/** Existential live domain used at dynamic and serialization boundaries. */
sealed trait SomeFiniteDomain:
  type S
  val value: FiniteDomain[S]

/** Existential persisted space used by the registry. */
sealed trait SomeFiniteSpace extends SomeFiniteDomain:
  override val value: FiniteSpace[S]

/** Result of checked restoration.
  *
  * The abstract `S` prevents callers from inventing static owner evidence.
  */
sealed trait DomainResolution:
  type S
  val registry: DomainRegistry
  val space: FiniteSpace[S]

object FiniteDomain:
  opaque type IndexValue[S] = Int

  object IndexValue:
    extension [S](index: IndexValue[S])
      inline def ordinal: Int =
        index

      /** Compatibility spelling for the former owned-point API. */
      inline def value: Int =
        index

  private def unsafeIndex[S](ordinal: Int): IndexValue[S] =
    ordinal

  private final class LiveEphemeralDomain[S](
      name: DomainName,
      size: Int
  ) extends FiniteDomain[S](name, size, None)

  private final class PackedFiniteDomain[A](
      val value: FiniteDomain[A]
  ) extends SomeFiniteDomain:
    type S = A

  /** Create a process-local derived domain.
    *
    * Ephemeral domains cannot align with independently created owners and cannot be
    * serialized as persisted spaces. They are appropriate for selection-position
    * domains and other derived in-memory structures.
    */
  def ephemeral(
      name: String,
      size: Int
  ): Either[DomainError, SomeFiniteDomain] =
    for
      parsedName <- DomainName.parse(name)
      _ <-
        if size < 0 then Left(DomainError.NegativeSize(size))
        else Right(())
    yield ephemeralValidated(parsedName, size)

  private[locus4s] def ephemeralValidated(
      name: DomainName,
      size: Int
  ): SomeFiniteDomain =
    final class EphemeralOwner
    new PackedFiniteDomain(
      new LiveEphemeralDomain[EphemeralOwner](name, size)
    )

/** Immutable registry that canonicalizes caller-supplied persistent identity.
  *
  * It deliberately does not generate fresh identifiers. The caller or an
  * effectful/atomic factory owns that policy, so branching this pure value cannot mint
  * duplicate persistent identities.
  */
final class DomainRegistry private (
    private val entries: Map[DomainId, SomeFiniteSpace]
):
  def register(
      record: DomainRecord
  ): Either[DomainRestoreError, DomainResolution] =
    restore(record)

  def restore(
      record: DomainRecord
  ): Either[DomainRestoreError, DomainResolution] =
    entries.get(record.id) match
      case Some(existing) =>
        if existing.value.key == record.key then
          Right(DomainRegistry.resolution(this, existing.value))
        else
          Left(
            DomainRestoreError.ConflictingKey(
              existing.value.record,
              record
            )
          )
      case None =>
        Right(registerNew(record))

  def find(id: DomainId): Option[SomeFiniteSpace] =
    entries.get(id)

  def size: Int =
    entries.size

  private def registerNew(record: DomainRecord): DomainResolution =
    final class RestoredDomain
    val space =
      new DomainRegistry.LiveFiniteSpace[RestoredDomain](record)
    val packed = DomainRegistry.packed(space)
    DomainRegistry.resolution(
      new DomainRegistry(entries.updated(record.id, packed)),
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
    new DomainRegistry(Map.empty)

/** Checked evidence that two live owners present one domain.
  *
  * Persisted owners align when their structural keys agree. An ephemeral owner aligns
  * only with itself. Its ordinal action is identity, so transport is allocation-free
  * and total after the alignment has been constructed.
  */
final class DomainAlignment[A, B] private (
    val left: FiniteDomain[A],
    val right: FiniteDomain[B]
):
  def toRight(index: Index[A]): Index[B] =
    right.indexAtOrdinal(index.ordinal)

  def toLeft(index: Index[B]): Index[A] =
    left.indexAtOrdinal(index.ordinal)

  def reverse: DomainAlignment[B, A] =
    new DomainAlignment(right, left)

  def andThen[C](
      that: DomainAlignment[B, C]
  ): DomainAlignment[A, C] =
    new DomainAlignment(left, that.right)

  def transport(region: Region[A]): Region[B] =
    region.rebind(this)

  def transport(selection: Selection[A]): Selection[B] =
    selection.rebind(this)

  def transportFrom[Y](mapping: TotalMap[A, Y]): TotalMap[B, Y] =
    mapping.rebindFrom(this)

  def transportTo[X](mapping: TotalMap[X, A]): TotalMap[X, B] =
    mapping.rebindTo(this)

  def transportFrom[Y](mapping: PartialMap[A, Y]): PartialMap[B, Y] =
    mapping.rebindFrom(this)

  def transportTo[X](mapping: PartialMap[X, A]): PartialMap[X, B] =
    mapping.rebindTo(this)

  def transportFrom[Y](injection: Injection[A, Y]): Injection[B, Y] =
    injection.rebindFrom(this)

  def transportTo[X](injection: Injection[X, A]): Injection[X, B] =
    injection.rebindTo(this)

  def transportFrom[Y](surjection: Surjection[A, Y]): Surjection[B, Y] =
    surjection.rebindFrom(this)

  def transportTo[X](surjection: Surjection[X, A]): Surjection[X, B] =
    surjection.rebindTo(this)

  def transportFrom[Y](bijection: Bijection[A, Y]): Bijection[B, Y] =
    bijection.rebindFrom(this)

  def transportTo[X](bijection: Bijection[X, A]): Bijection[X, B] =
    bijection.rebindTo(this)

  def transportFrom[Y](
      surjection: PartialSurjection[A, Y]
  ): PartialSurjection[B, Y] =
    surjection.rebindFrom(this)

  def transportTo[X](
      surjection: PartialSurjection[X, A]
  ): PartialSurjection[X, B] =
    surjection.rebindTo(this)

  def transportFrom[Y](relation: Relation[A, Y]): Relation[B, Y] =
    relation.rebindFrom(this)

  def transportTo[X](relation: Relation[X, A]): Relation[X, B] =
    relation.rebindTo(this)

object DomainAlignment:
  def identity[S](space: FiniteDomain[S]): DomainAlignment[S, S] =
    new DomainAlignment(space, space)

  def check[A, B](
      left: FiniteDomain[A],
      right: FiniteDomain[B]
  ): Either[DomainAlignmentError, DomainAlignment[A, B]] =
    if left.sameRuntimeOwnerAs(right) || left.samePersistentIdentityAs(right)
    then Right(new DomainAlignment(left, right))
    else Left(DomainAlignmentError(left.descriptor, right.descriptor))
