package org.example.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.common.GameBoard;
import org.example.common.Move;
import org.example.common.Piece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonProtocolTest {
    @Test
    void parsesPublicMoveMessageIntoInternalMove() {
        JsonObject json = new JsonObject();
        json.addProperty("messageType", "move");
        json.addProperty("fromX", "a");
        json.addProperty("fromY", 0);
        json.addProperty("toX", "a");
        json.addProperty("toY", 1);
        json.addProperty("isFlip", false);

        Move move = JsonProtocol.toMove(json);

        assertEquals("a0", move.getSource());
        assertEquals("a1", move.getDestination());
    }

    @Test
    void createsMoveResultWithFlipResultForReveal() {
        Move move = new Move("b7", "b7", 3, 123L);

        JsonObject result = JsonProtocol.moveResult(move, true, true);

        assertEquals("moveResult", result.get("messageType").getAsString());
        assertTrue(result.get("success").getAsBoolean());
        assertTrue(result.get("valid").getAsBoolean());
        assertEquals("cannon", result.get("flipResult").getAsString());
        JsonObject publicMove = result.getAsJsonObject("move");
        assertEquals("b", publicMove.get("fromX").getAsString());
        assertEquals(7, publicMove.get("fromY").getAsInt());
        assertTrue(publicMove.get("isFlip").getAsBoolean());
    }

    @Test
    void createsInitialBoardUsingPublicPieceNames() {
        GameBoard board = new GameBoard(42L);

        JsonArray snapshot = JsonProtocol.initialBoard(board);

        assertEquals(32, snapshot.size());
        JsonObject redGeneral = null;
        for (int i = 0; i < snapshot.size(); i++) {
            JsonObject cell = snapshot.get(i).getAsJsonObject();
            if ("e".equals(cell.get("x").getAsString()) && cell.get("y").getAsInt() == 9) {
                redGeneral = cell;
                break;
            }
        }
        assertNotNull(redGeneral);
        assertEquals("king", redGeneral.get("piece").getAsString());
        assertTrue(redGeneral.get("visible").getAsBoolean());
    }

    @Test
    void mapsPublicColorToSide() {
        assertEquals(Piece.Side.RED, JsonProtocol.sideFromColor("red"));
        assertEquals(Piece.Side.BLACK, JsonProtocol.sideFromColor("black"));
    }
}
