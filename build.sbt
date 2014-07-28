name := """Hanni"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  javaWs
)

libraryDependencies += "commons-io" % "commons-io" % "2.4"

libraryDependencies += "com.google.guava" % "guava" % "17.0"

libraryDependencies += "commons-collections" % "commons-collections" % "3.2.1"

libraryDependencies += "org.hibernate" % "hibernate" % "3.2.0.ga"

libraryDependencies += "org.hibernate" % "hibernate-annotations" % "3.5.6-Final"

libraryDependencies += "org.hibernate" % "hibernate-commons-annotations" % "3.3.0.ga"

libraryDependencies += "log4j" % "log4j" % "1.2.17"

//lazy val SimLibProject = RootProject(uri("https://github.com/suddin/ca.usask.cs.srlab.simLib.git"))
//lazy val core = project dependsOn SimLibProject

unmanagedSourceDirectories in Compile += baseDirectory.value / "ca.usask.cs.srlab.simLib/src/main/java"

