package com.hashmap.haf.metadata.config.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import com.hashmap.haf.metadata.config.actor.message.metadata.CreateMetadataConfigMsg;
import com.hashmap.haf.metadata.config.actor.message.metadata.DeleteMetadataConfigMsg;
import com.hashmap.haf.metadata.config.actor.message.metadata.UpdateMetadataConfigMsg;
import com.hashmap.haf.metadata.config.actor.message.query.CreateQueryMsg;
import com.hashmap.haf.metadata.config.actor.message.query.DeleteQueryMsg;
import com.hashmap.haf.metadata.config.actor.message.query.UpdateQueryMsg;
import com.hashmap.haf.metadata.config.model.MetadataConfig;
import com.hashmap.haf.metadata.config.model.MetadataConfigId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
@Slf4j
public class ManagerActorService {

    @Autowired
    private ActorSystemContext actorSystemContext;

    private ActorSystem system;

    private ActorRef managerActor;

    @PostConstruct
    public void initActorSystem() {
        log.info("Initializing Metadata Config Actor System");
        actorSystemContext.setActorService(this);

        system = ActorSystem.create("MetadataConfigActorSystem");

        log.info("Creating Metadata Manager Actor");
        managerActor = system.actorOf(ManagerActor.props(), "MetadataConfigManagerActor");

    }

    @PreDestroy
    public void stopActorSystem() {
        Future<Terminated> status = system.terminate();
        try {
            Terminated terminated = Await.result(status, Duration.Inf());
            log.info("Actor system terminated: {}", terminated);
        } catch (Exception e) {
            log.error("Failed to terminate actor system.", e);
        }
    }

    public void createMetadataConfig(MetadataConfig metadataConfig) {
        log.info("Processing Create Metadata Config msg");
        managerActor.tell(new CreateMetadataConfigMsg(metadataConfig), ActorRef.noSender());
    }

    public void updateMetadataConfig(MetadataConfig metadataConfig) {
        log.info("Processing Update Metadata Config  msg");
        managerActor.tell(new UpdateMetadataConfigMsg(metadataConfig), ActorRef.noSender());
    }

    public void deleteMetadataConfig(MetadataConfig metadataConfig) {
        log.info("Processing Delete Metadata Config  msg");
        managerActor.tell(new DeleteMetadataConfigMsg(metadataConfig), ActorRef.noSender());
    }

    public void createQuery(String query, MetadataConfigId metadataConfigId) {
        log.info("Processing Create Query  msg");
        managerActor.tell(new CreateQueryMsg(query, metadataConfigId), ActorRef.noSender());
    }

    public void updateQuery(String query, MetadataConfigId metadataConfigId) {
        log.info("Processing Update Query msg");
        managerActor.tell(new UpdateQueryMsg(query, metadataConfigId), ActorRef.noSender());
    }

    public void deleteQuery(String query, MetadataConfigId metadataConfigId) {
        log.info("Processing Delete Query  msg");
        managerActor.tell(new DeleteQueryMsg(query, metadataConfigId), ActorRef.noSender());
    }
}
