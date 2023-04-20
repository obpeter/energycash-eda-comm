import com.typesafe.sbt.packager.docker._
import scalapb.GeneratorOption._

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

lazy val emilVersion     = "0.12.0"
lazy val akkaHttpVersion = "10.2.6"
//lazy val akkaVersion     = "2.6.19"
lazy val akkaVersion     = "2.7.0"
lazy val courierVersion  = "3.0.1"
lazy val alpakkaVersion  = "4.0.0"
lazy val circeVersion    = "0.14.3"
lazy val slickVersion = "3.4.1"

val appVersion      = "0.0.1"

lazy val root = (project in file("."))
  .enablePlugins(ScalaxbPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(dockerSettings)
  .settings(
    name := "EmailClient",

    organization := "vfeeg-development",

    idePackagePrefix := Some("at.energydash"),

    resolvers += "repo.jenkins-ci.org" at "https://repo.jenkins-ci.org/releases",

    akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),

//    akkaGrpcCodeGeneratorSettings / target := crossTarget.value / "src_managed1" /*/ Defaults.nameForSrc(configuration.value.name)*/,
//
//    akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package"),

    libraryDependencies ++= Seq(
      "com.github.daddykotex" %% "courier" % courierVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
//      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % alpakkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % alpakkaVersion,
//      "com.enragedginger" %% "akka-quartz-scheduler" % "1.9.3-akka-2.6.x",

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.5",
      //      "io.spray" %% "spray-json" % "1.3.6",

      "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

//      "org.scalactic" %% "scalactic" % "3.0.8",
//      "org.scalatest" %% "scalatest" % "3.2.14" % Test,
//      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

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

//    Compile / PB.targets := Seq(
//      scalapb.gen(FlatPackage) -> (Compile / akkaGrpcCodeGeneratorSettings / target).value
//    ),

//    Compile / akkaGrpcCodeGeneratorSettings / target := crossTarget.value / "src_managed" / "main" / "akka-grpc",

    Compile / scalaxb / scalaxbPackageName := "xmlprotocol",

    Compile / managedSourceDirectories ++= Seq(
      target.value / "scala-2.13" / "akka-grpc" / "main",
      target.value / "scala-2.13" / "src_managed" / "main"
    ),

    Test / javaOptions ++= Seq(s"-Dconfig.file=${sourceDirectory.value}/test/resources/application-test.conf"),

//    Test / resourceDirectory := baseDirectory.value / "test-resources",

    Test / fork := true,

  )

lazy val dockerSettings = Seq(
  Docker / packageName := "eda-email-connector",
  Docker / maintainer := "vfeeg <vfeeg.org>",
//  Docker / dockerPackageMappings += (baseDirectory.value / "src" / "universal" / "application-app.conf") -> "application-app.conf",
  dockerBaseImage := "openjdk:17-slim-buster",
//  dockerExposedPorts := Seq(9000),
//  Docker / daemonUserUid := None,
//  Docker / daemonUser := "daemon",
  dockerExposedVolumes := Seq("/conf"),
  dockerExposedVolumes := Seq("/storage/prod"),
  dockerRepository := Some("ghcr.io"),
  dockerUsername := Some("vfeeg-development"),
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(8880),
  dockerCommands := dockerCommands.value.filterNot {
    case ExecCmd("ENTRYPOINT", _) => true
    case cmd => false
  },
  dockerCommands ++= Seq(
//    Cmd("ADD", "application-app.conf", "/conf/application.conf"),
    Cmd("LABEL", s"""version="${appVersion}""""),
    ExecCmd("CMD", "/opt/docker/bin/emailclient", "-Dconfig.file=/conf/application.conf")
  ),
  dockerChmodType := DockerChmodType.UserGroupWriteExecute
)
