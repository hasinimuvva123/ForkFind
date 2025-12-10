package com.restaurant.http;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.messages.Messages.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class RestaurantHttpServer extends AllDirectives {

    private final ActorRef<RestaurantMessage> routingActor;
    private final ActorRef<RestaurantMessage> loggingActor; // Add logging actor
    private final ActorSystem<?> system;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestaurantHttpServer(ActorRef<RestaurantMessage> routingActor,
            ActorRef<RestaurantMessage> loggingActor,
            ActorSystem<?> system) {
        this.routingActor = routingActor;
        this.loggingActor = loggingActor;
        this.system = system;
    }

    public Route createRoute() {
        return concat(
                // API endpoint for queries
                pathPrefix("api", () -> concat(
                        path("query", () -> post(() -> entity(Jackson.unmarshaller(QueryRequestDTO.class), dto -> {
                            // Use ASK pattern to get response from actor system
                            CompletionStage<QueryResponse> futureResponse = AskPattern.ask(
                                    routingActor,
                                    replyTo -> new QueryRequest(dto.query, dto.queryType, replyTo),
                                    Duration.ofSeconds(30),
                                    system.scheduler());

                            return onSuccess(futureResponse, response -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("response", response.response);
                                result.put("success", response.success);

                                try {
                                    String json = objectMapper.writeValueAsString(result);
                                    return complete(StatusCodes.OK,
                                            HttpEntities.create(ContentTypes.APPLICATION_JSON, json));
                                } catch (Exception e) {
                                    return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                            "Error serializing response");
                                }
                            });
                        }))),
                        path("logs", () -> get(() -> {
                            // Ask LoggingActor for logs
                            CompletionStage<GetLogsResponse> futureLogs = AskPattern.ask(
                                    loggingActor,
                                    GetLogsRequest::new,
                                    Duration.ofSeconds(5),
                                    system.scheduler());

                            return onSuccess(futureLogs,
                                    logsResp -> complete(StatusCodes.OK, logsResp.logs, Jackson.marshaller()));
                        })))),
                // Serve index.html at root
                path("", () -> get(() -> getFromResource("static/index.html"))),
                // Serve static files
                pathPrefix("static", () -> getFromResourceDirectory("static")));
    }

    // DTO for incoming requests
    public static class QueryRequestDTO {
        public String query;
        public String queryType;

        public QueryRequestDTO() {
        }

        public QueryRequestDTO(String query, String queryType) {
            this.query = query;
            this.queryType = queryType;
        }
    }

    public void start(String host, int port) {
        Http.get(system).newServerAt(host, port).bind(createRoute());
        system.log().info("🌐 HTTP Server started at http://{}:{}/", host, port);
        system.log().info("📱 Access the UI at http://{}:{}/", host, port);
    }
}