package org.example.common;

import java.io.Serializable;

public class Move implements Serializable {
    private static final long serialVersionUID = 1L;

    private String source;
    private String destination;
    private Integer type;
    private long turnStartTime;
    private Boolean flip;

    public Move() {
    }

    public Move(String source, String destination, Integer type, long turnStartTime) {
        this.source = source;
        this.destination = destination;
        this.type = type;
        this.turnStartTime = turnStartTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public long getTurnStartTime() {
        return turnStartTime;
    }

    public void setTurnStartTime(long turnStartTime) {
        this.turnStartTime = turnStartTime;
    }

    public boolean isFlip() {
        if (flip != null) {
            return flip;
        }
        return source != null && source.equals(destination);
    }

    public Boolean getFlip() {
        return flip;
    }

    public void setFlip(Boolean flip) {
        this.flip = flip;
    }
}
