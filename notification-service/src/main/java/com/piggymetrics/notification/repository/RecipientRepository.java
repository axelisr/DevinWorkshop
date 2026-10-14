package com.piggymetrics.notification.repository;

import com.piggymetrics.notification.domain.Frequency;
import com.piggymetrics.notification.domain.Recipient;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
		long now = System.currentTimeMillis();
		return findReadyForBackup(
				new Date(now - TimeUnit.DAYS.toMillis(Frequency.WEEKLY.getDays())),
				new Date(now - TimeUnit.DAYS.toMillis(Frequency.MONTHLY.getDays())),
				new Date(now - TimeUnit.DAYS.toMillis(Frequency.QUARTERLY.getDays())));
	}

	default List<Recipient> findReadyForRemind() {
		long now = System.currentTimeMillis();
		return findReadyForRemind(
				new Date(now - TimeUnit.DAYS.toMillis(Frequency.WEEKLY.getDays())),
				new Date(now - TimeUnit.DAYS.toMillis(Frequency.MONTHLY.getDays())),
				new Date(now - TimeUnit.DAYS.toMillis(Frequency.QUARTERLY.getDays())));
	}
}
