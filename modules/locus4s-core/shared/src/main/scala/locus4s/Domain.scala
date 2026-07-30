package locus4s

enum DomainError:
  case EmptyId
  case EmptyName
  case EmptyFingerprint
  case NegativeSize(size: Int)

  def message: String =
    this match
      case EmptyId =>
        "domain id must be non-empty"
      case EmptyName =>
        "domain name must be non-empty"
      case EmptyFingerprint =>
        "domain fingerprint must be non-empty when supplied"
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

/** Optional opaque structural fingerprint supplied by a downstream domain owner.
  *
  * locus4s never interprets this value. A geometry or topology library may use it to
  * distinguish two indexed domains that share a size but not a canonical structure.
  */
opaque type DomainFingerprint = String

object DomainFingerprint:
  def parse(value: String): Either[DomainError, DomainFingerprint] =
    val normalized = value.trim
    if normalized.isEmpty then Left(DomainError.EmptyFingerprint)
    else Right(normalized)

  extension (fingerprint: DomainFingerprint)
    def value: String =
      fingerprint

/** Stable structural identity for a persistable finite domain.
  *
  * Presentation metadata is deliberately absent. Two records with the same key denote
  * the same persisted indexed domain even when their labels differ.
  */
final class DomainKey private (
    val id: DomainId,
    val size: Int,
    val fingerprint: Option[DomainFingerprint]
):
  override def equals(other: Any): Boolean =
    other match
      case that: DomainKey =>
        id == that.id &&
        size == that.size &&
        fingerprint == that.fingerprint
      case _ =>
        false

  override def hashCode(): Int =
    31 * (31 * id.hashCode() + size) + fingerprint.hashCode()

  override def toString: String =
    val suffix =
      fingerprint.fold("")(value => s", fingerprint=${value.value}")
    s"DomainKey(${id.value}, size=$size$suffix)"

object DomainKey:
  def make(
      id: DomainId,
      size: Int,
      fingerprint: Option[DomainFingerprint] = None
  ): Either[DomainError, DomainKey] =
    DomainId
      .parse(id.value)
      .flatMap: parsedId =>
        if size < 0 then Left(DomainError.NegativeSize(size))
        else
          val validatedFingerprint =
            fingerprint match
              case Some(value) =>
                DomainFingerprint.parse(value.value).map(Some(_))
              case None =>
                Right(None)
          validatedFingerprint.map(new DomainKey(parsedId, size, _))

  def parse(
      id: String,
      size: Int,
      fingerprint: Option[String] = None
  ): Either[DomainError, DomainKey] =
    for
      parsedId <- DomainId.parse(id)
      parsedFingerprint <-
        fingerprint match
          case Some(value) => DomainFingerprint.parse(value).map(Some(_))
          case None        => Right(None)
      key <- make(parsedId, size, parsedFingerprint)
    yield key

/** Human-facing metadata that may change without changing domain identity. */
final class DomainMetadata private (val name: DomainName):
  override def equals(other: Any): Boolean =
    other match
      case that: DomainMetadata =>
        name == that.name
      case _ =>
        false

  override def hashCode(): Int =
    name.hashCode()

  override def toString: String =
    s"DomainMetadata(${name.value})"

object DomainMetadata:
  def make(name: DomainName): Either[DomainError, DomainMetadata] =
    DomainName.parse(name.value).map(new DomainMetadata(_))

  def parse(name: String): Either[DomainError, DomainMetadata] =
    DomainName.parse(name).map(new DomainMetadata(_))

/** Persistable domain description.
  *
  * Runtime ownership is intentionally absent. Restoring this record through a registry
  * recovers one live owner for its structural key in that registry.
  */
final class DomainRecord private (
    val key: DomainKey,
    val metadata: DomainMetadata
):
  def id: DomainId =
    key.id

  def name: DomainName =
    metadata.name

  def size: Int =
    key.size

  def fingerprint: Option[DomainFingerprint] =
    key.fingerprint

  def samePersistentIdentityAs(that: DomainRecord): Boolean =
    key == that.key

  override def equals(other: Any): Boolean =
    other match
      case that: DomainRecord =>
        key == that.key && metadata == that.metadata
      case _ =>
        false

  override def hashCode(): Int =
    31 * key.hashCode() + metadata.hashCode()

  override def toString: String =
    s"DomainRecord($key, $metadata)"

object DomainRecord:
  def make(
      key: DomainKey,
      name: String
  ): Either[DomainError, DomainRecord] =
    DomainMetadata.parse(name).map(new DomainRecord(key, _))

  def make(
      id: DomainId,
      name: String,
      size: Int,
      fingerprint: Option[DomainFingerprint] = None
  ): Either[DomainError, DomainRecord] =
    for
      key <- DomainKey.make(id, size, fingerprint)
      record <- make(key, name)
    yield record

  def parse(
      id: String,
      name: String,
      size: Int,
      fingerprint: Option[String] = None
  ): Either[DomainError, DomainRecord] =
    for
      key <- DomainKey.parse(id, size, fingerprint)
      record <- make(key, name)
    yield record

/** Safe diagnostic description of either a persisted or an ephemeral owner. */
final class DomainDescriptor private (
    val name: DomainName,
    val size: Int,
    val persistentKey: Option[DomainKey]
):
  override def equals(other: Any): Boolean =
    other match
      case that: DomainDescriptor =>
        name == that.name &&
        size == that.size &&
        persistentKey == that.persistentKey
      case _ =>
        false

  override def hashCode(): Int =
    31 * (31 * name.hashCode() + size) + persistentKey.hashCode()

  override def toString: String =
    persistentKey match
      case Some(key) =>
        s"DomainDescriptor(${name.value}, $key)"
      case None =>
        s"DomainDescriptor(${name.value}, size=$size, ephemeral)"

object DomainDescriptor:
  private[locus4s] def persistent(record: DomainRecord): DomainDescriptor =
    new DomainDescriptor(record.name, record.size, Some(record.key))

  private[locus4s] def ephemeral(
      name: DomainName,
      size: Int
  ): DomainDescriptor =
    new DomainDescriptor(name, size, None)

enum DomainRestoreError:
  case ConflictingKey(existing: DomainRecord, requested: DomainRecord)

  def message: String =
    this match
      case ConflictingKey(existing, requested) =>
        s"domain id ${requested.id.value} is already registered with " +
          s"${existing.key}, not ${requested.key}"

final case class DomainAlignmentError(
    left: DomainDescriptor,
    right: DomainDescriptor
):
  def message: String =
    s"domains do not align: $left != $right"

/** Runtime owner mismatch whose persistent-identity facts are always derived. */
final class SpaceMismatch private (
    val expected: DomainDescriptor,
    val actual: DomainDescriptor
):
  def persistentIdentityMatches: Boolean =
    expected.persistentKey.nonEmpty &&
      expected.persistentKey == actual.persistentKey

  def message: String =
    val suffix =
      if persistentIdentityMatches then
        "; persistent keys agree but runtime owners differ; align explicitly"
      else ""
    s"domain owner mismatch: expected $expected, found $actual$suffix"

  override def equals(other: Any): Boolean =
    other match
      case that: SpaceMismatch =>
        expected == that.expected && actual == that.actual
      case _ =>
        false

  override def hashCode(): Int =
    31 * expected.hashCode() + actual.hashCode()

  override def toString: String =
    s"SpaceMismatch($expected, $actual)"

object SpaceMismatch:
  /** Construct an invariant-safe diagnostic from the two actual owners.
    *
    * The persistent-identity fact is derived and cannot be supplied by the caller.
    */
  def between(
      expected: FiniteDomain[?],
      actual: FiniteDomain[?]
  ): SpaceMismatch =
    new SpaceMismatch(expected.descriptor, actual.descriptor)
