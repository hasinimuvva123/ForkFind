package com.restaurant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

public class DietarySpecialistActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> loggingActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> loggingActor) {
        return Behaviors.setup(context -> new DietarySpecialistActor(context, loggingActor));
    }

    private DietarySpecialistActor(ActorContext<RestaurantMessage> context,
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
        System.out.println("║ 🌿 DIETARY SPECIALIST ACTOR - Received Forwarded Request       ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Query: " + request.query);
        System.out.println("║ Note: This was FORWARDED from MenuActor");
        System.out.println("║ Will respond DIRECTLY to original sender (not to MenuActor)");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        String response = getDietaryResponse(request.query.toLowerCase());
        String specialistResponse = "🌿 **Dietary Specialist**: \n" + response;

        loggingActor.tell(new LogMessage("DietarySpecialistActor --[tell]--> User", "INFO"));
        request.replyTo.tell(new QueryResponse(specialistResponse, true));

        return this;
    }

    private String getDietaryResponse(String query) {
        if (query.contains("gluten")) {
            return "**Gluten Policy**: \n" +
                    "We have a dedicated gluten-free fryer. Items marked **GF** are safe. \n" +
                    "However, our bakery uses flour, so severe celiac cross-contamination is possible.";
        } else if (query.contains("vegan")) {
            return "**Vegan Options**: \n" +
                    "• **Impossible Burger** (Specify no mayo)\n" +
                    "• **House Pasta** (Ask for oil instead of cream)\n" +
                    "• **Garden Salad** (Balsamic is vegan)";
        } else if (query.contains("nut") || query.contains("peanut")) {
            return "⚠️ **Nut Allergy Warning**: \n" +
                    "We use peanuts and tree nuts in our desserts and pesto. Please inform your server immediately.";
        } else if (query.contains("seafood") || query.contains("shellfish")) {
            return "⚠️ **Shellfish Sensitivity**: \n" +
                    "Our Calamari and Grilled Salmon are prepared in a kitchen that handles shellfish. \n" +
                    "Avoid the 'Seafood Platter' and 'Lobster Bisque'.";
        } else if (query.contains("soy")) {
            return "**Soy Information**: \n" +
                    "Soy is present in our Teriyaki Glaze and some salad dressings. \n" +
                    "Please ask for 'No Soy' preparation on grilled items.";
        } else if (query.contains("dairy") || query.contains("lactose")) {
            return "**Dairy Free**: \n" +
                    "Our burgers can be served without cheese. \n" +
                    "The Sorbet Trio is strictly dairy-free.";
        } else if (query.contains("egg")) {
            return "⚠️ **Egg Allergy**: \n" +
                    "Our pasta and brioche buns contain eggs. \n" +
                    "Please request gluten-free buns or salad base instead.";
        } else {
            return "**Dietary Info**: \n" +
                    "We take allergies seriously. Supported checks: Gluten, Vegan, Nut, Seafood, Soy, Dairy, Egg. \n" +
                    "Please allow us 15 minutes extra to prepare special dietary requests carefully.";
        }
    }
}