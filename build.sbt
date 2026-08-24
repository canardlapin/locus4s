import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbtcrossproject.{CrossProject, CrossType}
import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*

ThisBuild / organization := "io.github.canardlapin"
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / homepage := Some(url("https://github.com/canardlapin/locus4s"))
ThisBuild / licenses := List(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/canardlapin/locus4s"),
    "scm:git:https://github.com/canardlapin/locus4s.git",
    Some("scm:git:git@github.com:canardlapin/locus4s.git")
  )
)
ThisBuild / developers := List(
  Developer(
    id = "canardlapin",
    name = "canardlapin",
    email = "307091466+canardlapin@users.noreply.github.com",
    url = url("https://github.com/canardlapin")
  )
)

// sbt-ci-release/sbt-dynver owns the version. Only a clean exact v0.1.x,
// v0.1.x-Mn, or v0.1.x-RCn tag is publishable. Every other Git state remains a
// unique snapshot, so an ordinary branch can never emit a stable coordinate.
def locus4sReleaseVersion(out: sbtdynver.GitDescribeOutput): String = {
  val taggedVersion = out.ref.value.stripPrefix("v")
  val allowedTag =
    taggedVersion.matches("0\\.1\\.[0-9]+(?:-(?:M|RC)[1-9][0-9]*)?")
  val exactTag =
    allowedTag && out.ref.value.startsWith("v") && out.commitSuffix.distance == 0
  val base = if (exactTag) taggedVersion else "0.1.0"
  val commit =
    if (out.commitSuffix.distance == 0) ""
    else s"+${out.commitSuffix.distance}-${out.commitSuffix.sha}"
  val dirty =
    if (out.dirtySuffix.value.isEmpty) ""
    else s"+${out.dirtySuffix.value.stripPrefix("+")}"
  val publishable = exactTag && dirty.isEmpty
  if (publishable) base else s"$base$commit$dirty-SNAPSHOT"
}

def locus4sReleaseFallbackVersion(date: java.util.Date): String =
  s"0.1.0-SNAPSHOT-${sbtdynver.DynVer.timestamp(date)}"

inThisBuild(
  List(
    version := dynverGitDescribeOutput.value.mkVersion(
      locus4sReleaseVersion,
      locus4sReleaseFallbackVersion(dynverCurrentDate.value)
    ),
    isSnapshot := version.value.endsWith("-SNAPSHOT"),
    dynver := {
      val date = new java.util.Date
      sbtdynver.DynVer
        .getGitDescribeOutput(date)
        .map(locus4sReleaseVersion)
        .getOrElse(locus4sReleaseFallbackVersion(date))
    }
  )
)

lazy val locus4sCompatibilityBaseline = settingKey[Option[String]](
  "Immutable locus4s release used as the binary, source, and TASTy compatibility baseline"
)

ThisBuild / locus4sCompatibilityBaseline :=
  sys.props
    .get("locus4s.compatibility.baseline")
    .map(_.trim)
    .filter(_.nonEmpty)

ThisBuild / versionPolicyIgnoredInternalDependencyVersions :=
  Some("^0\\.1\\.0(?:\\+.*)?-SNAPSHOT(?:-.*)?$".r)

lazy val admittedCompatibilitySettings = Seq(
  versionPolicyIntention := Compatibility.BinaryAndSourceCompatible,
  versionPolicyPreviousVersions := locus4sCompatibilityBaseline.value.toSeq,
  tastyMiMaPreviousArtifacts ++= locus4sCompatibilityBaseline.value
    .map(baseline => projectID.value.withRevision(baseline))
    .toSet
)

lazy val releaseSnapshotCheck = taskKey[Unit](
  "Reject external SNAPSHOT dependencies from an admitted artifact"
)

lazy val releaseInternalModules = Set(
  "locus4s-core_3",
  "locus4s-core_sjs1_3",
  "locus4s-data_3",
  "locus4s-data_sjs1_3",
  "locus4s-laws_3",
  "locus4s-laws_sjs1_3"
)

lazy val releaseSnapshotSettings = Seq(
  releaseSnapshotCheck := {
    val reports = Seq((Compile / update).value, (Runtime / update).value)
    val snapshots = reports
      .flatMap(_.configurations)
      .flatMap(_.modules)
      .map(_.module)
      .filter(_.revision.toUpperCase.contains("SNAPSHOT"))
      .filterNot(module =>
        module.organization == organization.value &&
          releaseInternalModules.contains(module.name)
      )
      .distinct
    if (snapshots.nonEmpty) {
      val rendered = snapshots
        .map(module => s"${module.organization}:${module.name}:${module.revision}")
        .mkString(", ")
      sys.error(s"release artifact has prohibited SNAPSHOT dependencies: $rendered")
    }
  }
)

lazy val releaseVersionCheck = taskKey[Unit](
  "Require a clean tag-derived 0.1 release version"
)

lazy val releaseStagingCheck = taskKey[Unit](
  "Require sbt-ci-release's local Central staging repository"
)

lazy val releaseVersionSettings = Seq(
  releaseVersionCheck := {
    val candidate = version.value
    val releasePattern = "0\\.1\\.[0-9]+(?:-(?:M|RC)[1-9][0-9]*)?"
    if (candidate.endsWith("-SNAPSHOT") || !candidate.matches(releasePattern))
      sys.error(
        s"0.1 publication requires an exact clean release tag; derived version was $candidate"
      )
  },
  releaseStagingCheck := {
    val candidate = version.value
    if (isSnapshot.value)
      sys.error(s"release candidate $candidate is still classified as a snapshot")
    publishTo.value match {
      case Some(destination) if destination.name == "local-staging" => ()
      case other                                                    =>
        sys.error(
          s"release candidate $candidate must publish to local-staging, found ${other.fold("no destination")(_.toString)}"
        )
    }
  }
)
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Werror"
)
ThisBuild / Test / parallelExecution := false

lazy val sharedSettings = Seq(
  pomIncludeRepository := (_ => false),
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit" % "1.3.0" % Test,
    "org.scalameta" %%% "munit-scalacheck" % "1.3.0" % Test
  )
)

def locusProject(artifact: String) =
  CrossProject(artifact, file(s"modules/$artifact"))(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .settings(sharedSettings)
    .settings(admittedCompatibilitySettings)
    .settings(releaseSnapshotSettings)
    .settings(releaseVersionSettings)
    .settings(name := artifact)
    .jsSettings(
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
    )

lazy val locus4sCore =
  locusProject("locus4s-core")
    .settings(
      description := "Identity-safe finite domains, indices, regions, maps, relations, alignment, and persistence records."
    )

lazy val locus4sData =
  locusProject("locus4s-data")
    .dependsOn(locus4sCore)
    .settings(
      description := "Representation-neutral fields, views, pullbacks, sections, and deterministic finite-domain aggregation."
    )

lazy val locus4sLaws =
  locusProject("locus4s-laws")
    .dependsOn(locus4sCore, locus4sData)
    .settings(
      description := "Reusable law functions for locus4s core and data algebras."
    )

lazy val docs =
  project
    .in(file("site"))
    .dependsOn(locus4sCore.jvm, locus4sData.jvm, locus4sLaws.jvm)
    .enablePlugins(TypelevelSitePlugin)
    .settings(
      name := "locus4s-docs",
      description := "Executable Scala guide and reference documentation for locus4s.",
      publish / skip := true,
      mdocExtraArguments += "--no-link-hygiene"
    )

lazy val root =
  project
    .in(file("."))
    .aggregate(
      locus4sCore.jvm,
      locus4sCore.js,
      locus4sData.jvm,
      locus4sData.js,
      locus4sLaws.jvm,
      locus4sLaws.js,
      docs
    )
    .settings(
      name := "locus4s-root",
      publish / skip := true
    )

addCommandAlias("compileAll", ";root/compile")
addCommandAlias("testAll", ";root/test")
addCommandAlias(
  "testFullOptJS",
  ";set Global / scalaJSStage := FullOptStage;" +
    "locus4s-coreJS/test;" +
    "locus4s-dataJS/test;" +
    "locus4s-lawsJS/test"
)
addCommandAlias(
  "checkAll",
  ";scalafmtCheckAll;scalafmtSbtCheck;compileAll;testAll"
)
addCommandAlias(
  "docsCheck",
  ";locus4s-coreJVM/doc;" +
    "locus4s-dataJVM/doc;" +
    "locus4s-lawsJVM/doc;" +
    "docs/tlSite"
)
addCommandAlias(
  "releaseDependencyCheck",
  ";locus4s-coreJVM/releaseSnapshotCheck;" +
    "locus4s-coreJS/releaseSnapshotCheck;" +
    "locus4s-dataJVM/releaseSnapshotCheck;" +
    "locus4s-dataJS/releaseSnapshotCheck;" +
    "locus4s-lawsJVM/releaseSnapshotCheck;" +
    "locus4s-lawsJS/releaseSnapshotCheck"
)
addCommandAlias(
  "releasePreflight",
  ";releaseDependencyCheck;" +
    "locus4s-coreJVM/releaseVersionCheck;locus4s-coreJS/releaseVersionCheck;" +
    "locus4s-dataJVM/releaseVersionCheck;locus4s-dataJS/releaseVersionCheck;" +
    "locus4s-lawsJVM/releaseVersionCheck;locus4s-lawsJS/releaseVersionCheck;" +
    "locus4s-coreJVM/releaseStagingCheck;locus4s-coreJS/releaseStagingCheck;" +
    "locus4s-dataJVM/releaseStagingCheck;locus4s-dataJS/releaseStagingCheck;" +
    "locus4s-lawsJVM/releaseStagingCheck;locus4s-lawsJS/releaseStagingCheck"
)
addCommandAlias(
  "releaseUnsigned",
  ";releasePreflight;" +
    "locus4s-coreJVM/publish;locus4s-coreJS/publish;" +
    "locus4s-dataJVM/publish;locus4s-dataJS/publish;" +
    "locus4s-lawsJVM/publish;locus4s-lawsJS/publish"
)
addCommandAlias(
  "releaseSigned",
  ";releasePreflight;" +
    "locus4s-coreJVM/publishSigned;locus4s-coreJS/publishSigned;" +
    "locus4s-dataJVM/publishSigned;locus4s-dataJS/publishSigned;" +
    "locus4s-lawsJVM/publishSigned;locus4s-lawsJS/publishSigned"
)
addCommandAlias(
  "compatibilityCheck",
  ";locus4s-coreJVM/versionPolicyCheck;locus4s-coreJVM/tastyMiMaReportIssues;" +
    "locus4s-coreJS/versionPolicyCheck;locus4s-coreJS/tastyMiMaReportIssues;" +
    "locus4s-dataJVM/versionPolicyCheck;locus4s-dataJVM/tastyMiMaReportIssues;" +
    "locus4s-dataJS/versionPolicyCheck;locus4s-dataJS/tastyMiMaReportIssues;" +
    "locus4s-lawsJVM/versionPolicyCheck;locus4s-lawsJVM/tastyMiMaReportIssues;" +
    "locus4s-lawsJS/versionPolicyCheck;locus4s-lawsJS/tastyMiMaReportIssues"
)
addCommandAlias(
  "makeReleasePoms",
  ";locus4s-coreJVM/makePom;locus4s-coreJS/makePom;" +
    "locus4s-dataJVM/makePom;locus4s-dataJS/makePom;" +
    "locus4s-lawsJVM/makePom;locus4s-lawsJS/makePom"
)
addCommandAlias(
  "releaseSourceCourt",
  ";checkAll;testFullOptJS;docsCheck;compatibilityCheck;releasePreflight"
)
