package com.piggymetrics.notification.domain;

import org.hibernate.validator.constraints.Email;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Document(collection = "recipients")
@CompoundIndexes({
		@CompoundIndex(name = "backup_ready_idx", def = "{'scheduledNotifications.BACKUP.active': 1, " +
				"'scheduledNotifications.BACKUP.frequency': 1, 'scheduledNotifications.BACKUP.lastNotified': 1}"),
		@CompoundIndex(name = "remind_ready_idx", def = "{'scheduledNotifications.REMIND.active': 1, " +
				"'scheduledNotifications.REMIND.frequency': 1, 'scheduledNotifications.REMIND.lastNotified': 1}")
})
public class Recipient {

	@Id
	private String accountName;

	@NotNull
	@Email
	private String email;

	@Valid
	private Map<NotificationType, NotificationSettings> scheduledNotifications;

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Map<NotificationType, NotificationSettings> getScheduledNotifications() {
		return scheduledNotifications;
	}

	public void setScheduledNotifications(Map<NotificationType, NotificationSettings> scheduledNotifications) {
		this.scheduledNotifications = scheduledNotifications;
	}

	@Override
	public String toString() {
		return "Recipient{" +
				"accountName='" + accountName + '\'' +
				", email='" + email + '\'' +
				'}';
	}
}
