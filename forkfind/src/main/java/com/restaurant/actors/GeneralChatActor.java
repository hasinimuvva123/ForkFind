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
    private final ActorRef<RestaurantMessage> retrievalActor;
    private final ActorRef<RestaurantMessage> loggingActor;

    public static Behavior<RestaurantMessage> create(
            ActorRef<RestaurantMessage> llmActor,
            ActorRef<RestaurantMessage> retrievalActor,
            ActorRef<RestaurantMessage> loggingActor) {
        return Behaviors.setup(context -> new GeneralChatActor(context, llmActor, retrievalActor, loggingActor));
    }

    private GeneralChatActor(ActorContext<RestaurantMessage> context,
            ActorRef<RestaurantMessage> llmActor,
            ActorRef<RestaurantMessage> retrievalActor,
            ActorRef<RestaurantMessage> loggingActor) {
        super(context);
        this.llmActor = llmActor;
        this.retrievalActor = retrievalActor;
        this.loggingActor = loggingActor;
    }

    // Internal wrapper class to carry context
    private static class WrappedRetrievalResult implements RestaurantMessage {
        public final RetrievalResponse response;
        public final String originalQuery;
        public final ActorRef<QueryResponse> originalReplyTo;

        public WrappedRetrievalResult(RetrievalResponse response, String originalQuery,
                ActorRef<QueryResponse> originalReplyTo) {
            this.response = response;
            this.originalQuery = originalQuery;
            this.originalReplyTo = originalReplyTo;
        }
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryRequest.class, this::onQueryRequest)
                .onMessage(WrappedRetrievalResult.class, this::onWrappedRetrievalResult)
                .onMessage(WrappedLLMResponse.class, this::onLLMResponse)
                .build();
    }

    private Behavior<RestaurantMessage> onQueryRequest(QueryRequest request) {
        // If the query type is "chat", utilize DIRECT LLM (No RAG).
        // If the query type is "menu", "order", "reservation", utilize RAG (Context +
        // LLM).

        boolean isGeneralChat = "chat".equalsIgnoreCase(request.queryType);

        if (isGeneralChat) {
            System.out.println("\n--- [GeneralChatActor] Processing CASUAL CHAT (No RAG) ---");
            String prompt = "You are a helpful and friendly AI assistant. Answer this: \"" + request.query + "\"";
            askLLM(prompt, request.replyTo);
        } else {
            System.out.println("\n--- [GeneralChatActor] Processing RAG QUERY (With Knowledge Base) ---");
            loggingActor.tell(new LogMessage("GeneralChatActor --[ask]--> RetrievalActor", "INFO"));

            ActorRef<QueryResponse> originalReplyTo = request.replyTo;
            String originalQuery = request.query;

            getContext().ask(
                    RetrievalResponse.class,
                    retrievalActor,
                    Duration.ofSeconds(3),
                    (ActorRef<RetrievalResponse> ref) -> new RetrievalRequest(originalQuery, ref),
                    (response, throwable) -> {
                        if (throwable != null) {
                            // Fallback to empty context on error
                            return new WrappedRetrievalResult(new RetrievalResponse("", false), originalQuery,
                                    originalReplyTo);
                        }
                        return new WrappedRetrievalResult(response, originalQuery, originalReplyTo);
                    });
        }
        return this;
    }

    private Behavior<RestaurantMessage> onWrappedRetrievalResult(WrappedRetrievalResult wrapper) {
        String context = wrapper.response != null ? wrapper.response.context : "";
        String query = wrapper.originalQuery;

        System.out.println("   ↳ GeneralChatActor received RAG Context (" + context.length() + " chars)");

        String prompt = "You are a specialized restaurant assistant for ForkFind.\n" +
                "Use the following KNOWLEDGE BASE to answer the user request carefully.\n" +
                "IMPORTANT: If the user asks to see the menu, you MUST list all the items found in the KNOWLEDGE BASE below. Do not summarize.\n"
                +
                "Format the output nicely with bullet points.\n\n" +
                "=== KNOWLEDGE BASE ===\n" + context + "\n\n" +
                "=== END KNOWLEDGE BASE ===\n\n" +
                "User Query: \"" + query + "\"";

        askLLM(prompt, wrapper.originalReplyTo);
        return this;
    }

    private void askLLM(String prompt, ActorRef<QueryResponse> replyTo) {
        loggingActor.tell(new LogMessage("GeneralChatActor --[ask]--> LLMActor", "INFO"));

        getContext().ask(
                LLMResponse.class,
                llmActor,
                Duration.ofSeconds(60),
                (ActorRef<LLMResponse> ref) -> new LLMRequest(prompt, ref),
                (response, throwable) -> {
                    if (throwable != null) {
                        return new WrappedLLMResponse(
                                new LLMResponse("Error: " + throwable.getMessage(), false), replyTo);
                    }
                    return new WrappedLLMResponse(response, replyTo);
                });
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
