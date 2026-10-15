package com.piggymetrics.notification.repository;

import com.piggymetrics.notification.domain.Frequency;
import com.piggymetrics.notification.domain.NotificationSettings;
import com.piggymetrics.notification.domain.NotificationType;
import com.piggymetrics.notification.domain.Recipient;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.Map;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
class RecipientRepositoryTest {

	@Autowired
	private RecipientRepository repository;

	@BeforeEach
	void setup() {
		repository.deleteAll();
	}

	@Test
	void shouldFindByAccountName() {

		NotificationSettings remind = new NotificationSettings();
		remind.setActive(true);
		remind.setFrequency(Frequency.WEEKLY);
		remind.setLastNotified(new Date(0));

		NotificationSettings backup = new NotificationSettings();
		backup.setActive(false);
		backup.setFrequency(Frequency.MONTHLY);
		backup.setLastNotified(new Date());

		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(
				NotificationType.BACKUP, backup,
				NotificationType.REMIND, remind
		));

		repository.save(recipient);

		Recipient found = repository.findByAccountName(recipient.getAccountName());
		assertEquals(recipient.getAccountName(), found.getAccountName());
		assertEquals(recipient.getEmail(), found.getEmail());

		assertEquals(recipient.getScheduledNotifications().get(NotificationType.BACKUP).getActive(),
				found.getScheduledNotifications().get(NotificationType.BACKUP).getActive());
		assertEquals(recipient.getScheduledNotifications().get(NotificationType.BACKUP).getFrequency(),
				found.getScheduledNotifications().get(NotificationType.BACKUP).getFrequency());
		assertEquals(recipient.getScheduledNotifications().get(NotificationType.BACKUP).getLastNotified(),
				found.getScheduledNotifications().get(NotificationType.BACKUP).getLastNotified());

		assertEquals(recipient.getScheduledNotifications().get(NotificationType.REMIND).getActive(),
				found.getScheduledNotifications().get(NotificationType.REMIND).getActive());
		assertEquals(recipient.getScheduledNotifications().get(NotificationType.REMIND).getFrequency(),
				found.getScheduledNotifications().get(NotificationType.REMIND).getFrequency());
		assertEquals(recipient.getScheduledNotifications().get(NotificationType.REMIND).getLastNotified(),
				found.getScheduledNotifications().get(NotificationType.REMIND).getLastNotified());
	}

	@Test
	void shouldFindReadyForRemindWhenFrequencyIsWeeklyAndLastNotifiedWas8DaysAgo() {

		NotificationSettings remind = new NotificationSettings();
		remind.setActive(true);
		remind.setFrequency(Frequency.WEEKLY);
		remind.setLastNotified(DateUtils.addDays(new Date(), -8));

		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(
				NotificationType.REMIND, remind
		));

		repository.save(recipient);

		List<Recipient> found = repository.findReadyForRemind();
		assertFalse(found.isEmpty());
	}

	@Test
	void shouldNotFindReadyForRemindWhenFrequencyIsWeeklyAndLastNotifiedWasYesterday() {

		NotificationSettings remind = new NotificationSettings();
		remind.setActive(true);
		remind.setFrequency(Frequency.WEEKLY);
		remind.setLastNotified(DateUtils.addDays(new Date(), -1));

		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(
				NotificationType.REMIND, remind
		));

		repository.save(recipient);

		List<Recipient> found = repository.findReadyForRemind();
		assertTrue(found.isEmpty());
	}

	@Test
	void shouldNotFindReadyForRemindWhenNotificationIsNotActive() {

		NotificationSettings remind = new NotificationSettings();
		remind.setActive(false);
		remind.setFrequency(Frequency.WEEKLY);
		remind.setLastNotified(DateUtils.addDays(new Date(), -30));

		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(
				NotificationType.REMIND, remind
		));

		repository.save(recipient);

		List<Recipient> found = repository.findReadyForRemind();
		assertTrue(found.isEmpty());
	}

	@Test
	void shouldNotFindReadyForBackupWhenFrequencyIsQuaterly() {

		NotificationSettings remind = new NotificationSettings();
		remind.setActive(true);
		remind.setFrequency(Frequency.QUARTERLY);
		remind.setLastNotified(DateUtils.addDays(new Date(), -91));

		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(
				NotificationType.BACKUP, remind
		));

		repository.save(recipient);

		List<Recipient> found = repository.findReadyForBackup();
		assertFalse(found.isEmpty());
	}

	@Test
	void shouldNotFindReadyForBackupWhenFrequencyIsQuarterlyAndLastNotifiedWas40DaysAgo() {

		saveRecipient(NotificationType.BACKUP, settings(true, Frequency.QUARTERLY, DateUtils.addDays(new Date(), -40)));

		assertTrue(repository.findReadyForBackup().isEmpty());
	}

	@Test
	void shouldFindReadyForRemindWhenFrequencyIsMonthlyAndLastNotifiedWas31DaysAgo() {

		saveRecipient(NotificationType.REMIND, settings(true, Frequency.MONTHLY, DateUtils.addDays(new Date(), -31)));

		assertEquals(1, repository.findReadyForRemind().size());
		assertTrue(repository.findReadyForBackup().isEmpty());
	}

	@Test
	void shouldNotFindReadyForRemindWhenLastNotifiedIsMissing() {

		saveRecipient(NotificationType.REMIND, settings(true, Frequency.WEEKLY, null));

		assertTrue(repository.findReadyForRemind().isEmpty());
	}

	@Test
	void shouldSelectOnlyRecipientsNotifiedStrictlyBeforeCutoff() {

		Date cutoff = DateUtils.addDays(new Date(), -7);
		Date never = new Date(0);

		saveRecipient(NotificationType.REMIND, settings(true, Frequency.WEEKLY, cutoff));
		assertTrue(repository.findReadyForRemind(cutoff, never, never).isEmpty());

		saveRecipient(NotificationType.REMIND, settings(true, Frequency.WEEKLY, new Date(cutoff.getTime() - 1)));
		assertEquals(1, repository.findReadyForRemind(cutoff, never, never).size());
	}

	@Test
	void shouldUseCalendarDaysWhenWeeklyCutoffCrossesSpringForwardTransition() {

		ZoneId zone = ZoneId.of("America/New_York");
		ZonedDateTime now = ZonedDateTime.of(2026, 3, 9, 12, 0, 0, 0, zone);
		Date lastNotified = Date.from(ZonedDateTime.of(2026, 3, 2, 11, 30, 0, 0, zone).toInstant());

		saveRecipient(NotificationType.REMIND, settings(true, Frequency.WEEKLY, lastNotified));

		assertEquals(1, repository.findReadyForRemind(now).size());
	}

	@Test
	void shouldUseCalendarDaysWhenWeeklyCutoffCrossesFallBackTransition() {

		ZoneId zone = ZoneId.of("America/New_York");
		ZonedDateTime now = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, zone);
		Date lastNotified = Date.from(ZonedDateTime.of(2026, 10, 26, 12, 30, 0, 0, zone).toInstant());

		saveRecipient(NotificationType.BACKUP, settings(true, Frequency.WEEKLY, lastNotified));

		assertTrue(repository.findReadyForBackup(now).isEmpty());
	}

	private NotificationSettings settings(boolean active, Frequency frequency, Date lastNotified) {
		NotificationSettings settings = new NotificationSettings();
		settings.setActive(active);
		settings.setFrequency(frequency);
		settings.setLastNotified(lastNotified);
		return settings;
	}

	private void saveRecipient(NotificationType type, NotificationSettings settings) {
		Recipient recipient = new Recipient();
		recipient.setAccountName("test");
		recipient.setEmail("test@test.com");
		recipient.setScheduledNotifications(Map.of(type, settings));
		repository.save(recipient);
	}
}
