package com.tudux.taxi.actors.passenger

import akka.actor.{ActorLogging, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.PersistentActor
import com.tudux.taxi.actors.common.response.CommonOperationResponse.OperationResponse

case class TaxiTripPassengerInfo(passengerCount: Int, deletedFlag: Boolean = false)

sealed trait TaxiTripPassengerInfoCommand
object TaxiTripPassengerInfoCommand {
  case class CreateTaxiTripPassengerInfo(tripId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfo) extends TaxiTripPassengerInfoCommand
  case class GetTaxiTripPassengerInfo(tripId: String) extends TaxiTripPassengerInfoCommand
  case class UpdateTaxiTripPassenger(tripId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfo) extends TaxiTripPassengerInfoCommand
  case class DeleteTaxiTripPassenger(tripId: String) extends TaxiTripPassengerInfoCommand
}


sealed trait TaxiTripPassengerInfoEvent
object TaxiTripPassengerInfoStatEvent{
  case class TaxiTripPassengerInfoCreatedEvent(tripId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfo) extends TaxiTripPassengerInfoEvent
  case class UpdatedTaxiTripPassengerEvent(tripId: String, taxiTripPassengerInfoStat: TaxiTripPassengerInfo) extends TaxiTripPassengerInfoEvent
  case class DeletedTaxiTripPassengerEvent(tripId: String) extends TaxiTripPassengerInfoEvent
}


object PassengerInfoActorShardingSettings {
  import TaxiTripPassengerInfoCommand._

  val numberOfShards = 10 //  use 10x number of nodes in your cluster
  // this help to map the corresponding message to a respective entity
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case createTaxiTripPassengerInfo@CreateTaxiTripPassengerInfo(tripId,_) =>
      (tripId, createTaxiTripPassengerInfo)
    case msg@GetTaxiTripPassengerInfo(tripId) =>
      (tripId, msg)
    case msg@DeleteTaxiTripPassenger(tripId) =>
      (tripId, msg)
    case msg@UpdateTaxiTripPassenger(tripId,_) =>
      (tripId, msg)
  }

  // this help to map the corresponding message to a respective shard
  val extractShardId: ShardRegion.ExtractShardId = {
    case CreateTaxiTripPassengerInfo(tripId,_) =>
      val shardId = tripId.hashCode.abs % numberOfShards
      shardId.toString
    case GetTaxiTripPassengerInfo(statId) =>
      val shardId = statId.hashCode.abs % numberOfShards
      shardId.toString
    case DeleteTaxiTripPassenger(statId) =>
      val shardId = statId.hashCode.abs % numberOfShards
      shardId.toString
    case UpdateTaxiTripPassenger(statId,_) =>
      val shardId = statId.hashCode.abs % numberOfShards
      shardId.toString
    case ShardRegion.StartEntity(tripId) =>
      tripId
  }

}
object PersistentTaxiTripPassengerInfo {
  def props: Props = Props(new PersistentTaxiTripPassengerInfo)
}
class PersistentTaxiTripPassengerInfo extends PersistentActor with ActorLogging {

  import TaxiTripPassengerInfoCommand._
  import TaxiTripPassengerInfoStatEvent._

  var state: TaxiTripPassengerInfo = TaxiTripPassengerInfo(0)

  // override def persistenceId: String = id
  override def persistenceId: String = "PassengerInfo" + "-" + context.parent.path.name + "-" + self.path.name

  override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    log.error("persist failure being triggered")
    sender() ! OperationResponse("", Left("Failure"), Left(cause.getMessage))
    super.onPersistFailure(cause, event, seqNr)
  }

  override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    log.error("persist rejected being triggered")
    sender() ! OperationResponse("", Left("Failure"), Left(cause.getMessage))
    super.onPersistFailure(cause, event, seqNr)
  }

  override def receiveCommand: Receive = {
    case CreateTaxiTripPassengerInfo(tripId,taxiTripPassengerInfoStat) =>
      persist(TaxiTripPassengerInfoCreatedEvent(tripId,taxiTripPassengerInfoStat)) { _ =>
        log.info(s"Creating Passenger Info Stat $taxiTripPassengerInfoStat")
        state = taxiTripPassengerInfoStat
        sender() ! OperationResponse(tripId,Right("Success"))
      }
    case GetTaxiTripPassengerInfo(tripId) =>
      log.info(s"Request to return Passenger Info for tripId: $tripId")
      sender() ! state
    case UpdateTaxiTripPassenger(tripId,taxiTripPassengerInfoStat) =>
      log.info(s"Applying update for Passenger Info for id $tripId")
      persist(UpdatedTaxiTripPassengerEvent(tripId, taxiTripPassengerInfoStat)) { _ =>
        state = taxiTripPassengerInfoStat
        sender() ! OperationResponse(tripId,Right("Success"))
      }
    case DeleteTaxiTripPassenger(tripId) =>
      log.info("Deleting taxi cost stat")
      persist(DeletedTaxiTripPassengerEvent(tripId)) { _ =>
        state = state.copy(deletedFlag = true)
        sender() ! OperationResponse(tripId,Right("Success"))
      }

    case _ =>
      log.info(s"Received something else at ${self.path.name}")

  }

  override def receiveRecover: Receive = {
    case TaxiTripPassengerInfoCreatedEvent(tripId,taxiTripPassengerInfoStat) =>
      log.info(s"Recovering Passenger Info Stat $tripId")
      state = taxiTripPassengerInfoStat
    case   UpdatedTaxiTripPassengerEvent(tripId,taxiTripPassengerInfoStat) =>
      log.info(s"Recovered Update Event applied for Passenger info Id: $tripId")
      state = taxiTripPassengerInfoStat
    case DeletedTaxiTripPassengerEvent(tripId) =>
      log.info(s"Recovered Deleted Event applied for Passenger info Id: $tripId")
      state = state.copy(deletedFlag = true)
  }
}


