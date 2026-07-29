package locus4s

enum DomainError:
  case EmptyId
  case EmptyName
  case NegativeSize(size: Int)

  def message: String =
    this match
      case EmptyId =>
        "domain id must be non-empty"
      case EmptyName =>
        "domain name must be non-empty"
      case NegativeSize(size) =>
        s"domain size must be non-negative, found $size"

opaque type DomainId = String

object DomainId:
  def parse(value: String): Either[DomainError, DomainId] =
    val normalized = value.trim
    if normalized.isEmpty then Left(DomainError.EmptyId)
    else Right(normalized)

  extension (id: DomainId)
    def value: String =
      id

opaque type DomainName = String

object DomainName:
  def parse(value: String): Either[DomainError, DomainName] =
    val normalized = value.trim
    if normalized.isEmpty then Left(DomainError.EmptyName)
    else Right(normalized)

  extension (name: DomainName)
    def value: String =
      name

/** Stable data sufficient to persist and restore a finite domain.
  *
  * Runtime ownership is intentionally absent. Restoring this record through a
  * registry recovers one live owner for that registry.
  */
final class DomainRecord private (
    val id: DomainId,
    val name: DomainName,
    val size: Int
):
  override def equals(other: Any): Boolean =
    other match
      case that: DomainRecord =>
        id == that.id && name == that.name && size == that.size
      case _ =>
        false

  override def hashCode(): Int =
    31 * (31 * id.hashCode() + name.hashCode()) + size

  override def toString: String =
    s"DomainRecord(${id.value}, ${name.value}, $size)"

object DomainRecord:
  def make(
      id: DomainId,
      name: String,
      size: Int
  ): Either[DomainError, DomainRecord] =
    for
      parsedId <- DomainId.parse(id.value)
      parsedName <- DomainName.parse(name)
      record <- fromValidated(parsedId, parsedName, size)
    yield record

  def parse(
      id: String,
      name: String,
      size: Int
  ): Either[DomainError, DomainRecord] =
    for
      parsedId <- DomainId.parse(id)
      record <- make(parsedId, name, size)
    yield record

  private def fromValidated(
      id: DomainId,
      name: DomainName,
      size: Int
  ): Either[DomainError, DomainRecord] =
    if size < 0 then Left(DomainError.NegativeSize(size))
    else Right(new DomainRecord(id, name, size))

enum DomainRestoreError:
  case ConflictingRecord(existing: DomainRecord, requested: DomainRecord)

  def message: String =
    this match
      case ConflictingRecord(existing, requested) =>
        s"domain id ${requested.id.value} is already registered as " +
          s"${existing.name.value}[${existing.size}], not " +
          s"${requested.name.value}[${requested.size}]"

final case class DomainAlignmentError(
    left: DomainRecord,
    right: DomainRecord
):
  def message: String =
    s"domains do not align: ${left.id.value}/${left.name.value}[${left.size}] " +
      s"!= ${right.id.value}/${right.name.value}[${right.size}]"

final case class SpaceMismatch(
    expected: DomainRecord,
    actual: DomainRecord,
    persistentIdentityMatches: Boolean
):
  def message: String =
    val suffix =
      if persistentIdentityMatches then
        "; persistent records agree but runtime owners differ; align explicitly"
      else ""
    s"domain owner mismatch: expected ${expected.id.value}, " +
      s"found ${actual.id.value}$suffix"

enum DomainIdSourceError:
  case Exhausted
  case InvalidGeneratedId(error: DomainError)

  def message: String =
    this match
      case Exhausted =>
        "domain id source is exhausted"
      case InvalidGeneratedId(error) =>
        s"domain id source generated an invalid id: ${error.message}"

enum DomainIdSourceConfigError:
  case InvalidPrefix(error: DomainError)
  case NegativeStart(startAt: Long)

  def message: String =
    this match
      case InvalidPrefix(error) =>
        s"invalid domain id prefix: ${error.message}"
      case NegativeStart(startAt) =>
        s"domain id sequence start must be non-negative, found $startAt"

/** One immutable step from an explicit persistent-identity source. */
final case class DomainIdStep(
    id: DomainId,
    nextSource: DomainIdSource
)

/** Explicit, immutable policy for generating persistent domain identifiers.
  *
  * Implementations must be referentially transparent: repeated evaluation of
  * `next` on the same source value must return the same step.
  */
trait DomainIdSource:
  def next: Either[DomainIdSourceError, DomainIdStep]

object DomainIdSource:
  def sequential(
      prefix: String,
      startAt: Long = 0L
  ): Either[DomainIdSourceConfigError, DomainIdSource] =
    DomainId
      .parse(prefix)
      .left
      .map(DomainIdSourceConfigError.InvalidPrefix.apply)
      .flatMap: parsedPrefix =>
      if startAt < 0 then
        Left(DomainIdSourceConfigError.NegativeStart(startAt))
      else Right(Sequential(parsedPrefix, startAt))

  private final case class Sequential(
      prefix: DomainId,
      nextValue: Long
  ) extends DomainIdSource:
    def next: Either[DomainIdSourceError, DomainIdStep] =
      if nextValue == Long.MaxValue then
        Left(DomainIdSourceError.Exhausted)
      else
        DomainId
          .parse(s"${prefix.value}-$nextValue")
          .left
          .map(DomainIdSourceError.InvalidGeneratedId.apply)
          .map: id =>
            DomainIdStep(id, copy(nextValue = nextValue + 1L))

enum DomainFreshError:
  case MissingIdSource
  case InvalidRecord(error: DomainError)
  case IdSourceFailure(error: DomainIdSourceError)
  case GeneratedIdCollision(id: DomainId)

  def message: String =
    this match
      case MissingIdSource =>
        "fresh construction requires an explicit DomainIdSource"
      case InvalidRecord(error) =>
        error.message
      case IdSourceFailure(error) =>
        error.message
      case GeneratedIdCollision(id) =>
        s"domain id source generated an already registered id: ${id.value}"
