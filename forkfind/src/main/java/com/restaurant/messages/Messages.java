package com.restaurant.messages;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Messages {

    // Base message interface - no Serializable needed for Jackson
    public interface RestaurantMessage {
    }

    // Query message from user
    public static class QueryRequest implements RestaurantMessage {
        public final String query;
        public final String queryType; // "menu", "order", "reservation"
        public final ActorRef<QueryResponse> replyTo;

        @JsonCreator
        public QueryRequest(
                @JsonProperty("query") String query,
                @JsonProperty("queryType") String queryType,
                @JsonProperty("replyTo") ActorRef<QueryResponse> replyTo) {
            this.query = query;
            this.queryType = queryType;
            this.replyTo = replyTo;
        }
    }

    // Response message
    public static class QueryResponse implements RestaurantMessage {
        public final String response;
        public final boolean success;

        @JsonCreator
        public QueryResponse(
                @JsonProperty("response") String response,
                @JsonProperty("success") boolean success) {
            this.response = response;
            this.success = success;
        }

        @JsonProperty("response")
        public String getResponse() {
            return response;
        }

        @JsonProperty("success")
        public boolean isSuccess() {
            return success;
        }
    }

    // LLM Request
    public static class LLMRequest implements RestaurantMessage {
        public final String prompt;
        public final ActorRef<LLMResponse> replyTo;

        @JsonCreator
        public LLMRequest(
                @JsonProperty("prompt") String prompt,
                @JsonProperty("replyTo") ActorRef<LLMResponse> replyTo) {
            this.prompt = prompt;
            this.replyTo = replyTo;
        }
    }

    // LLM Response
    public static class LLMResponse implements RestaurantMessage {
        public final String result;
        public final boolean success;

        @JsonCreator
        public LLMResponse(
                @JsonProperty("result") String result,
                @JsonProperty("success") boolean success) {
            this.result = result;
            this.success = success;
        }
    }

    // Logging message
    public static class LogMessage implements RestaurantMessage {
        public final String message;
        public final String level; // INFO, WARN, ERROR

        @JsonCreator
        public LogMessage(
                @JsonProperty("message") String message,
                @JsonProperty("level") String level) {
            this.message = message;
            this.level = level;
        }
    }

    // Wrapped message for forwarding
    public static class WrappedLLMResponse implements RestaurantMessage {
        public final LLMResponse response;
        public final ActorRef<QueryResponse> originalReplyTo;

        @JsonCreator
        public WrappedLLMResponse(@JsonProperty("response") LLMResponse response) {
            this.response = response;
            this.originalReplyTo = null;
        }

        public WrappedLLMResponse(LLMResponse response, ActorRef<QueryResponse> originalReplyTo) {
            this.response = response;
            this.originalReplyTo = originalReplyTo;
        }
    }

    // --- New Messages for Option 2: OrderActor ASKS MenuActor ---

    public static class ValidateItemRequest implements RestaurantMessage {
        public final String itemName;
        public final ActorRef<ValidateItemResponse> replyTo;

        @JsonCreator
        public ValidateItemRequest(
                @JsonProperty("itemName") String itemName,
                @JsonProperty("replyTo") ActorRef<ValidateItemResponse> replyTo) {
            this.itemName = itemName;
            this.replyTo = replyTo;
        }
    }

    public static class ValidateItemResponse implements RestaurantMessage {
        public final boolean isValid;
        public final double price;
        public final String description;

        @JsonCreator
        public ValidateItemResponse(
                @JsonProperty("isValid") boolean isValid,
                @JsonProperty("price") double price,
                @JsonProperty("description") String description) {
            this.isValid = isValid;
            this.price = price;
            this.description = description;
        }
    }

    // Adapter for OrderActor to receive the result of the ASK
    public static class WrappedMenuValidationResponse implements RestaurantMessage {
        public final ValidateItemResponse response;
        public final ActorRef<QueryResponse> originalReplyTo;

        public WrappedMenuValidationResponse(ValidateItemResponse response, ActorRef<QueryResponse> originalReplyTo) {
            this.response = response;
            this.originalReplyTo = originalReplyTo;
        }
    }

    // Get logs request
    public static class GetLogsRequest implements RestaurantMessage {
        public final ActorRef<GetLogsResponse> replyTo;

        @JsonCreator
        public GetLogsRequest(@JsonProperty("replyTo") ActorRef<GetLogsResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Get logs response
    public static class GetLogsResponse implements RestaurantMessage {
        public final java.util.List<String> logs;

        @JsonCreator
        public GetLogsResponse(@JsonProperty("logs") java.util.List<String> logs) {
            this.logs = logs;
        }
    }
}