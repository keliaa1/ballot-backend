package com.ballot.repository;

import com.ballot.entity.Notification;
import com.ballot.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /**
     * Hard-coded descending sort — kept for SSE / unread-count paths that don't need flexibility.
     */
    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Flexible sort — sort field and direction are driven by the {@link Pageable} argument.
     * Used by the notifications list endpoint when the client supplies sortBy/direction params.
     */
    Page<Notification> findAllByUserId(String userId, Pageable pageable);

    long countByUserIdAndStatus(String userId, NotificationStatus status);

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    int markAllAsReadByUserId(String userId);

    /**
     * Used by the scheduler to purge old READ notifications (keeps DB lean).
     * Deletes all READ notifications created before the given cutoff date.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.status = 'READ' AND n.createdAt < :cutoff")
    int deleteOldReadNotifications(LocalDateTime cutoff);
}
