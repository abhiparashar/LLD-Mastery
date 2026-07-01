package com.lld.observerpattern;

import java.util.HashSet;
import java.util.Set;

public class NotificationPublisher implements Subject {
    private final Set<Observer> observers = new HashSet<>();
    private String latestNotification;

    @Override
    public void subscribe(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void unsubscribe(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers() {
        for (Observer observer : observers) {
            observer.update();
        }
    }

    public void publish(String notification) {
        this.latestNotification = notification;
        notifyObservers();
    }

    public String getLatestNotification() {
        return latestNotification;
    }
}
