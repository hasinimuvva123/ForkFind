# 🍽️ ForkFind - Intelligent Restaurant Actor System

ForkFind is a robust, reactive restaurant management system built using **Java** and the **Akka Actor Model**. It demonstrates advanced distributed system concepts, including message-driven architectures, self-healing actors, and asynchronous communication patterns.

The system features an intelligent chat interface where users can browse menus, place orders, make reservations, and chat with an AI assistant.

---

## 🏛️ Architecture & Actor System

This project is a showcase of the **Akka Actor Model**, utilizing three distinct communication patterns to handle different types of business logic.

### 1. The "Trifecta" of Communication Patterns
ForkFind explicitly implements the three core Akka messaging patterns, plus **Retrieval-Augmented Generation (RAG)**:

*   **🔥 TELL (Fire-and-Forget)**
    *   **Usage**: Logging, Status Updates, Final User Responses.
    *   **Logic**: Actors send a message and immediately proceed without waiting for an acknowledgement.
    *   **Example**: `OrderActor` tells `LoggingActor` to record an event.

*   **❓ ASK (Request-Response)**
    *   **Usage**: Inter-actor validation and AI generation.
    *   **Logic**: An actor sends a message and **waits** (non-blocking) for a specific reply before proceeding.
    *   **Example**: `OrderActor` **ASKS** `MenuActor` *"Is this 'Burger' valid?"* before confirming an order.

*   **⏩ FORWARD (Delegation)**
    *   **Usage**: Specialized handling (Dietary restrictions).
    *   **Logic**: An actor passes a message to another actor, preserving the original sender's reference. The final actor replies directly to the user.
    *   **Example**: `MenuActor` detects "vegan" keywords and **FORWARDS** the request to `DietarySpecialistActor`.

### 2. Actor Hierarchy
*   **`RoutingActor`**: The central traffic controller. Inspects the query intent (Menu, Order, Reservation, Chat) and routes it to the appropriate specialized actor.
*   **`MenuActor`**: Handles menu queries. Uses **FORWARD** to delegate allergy questions.
*   **`OrderActor`**: process orders. Uses **ASK** to validate items with `MenuActor`.
*   **`ReservationActor`**: Manages table bookings. Supports dynamic parsing (Date/Time/Party) and stateful cancellations.
*   **`GeneralChatActor`**: Handles casual conversation. Uses **ASK** to query `RetrievalActor` for knowledge, then `LLMActor` for generation (RAG Pattern).
*   **`RetrievalActor`**: Performs keyword-based search on the knowledge base (`menu_knowledge.txt`).
*   **`LLMActor`**: Integration point for Large Language Models (LLM).
*   **`DietarySpecialistActor`**: Specialized expert for allergy and dietary info (Soy, Gluten, Nuts, etc.).

---

## 🚀 Getting Started

### Prerequisites
*   **Java 17+**
*   **Maven**

### Installation
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/ForkFind.git
    cd ForkFind
    ```

2.  **Configuration**:
    *   Ensure you have a `.env` file in `forkfind/` if you plan to use real LLM features (optional).

3.  **Build the Project**:
    ```bash
    cd forkfind
    mvn clean install
    ```

### ▶️ Running the Application

Run the Main class, which starts both the backend (Node2) and frontend (Node1) systems:

```bash
mvn exec:java -Dexec.mainClass="com.restaurant.Main"
```

Once started, open your browser and go to:
👉 **http://localhost:8080**

---

## 🎮 How to Use (Chat Examples)

Try typing these commands in the web interface to see different actor patterns in action:

| Pattern | User Query | Internal Flow |
| :--- | :--- | :--- |
| **TELL** | `Check order status` | `OrderActor` checks status → Sends reply immediately. |
| **ASK** | `Order Burger` | `OrderActor` pauses → **ASKS** `MenuActor` ("Is Burger valid?") → Receives "Yes" → Confirms Order. |
| **FORWARD** | `Do you have vegan options?` | `MenuActor` sees "vegan" → **FORWARDS** to `DietarySpecialistActor` → Specialist replies directly to you. |
| **LLM** | `Tell me a joke about food` | `GeneralChatActor` **ASKS** `LLMActor` → Returns AI response. |
| **Simple** | `Book a table` | `ReservationActor` handles this logic directly. |

---

## 📂 Project Structure

```
forkfind/
├── src/main/java/com/restaurant/
│   ├── actors/               # All Akka Actors reside here
│   │   ├── RoutingActor.java # Central router
│   │   ├── MenuActor.java    # Menu logic & validation
│   │   ├── OrderActor.java   # Order processing
│   │   ├── ReservationActor.java # Reservation logic
│   │   ├── RetrievalActor.java # RAG Knowledge Retrieval
│   │   ├── GeneralChatActor.java # LLM Orchestrator
│   │   ├── DietarySpecialistActor.java # Allergy expert
│   │   └── LLMActor.java     # AI Integration
│   ├── messages/
│   │   └── Messages.java     # Immutable message protocols
│   ├── http/
│   │   └── RestaurantHttpServer.java # Web server implementation
│   └── Main.java             # Entry point (boots Node1 & Node2)
├── src/main/resources/
│   ├── menu_knowledge.txt    # RAG Knowledge Base
│   └── static/               # HTML/CSS Frontend
└── pom.xml                   # Maven dependencies
```

---

## 🔧 Technologies

*   **Akka Implementation**: Akka Typed Actors
*   **Language**: Java 17
*   **Build Tool**: Maven3
*   **Frontend**: HTML5, CSS3, JavaScript (Vanilla)

---

**ForkFind** — *Where Reactive Actors Serve You Better.*