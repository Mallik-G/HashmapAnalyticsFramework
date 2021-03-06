package com.hashmap.haf.metadata.config.actors;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import com.hashmap.haf.metadata.config.actors.message.MessageType;
import com.hashmap.haf.metadata.config.actors.message.metadata.MetadataMessage;
import com.hashmap.haf.metadata.config.actors.message.metadata.RunIngestionMsg;
import com.hashmap.haf.metadata.config.actors.message.query.ExecuteQueryMsg;
import com.hashmap.haf.metadata.config.actors.message.query.QueryMessage;
import com.hashmap.haf.metadata.config.actors.service.ActorSystemContext;
import com.hashmap.haf.metadata.config.actors.service.ManagerActorService;
import com.hashmap.haf.metadata.config.model.config.MetadataConfig;
import com.hashmap.haf.metadata.config.model.query.MetadataQuery;
import com.hashmap.haf.metadata.config.model.query.MetadataQueryId;
import com.mysql.jdbc.CommunicationsException;
import com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MetadataConfigActor extends AbstractActor {

    private MetadataConfig metadataConfig;
    private final Map<MetadataQueryId, ActorRef> metadataQueryIdToActor = new HashMap<>();
    private final Map<ActorRef, MetadataQueryId> actorToMetadataQueryId = new HashMap<>();
    private final ActorSystemContext actorSystemContext;

    static public Props props(ActorSystemContext actorSystemContext) {
        return Props.create(MetadataConfigActor.class, actorSystemContext).withDispatcher(getMetadataDispatcher());
    }

    private MetadataConfigActor(ActorSystemContext actorSystemContext){
        this.actorSystemContext = actorSystemContext;
    }

    private SupervisorStrategy strategy = new OneForOneStrategy(3, Duration.create(3, TimeUnit.SECONDS),
            DeciderBuilder
                    .match(CommunicationsException.class, e -> {
                        log.warn("CommunicationsException {}", e.getMessage());
                        return akka.actor.SupervisorStrategy.restart();
                    })
                    .match(MySQLSyntaxErrorException.class, e -> {
                        log.warn("MySQLSyntaxErrorException {}", e.getMessage());
                        return akka.actor.SupervisorStrategy.stop();
                    })
                    .match(SQLException.class, e -> {
                        log.warn("SQLException {}", e.getMessage());
                        return akka.actor.SupervisorStrategy.restart();
                    })
                    .match(MalformedURLException.class, e -> {
                        log.warn("MalformedURLException {}", e.getMessage());
                        return akka.actor.SupervisorStrategy.stop();
                    })
                    .match(IOException.class, e -> {
                        log.warn("IOException {}", e.getMessage());
                        return akka.actor.SupervisorStrategy.restart();
                    })
                    .match(Exception.class, e -> {
                        log.warn("Exception {}", e.getMessage());
                        return akka.actor.SupervisorStrategy.restart();
                    })
                    .matchAny(o -> akka.actor.SupervisorStrategy.escalate())
                    .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    private  void processMetadataConfigMsg(MetadataMessage message) {
        if (message.getMessageType() == MessageType.CREATE) {
            log.debug("Message Create metadataConfig actor");
            metadataConfig = message.getMetadataConfig();
        } else if (message.getMessageType() == MessageType.UPDATE) {
            log.debug("Updating metadataConfig actors for {}", metadataConfig.getId());
            metadataConfig = message.getMetadataConfig();
        } else if (message.getMessageType() == MessageType.DELETE) {
            log.debug("Deleting metadataConfig actors for {}",  metadataConfig.getId());
            context().stop(self());
        }
    }

    private void processQueryMsg(QueryMessage message) {
        metadataConfig = message.getMetadataConfig();
        MetadataQuery metadataQuery = message.getMetadataQuery();
        ActorRef metadataQueryActor = metadataQueryIdToActor.get(metadataQuery.getId());
        if(metadataQueryActor != null) {
            log.debug("Found metadataQuery actors for MetadataQueryId : {}", metadataQuery.getId());
            metadataQueryActor.tell(message, ActorRef.noSender());
        } else {
            log.debug("Creating metadataQuery actors for MetadataQueryId : {}", metadataQuery.getId());
            createMetadataQueryActor(message, metadataConfig);
        }
    }

    private void createMetadataQueryActor(QueryMessage message, MetadataConfig metadataConfig) {
        ActorRef metadataQueryActor = getContext().actorOf(MetadataQueryActor.props(actorSystemContext, metadataConfig, message.getMetadataQuery()), message.getMetadataQuery().getId().toString());
        getContext().watch(metadataQueryActor);
        metadataQueryIdToActor.put(message.getMetadataQuery().getId(), metadataQueryActor);
        actorToMetadataQueryId.put(metadataQueryActor, message.getMetadataQuery().getId());
        metadataQueryActor.tell(message, ActorRef.noSender());
    }

    private void processMessage(Object message) {
        if (message instanceof RunIngestionMsg) {
            metadataConfig  = ((RunIngestionMsg)message).getMetadataConfig();
            for (Map.Entry<MetadataQueryId, ActorRef> entry : metadataQueryIdToActor.entrySet()) {
                ActorRef queryActor = entry.getValue();
                queryActor.tell(new ExecuteQueryMsg(), ActorRef.noSender());
            }
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef metadataQueryActor = t.getActor();
        MetadataQueryId metadataQueryId = actorToMetadataQueryId.get(metadataQueryActor);
        log.info("MetadataQuery actors for {} has been terminated", metadataQueryId);
        actorToMetadataQueryId.remove(metadataQueryActor);
        metadataQueryIdToActor.remove(metadataQueryId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MetadataMessage.class, this::processMetadataConfigMsg)
                .match(QueryMessage.class, this::processQueryMsg)
                .match(RunIngestionMsg.class, this::processMessage)
                .match(Terminated.class, this::onTerminated)
                .matchAny(o -> log.warn("received unknown message [{}]", o.getClass().getName()))
                .build();
    }

    private static String getMetadataDispatcher() {
        return ManagerActorService.METADATA_DISPATCHER;
    }

}
