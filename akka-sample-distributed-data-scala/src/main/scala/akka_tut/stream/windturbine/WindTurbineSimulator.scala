package akka_tut.stream.windturbine

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCode
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

/**
  * Created by liaoshifu on 17/11/10
  */
object WindTurbineSimulator {

  def props(id: String, endpoint: String)(implicit materializer: ActorMaterializer) =
    Props(classOf[WindTurbineSimulator], id, endpoint, materializer)

  final case object Upgraded
  final case object Connected
  final case object Terminated
  final case object QueryActorPath
  final case class ConnectionFailure(ex: Throwable)
  final case class FailedUpgrade(statusCode: StatusCode)
  final case class SimulatorActorPath(path: String)

  case class WindTurbineSimulatorException(id: String) extends Exception(id)
  
}

class WindTurbineSimulator(id: String, endpoint: String)
                          (implicit materializer: ActorMaterializer) extends Actor with ActorLogging {
  import WindTurbineSimulator._

  implicit private val system = context.system
  implicit private val ec = system.dispatcher

  val webSocket = WebSocketClient(id, endpoint, self)

  override def postStop(): Unit = {
    log.info(s"$id : Stopping WebSocket connection")
    webSocket.killSwitch.shutdown()
  }

  override def receive: Receive = {
    case Upgraded =>
      log.info(s"$id : WebSocket upgraded")

    case FailedUpgrade(statusCode) =>
      log.error(s"$id : Failed to upgrade WebSocket connection : $statusCode")
      throw WindTurbineSimulatorException(id)
    case ConnectionFailure(ex) =>
      log.error(s"$id : Failed to establish WebSocket connection $ex")
      throw WindTurbineSimulatorException(id)
    case Connected =>
      log.info(s"$id : WebSocket connected")
      context.become(running)
  }

  def running: Receive = {

    case QueryActorPath =>
      sender() ! SimulatorActorPath(context.self.path.toString)

    case Terminated =>
      log.error(s"$id : WebSocket connection terminated")
      throw WindTurbineSimulatorException(id)
  }
}

object WindTurbineData {
  def apply(id: String) = new WindTurbineData(id)
}

class WindTurbineData(id: String) {
  val random = Random

  def getNext: String = {
    val timestamp = System.currentTimeMillis / 1000
    val power = f"${random.nextDouble() * 10}%.2f"
    val rotorSpeed = f"${random.nextDouble() * 10}%.2f"
    val windSpeed = f"${random.nextDouble() * 100}%.2f"

    s"""{
       |    "id": "$id",
       |    "timestamp": $timestamp,
       |    "measurements": {
       |        "power": $power,
       |        "rotor_speed": $rotorSpeed,
       |        "wind_speed": $windSpeed
       |    }
       |}""".stripMargin
  }
}

object SimulateWindTurbines extends App {
  implicit val system = ActorSystem("SimulateWindTurbines")
  implicit val materializer = ActorMaterializer()

  /*for (_ <- 1 to 1000) {
    val id = java.util.UUID.randomUUID().toString
    system.actorOf(WindTurbineSimulator.props(id, Config.endpoint), id)
  }*/
  Source(1 to 1000)
    .throttle(
      elements = 100,
      per = 1 second,
      maximumBurst = 100,
      mode = ThrottleMode.shaping
    )
    .map { _ =>
      val id = java.util.UUID.randomUUID().toString
      system.actorOf(WindTurbineSimulator.props(id, Config.endpoint), id)
    }
    .runWith(Sink.ignore)
}

object Config {
  val endpoint = "ws://localhost:8080"
}