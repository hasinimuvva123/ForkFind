package com.restaurant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

public class RoutingActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> menuActor;
    private final ActorRef<RestaurantMessage> orderActor;
    private final ActorRef<RestaurantMessage> reservationActor;
    private final ActorRef<RestaurantMessage> generalChatActor;
    private final ActorRef<RestaurantMessage> loggingActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> menuActor,
            ActorRef<RestaurantMessage> orderActor,
            ActorRef<RestaurantMessage> reservationActor,
            ActorRef<RestaurantMessage> generalChatActor,
            ActorRef<RestaurantMessage> loggingActor) {
        return Behaviors.setup(context -> new RoutingActor(context, menuActor, orderActor, reservationActor,
                generalChatActor, loggingActor));
    }

    private RoutingActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> menuActor,
            ActorRef<RestaurantMessage> orderActor,
            ActorRef<RestaurantMessage> reservationActor,
            ActorRef<RestaurantMessage> generalChatActor,
            ActorRef<RestaurantMessage> loggingActor) {
        super(context);
        this.menuActor = menuActor;
        this.orderActor = orderActor;
        this.reservationActor = reservationActor;
        this.generalChatActor = generalChatActor;
        this.loggingActor = loggingActor;
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryRequest.class, this::onQueryRequest)
                .build();
    }

    private Behavior<RestaurantMessage> onQueryRequest(QueryRequest request) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 📨 ROUTING ACTOR - Received Query                              ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Query: " + request.query);
        System.out.println("║ Type: " + request.queryType);
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // ========== TELL PATTERN (Fire-and-Forget) ==========
        System.out.println("\n🔥 [TELL PATTERN] RoutingActor → LoggingActor");
        System.out.println("   ↳ Fire-and-forget: Sending log message without waiting for response");
        loggingActor.tell(new LogMessage(
                "Routing query: '" + request.query + "' [Type: " + request.queryType + "]",
                "INFO"));

        // Override: specific keywords trigger GeneralChatActor (to demonstrate ASK
        // pattern from main box)
        if (request.query.toLowerCase().startsWith("chat") || request.query.toLowerCase().startsWith("ask")) {
            System.out.println("\n📍 Routing to GeneralChatActor (Override for ASK pattern)");
            loggingActor.tell(new LogMessage("RoutingActor --[sendto]--> GeneralChatActor", "INFO"));
            generalChatActor.tell(request);
            return this;
        }

        // Route to appropriate actor based on query type
        switch (request.queryType.toLowerCase()) {
            case "menu":
                System.out.println("\n📍 Routing to MenuActor");
                loggingActor.tell(new LogMessage("RoutingActor --[sendto]--> MenuActor", "INFO"));
                menuActor.tell(request);
                break;
            case "order":
                System.out.println("\n📍 Routing to OrderActor");
                loggingActor.tell(new LogMessage("RoutingActor --[sendto]--> OrderActor", "INFO"));
                orderActor.tell(request);
                break;
            case "chat":
                System.out.println("\n📍 Routing to GeneralChatActor");
                loggingActor.tell(new LogMessage("RoutingActor --[sendto]--> GeneralChatActor", "INFO"));
                generalChatActor.tell(request);
                break;
            case "reservation":
                System.out.println("\n📍 Routing to ReservationActor");
                loggingActor.tell(new LogMessage("RoutingActor --[sendto]--> ReservationActor", "INFO"));
                reservationActor.tell(request);
                break;
            default:
                System.out.println("\n❌ Unknown query type: " + request.queryType);
                loggingActor
                        .tell(new LogMessage("RoutingActor --[error]--> Unknown Type: " + request.queryType, "ERROR"));
                request.replyTo.tell(new QueryResponse(
                        "Unknown query type. Please specify: menu, order, or reservation",
                        false));
        }

        return this;
    }
}