package com.liyaqa.membership

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MembershipExpiryJob(
    private val membershipService: MembershipService,
) {
    private val log = LoggerFactory.getLogger(MembershipExpiryJob::class.java)

    @Scheduled(cron = "0 0 22 * * *", zone = "UTC")
    fun processExpiredMemberships() {
        log.info("Starting membership expiry job")
        membershipService.expireOverdueMemberships()
        log.info("Membership expiry job completed")
    }
}
