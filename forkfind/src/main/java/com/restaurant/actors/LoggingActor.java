package com.restaurant.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggingActor extends AbstractBehavior<RestaurantMessage> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Behavior<RestaurantMessage> create() {
        return Behaviors.setup(LoggingActor::new);
    }

    private LoggingActor(ActorContext<RestaurantMessage> context) {
        super(context);
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 📝 LOGGING ACTOR - Started                                     ║");
        System.out.println("║ Ready to receive TELL messages (fire-and-forget)               ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");
    }

    private final java.util.List<String> logs = new java.util.ArrayList<>();
    private static final int MAX_LOGS = 50;

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(LogMessage.class, this::onLogMessage)
                .onMessage(GetLogsRequest.class, this::onGetLogsRequest)
                .build();
    }

    private Behavior<RestaurantMessage> onLogMessage(LogMessage log) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] [%s] %s", timestamp, log.level, log.message);

        // Store log
        logs.add(logEntry);
        if (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 📝 LOGGING ACTOR - Received TELL Message                       ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Level: " + log.level);
        System.out.println("║ Message: " + log.message);
        System.out.println("║ Timestamp: " + timestamp);
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        return this;
    }

    private Behavior<RestaurantMessage> onGetLogsRequest(GetLogsRequest request) {
        request.replyTo.tell(new GetLogsResponse(new java.util.ArrayList<>(logs)));
        return this;
    }
}