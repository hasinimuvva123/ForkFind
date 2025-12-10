package com.restaurant.actors;

import java.time.Duration;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

public class OrderActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> loggingActor;
    private final ActorRef<RestaurantMessage> menuActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> loggingActor,
            ActorRef<RestaurantMessage> menuActor) {
        return Behaviors.setup(context -> new OrderActor(context, loggingActor, menuActor));
    }

    private OrderActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> loggingActor,
            ActorRef<RestaurantMessage> menuActor) {
        super(context);
        this.loggingActor = loggingActor;
        this.menuActor = menuActor;
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryRequest.class, this::onQueryRequest)
                .onMessage(WrappedMenuValidationResponse.class, this::onMenuValidationResponse)
                .build();
    }

    private Behavior<RestaurantMessage> onQueryRequest(QueryRequest request) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 📦 ORDER ACTOR - Processing Order Query                        ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Query: " + request.query);
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // Check if this is a "Place Order" request
        if (request.query.toLowerCase().contains("order") && !request.query.toLowerCase().contains("status")) {
            // Extract item name (simple heuristic: everything after "order")
            String itemName = request.query.toLowerCase().replace("order", "").trim();
            if (itemName.isEmpty()) {
                request.replyTo.tell(
                        new QueryResponse("⚠️ Please specify what you want to order. Example: 'Order Burger'", true));
                return this;
            }

            // ========== ASK PATTERN ==========
            System.out.println("\n❓ [ASK PATTERN] OrderActor → MenuActor");
            System.out.println("   ↳ Asking: 'Is " + itemName + " valid?'");

            loggingActor.tell(new LogMessage("OrderActor --[ask]--> MenuActor", "INFO"));

            ActorRef<QueryResponse> replyTo = request.replyTo;

            getContext().ask(
                    ValidateItemResponse.class,
                    menuActor,
                    Duration.ofSeconds(3),
                    (ActorRef<ValidateItemResponse> ref) -> new ValidateItemRequest(itemName, ref),
                    (response, throwable) -> {
                        if (throwable != null) {
                            return new WrappedMenuValidationResponse(new ValidateItemResponse(false, 0, "Error"),
                                    replyTo);
                        }
                        return new WrappedMenuValidationResponse(response, replyTo);
                    });

        } else {
            // Handle regular status queries immediately (TELL)
            String response = getOrderResponse(request.query.toLowerCase());
            loggingActor.tell(new LogMessage("OrderActor --[tell]--> User", "INFO"));
            request.replyTo.tell(new QueryResponse(response, true));
        }

        return this;
    }

    private Behavior<RestaurantMessage> onMenuValidationResponse(WrappedMenuValidationResponse wrapper) {
        ValidateItemResponse response = wrapper.response;

        System.out.println("\n✅ [ASK RESPONSE] OrderActor received reply");
        System.out.println("   ↳ Valid: " + response.isValid);
        System.out.println("   ↳ Price: $" + response.price);

        loggingActor.tell(new LogMessage("MenuActor --[reply]--> OrderActor", "INFO"));

        String userResponse;
        if (response.isValid) {
            userResponse = "✅ **Order Confirmed**: \n" +
                    "1x " + response.description + " ($" + response.price + ")\n" +
                    "Your order has been sent to the kitchen.";
        } else {
            userResponse = "🚫 **Item Not Found**: \n" +
                    "Sorry, we couldn't find that item on our menu.";
        }

        loggingActor.tell(new LogMessage("OrderActor --[tell]--> User", "INFO"));
        wrapper.originalReplyTo.tell(new QueryResponse(userResponse, true));

        return this;
    }

    private String getOrderResponse(String query) {
        if (query.contains("status") || query.contains("track") || query.contains("where")) {
            return "📦 **Order Status**: \n" +
                    "Order #1234 is currently **Cooking**. \n" +
                    "Estimated time remaining: **15 minutes**.";
        } else if (query.contains("cancel")) {
            return "🚫 **Cancellation Failed**: \n" +
                    "Sorry, Order #1234 is already in the kitchen and cannot be cancelled at this stage.";
        } else if (query.contains("change") || query.contains("modify")) {
            return "✏️ **Modify Order**: \n" +
                    "To modify your order, please call the front desk at 555-0199 immediately.";
        } else {
            return "📦 **Order Assistant**: \n" +
                    "I can help you check your **status**, **cancel** an order, or make **changes**.\n" +
                    "What would you like to do?";
        }
    }
}