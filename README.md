# Finding Potential Friends via Link Prediction

## This is a Spark Scala project to perform simple link prediction on nodes of a social network graph. This README file will serve as a simple tutorial to follow along or reproduce the results of this project.

### Data Description:
Our dataset is the [SNAP Facebook graph](https://snap.stanford.edu/data/ego-Facebook.html) data and we specifically use the facebook.tar.gz dataset. It is a directory of different graph dataset file types - the only file type we actually care about is the .edges type. Moreover, there are ten different networks (i.e. circles), so we care about the ten .edges files in the directory.

### High-Level Project Description:
We are trying to build a simple Scala command line program that will take in as input a user id and return a list of potential friends, across different circles. This can be accomplished by solving the graph problem of [link prediction](https://en.wikipedia.org/wiki/Link_prediction). More specifically, we will use [Jaccard’s measure](https://en.wikipedia.org/wiki/Link_prediction#Jaccard_measure). Lastly, we can utilize Apache Spark’s [GraphX](https://spark.apache.org/docs/latest/graphx-programming-guide.html) library to process graph data efficiently.

### Environment Setup:
To write, debug, and refactor Spark Scala code, we will use [IntelliJ Idea Community Edition](https://www.jetbrains.com/idea/download/). 
First, install the Scala plugin for IntelliJ, then create a Scala project of sbt type specifically. Next, ensure the SDK version is 1.8 (If not already installed, you can click Add SDK and select the 1.8 version to download). At the time of writing this, we use Scala version 2.12 (because of the Spark 3.0.1 version we’ll choose later on) which may differ from the version of the latest Scala plugin for IntelliJ. However, using other versions of Scala besides 12 should work as long as the Scala version number and appropriate Spark version number are used in the build.sbt file. Create the IntelliJ project.
Now, go to the build.sbt file and edit it to be like:

```
name := “LinkPrediction”

version := “0.1”

scalaVersion := “2.12.0”

libraryDependencies := Seq(
  “org.apache.spark” %% “spark-core” % “3.0.1”,
  “org.apache.spark” %% “spark-sql” % “3.0.1”,
  “org.apache.spark” %% “spark-graphx” % “3.0.1”,
)
```

This way, the build of our project will find the Spark API via Maven. Also, go to the File tab of IntelliJ, then Project Structure, and then Project. Make sure that the Project SDK is 1.8 and that Project Language Level is set to “8” or something indicating Java8 is being used. Once again, note that in the build.sbt file, you could use different versions of Spark as long as the Scala version is adjusted accordingly. For more info, there is the [Spark/Scala compatibilities](https://mvnrepository.com/artifact/org.apache.spark/spark-core) and the [Maven Central Repository](https://repo1.maven.org/maven2/org/apache/spark/) to see what versions are currently up. Now, build the project in IntelliJ to make sure it succeeds. At this point, you could move the SNAP repository into the project for a simpler file path later on (you don’t need to nest it in any folders or anything) or leave it wherever is easiest. 
Finally, we’ll do a last sanity check to ensure that our Scala will compile properly and that the Spark dependencies will be located. Add a Scala Object under src/main/scala directory, called LinkPred.scala. Put the following code into it:

```
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.GraphLoader

object LinkPred {
  def main(args: Array[String]): Unit = {
    // Creates a Spark Context - Local Mode with two threads
    val conf = new SparkConf().setAppName("LinkPred").setMaster("local[2]")
    val sc = new SparkContext(conf)
    // Import graph from edges file
    val graph = GraphLoader.edgeListFile(sc,"/path/to/SNAP/directory/0.edges")
    // Count number of edges and vertices to ensure graphx api is functioning correct
    println("Number of vertices : " + graph.vertices.count())
    println("Number of edges : " + graph.edges.count())
  }
}
```

This will print out the number of vertices and number of edges in the “0” circle/network. They should be approximately: 
- Number of vertices : 333
- Number of edges : 5038
