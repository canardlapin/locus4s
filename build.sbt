import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbtcrossproject.{CrossProject, CrossType}
import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*

ThisBuild / organization := "io.github.canardlapin"
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / homepage := Some(url("https://github.com/canardlapin/locus4s"))
ThisBuild / licenses := List(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
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
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit" % "1.3.0" % Test,
    "org.scalameta" %%% "munit-scalacheck" % "1.3.0" % Test
  )
)

def locusProject(artifact: String) =
  CrossProject(artifact, file(s"modules/$artifact"))(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .settings(sharedSettings)
    .settings(name := artifact)
    .jsSettings(
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
    )

lazy val locus4sCore =
  locusProject("locus4s-core")

lazy val locus4sData =
  locusProject("locus4s-data")
    .dependsOn(locus4sCore)

lazy val locus4sLaws =
  locusProject("locus4s-laws")
    .dependsOn(locus4sCore, locus4sData)

lazy val root =
  project
    .in(file("."))
    .aggregate(
      locus4sCore.jvm,
      locus4sCore.js,
      locus4sData.jvm,
      locus4sData.js,
      locus4sLaws.jvm,
      locus4sLaws.js
    )
    .settings(
      name := "locus4s-root",
      publish / skip := true
    )

addCommandAlias("compileAll", ";root/compile")
addCommandAlias("testAll", ";root/test")
