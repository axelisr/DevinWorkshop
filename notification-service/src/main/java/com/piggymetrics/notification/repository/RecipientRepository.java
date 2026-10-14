package com.piggymetrics.notification.repository;

import com.piggymetrics.notification.domain.Frequency;
import com.piggymetrics.notification.domain.Recipient;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Repository
public interface RecipientRepository extends CrudRepository<Recipient, String> {

	Recipient findByAccountName(String name);

	// frequency is persisted as its day count (see FrequencyWriterConverter), so the literals below must match Frequency.getDays()
	@Query("{ 'scheduledNotifications.BACKUP.active': true, $or: [ " +
			"{ 'scheduledNotifications.BACKUP.frequency': 7, 'scheduledNotifications.BACKUP.lastNotified': { $lt: ?0 } }, " +
			"{ 'scheduledNotifications.BACKUP.frequency': 30, 'scheduledNotifications.BACKUP.lastNotified': { $lt: ?1 } }, " +
			"{ 'scheduledNotifications.BACKUP.frequency': 90, 'scheduledNotifications.BACKUP.lastNotified': { $lt: ?2 } } ] }")
	List<Recipient> findReadyForBackup(Date weeklyCutoff, Date monthlyCutoff, Date quarterlyCutoff);

	@Query("{ 'scheduledNotifications.REMIND.active': true, $or: [ " +
			"{ 'scheduledNotifications.REMIND.frequency': 7, 'scheduledNotifications.REMIND.lastNotified': { $lt: ?0 } }, " +
			"{ 'scheduledNotifications.REMIND.frequency': 30, 'scheduledNotifications.REMIND.lastNotified': { $lt: ?1 } }, " +
			"{ 'scheduledNotifications.REMIND.frequency': 90, 'scheduledNotifications.REMIND.lastNotified': { $lt: ?2 } } ] }")
	List<Recipient> findReadyForRemind(Date weeklyCutoff, Date monthlyCutoff, Date quarterlyCutoff);

	default List<Recipient> findReadyForBackup() {
		return findReadyForBackup(ZonedDateTime.now());
	}

	default List<Recipient> findReadyForBackup(ZonedDateTime now) {
		return findReadyForBackup(Frequency.WEEKLY.cutoff(now), Frequency.MONTHLY.cutoff(now), Frequency.QUARTERLY.cutoff(now));
	}

	default List<Recipient> findReadyForRemind() {
		return findReadyForRemind(ZonedDateTime.now());
	}

	default List<Recipient> findReadyForRemind(ZonedDateTime now) {
		return findReadyForRemind(Frequency.WEEKLY.cutoff(now), Frequency.MONTHLY.cutoff(now), Frequency.QUARTERLY.cutoff(now));
	}
}
