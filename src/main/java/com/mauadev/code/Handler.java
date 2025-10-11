package com.mauadev.code;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.mauadev.code.entities.Board;
import com.mauadev.code.entities.Coordinate;
import com.mauadev.code.entities.GameState;
import com.mauadev.code.entities.Snake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Gson é uma biblioteca para converter objetos Java para JSON e vice-versa.
    private static final Gson gson = new Gson();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        String path = request.getPath();
        Object responseBody = null;

        try {
            // Roteador para os diferentes endpoints da API BattleSnake
            switch (path) {
                case "/":
                    // Informações da sua cobra
                    responseBody = handleInfo();
                    break;
                case "/start":
                    // Lógica para o início do jogo
                    handleStart(request, context);
                    break;
                case "/move":
                    // Lógica para decidir o próximo movimento
                    responseBody = handleMove(request, context);
                    break;
                case "/end":
                    // Lógica para o fim do jogo
                    handleEnd(request, context);
                    break;
                default:
                    // Se a rota não for encontrada, retorna um erro 404
                    // Precisamos passar \ antes das aspas para não dar erro quando convertemos pra json
                    return response.withStatusCode(404).withBody("{\"error\": \"Path not found\"}");
            }

            // Configura a resposta de sucesso
            response.setStatusCode(200);
            response.setHeaders(Collections.singletonMap("Content-Type", "application/json"));
            if (responseBody != null) {
                // Converte o objeto de resposta para uma string JSON
                response.setBody(gson.toJson(responseBody));
            }

        } catch (Exception e) {
            // Em caso de erro em qualquer parte da lógica
            context.getLogger().log("ERROR: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody(String.format("{\"error\": \"%s\"}", e.getMessage()));
        }

        return response;
    }

    /**
     * Responde ao endpoint / com as informações da sua cobra. 🐍
     */
    private Map<String, String> handleInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("apiversion", "1.333");
        info.put("author", "leo-Lorio");
        info.put("color", "#fd4949ff"); // Ex: Cinza
        info.put("head", "silly");
        info.put("tail", "coffee");
        return info;
    }

    /**
     * Chamado no início de cada jogo. Não precisa retornar nada.
     */
    private void handleStart(APIGatewayProxyRequestEvent request, Context context) {
        // Você pode usar o corpo da requisição (request.getBody()) para obter o estado inicial do jogo.
        context.getLogger().log("Game Started!");
    }

    private Map<String, String> handleMove(APIGatewayProxyRequestEvent request, Context context) {
        GameState gameState = gson.fromJson(request.getBody(), GameState.class);
        Snake you = gameState.getYou();
        Coordinate head = you.getHead();
        Board board = gameState.getBoard();

        Map<String, Coordinate> possibleMoves = new HashMap<>();
        possibleMoves.put("up", new Coordinate(String.valueOf(head.getX()), String.valueOf(head.getY() + 1)));
        possibleMoves.put("down", new Coordinate(String.valueOf(head.getX()), String.valueOf(head.getY() - 1)));
        possibleMoves.put("left", new Coordinate(String.valueOf(head.getX() - 1), String.valueOf(head.getY())));
        possibleMoves.put("right", new Coordinate(String.valueOf(head.getX() + 1), String.valueOf(head.getY())));

        List<String> safeMoves = new ArrayList<>();
        for (Map.Entry<String, Coordinate> entry : possibleMoves.entrySet()) {
            if (isMoveSafe(entry.getValue(), you, board)) {
                safeMoves.add(entry.getKey());
            }
        }

        String chosenMove;
        if (safeMoves.isEmpty()) {
            chosenMove = "down";
            context.getLogger().log("WARN: No safe moves detected! Moving down by default.");
        } else {
            Coordinate target = findBestTarget(gameState);
            if (target != null) {
                chosenMove = moveTowardsTarget(target, safeMoves, head);
            } else {
                chosenMove = safeMoves.get(0);
            }
        }

        context.getLogger().log("MOVE: " + chosenMove);
        Map<String, String> move = new HashMap<>();
        move.put("move", chosenMove);
        move.put("shout", "Indo para " + chosenMove + "!");
        return move;
    }

    private Coordinate findBestTarget(GameState gameState) {
        Snake you = gameState.getYou();
        Board board = gameState.getBoard();
        List<Coordinate> foodList = board.getFood();
        Coordinate head = you.getHead();

        if (!foodList.isEmpty()) {
            Coordinate closestFood = null;
            int minDistance = Integer.MAX_VALUE;
            for (Coordinate food : foodList) {
                int distance = getDistance(head, food);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestFood = food;
                }
            }
            return closestFood;
        }

        Snake closestEnemy = null;
        int minDistance = Integer.MAX_VALUE;
        for (Snake enemy : board.getSnakes()) {
            if (enemy.getId().equals(you.getId())) {
                continue;
            }
            int distance = getDistance(head, enemy.getHead());
            if (distance < minDistance) {
                minDistance = distance;
                closestEnemy = enemy;
            }
        }

        return closestEnemy != null ? closestEnemy.getHead() : null;
    }

    private String moveTowardsTarget(Coordinate target, List<String> safeMoves, Coordinate head) {
        String bestMove = safeMoves.get(0);
        int minDistance = Integer.MAX_VALUE;

        for (String move : safeMoves) {
            Coordinate nextCoord = new Coordinate();
            switch (move) {
                case "up":
                    nextCoord.setX(head.getX());
                    nextCoord.setY(head.getY() + 1);
                    break;
                case "down":
                    nextCoord.setX(head.getX());
                    nextCoord.setY(head.getY() - 1);
                    break;
                case "left":
                    nextCoord.setX(head.getX() - 1);
                    nextCoord.setY(head.getY());
                    break;
                case "right":
                    nextCoord.setX(head.getX() + 1);
                    nextCoord.setY(head.getY());
                    break;
            }

            int distance = getDistance(nextCoord, target);
            if (distance < minDistance) {
                minDistance = distance;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int getDistance(Coordinate c1, Coordinate c2) {
        return Math.abs(c1.getX() - c2.getX()) + Math.abs(c1.getY() - c2.getY());
    }

    private boolean isMoveSafe(Coordinate targetCoord, Snake you, Board board) {
        int x = targetCoord.getX();
        int y = targetCoord.getY();

        if (x < 0 || x >= board.getWidth() || y < 0 || y >= board.getHeight()) {
            return false;
        }

        for (Snake snake : board.getSnakes()) {
            List<Coordinate> body = snake.getBody();
            // A última parte do corpo (cauda) vai se mover, então podemos ignorá-la na verificação,
            // a menos que a cobra tenha acabado de comer e seu comprimento tenha aumentado.
            int segmentsToCheck = snake.getHealth() == 100 ? body.size() : body.size() - 1;

            for (int i = 0; i < segmentsToCheck; i++) {
                Coordinate bodyPart = body.get(i);
                if (x == bodyPart.getX() && y == bodyPart.getY()) {
                    return false;
                }
            }
        }
        
        return true;
    }


    /**
     * Chamado no final de cada jogo. Não precisa retornar nada.
     */
    private void handleEnd(APIGatewayProxyRequestEvent request, Context context) {
        // Você pode analisar a requisição para saber se venceu ou perdeu.
        context.getLogger().log("Game Ended!");
    }
}