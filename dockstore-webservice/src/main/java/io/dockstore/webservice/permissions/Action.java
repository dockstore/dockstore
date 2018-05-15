package io.dockstore.webservice.permissions;

public enum Action {
    WRITE("write"),
    READ("read"),
    DELETE("delete"),
    SHARE("share");

    private final String name;

    Action(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
