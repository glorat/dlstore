import sbt._
import Keys._

object ThisBuild extends Build {
  lazy val root = Project(id = "dlstore-root",
    base = file(".")) aggregate(dlstore, playstore)

  lazy val dlstore = project

  lazy val playstore = project.dependsOn(dlstore % "compile->compile;compile->test").enablePlugins(play.PlayScala)

}
