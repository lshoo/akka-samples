package akka_tut.stream.windturbine

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.stream.ActorMaterializer

import scala.language.postfixOps
import scala.concurrent.duration._

/**
  * Created by liaoshifu on 17/11/13
  *
  * http://blog.colinbreck.com/integrating-akka-streams-and-akka-actors-part-iii/
  */
object WindTurbineSupervisor {

  final case object StartSimulator
  final case class StartClient(id: String)

  def props(implicit materializer: ActorMaterializer) =
    Props(classOf[WindTurbineSupervisor], materializer)
}

class WindTurbineSupervisor(implicit materializer: ActorMaterializer) extends Actor {
  import WindTurbineSupervisor._

  override def preStart(): Unit = {
    self ! StartClient(self.path.name)
  }

  override def receive: Receive = {
    case StartClient(id) =>
      val supervisor = BackoffSupervisor.props(
        Backoff.onFailure(
          WindTurbineSimulator.props(id, Config.endpoint),
          childName = id,
          minBackoff = 1.second,
          maxBackoff = 30.seconds,
          randomFactor = 0.2
        )
      )

      context.become(running(
        context.actorOf(supervisor, name = s"$id-backoff-supervisor")
      ))

  }

  def running(child: ActorRef): Receive = {
    case message => child forward message
  }

}
