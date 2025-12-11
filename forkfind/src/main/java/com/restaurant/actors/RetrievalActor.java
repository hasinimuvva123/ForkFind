package com.restaurant.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.restaurant.messages.Messages.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class RetrievalActor extends AbstractBehavior<RestaurantMessage> {

    private final ActorRef<RestaurantMessage> loggingActor;
    private final String knowledgeBaseContent;

    public static Behavior<RestaurantMessage> create(ActorRef<RestaurantMessage> loggingActor) {
        return Behaviors.setup(context -> new RetrievalActor(context, loggingActor));
    }

    private RetrievalActor(ActorContext<RestaurantMessage> context, ActorRef<RestaurantMessage> loggingActor) {
        super(context);
        this.loggingActor = loggingActor;
        this.knowledgeBaseContent = loadKnowledgeBase();
    }

    private String loadKnowledgeBase() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("menu_knowledge.txt")) {
            if (inputStream == null) {
                System.err.println("❌ RetrievalActor: menu_knowledge.txt not found in resources!");
                return "";
            }
            try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
                String content = scanner.useDelimiter("\\A").next();
                System.out.println(
                        "✅ RetrievalActor: Knowledge base loaded successfully (" + content.length() + " chars)");
                return content;
            }
        } catch (IOException e) {
            System.err.println("❌ RetrievalActor: Error loading knowledge base: " + e.getMessage());
            return "";
        }
    }

    @Override
    public Receive<RestaurantMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RetrievalRequest.class, this::onRetrievalRequest)
                .build();
    }

    private Behavior<RestaurantMessage> onRetrievalRequest(RetrievalRequest request) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║ 🔍 RETRIEVAL ACTOR - Searching Knowledge Base                   ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Keywords: " + request.keywords);
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        loggingActor.tell(new LogMessage("RetrievalActor: Searching for '" + request.keywords + "'", "INFO"));

        String result = performSearch(request.keywords);

        System.out.println("   ↳ Found: "
                + (result.isEmpty() ? "Nothing" : result.substring(0, Math.min(50, result.length())) + "..."));

        request.replyTo.tell(new RetrievalResponse(result, true));
        return this;
    }

    private String performSearch(String query) {
        if (knowledgeBaseContent.isEmpty())
            return "";

        String q = query.toLowerCase();
        // Fallback for generic "Show me the menu" queries
        if (q.contains("menu") || q.contains("list") || q.contains("show") || q.contains("have")
                || q.contains("options")) {
            // Return the whole file or a large summary (limited to avoid token limits if
            // file gets huge)
            // Since our file is small (<3000 chars), we can return it all.
            System.out.println("   ↳ General query detected. Returning FULL knowledge base.");
            return knowledgeBaseContent;
        }

        String[] keywords = q.split("\\s+");
        List<String> matchingBlocks = new ArrayList<>();

        // Simple chunking by double newlines (paragraphs)
        String[] blocks = knowledgeBaseContent.split("\n\n");

        for (String block : blocks) {
            String blockLower = block.toLowerCase();
            boolean match = false;

            // Check if any keyword matches
            for (String keyword : keywords) {
                // Ignore small words
                if (keyword.length() > 3 && blockLower.contains(keyword)) {
                    match = true;
                    break;
                }
            }

            if (match) {
                matchingBlocks.add(block.trim());
            }
        }

        if (matchingBlocks.isEmpty()) {
            return "No specific menu details found for your query.";
        }

        return String.join("\n\n", matchingBlocks);
    }
}
