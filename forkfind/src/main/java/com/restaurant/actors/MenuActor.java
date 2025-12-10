package com.restaurant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

public class MenuActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> loggingActor;
    private final ActorRef<RestaurantMessage> dietarySpecialistActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> loggingActor,
            ActorRef<RestaurantMessage> dietarySpecialistActor) {
        return Behaviors.setup(context -> new MenuActor(context, loggingActor, dietarySpecialistActor));
    }

    private MenuActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> loggingActor,
            ActorRef<RestaurantMessage> dietarySpecialistActor) {
        super(context);
        this.loggingActor = loggingActor;
        this.dietarySpecialistActor = dietarySpecialistActor;
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryRequest.class, this::onQueryRequest)
                .onMessage(ValidateItemRequest.class, this::onValidateItemRequest)
                .build();
    }

    private Behavior<RestaurantMessage> onQueryRequest(QueryRequest request) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 🍽️  MENU ACTOR - Processing Request                            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Query: " + request.query);
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // Check if query contains dietary keywords
        String queryLower = request.query.toLowerCase();
        boolean isDietaryQuery = queryLower.contains("vegan") ||
                queryLower.contains("vegetarian") ||
                queryLower.contains("gluten") ||
                queryLower.contains("dairy") ||
                queryLower.contains("allergy") ||
                queryLower.contains("allergen") ||
                queryLower.contains("dietary");

        if (isDietaryQuery) {
            // ========== FORWARD PATTERN ==========
            System.out.println("\n🔀 [FORWARD PATTERN] MenuActor → DietarySpecialistActor");
            System.out.println("   ↳ Detected dietary keywords in query");
            System.out.println("   ↳ Delegating to specialist (preserving original sender)");

            // TELL to logging (fire-and-forget)
            loggingActor.tell(new LogMessage("MenuActor --[forward]--> DietarySpecialistActor", "INFO"));

            // FORWARD: Pass request to specialist with original sender preserved
            dietarySpecialistActor.tell(request);

        } else {
            System.out.println("\n   ↳ Regular menu query - handling internally (Logic-Based)");

            String response = getMenuResponse(queryLower);

            loggingActor.tell(new LogMessage("MenuActor --[tell]--> User", "INFO"));
            request.replyTo.tell(new QueryResponse(response, true));
        }

        return this;
    }

    private Behavior<RestaurantMessage> onValidateItemRequest(ValidateItemRequest request) {
        System.out.println("\n   ↳ MenuActor: Validating item '" + request.itemName + "' for OrderActor");

        String item = request.itemName.toLowerCase();
        double price = 0.0;
        String desc = "";
        boolean valid = false;

        // Simple database logic
        if (item.contains("burger")) {
            valid = true;
            price = 16.0;
            desc = "Classic ForkFind Burger";
        } else if (item.contains("pasta")) {
            valid = true;
            price = 22.0;
            desc = "House Pasta";
        } else if (item.contains("salad")) {
            valid = true;
            price = 12.0;
            desc = "Garden Salad";
        } else if (item.contains("steak")) {
            valid = true;
            price = 28.0;
            desc = "Steak Frites";
        }

        loggingActor.tell(new LogMessage("MenuActor --[reply]--> OrderActor", "INFO"));
        request.replyTo.tell(new ValidateItemResponse(valid, price, desc));

        return this;
    }

    private String getMenuResponse(String query) {
        if (query.contains("burger")) {
            return "🍔 **Classic ForkFind Burger**: \n" +
                    "A juicy 1/2 lb beef patty topped with aged cheddar, caramelized onions, lettuce, and our secret sauce on a brioche bun. Served with fries. ($16)";
        } else if (query.contains("pasta") || query.contains("spaghetti") || query.contains("carbonara")) {
            return "🍝 **House Pasta**: \n" +
                    "Freshly made linguine with truffle cream sauce, mushrooms, and parmesan crisp. A vegetarian favorite! ($22)";
        } else if (query.contains("salad")) {
            return "🥗 **Garden Salad**: \n" +
                    "Fresh greens, cherry tomatoes, cucumbers, and balsamic vinaigrette. ($12)";
        } else if (query.contains("drink") || query.contains("wine") || query.contains("beer")) {
            return "🍷 **Drinks Menu**: \n" +
                    "We offer a selection of craft beers ($8), house wines ($10/glass), and signature cocktails ($14).";
        } else {
            return "📋 **ForkFind User Menu**: \n\n" +
                    "• **Classic Burger** ($16)\n" +
                    "• **Truffle Pasta** ($22)\n" +
                    "• **Garden Salad** ($12)\n" +
                    "• **Steak Frites** ($28)\n\n" +
                    "Ask specifically about an item for more details!";
        }
    }
}