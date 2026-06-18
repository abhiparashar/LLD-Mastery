package com.lld.movieticket.model;

import java.util.UUID;

public class Movie {
  private final String movieId;
  private final String movieName;
  private final String genre;
  private final String language;
  private final int durationMinutes;

  public Movie(String movieName, String genre, String language, int durationMinutes) {
    this.movieId = UUID.randomUUID().toString();
    this.movieName = movieName;
    this.language = language;
    this.genre = genre;
    this.durationMinutes = durationMinutes;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public String getGenre() {
    return genre;
  }

  public String getLanguage() {
    return language;
  }

  public String getMovieId() {
    return movieId;
  }

  public String getMovieName() {
    return movieName;
  }
}
