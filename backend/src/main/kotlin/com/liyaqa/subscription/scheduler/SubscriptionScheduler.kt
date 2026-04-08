package com.liyaqa.subscription.scheduler

import com.liyaqa.subscription.service.SubscriptionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SubscriptionScheduler(
    private val subscriptionService: SubscriptionService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionScheduler::class.java)
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Riyadh")
    fun processSubscriptionLifecycle() {
        log.info("Starting subscription lifecycle processing...")
        subscriptionService.transitionExpiredToGrace()
        subscriptionService.transitionGraceToExpired()
        subscriptionService.sendExpiryNotifications()
        log.info("Subscription lifecycle processing complete.")
    }
}
