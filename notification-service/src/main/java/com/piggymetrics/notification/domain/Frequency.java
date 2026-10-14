package com.piggymetrics.notification.domain;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.stream.Stream;

public enum Frequency {

	WEEKLY(7), MONTHLY(30), QUARTERLY(90);

	private int days;

	Frequency(int days) {
		this.days = days;
	}

	public int getDays() {
		return days;
	}

	/**
	 * Subtracts calendar days (not fixed 24h periods) in {@code now}'s zone, matching the
	 * JavaScript {@code Date.setDate} arithmetic of the former {@code $where} queries across DST changes.
	 */
	public Date cutoff(ZonedDateTime now) {
		return Date.from(now.minusDays(days).toInstant());
	}

	public static Frequency withDays(int days) {
		return Stream.of(Frequency.values())
				.filter(f -> f.getDays() == days)
				.findFirst()
				.orElseThrow(IllegalArgumentException::new);
	}
}
