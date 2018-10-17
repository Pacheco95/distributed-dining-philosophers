/*
 * Copyright (c) 2018. Michael Pacheco - All Rights Reserved
 * mdpgd95@gmail.com
 */

import java.io.Serializable;

public class Message implements Serializable {
    private Object message;
    private Kind kind;

    public enum Kind {FORK_ACQUIRED, FORK_IN_USE, RELEASE_FORK, REQUEST_FORK, SETUP, START, STOP}

    Message(Kind kind) {
        this("", kind);
    }

    Message(Object message, Kind kind) {
        this.message = message;
        this.kind = kind;
    }

    Object getMessage() {
        return message;
    }

    Kind getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return String.format("<%s> \"%s\"", kind, message);
    }
}
