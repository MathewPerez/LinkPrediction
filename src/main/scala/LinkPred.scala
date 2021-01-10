import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.GraphLoader

object LinkPred {
  def main(args: Array[String]): Unit = {
    // Creates a Spark Context - Local Mode with two threads
    val conf = new SparkConf().setAppName("LinkPred").setMaster("local[2]")
    val sc = new SparkContext(conf)
    // Import graph from edges file
    val graph = GraphLoader.edgeListFile(sc,"/Users/mathewperez/IdeaProjects/LinkPrediction/facebook/0.edges")
    // Count number of edges and vertices to ensure graphx api is functioning correct
    println("Number of vertices : " + graph.vertices.count())
    println("Number of edges : " + graph.edges.count())
  }
}
