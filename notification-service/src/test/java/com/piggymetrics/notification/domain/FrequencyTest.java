package com.piggymetrics.notification.domain;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class FrequencyTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	@Test
	public void shouldSubtractCalendarDaysAcrossSpringForwardTransition() {
		ZonedDateTime now = ZonedDateTime.of(2026, 3, 9, 12, 0, 0, 0, NEW_YORK);
		Date expected = Date.from(ZonedDateTime.of(2026, 3, 2, 12, 0, 0, 0, NEW_YORK).toInstant());

		assertEquals(expected, Frequency.WEEKLY.cutoff(now));
	}

	@Test
	public void shouldSubtractCalendarDaysAcrossFallBackTransition() {
		ZonedDateTime now = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, NEW_YORK);
		Date expected = Date.from(ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, NEW_YORK).toInstant());

		assertEquals(expected, Frequency.WEEKLY.cutoff(now));
	}

	@Test
	public void shouldSubtractFrequencyDays() {
		ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, NEW_YORK);

		assertEquals(Date.from(now.minusDays(30).toInstant()), Frequency.MONTHLY.cutoff(now));
		assertEquals(Date.from(now.minusDays(90).toInstant()), Frequency.QUARTERLY.cutoff(now));
	}
}
