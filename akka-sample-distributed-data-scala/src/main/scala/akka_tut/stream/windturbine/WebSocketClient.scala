package akka_tut.stream.windturbine

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, KillSwitches, SourceShape}
import akka_tut.stream.windturbine.WindTurbineSimulator._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Created by liaoshifu on 17/11/10
  */
object WebSocketClient {

  def apply(id: String, endpoint: String, supervisor: ActorRef)
           (
             implicit
           system: ActorSystem,
             materializer: ActorMaterializer,
             ec: ExecutionContext
           ) = {
    new WebSocketClient(id, endpoint, supervisor)(system, materializer, ec)
  }

}

class WebSocketClient(id: String, endpoint: String, supervisor: ActorRef)
                     (
                     implicit
                     system: ActorSystem,
                     materializer: ActorMaterializer,
                     ec: ExecutionContext
                     ) {

  val webSocket: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = {
    val webSocketUri = s"$endpoint/$id"
    Http().webSocketClientFlow(WebSocketRequest(webSocketUri))
  }

  val outgoing = GraphDSL.create() { implicit builder =>
    val data = WindTurbineData(id)

    val flow = builder.add {
      Source.tick(1 second, 1 second, ())
        .map(_ => TextMessage(data.getNext))
    }

    SourceShape(flow.out)
  }

  val incoming = GraphDSL.create() { implicit builder =>
    val flow = builder.add {
      Flow[Message]
        .collect {
          case TextMessage.Strict(text) =>
            Future.successful(text)

          case TextMessage.Streamed(textStream) =>
            textStream.runFold("")(_ + _)
              .flatMap(Future.successful)
        }
        .mapAsync(1)(identity)
        .map(println)
    }

    FlowShape(flow.in, flow.out)
  }

  val ((upgradeResponse, killSwitch), closed) = Source.fromGraph(outgoing)
    .viaMat(webSocket)(Keep.right)  // Keep the materialized Future[WebSocketUpgradeResponse]
    .viaMat(KillSwitches.single)(Keep.both) // also keep the KillSwitch
    .via(incoming)
    .toMat(Sink.ignore)(Keep.both)
    .run()

  val connected =
    upgradeResponse.map { upgrade =>
      upgrade.response.status match {
        case StatusCodes.SwitchingProtocols => supervisor ! Upgraded
        case statusCode => supervisor ! FailedUpgrade(statusCode)
      }
    }

  connected.onComplete {
    case Success(_) => supervisor ! Connected
    case Failure(ex) => supervisor ! ConnectionFailure(ex)
  }

  closed.map { _ =>
    supervisor ! Terminated
  }
}