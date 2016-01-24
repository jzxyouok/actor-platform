package im.actor.server.api.rpc.service.encryption

import akka.actor.ActorSystem
import im.actor.api.rpc._
import im.actor.api.rpc.encryption._
import im.actor.api.rpc.misc.ResponseVoid
import im.actor.api.rpc.peers.ApiUserOutPeer
import im.actor.server.encryption.EncryptionExtension

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.{ ExecutionContext, Future }

final class EncryptionServiceImpl(implicit system: ActorSystem) extends EncryptionService {
  import PeerHelpers._

  override implicit protected val ec: ExecutionContext = system.dispatcher
  private val encExt = EncryptionExtension(system)

  override def jhandleLoadPublicKeyGroups(
    userPeer:   ApiUserOutPeer,
    clientData: ClientData
  ): Future[HandlerResult[ResponsePublicKeyGroups]] =
    authorized(clientData) { implicit client ⇒
      withUserOutPeerF(userPeer) {
        for {
          keyGroups ← encExt.fetchApiKeyGroups(userPeer.userId)
        } yield Ok(ResponsePublicKeyGroups(keyGroups))
      }
    }

  override def jhandleCreateNewKeyGroup(
    identityKey:          ApiEncryptionKey,
    supportedEncryptions: IndexedSeq[String],
    keys:                 IndexedSeq[ApiEncryptionKey],
    signatures:           IndexedSeq[ApiEncryptionKeySignature],
    clientData:           ClientData
  ): Future[HandlerResult[ResponseCreateNewKeyGroup]] =
    authorized(clientData) { client ⇒
      for {
        id ← encExt.createKeyGroup(client.userId, supportedEncryptions, identityKey, keys, signatures)
      } yield Ok(ResponseCreateNewKeyGroup(id))
    }

  override def jhandleDeleteKeyGroup(
    keyGroupId: Int,
    clientData: ClientData
  ): Future[HandlerResult[ResponseVoid]] =
    authorized(clientData) { client ⇒
      for {
        _ ← encExt.deleteKeyGroup(client.userId, keyGroupId)
      } yield Ok(ResponseVoid)
    }

  override def jhandleLoadPublicKey(
    userPeer:   ApiUserOutPeer,
    keyGroupId: Int,
    keyIds:     IndexedSeq[Long],
    clientData: ClientData
  ): Future[HandlerResult[ResponsePublicKeys]] =
    authorized(clientData) { implicit client ⇒
      withUserOutPeerF(userPeer) {
        val keyIdsSet = keyIds.toSet

        for {
          (ks, signs) ← encExt.fetchApiKeys(userPeer.userId, keyGroupId, keyIdsSet)
          (eks, esigns) ← encExt.fetchApiEphermalKeys(userPeer.userId, keyGroupId, keyIdsSet)
        } yield Ok(ResponsePublicKeys(ks ++ eks, signs ++ esigns))
      }
    }

  override def jhandleUploadEphermalKey(
    keyGroupId: Int,
    keys:       IndexedSeq[ApiEncryptionKey],
    signatures: IndexedSeq[ApiEncryptionKeySignature],
    clientData: ClientData
  ): Future[HandlerResult[ResponseVoid]] =
    authorized(clientData) { client ⇒
      for {
        _ ← encExt.createEphermalKeys(client.userId, keyGroupId, keys.toVector, signatures.toVector)
      } yield Ok(ResponseVoid)
    }

  override def jhandleLoadEphermalPublicKeys(
    userPeer:   ApiUserOutPeer,
    keyGroupId: Int,
    clientData: ClientData
  ): Future[HandlerResult[ResponsePublicKeys]] =
    authorized(clientData) { implicit client ⇒
      withUserOutPeerF(userPeer) {
        for {
          (keys, signs) ← encExt.fetchApiEphermalKeys(userPeer.userId, keyGroupId)
        } yield {
          val (respKeys, respSigns) =
            if (keys.nonEmpty) {
              val key = keys(ThreadLocalRandom.current().nextInt(keys.length))
              val respSigns = signs filter (_.keyId == key.keyId)
              (Vector(key), respSigns)
            } else (keys, signs)

          Ok(ResponsePublicKeys(respKeys, respSigns))
        }
      }
    }

  override def jhandleDisconnectKeyGroup(
    keyGroupId: Int,
    clientData: ClientData
  ): Future[HandlerResult[ResponseVoid]] = Future.failed(new RuntimeException("Not implemented"))

  override def jhandleConnectKeyGroup(
    keyGroupId: Int,
    clientData: ClientData
  ): Future[HandlerResult[ResponseVoid]] = Future.failed(new RuntimeException("Not implemented"))
}