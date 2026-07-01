package com.lld.observerpattern;

public class Follower implements Observer {
    private final String name;
    private final NotificationPublisher publisher;

    public Follower(String name, NotificationPublisher publisher) {
        this.name = name;
        this.publisher = publisher;
    }

    @Override
    public void update() {
        System.out.println(name + " received: " + publisher.getLatestNotification());
    }
}
