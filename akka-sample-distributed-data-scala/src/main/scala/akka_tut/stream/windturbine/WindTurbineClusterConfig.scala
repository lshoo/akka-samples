package akka_tut.stream.windturbine

import akka.cluster.sharding._
import akka.cluster.sharding.ShardRegion._

/**
  * Created by liaoshifu on 17/11/13
  */
object WindTurbineClusterConfig {

  private val numberOfShards = 100

  case class EntityEnvelope(id: String, payload: String)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id, payload)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => (id.hashCode() % numberOfShards).toString
    case ShardRegion.StartEntity(id) => (id.hashCode % numberOfShards).toString
  }
}
