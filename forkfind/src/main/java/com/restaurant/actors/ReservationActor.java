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

    // State to track active reservations (InMemory)
    private final java.util.Set<String> activeReservations = new java.util.HashSet<>();

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

        String response = getReservationResponse(request.query); // Remove toLowerCase() here to preserve Case for ID
                                                                 // extraction if needed, but safe to handle inside

        loggingActor.tell(new LogMessage("ReservationActor --[tell]--> User", "INFO"));
        request.replyTo.tell(new QueryResponse(response, true));

        return this;
    }

    private String getReservationResponse(String query) {
        String qLower = query.toLowerCase();

        if (qLower.contains("cancel")) {
            // Extract ID: Look for RES-XXXX
            java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("RES-(\\d{4})",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher idMatcher = idPattern.matcher(query);

            if (idMatcher.find()) {
                String idToCancel = idMatcher.group(0).toUpperCase(); // RES-XXXX

                if (activeReservations.contains(idToCancel)) {
                    activeReservations.remove(idToCancel);
                    System.out.println("❌ [ReservationActor] Cancelled reservation: " + idToCancel);
                    return "❌ **Reservation Cancelled**: \n" +
                            "Reservation **" + idToCancel + "** has been successfully cancelled.";
                } else {
                    return "⚠️ **Cancellation Failed**: \n" +
                            "Could not find active reservation with ID **" + idToCancel + "**.";
                }
            } else {
                return "❓ **Missing ID**: \n" +
                        "Please provide the reservation ID to cancel (e.g., 'Cancel RES-1234').";
            }
        } else if (qLower.contains("time") || qLower.contains("slot") || qLower.contains("available")) {
            return "📅 **Availability Today**: \n" +
                    "• **6:00 PM** (Patio)\n" +
                    "• **7:30 PM** (Main Dining)\n" +
                    "• **9:00 PM** (Bar Seating)\n" +
                    "Reply with a time to book.";
        } else if (qLower.contains("book") || qLower.contains("reserve") || qLower.contains("reservation")) {
            // Dynamic Parsing Logic
            String time = "7:00 PM"; // Default
            String guests = "2"; // Default

            // Extract Time
            java.util.regex.Pattern timePattern = java.util.regex.Pattern
                    .compile("(\\d{1,2}(?::\\d{2})?\\s?(?:AM|PM|am|pm)?)");
            java.util.regex.Matcher timeMatcher = timePattern.matcher(query);
            if (timeMatcher.find()) {
                time = timeMatcher.group(1).toUpperCase();
            }

            // Extract Party Size
            java.util.regex.Pattern guestPattern = java.util.regex.Pattern
                    .compile("(\\d+)\\s?(?:people|guests|persons|ppl)");
            java.util.regex.Matcher guestMatcher = guestPattern.matcher(qLower);
            if (guestMatcher.find()) {
                guests = guestMatcher.group(1);
            } else {
                java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile("table for\\s?(\\d+)");
                java.util.regex.Matcher tableMatcher = tablePattern.matcher(qLower);
                if (tableMatcher.find()) {
                    guests = tableMatcher.group(1);
                }
            }

            // Generte and Store ID
            int confId = 1000 + (int) (Math.random() * 9000);
            String fullId = "RES-" + confId;
            activeReservations.add(fullId);
            System.out.println("✅ [ReservationActor] Stored new reservation: " + fullId);

            return "✅ **Reservation Confirmed**: \n" +
                    "Table for " + guests + " guests booked at **" + time + "** in Main Dining. \n" +
                    "Confirmation **" + fullId + "**.\n" +
                    "(Use this ID to cancel if needed)";
        } else {
            return "📅 **Reservations**: \n" +
                    "We are open daily from 5pm to 10pm. \n" +
                    "Example: 'Book a table for 4 people at 8pm'";
        }
    }
}