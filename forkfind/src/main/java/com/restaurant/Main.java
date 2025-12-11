package com.restaurant;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.Cluster;
import com.restaurant.actors.*;
import com.restaurant.http.RestaurantHttpServer;
import com.restaurant.messages.Messages.RestaurantMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.concurrent.CountDownLatch;

/**
 * SINGLE FILE SOLUTION - Complete Restaurant Management System
 * Starts both Node1 (Frontend) and Node2 (Backend) in separate threads
 *
 * Run with: mvn exec:java -Dexec.mainClass="com.restaurant.Main"
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("🚀 Starting Restaurant Management System");
        System.out.println("========================================");

        // Latch to keep main thread alive
        CountDownLatch latch = new CountDownLatch(1);

        // Start Node2 (Backend) in a separate thread
        Thread node2Thread = new Thread(() -> {
            try {
                System.out.println("📦 Starting Node2 (Backend)...");
                startNode2();
            } catch (Exception e) {
                System.err.println("❌ Error starting Node2: " + e.getMessage());
                e.printStackTrace();
            }
        }, "Node2-Thread");
        node2Thread.start();

        // Wait for Node2 to initialize
        try {
            System.out.println("⏳ Waiting for Node2 to initialize...");
            Thread.sleep(5000); // 5 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Start Node1 (Frontend) in a separate thread
        Thread node1Thread = new Thread(() -> {
            try {
                System.out.println("📦 Starting Node1 (Frontend)...");
                startNode1();
            } catch (Exception e) {
                System.err.println("❌ Error starting Node1: " + e.getMessage());
                e.printStackTrace();
            }
        }, "Node1-Thread");
        node1Thread.start();

        // Wait a bit for HTTP server to start
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n========================================");
        System.out.println("✅ System Ready!");
        System.out.println("========================================");
        System.out.println("🌐 Access the UI at: http://localhost:8080");
        System.out.println("📝 Press Ctrl+C to stop the system");
        System.out.println("========================================\n");

        // Keep main thread alive
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts Node2 (Backend) with LLMActor, MenuActor, DietarySpecialistActor,
     * ReservationActor
     */
    private static void startNode2() {
        Config config = ConfigFactory.parseString("akka.cluster.roles = [backend]")
                .withFallback(ConfigFactory.load("node2"));

        ActorSystem<Void> system = ActorSystem.create(
                Behaviors.setup(context -> {
                    Cluster cluster = Cluster.get(context.getSystem());
                    context.getLog().info("========================================");
                    context.getLog().info("🚀 Node2 (Backend) started");
                    context.getLog().info("Roles: {}", cluster.selfMember().getRoles());
                    context.getLog().info("Address: {}", cluster.selfMember().address());
                    context.getLog().info("========================================");

                    // Create LLMActor on Node2 (responds to ASK messages)
                    ActorRef<RestaurantMessage> llmActor = context.spawn(LLMActor.create(), "llm-actor");
                    context.getLog().info("✅ LLMActor created on Node2");

                    // Create placeholder for LoggingActor (exists on Node1)
                    ActorRef<RestaurantMessage> loggingActorPlaceholder = context.spawn(Behaviors.empty(),
                            "logging-actor-placeholder");

                    // Create DietarySpecialistActor on Node2 (receives FORWARD messages)
                    ActorRef<RestaurantMessage> dietarySpecialistActor = context.spawn(
                            DietarySpecialistActor.create(loggingActorPlaceholder),
                            "dietary-specialist-actor");
                    context.getLog().info("✅ DietarySpecialistActor created on Node2");

                    // Create MenuActor on Node2 (uses FORWARD pattern)
                    ActorRef<RestaurantMessage> menuActor = context.spawn(
                            MenuActor.create(loggingActorPlaceholder, dietarySpecialistActor),
                            "menu-actor");
                    context.getLog().info("✅ MenuActor created on Node2");

                    // Create ReservationActor on Node2
                    ActorRef<RestaurantMessage> reservationActor = context.spawn(
                            ReservationActor.create(loggingActorPlaceholder),
                            "reservation-actor");
                    context.getLog().info("✅ ReservationActor created on Node2");

                    // Create RetrievalActor (RAG) on Node2
                    ActorRef<RestaurantMessage> retrievalActor = context.spawn(
                            RetrievalActor.create(loggingActorPlaceholder),
                            "retrieval-actor");
                    context.getLog().info("✅ RetrievalActor created on Node2");

                    context.getLog().info("========================================");
                    context.getLog().info("Node2 Actors Summary:");
                    context.getLog().info("  - LLMActor (processes ASK)");
                    context.getLog().info("  - MenuActor (uses FORWARD)");
                    context.getLog().info("  - RetrievalActor (RAG Knowledge Base)");
                    context.getLog().info("  - DietarySpecialistActor (receives FORWARD)");
                    context.getLog().info("  - ReservationActor (handles reservations)");
                    context.getLog().info("========================================");

                    return Behaviors.empty();
                }),
                "RestaurantSystem",
                config);
    }

    /**
     * Starts Node1 (Frontend) with RoutingActor, OrderActor, LoggingActor, and HTTP
     * Server
     */
    private static void startNode1() {
        Config config = ConfigFactory.parseString("akka.cluster.roles = [frontend]")
                .withFallback(ConfigFactory.load("node1"));

        ActorSystem<Void> system = ActorSystem.create(
                Behaviors.setup(context -> {
                    Cluster cluster = Cluster.get(context.getSystem());
                    context.getLog().info("========================================");
                    context.getLog().info("🚀 Node1 (Frontend) started");
                    context.getLog().info("Roles: {}", cluster.selfMember().getRoles());
                    context.getLog().info("Address: {}", cluster.selfMember().address());
                    context.getLog().info("========================================");

                    // Create LoggingActor on Node 1
                    ActorRef<RestaurantMessage> loggingActor = context.spawn(LoggingActor.create(), "logging-actor");
                    context.getLog().info("✅ LoggingActor created on Node1");

                    // Create LOCAL instances of backend actors (Monolith mode for reliability)
                    ActorRef<RestaurantMessage> llmActor = context.spawn(LLMActor.create(), "llm-actor");

                    ActorRef<RestaurantMessage> dietarySpecialistActor = context
                            .spawn(DietarySpecialistActor.create(loggingActor), "dietary-specialist-actor");

                    ActorRef<RestaurantMessage> menuActor = context
                            .spawn(MenuActor.create(loggingActor, dietarySpecialistActor), "menu-actor");

                    ActorRef<RestaurantMessage> reservationActor = context
                            .spawn(ReservationActor.create(loggingActor), "reservation-actor");

                    // Create RetrievalActor on Node1 (Local instance for simplicity in this demo)
                    ActorRef<RestaurantMessage> retrievalActor = context.spawn(
                            RetrievalActor.create(loggingActor),
                            "retrieval-actor");
                    context.getLog().info("✅ RetrievalActor created on Node1");

                    // Create GeneralChatActor on Node1
                    ActorRef<RestaurantMessage> generalChatActor = context
                            .spawn(GeneralChatActor.create(llmActor, retrievalActor, loggingActor),
                                    "general-chat-actor");
                    context.getLog().info("✅ GeneralChatActor created on Node1");

                    // Create OrderActor on Node1
                    ActorRef<RestaurantMessage> orderActor = context.spawn(OrderActor.create(loggingActor, menuActor),
                            "order-actor");
                    context.getLog().info("✅ OrderActor created on Node1");

                    // Create RoutingActor on Node1 with REAL actors
                    ActorRef<RestaurantMessage> routingActor = context.spawn(
                            RoutingActor.create(menuActor, orderActor, reservationActor, generalChatActor,
                                    loggingActor),
                            "routing-actor");
                    context.getLog().info("✅ RoutingActor created on Node1");

                    // Start HTTP server
                    RestaurantHttpServer httpServer = new RestaurantHttpServer(routingActor, loggingActor,
                            context.getSystem());
                    httpServer.start("localhost", 8080);

                    context.getLog().info("========================================");
                    context.getLog().info("Node1 Actors Summary:");
                    context.getLog().info("  - RoutingActor (routes queries)");
                    context.getLog().info("  - OrderActor (handles orders)");
                    context.getLog().info("  - LoggingActor (receives TELL)");
                    context.getLog().info("========================================");

                    return Behaviors.empty();
                }),
                "RestaurantSystem",
                config);
    }
}