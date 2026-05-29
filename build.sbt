import com.typesafe.sbt.packager.docker.*

ThisBuild / version := "0.2.2"

ThisBuild / scalaVersion := "2.13.9"

val appVersion      = "v0.2.2"

lazy val emilVersion     = "0.12.0"
lazy val akkaHttpVersion = "10.2.6"
lazy val akkaVersion     = "2.7.0"
lazy val courierVersion  = "3.0.1"
lazy val alpakkaVersion  = "4.0.0"
lazy val circeVersion    = "0.14.3"
lazy val slickVersion = "3.4.1"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(dockerSettings)
  .settings(
    name := "EmailClient",

    organization := "vfeeg-development",

    idePackagePrefix := Some("at.energydash"),

    resolvers += "repo.jenkins-ci.org" at "https://repo.jenkins-ci.org/releases",

    akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),

    libraryDependencies ++= Seq(
      "com.github.daddykotex" %% "courier" % courierVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.37.0",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % alpakkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % alpakkaVersion,

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.5",
      //      "io.spray" %% "spray-json" % "1.3.6",

      "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

      "javax.xml.bind" % "jaxb-api" % "2.3.0",
      "com.sun.xml.bind" % "jaxb-core" % "2.3.0",
      "org.scala-lang.modules" %% "scala-xml" % "2.0.1",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",

      "io.crums" % "merkle-tree" % "1.0.0",
      "com.google.guava" % "guava" % "31.1-jre"
    ),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.14",
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
      "org.jvnet.mock-javamail" % "mock-javamail" % "1.12",
      "com.typesafe.slick" %% "slick-testkit" % slickVersion,
    ).map(_ % Test),

    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),

    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "org.postgresql" % "postgresql" % "42.2.5"
    ),

    Compile / managedSourceDirectories ++= Seq(
      target.value / "scala-2.13" / "akka-grpc" / "main",
      target.value / "scala-2.13" / "src_managed" / "main"
    ),

    Test / javaOptions ++= Seq(s"-Dconfig.file=${sourceDirectory.value}/test/resources/application-test.conf"),

    Test / fork := true,
  )

lazy val dockerSettings = Seq(
  Docker / packageName := "eda-email-connector",
  Docker / maintainer := "vfeeg <vfeeg.org>",
  Docker / version := appVersion,

  dockerBaseImage := "openjdk:17-slim-buster",
  dockerExposedVolumes := Seq("/conf"),
  dockerExposedVolumes := Seq("/storage/prod"),
  dockerRepository := Some("ghcr.io"),
  dockerUsername := Some("vfeeg-development"),
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(8880, 9090),
  dockerCommands := dockerCommands.value.filterNot {
    case ExecCmd("ENTRYPOINT", _) => true
    case cmd => false
  },
  dockerCommands ++= Seq(
    Cmd("LABEL", s"""version="${appVersion}""""),
    ExecCmd("CMD", "/opt/docker/bin/emailclient", "-Dconfig.file=/conf/application.conf")
  ),
  dockerChmodType := DockerChmodType.UserGroupWriteExecute
)
