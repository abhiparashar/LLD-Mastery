package com.lld.observerpattern;

public class Main {
    public static void main(String[] args) {
        NotificationPublisher publisher = new NotificationPublisher();

        Follower raj = new Follower("Raj", publisher);
        Follower priya = new Follower("Priya", publisher);

        publisher.subscribe(raj);
        publisher.subscribe(priya);

        publisher.publish("New video uploaded!");

        publisher.unsubscribe(priya);

        publisher.publish("Going live now!");
    }
}
