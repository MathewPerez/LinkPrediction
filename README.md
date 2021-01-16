# Finding Potential Friends via Link Prediction

## This is a Spark Scala project to perform simple link prediction on nodes of a social network graph. This README file will serve as a simple tutorial to follow along or reproduce the results of this project.

### Data Description:
Our dataset is the [SNAP Facebook graph](https://snap.stanford.edu/data/ego-Facebook.html) data and we specifically use the facebook_combined.txt.gz dataset. It is a file where each line contains an edge from one vertex_id to another (i.e. basically an adjacency list). We note that each edge represents friends on Facebook; hence, the graph is undirected.

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

This will print out the number of vertices and number of edges in the network. They should be approximately: 
- Number of vertices : 4039
- Number of edges : 88234

### Coding it up
A basic knowledge of Apache Spark is assumed for this work (i.e. familiar with RDDs, partitioning of RDDs, how Spark can run on top of HDFS). It is recommended to read the entire [GraphX](https://spark.apache.org/docs/latest/graphx-programming-guide.html) overview to understand how we’ll use its features for our use case. We will first add to our current code by putting in the necessary imports:

```
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.{EdgeDirection, GraphLoader, PartitionStrategy}
import scala.collection.mutable.PriorityQueue
```

Then, at the top of our main function we’ll add a simple command line input for the user we want to find potential friends for:

```
val userInput = scala.io.StdIn.readLine("Enter userid to find potential friends (Must be Int) : ")
val targetUserId = userInput.toInt
```

Next, we’ll create our graph with a partitioning scheme:

```
val graph = GraphLoader.edgeListFile(sc,"/path/to/SNAP/directory/facebook_combined.txt")
		.partitionBy(PartitionStrategy.CanonicalRandomVertexCut)
```

The [partitionBy](https://spark.apache.org/docs/latest/api/scala/org/apache/spark/graphx/Graph.html#partitionBy(PartitionStrategy):Graph[VD,ED]) method comes from Graph class and we give it the CanonicalRandomVertexCut partitioning strategy as an argument. This partitioning scheme ensures identical undirected edges will be colocated. Obviously, this is not a huge factor when we are running our code locally and when our edges are not repeated, but it’s an important method in many cases at scale. More [partition strategies](https://spark.apache.org/docs/latest/api/scala/org/apache/spark/graphx/PartitionStrategy.html) exist for different use cases (i.e. collocate directed edges, collocate source vertices, collocate destination vertices). By default, Graphx does not repartition edges when a graph is built, so edges are left in their default partitions (e.g. their original HDFS blocks).

Next, we find the friends list for each user in the network via:

```
val neighbors = graph.collectNeighborIds(EdgeDirection.Either)
```

Note that we pass EdgeDirection.Either as an argument to ensure neighbors are counted both ways (i.e. treat it as an undirected graph, since Graphx uses directed multigraphs by default). Now, we continue by filtering out users from our potential friends list. Namely, we exclude users who the target user is already friends with and the target user themselves obviously:

```
// Find users who the target user is already friends with
val currFriends = neighbors.lookup(targetUserId)(0)
// Limit our search to users they are not friends with and exclude the target user themself
val availableUsers = neighbors.filter(v => !currFriends.contains(v._1) && v._1 != targetUserId)
```
Now, we’ll go back and cache the neighbors RDD to improve performance. With our small dataset and local spark instance, only a 0.3 sec improvement was observed. However, at scale, caching RDDs is good practice in Spark programming. After, we’re done with the neighbors RDD we can use unpersist to force it from memory (rather than relying on Spark to evict its partitions in LRU order):

```
// Collect the friends list of each user
val neighbors = graph.collectNeighborIds(EdgeDirection.Either).cache()
// Find users who the target user is already friends with
val currFriends = neighbors.lookup(targetUserId)(0)
// Limit our search to users they are not friends with and exclude the target user themself
val availableUsers = neighbors.filter(v => !currFriends.contains(v._1) && v._1.toInt != targetUserId)
neighbors.unpersist()
```

The final logic of our code is calculating the Jaccard measure for each potential friend and ranking them by the Jaccard measure in descending order. A note on the Jaccard measure is that its calculation is often too expensive, so a probabilistic approximation of Jaccard measure - MinHash - is used in practice. However, Spark [MinHash](https://towardsdatascience.com/scalable-jaccard-similarity-using-minhash-and-spark-85d00a007c5e) methods and calculations require more overhead and are not necessary for our small dataset. 

To compute the Jaccard measure for each potential friend and rank them, we’ll first create a simple Pair class consisting of two attributes: UserId and Jaccard Measure. Then, we intialize a Max Heap/Priority Queue :

```
final case class Pair(userid: Int, jaccard: Float){}
var queue = PriorityQueue.empty[Pair](Ordering.by((_: Pair).jaccard))
```

Next, we’ll iterate over the list of potential friends and calculate the Jaccard measure, then create the respective Pair object, and add this Pair to the heap:

```
for (x <- availableUsers.collect()) {
 val ovlp_len = currFriends.intersect(x._2).length
 val insctn_len = currFriends.union(x._2).length
 val jaccard = ovlp_len.toFloat/insctn_len.toFloat
 val p = Pair(x._1.toInt, jaccard)
 queue += p
}
```

Note, we can use collect() on availableUsers to keep it in memory because the RDD is small enough. To finish it all off, we dequeue the top 10 Pairs and print out the user id’s:

```
// Display potential friends list in desc order of Jaccard
println("Potential Friends:")
var i = 0
while (i < 10) {
 println(queue.dequeue().userid)
 i += 1
}
```

The final code should look like this:

``` 
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.{EdgeDirection, GraphLoader, PartitionStrategy}
import scala.collection.mutable.PriorityQueue

object LinkPred {
 def main(args: Array[String]): Unit = {
   // Ask for target userid
   val userInput = scala.io.StdIn.readLine("Enter userid to find potential friends (Must be Int) : ")
   val targetUserId = userInput.toInt
   // Creates a Spark Context - Local Mode with two threads
   val conf = new SparkConf().setAppName("LinkPred").setMaster("local[2]")
   val sc = new SparkContext(conf)
   // Optional - set to decrease Spark's runtime output verbosity
   sc.setLogLevel("WARN")
   // Create graph from edge file
   val graph = GraphLoader.edgeListFile(sc, "/Users/mathewperez/IdeaProjects/LinkPrediction/facebook/facebook_combined.txt")
     .partitionBy(PartitionStrategy.CanonicalRandomVertexCut)
   // Collect the friends list of each user
   val neighbors = graph.collectNeighborIds(EdgeDirection.Either).cache()
   // Find users who the target user is already friends with
   val currFriends = neighbors.lookup(targetUserId)(0)
   // Limit our search to users they are not friends with and exclude the target user themself
   val availableUsers = neighbors.filter(v => !currFriends.contains(v._1) && v._1.toInt != targetUserId)
   neighbors.unpersist()
   // Calculate and rank by Jaccard measure for each potential friend
   final case class Pair(userid: Int, jaccard: Float){}
   var queue = PriorityQueue.empty[Pair](Ordering.by((_: Pair).jaccard))
   for (x <- availableUsers.collect()) {
     val ovlp_len = currFriends.intersect(x._2).length
     val insctn_len = currFriends.union(x._2).length
     val jaccard = ovlp_len.toFloat/insctn_len.toFloat
     val p = Pair(x._1.toInt, jaccard)
     queue += p
   }
   // Display potential friends list in desc order of Jaccard
   println("Potential Friends:")
   var i = 0
   while (i < 10) {
     println(queue.dequeue().userid)
     i += 1
   }
 }
}
```
