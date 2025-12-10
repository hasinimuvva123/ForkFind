package com.restaurant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

public class ReservationActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> loggingActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> loggingActor) {
        return Behaviors.setup(context -> new ReservationActor(context, loggingActor));
    }

    private ReservationActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> loggingActor) {
        super(context);
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
        System.out.println("║ 📅 RESERVATION ACTOR - Processing Reservation Query            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Query: " + request.query);
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        String response = getReservationResponse(request.query.toLowerCase());

        loggingActor.tell(new LogMessage("ReservationActor --[tell]--> User", "INFO"));
        request.replyTo.tell(new QueryResponse(response, true));

        return this;
    }

    private String getReservationResponse(String query) {
        if (query.contains("time") || query.contains("slot") || query.contains("available")) {
            return "📅 **Availability Today**: \n" +
                    "• **6:00 PM** (Patio)\n" +
                    "• **7:30 PM** (Main Dining)\n" +
                    "• **9:00 PM** (Bar Seating)\n" +
                    "Reply with a time to book.";
        } else if (query.contains("book") || query.contains("reserve")) {
            return "✅ **Reservation Confirmed**: \n" +
                    "Table for 2 booked at **7:30 PM** in Main Dining. \n" +
                    "Confirmation #RES-9988.";
        } else if (query.contains("cancel")) {
            return "❌ **Reservation Cancelled**: \n" +
                    "Your reservation #RES-9988 has been cancelled.";
        } else {
            return "📅 **Reservations**: \n" +
                    "We are open daily from 5pm to 10pm. \n" +
                    "I can help you **check availability** or **book a table**.";
        }
    }
}