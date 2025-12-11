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

    // Cache for menu items (Name -> Price)
    private final java.util.Map<String, Double> menuCache = new java.util.HashMap<>();
    private final java.util.Map<String, String> descCache = new java.util.HashMap<>();

    private MenuActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> loggingActor,
            ActorRef<RestaurantMessage> dietarySpecialistActor) {
        super(context);
        this.loggingActor = loggingActor;
        this.dietarySpecialistActor = dietarySpecialistActor;
        loadMenuFromFile();
    }

    // Helper to load file content
    private void loadMenuFromFile() {
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("menu_knowledge.txt");
            if (is == null) {
                System.err.println("❌ MenuActor: Could not find menu_knowledge.txt");
                return;
            }
            String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\n");

            String currentItem = "";
            for (String line : lines) {
                line = line.trim();
                // Match lines like "Burger: $16"
                if (line.contains(": $")) {
                    String[] parts = line.split(": \\$");
                    if (parts.length == 2) {
                        String name = parts[0].trim();
                        try {
                            double price = Double.parseDouble(parts[1].split("\\s")[0]); // Handle "$16 (side)"
                            menuCache.put(name.toLowerCase(), price);
                            currentItem = name;

                            // Add nicely formatted name to description cache initially
                            descCache.put(name.toLowerCase(), name);
                        } catch (Exception e) {
                        }
                    }
                } else if (!currentItem.isEmpty() && !line.isEmpty() && !line.startsWith("===")
                        && !line.startsWith("Dietary")) {
                    // Add description detail
                    String key = currentItem.toLowerCase();
                    descCache.put(key, descCache.get(key) + ": " + line);
                    currentItem = ""; // Only take first line of description for simple summary
                }
            }
            System.out.println("✅ MenuActor: Loaded " + menuCache.size() + " items from knowledge base.");
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        String reqItem = request.itemName.toLowerCase();
        boolean valid = false;
        double price = 0.0;
        String desc = "";

        // Fuzzy Match from loaded cache
        for (String key : menuCache.keySet()) {
            if (key.contains(reqItem) || reqItem.contains(key)) {
                valid = true;
                price = menuCache.get(key);
                desc = descCache.get(key);
                break;
            }
        }

        loggingActor.tell(new LogMessage("MenuActor --[reply]--> OrderActor", "INFO"));
        request.replyTo.tell(new ValidateItemResponse(valid, price, desc));

        return this;
    }

    private String getMenuResponse(String query) {
        // Search in loaded cache
        for (String key : descCache.keySet()) {
            if (query.contains(key) || key.contains(query)) {
                return "🍽️ **" + descCache.get(key) + "** ($" + menuCache.get(key) + ")";
            }
        }

        return "📋 **Full Menu Available**: \n" +
                "Please ask specifically about items like Burger, Pasta, Tiramisu, etc. \n" +
                "(Use 'General Chat' to view the full menu list)";
    }
}