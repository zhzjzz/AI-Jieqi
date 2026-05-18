package org.example.common;

import java.io.Serializable;

public class Piece implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Side {
        RED, BLACK
    }

    private final Side side;
    private int type;
    private boolean revealed;

    public Piece(Side side, int type, boolean revealed) {
        this.side = side;
        this.type = type;
        this.revealed = revealed;
    }

    public Side getSide() {
        return side;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
    }

    public String shortName() {
        if (!revealed) {
            return "暗";
        }
        return switch (type) {
            case 0 -> "将";
            case 1 -> "车";
            case 2 -> "马";
            case 3 -> "炮";
            case 4 -> "兵";
            case 5 -> "士";
            case 6 -> "象";
            default -> "?";
        };
    }
}
