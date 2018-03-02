package akka_tut.stream

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.io.StdIn

/**
  * Created by liaoshifu on 17/11/10
  *
  * http://blog.colinbreck.com/integrating-akka-streams-and-akka-actors-part-ii/
  */
class PrintSomeNumbers(implicit materializer: ActorMaterializer) extends Actor {
  private implicit val ec = context.system.dispatcher

  /*Source(1 to 10)
    .map(_.toString)
    .runForeach(println)
    .map(_ => self ! "done")
*/
  private val (killSwitch, done) =
    Source.tick(0 second, 1 second, 2)
    .scan(1)(_ * _)
    .map(_.toString)
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(Sink.foreach(println))(Keep.both)
    .run()

  done.map(_ => self ! "done")

  override def receive: Receive = {
    case "stop" =>
      println("Stopping")
      killSwitch.shutdown()
      
    case "done" =>
      println("Done")
      context.stop(self)
  }
}

/*object TrivialExample extends App {
  implicit val system = ActorSystem("StreanAddActor")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  system.actorOf(Props(classOf[PrintSomeNumbers], materializer), "printer")

  StdIn.readLine()
  system.terminate()
}*/


object LessTrivialExample extends App {
  implicit val system = ActorSystem("StreanAddActor")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  
  val actor = system.actorOf(Props(classOf[PrintSomeNumbers], materializer), "printer")

  system.scheduler.scheduleOnce(5 seconds) {
    actor ! "stop"
  }
  StdIn.readLine()
  system.terminate()
}
