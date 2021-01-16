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
