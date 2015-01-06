import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.sbt.SbtGit._
import GitKeys._

organization := "org.typelevel"

name := "scodec-core"

scalaVersion := "2.11.4"

crossScalaVersions := Seq(scalaVersion.value, "2.10.4")

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-Xcheckinit",
  "-Xlint",
  "-Xverify",
  "-Yclosure-elim",
  "-Yinline",
  "-Yno-adapted-args")

scalacOptions in (Compile, doc) ++= {
  val tagOrBranch = if (version.value endsWith "SNAPSHOT") "master" else ("v" + version.value)
  Seq(
    "-diagrams",
    "-groups",
    "-implicits",
    "-implicits-show-all",
    "-sourcepath", baseDirectory.value.getAbsolutePath,
    "-doc-source-url", "https:///github.com/scodec/scodec/tree/" + tagOrBranch + "€{FILE_PATH}.scala"
  )
}

autoAPIMappings := true

apiURL := Some(url(s"http://docs.typelevel.org/api/scodec/core/stable/${version.value}/"))

licenses += ("Three-clause BSD-style", url("http://github.com/scodec/scodec/blob/master/LICENSE"))

unmanagedResources in Compile <++= baseDirectory map { base => (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get }

triggeredMessage := (_ => Watched.clearScreen)

resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"

libraryDependencies ++= Seq(
  "org.typelevel" %% "scodec-bits" % "1.0.4",
  "com.chuusai" % "shapeless" % "2.1.0-SNAPSHOT" cross CrossVersion.fullMapped {
    case "2.10.4" => "2.10.4"
    case x if x startsWith "2.11." => "2.11"
  },
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.50" % "test"
)

// Shapeless 2.1.0 on Scala 2.10 requires macro paradise
libraryDependencies ++= {
  if (scalaBinaryVersion.value startsWith "2.10") Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)) else Nil
}

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoPackage := "scodec"

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, gitHeadCommit)

osgiSettings

OsgiKeys.exportPackage := Seq("!scodec.bits,scodec.*;version=${Bundle-Version}")

OsgiKeys.importPackage := Seq(
  """scodec.bits.*;version="$<range;[==,=+);$<@>>"""",
  """scala.*;version="$<range;[==,=+);$<@>>"""",
  "*"
)

OsgiKeys.additionalHeaders := Map("-removeheaders" -> "Include-Resource,Private-Package")

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
  <url>http://github.com/scodec/scodec</url>
  <scm>
    <url>git@github.com:scodec/scodec.git</url>
    <connection>scm:git:git@github.com:scodec/scodec.git</connection>
  </scm>
  <developers>
    <developer>
      <id>mpilquist</id>
      <name>Michael Pilquist</name>
      <url>http://github.com/mpilquist</url>
    </developer>
    <developer>
      <id>pchiusano</id>
      <name>Paul Chiusano</name>
      <url>http://github.com/pchiusano</url>
    </developer>
  </developers>
)

pomPostProcess := { (node) =>
  import scala.xml._
  import scala.xml.transform._
  def stripIf(f: Node => Boolean) = new RewriteRule {
    override def transform(n: Node) =
      if (f(n)) NodeSeq.Empty else n
  }
  val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
  new RuleTransformer(stripTestScope).transform(node)(0)
}

releaseSettings

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts.copy(action = publishSignedAction),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

site.settings

site.includeScaladoc()

ghpages.settings

git.remoteRepo := "git@github.com:scodec/scodec.git"

mimaDefaultSettings

previousArtifact := previousVersion(version.value) map { pv =>
  organization.value % (normalizedName.value + "_" + scalaBinaryVersion.value) % pv
}

binaryIssueFilters ++= Seq(
)
