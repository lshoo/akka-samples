package akka_tut.stream.windturbine

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import akka.{Done, NotUsed}
import akka_tut.stream.windturbine.Total.{CurrentTotal, GetTotal, Increment, Increment2}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by liaoshifu on 17/11/10
  *
  * http://blog.colinbreck.com/integrating-akka-streams-and-akka-actors-part-i/
  */
object Total {
  case class Increment(value: Long)
  case object GetTotal
  case class CurrentTotal(value: Long)

  case object Init
  case object Ack
  case class Complete(id: Long)
  case class Increment2(value: Long, lastMessage: Long)
}

class Total extends Actor {
  var total: Long = 0

  override def receive: Receive = {

    case Increment(value) =>
      total = total + value
      sender() ! Done

    case GetTotal =>
      sender() ! CurrentTotal(total)
  }
}

object Messages {
  def parse(messages: Seq[String]): Seq[Long] = messages.map(_.length.toLong)

  def ack(m: String): String = s"OK $m"
}
object StreamAndActorApp extends App {

  implicit val system = ActorSystem("StreamActor")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val total = system.actorOf(Props[Total], "total")

  val measurementsWebSocket =
    Flow[Message]
    .collect {
      case TextMessage.Strict(text) =>
        Future.successful(text)

      case TextMessage.Streamed(textStream) =>
        textStream.runFold("")(_ + _)
          .flatMap(Future.successful)
    }
    .mapAsync(1)(identity)
    .groupedWithin(1000, 1 second)
    .map(messages => (messages.last, Messages.parse(messages)))
    .map {
      case (lastMessage, measurements) =>
        total ! Increment(measurements.sum)
        lastMessage
    }
    .map(Messages.ack)

  val measurementsWebSocket2 =
    Flow[Message]
      .collect {
        case TextMessage.Strict(text) =>
          Future.successful(text)

        case TextMessage.Streamed(textStream) =>
          textStream.runFold("")(_ + _)
            .flatMap(Future.successful)
      }
      .mapAsync(1)(identity)
      .groupedWithin(1000, 1 second)
      .map(messages => (messages.last, Messages.parse(messages)))
      .mapAsync(1) {
        case (lastMessage, measurements) =>
          import akka.pattern.ask
          implicit val timeout = Timeout(30 seconds)
          (total ? Increment(measurements.sum))
            .mapTo[Done]
            .map(_ => lastMessage)
      }
      .map(Messages.ack)

  val measurementsWebSocket3 = (sink: Sink[Increment2, NotUsed]) =>
    Flow[Message]
      .collect {
        case TextMessage.Strict(text) =>
          Future.successful(text)

        case TextMessage.Streamed(textStream) =>
          textStream.runFold("")(_ + _)
            .flatMap(Future.successful)
      }
      .mapAsync(1)(identity)
      .groupedWithin(1000, 1 second)
      .map(Messages.parse)
      .map(messages => Increment2(messages.sum, messages.last))
      .alsoTo(sink)
      .map(increment => Messages.ack(increment.lastMessage.toString))
}
