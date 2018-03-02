package akka_tut.masteringakka.bookstore.orderservices

import akka.actor.FSM.Failure
import akka.actor.{ActorIdentity, ActorRef, FSM, Identify, Props}


/**
  * Created by liaoshifu on 17/12/14
  */
object SalesOrderProcessor {

  case class CreateOrder(lineItems: List[Book], userId: String)

  case class Book(bookId: Int)
  case class BookstoreUser()
  case class SalesOrderLineItem()
  case class ErrorMessage(str: String, someString: Some[String])

  case class FindUserById(userId: String)
  case class FindBook(bookId: Int)
  case class FullResult(book: Book)


  def props = Props[SalesOrderProcessor]

  sealed trait State
  case object Idle extends State
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State
  case object WritingEntity extends State

  sealed trait Data {
    def originator: ActorRef
  }

  case class Inputs(originator: ActorRef, request: CreateOrder)
  trait InputsData extends Data {
    def inputs: Inputs
    def originator = inputs.originator
  }

  case object Uninitialized extends Data {
    override def originator: ActorRef = ActorRef.noSender
  }

  case class UnresolvedDependencies(inputs: Inputs, userMgr: Option[ActorRef] = None,
                                   bookMgr: Option[ActorRef] = None, creditHandler: Option[ActorRef] = None) extends InputsData
  case class ResolvedDependencies(inputs: Inputs, expectedBooks: Set[Int],
                                  user: Option[BookstoreUser], books: Map[Int, Book], userMgr: ActorRef,
                                  bookMgr: ActorRef, creditHandler: ActorRef
                                 ) extends InputsData
  case class LookUpData(inputs: Inputs, user: BookstoreUser,
                        items: List[SalesOrderLineItem], total: Double) extends InputsData
  object ResolutionIdent extends Enumeration {
    val Book, User, Credit = Value
  }

  val UserManagerName = "user-manager"
  val CreditHandlerName = "credit-handler"

  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))

  val BookMgrName = "book-manager"
}

class SalesOrderProcessor extends FSM[SalesOrderProcessor.State, SalesOrderProcessor.Data] {
  import SalesOrderProcessor._
  import concurrent.duration._
  import context.dispatcher

  val ResolveTimeout = 10 seconds

  val dao = new SaleOrderProcessDao

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(req: CreateOrder, _) =>
      lookup(BookMgrName) ! Identify(ResolutionIdent.Book)
      lookup(UserManagerName) ! Identify(ResolutionIdent.User)
      lookup(CreditHandlerName) ! Identify(ResolutionIdent.Credit)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), req))
  }

  when(ResolvingDependencies, ResolveTimeout)(transform {
    case Event(ActorIdentity(identifier: ResolutionIdent.Value, actor @ Some(ref)), data: UnresolvedDependencies) =>
      log.info("Resolved dependencies {}, {}", identifier, ref)
      val newData = identifier match {
        case ResolutionIdent.Book => data.copy(bookMgr = actor)
        case ResolutionIdent.User => data.copy(userMgr = actor)
        case ResolutionIdent.Credit => data.copy(creditHandler = actor)
      }

      stay using newData
  } using {
    case FSM.State(state, UnresolvedDependencies(inputs, Some(user), Some(book), Some(credit)), _, _, _) =>

      log.info("Resolved all dependencies, looking up entities")
      user ! FindUserById(inputs.request.userId)
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      expectedBooks.foreach(id => book ! FindBook(id))

      goto(LookingUpEntities) using ResolvedDependencies(inputs, expectedBooks, None, Map.empty, book, user, credit)
  })

  /*when(LookingUpEntities, 10 seconds)(transform {
    case Event(FullResult(b: Book), data: ResolvedDependencies) =>
      log.info("Looked up book: {}", b)

      // Make sure inventory is available
      val lineItemForBook = data.inputs.request.lineItems.find(_.bookId == b.bookId)
      lineItemForBook match {
        case None =>
          log.error("Got back a book for which we don't have a line item")
          data.originator ! unexpectedFail
          stop

        case Some(item) if item.quantity > b.inventoryAmount =>
          
      }
  })*/

  def lookup(name: String) = context.actorSelection(s"/user/$name")
  def unexpectedFail = Failure("error", "failure")
}

class SaleOrderProcessDao

val liveUserInScoreAndKeyRDD = rdd.filter(_.isIn)
.map(LiveUserInScoreHBase.fromLiveInOut)
.map(lu => (lu.user_id + lu.live_id, lu))
.reduceByKey { case (p1, _) =>
p1
}

val existInScoreRDD = liveUserInScoreAndKeyRDD.mapPartitions { keyAndLus =>
HBaseUtils.getExistList[LiveUserInScoreHBase](
LiveUserInScoreHBase.table_name, LiveUserInScoreHBase.cf, keyAndLus.map(_._2.rowkey).toSeq
).toIterator
}.map(lu => (lu.user_id + lu.live_id, lu))

val toInsertRDD = liveUserInScoreAndKeyRDD.subtractByKey(existInScoreRDD)

rddWriteToHBase(toInsertRDD, LiveUserInScoreHBase.table_name, LiveUserInScoreHBase.cf)