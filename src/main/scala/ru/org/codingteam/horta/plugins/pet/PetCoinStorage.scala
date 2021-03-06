package ru.org.codingteam.horta.plugins.pet

import akka.actor.{Actor, ActorLogging}
import akka.pattern.ask
import akka.util.Timeout
import ru.org.codingteam.horta.database.{StoreObject, ReadObject}

import scala.concurrent.Await
import scala.concurrent.duration._

case class GetPTC()
case class UpdateUserPTC(transactionName: String, user: String, delta: Int)
case class UpdateUserPTCWithOverflow(transactionName: String, user: String, delta: Int)
case class UpdateAllPTC(transactionName: String, delta: Int)
case class TransferPTC(transactionName: String, sourceUser: String, targetUser: String, amount: Int)

class PetCoinStorage(room: String) extends Actor with ActorLogging {

  val waitFor = 1.minute
  implicit val timeout = Timeout(waitFor)

  val store = context.actorSelection("/user/core/store")

  var coins: Option[Map[String, Int]] = None

  override def receive = {
    case GetPTC() => withCoins("watch", c => { sender ! c; (Some(c), 0) })
    case UpdateUserPTC(t, user, delta) => sender ! withCoins(t, updatePetCoins(user, delta))
    case UpdateUserPTCWithOverflow(t, user, delta) => sender ! withCoins(t, updatePetCoins(user, delta, false))
    case UpdateAllPTC(t, delta) => sender ! withCoins(t, updatePetCoins(delta))
    case TransferPTC(t, source, target, amount) => sender ! withCoins(t, transferPetCoins(source, target, amount))
  }

  private def withCoins(transactionName: String, action: Map[String, Int] => (Option[Map[String, Int]], Int)): Int = {
    val Some(oldCoins) = coins match {
      case Some(c) => Some(c)
      case None => Await.result(
        (store ? ReadObject(PetPlugin.name, PetCoinsId(room))).mapTo[Option[Map[String, Int]]],
        waitFor)
    }

    action(oldCoins) match {
      case (Some(newCoins), result) =>
        Await.result(
          store ? StoreObject(
            PetPlugin.name,
            Some(PetCoinsId(room)),
            PetCoinTransaction(transactionName, oldCoins, newCoins)),
          waitFor) match {
          case Some(_) => coins = Some(newCoins)
          case None => sys.error("Cannot process PTC transaction")
        }
        result
      case (None, 0) => 0
      case other => sys.error(s"Logic error: $other")
    }
  }

  private def updatePetCoins(user: String, delta: Int, checkBalance: Boolean = true)
                            (coins: Map[String, Int]): (Option[Map[String, Int]], Int) = {
    val balance = coins.get(user) match {
      case Some(currentBalance) => currentBalance
      case None => 0
    }

    balance + delta match {
      case newBalance if newBalance > 0 => (Some(coins + (user -> newBalance)), newBalance - balance)
      case 0 => (Some(coins - user), balance)
      case newBalance if newBalance < 0 && !checkBalance => (Some(coins - user), balance)
      case _ => (None, 0)
    }
  }

  private def updatePetCoins(delta: Int)(coins: Map[String, Int]): (Option[Map[String, Int]], Int) = {
    coins.keys.foldLeft((Some(coins), 0): (Option[Map[String, Int]], Int)) {
      case ((Some(c), diff), name) => updatePetCoins(name, delta, checkBalance = false)(c)
      case ((None, _), _) => sys.error("Impossible")
    }
  }

  private def transferPetCoins(source: String,
                               target: String,
                               amount: Int)(coins: Map[String, Int]): (Option[Map[String, Int]], Int) = {
    updatePetCoins(source, -amount)(coins) match {
      case (Some(coins), _) => updatePetCoins(target, amount)(coins)
      case (None, _) => (None, 0)
    }
  }

}
