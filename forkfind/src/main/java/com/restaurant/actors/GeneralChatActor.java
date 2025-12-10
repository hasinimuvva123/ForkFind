package com.restaurant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

import java.time.Duration;

public class GeneralChatActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> llmActor;
    private final ActorRef<RestaurantMessage> loggingActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> llmActor,
            ActorRef<RestaurantMessage> loggingActor) {
        return Behaviors.setup(context -> new GeneralChatActor(context, llmActor, loggingActor));
    }

    private GeneralChatActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> llmActor,
            ActorRef<RestaurantMessage> loggingActor) {
        super(context);
        this.llmActor = llmActor;
        this.loggingActor = loggingActor;
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryRequest.class, this::onQueryRequest)
                .onMessage(WrappedLLMResponse.class, this::onLLMResponse)
                .build();
    }

    private Behavior<RestaurantMessage> onQueryRequest(QueryRequest request) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 💬 GENERAL CHAT ACTOR - Processing Chat Request                 ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Query: " + request.query);
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // Simple, open-ended prompt for general chat
        String prompt = "You are a helpful and friendly AI assistant. " +
                "Answer the following question or engage in chat: \"" + request.query + "\"";

        // Capture replyTo locally for the lambda
        ActorRef<QueryResponse> replyTo = request.replyTo;

        loggingActor.tell(new LogMessage("GeneralChatActor --[ask]--> LLMActor", "INFO"));

        getContext().ask(
                LLMResponse.class,
                llmActor,
                Duration.ofSeconds(60), // Longer timeout for creative tasks
                (ActorRef<LLMResponse> ref) -> new LLMRequest(prompt, ref),
                (response, throwable) -> {
                    if (throwable != null) {
                        return new WrappedLLMResponse(
                                new LLMResponse("Error: " + throwable.getMessage(), false), replyTo);
                    }
                    return new WrappedLLMResponse(response, replyTo);
                });

        return this;
    }

    private Behavior<RestaurantMessage> onLLMResponse(WrappedLLMResponse wrapped) {
        loggingActor.tell(new LogMessage("LLMActor --[reply]--> GeneralChatActor", "INFO"));

        if (wrapped.originalReplyTo != null) {
            loggingActor.tell(new LogMessage("GeneralChatActor --[tell]--> User", "INFO"));
            wrapped.originalReplyTo.tell(new QueryResponse(wrapped.response.result, wrapped.response.success));
        }

        return this;
    }
}
