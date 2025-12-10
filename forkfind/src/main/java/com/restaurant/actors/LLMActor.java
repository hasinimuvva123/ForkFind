package com.restaurant.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.messages.Messages.*;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.IOException;

public class LLMActor extends AbstractBehavior<RestaurantMessage> {

    private static String API_KEY;
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    static {
        // Try to load from .env file first
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            // Prefer OPENROUTER_API_KEY, fallback to OPENAI_API_KEY
            API_KEY = dotenv.get("OPENROUTER_API_KEY");
            if (API_KEY == null || API_KEY.isEmpty()) {
                API_KEY = dotenv.get("OPENAI_API_KEY");
            }

            if (API_KEY != null) {
                System.out.println("✅ Loaded API Key from .env file");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Could not load .env file, trying environment variable...");
        }

        // Fall back to system environment variable if still null
        if (API_KEY == null || API_KEY.isEmpty()) {
            API_KEY = System.getenv("OPENROUTER_API_KEY");
            if (API_KEY == null || API_KEY.isEmpty()) {
                API_KEY = System.getenv("OPENAI_API_KEY");
            }

            if (API_KEY != null && !API_KEY.isEmpty()) {
                System.out.println("✅ Loaded API Key from environment variable");
            }
        }

        if (API_KEY == null || API_KEY.isEmpty()) {
            System.out.println("⚠️  WARNING: No API KEY found (checked OPENROUTER_API_KEY and OPENAI_API_KEY)");
            System.out.println("   ↳ Will use mock responses instead of real LLM");
        }
    }

    public static Behavior<RestaurantMessage> create() {
        return Behaviors.setup(LLMActor::new);
    }

    private LLMActor(ActorContext<RestaurantMessage> context) {
        super(context);
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(LLMRequest.class, this::onLLMRequest)
                .build();
    }

    private Behavior<RestaurantMessage> onLLMRequest(LLMRequest request) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 🤖 LLM ACTOR - Processing Request (via OpenRouter)              ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Prompt: " + request.prompt.substring(0, Math.min(60, request.prompt.length())) + "...");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        try {
            String response = callLLM(request.prompt);
            System.out.println("\n✅ LLM Response received successfully");
            System.out.println("   ↳ Sending response back to requester (completing ASK pattern)\n");

            // Reply back to the actor that asked (ASK pattern response)
            request.replyTo.tell(new LLMResponse(response, true));
        } catch (Exception e) {
            System.err.println("\n❌ LLM API Error: " + e.getMessage());
            request.replyTo.tell(new LLMResponse(
                    "Sorry, I couldn't process your request: " + e.getMessage(),
                    false));
        }

        return this;
    }

    private String callLLM(String prompt) throws IOException {
        // Check if API key is available
        if (API_KEY == null || API_KEY.isEmpty()) {
            System.out.println("   ↳ No API key found, using mock response");
            return generateMockResponse(prompt);
        }

        System.out.println("   ↳ Calling OpenRouter API (Streaming Mode)...");

        // Build request body - Enable Streaming
        String jsonBody = String.format(
                "{\"model\": \"openai/gpt-4o-mini\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 300, \"stream\": true}",
                prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json"));

        Request httpRequest = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "http://localhost:8080") // Site URL
                .addHeader("X-Title", "ForkFind Restaurant App") // Site Name
                .post(body)
                .build();

        // Execute request
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 401) {
                    System.out.println("⚠️  API Key Invalid/Unauthorized (401) - Falling back to MOCK MODE");
                    return generateMockResponse(prompt);
                }
                throw new IOException("API request failed: " + response.code() + " - " + response.message());
            }

            // Handle Streaming Response (Server-Sent Events)
            java.io.BufferedReader reader = new java.io.BufferedReader(response.body().charStream());
            StringBuilder fullResponse = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    try {
                        JsonNode node = objectMapper.readTree(data);
                        if (node.has("choices") && node.get("choices").size() > 0) {
                            JsonNode choice = node.get("choices").get(0);
                            if (choice.has("delta") && choice.get("delta").has("content")) {
                                String content = choice.get("delta").get("content").asText();
                                fullResponse.append(content);
                            }
                        }
                    } catch (Exception e) {
                        // Creating logging if parsing fails for a chunk, but continue streaming
                        // System.err.println("Error parsing stream chunk: " + e.getMessage());
                    }
                }
            }

            System.out.println("   ↳ OpenRouter API stream completed");
            if (fullResponse.length() == 0) {
                return "Error: Empty response from LLM provider";
            }
            return fullResponse.toString();
        }
    }

    private String generateMockResponse(String prompt) {
        // Generate contextual mock responses based on prompt content
        String promptLower = prompt.toLowerCase();

        if (promptLower.contains("dietary") || promptLower.contains("vegan") ||
                promptLower.contains("gluten") || promptLower.contains("allergen")) {
            return "Based on your dietary requirements:\n\n"
                    + "✓ Our Grilled Vegetable Medley is completely vegan and gluten-free\n"
                    + "✓ Ingredients: Seasonal vegetables, olive oil, herbs (no dairy, no gluten)\n"
                    + "✓ Prepared on dedicated gluten-free equipment\n"
                    + "✓ Alternative options: Quinoa Buddha Bowl, Mediterranean Salad\n\n"
                    + "All dishes can be customized to meet your specific dietary needs!";
        } else if (promptLower.contains("order")) {
            return "Order Management Update:\n\n"
                    + "📦 Your order has been received\n"
                    + "✓ Current status: In preparation\n"
                    + "✓ Estimated time: 25-30 minutes\n"
                    + "✓ Modifications can be made up to 15 minutes before completion\n\n"
                    + "Would you like to make any changes?";
        } else if (promptLower.contains("reservation")) {
            return "Reservation Information:\n\n"
                    + "📅 Available time slots today:\n"
                    + "• 6:30 PM - Window seating (2-4 guests)\n"
                    + "• 7:15 PM - Patio area (2-6 guests)\n"
                    + "• 8:00 PM - Main dining room (any party size)\n\n"
                    + "Would you like to proceed with booking?";
        } else {
            return "Thank you for your inquiry! Here are some recommendations:\n\n"
                    + "🍝 Pasta Specials: Truffle Carbonara, Seafood Linguine\n"
                    + "🥗 Salads: Caesar, Mediterranean, Garden Fresh\n"
                    + "🍰 Desserts: Tiramisu, Chocolate Lava Cake, Panna Cotta\n\n"
                    + "All dishes are prepared fresh daily with premium ingredients!";
        }
    }
}