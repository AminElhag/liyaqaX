package com.liyaqa.config

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.security.Roles
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("dev")
class DevDataLoader(
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = LoggerFactory.getLogger(DevDataLoader::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seed() {
        if (organizationRepository.count() > 0) {
            log.info("Seed data already exists — skipping.")
            return
        }

        log.info("Seeding dev data...")

        val org =
            organizationRepository.save(
                Organization(
                    nameAr =
                        "\u0645\u0624\u0633\u0633\u0629 \u0644\u064A\u0627\u0642\u0629 " +
                            "\u0627\u0644\u062A\u062C\u0631\u064A\u0628\u064A\u0629",
                    nameEn = "Liyaqa Demo Org",
                    email = "demo@liyaqa.com",
                    country = "SA",
                    timezone = "Asia/Riyadh",
                ),
            )

        val club =
            clubRepository.save(
                Club(
                    organizationId = org.id,
                    nameAr = "\u0646\u0627\u062F\u064A \u0625\u0643\u0633\u064A\u0631",
                    nameEn = "Elixir Gym",
                    email = "info@elixir.com",
                ),
            )

        val users =
            listOf(
                seedUser("admin@liyaqa.com", "Admin1234!", Roles.NEXUS_SUPER_ADMIN, null, null),
                seedUser("owner@elixir.com", "Owner1234!", Roles.CLUB_OWNER, org.id, club.id),
                seedUser("manager@elixir.com", "Manager1234!", Roles.CLUB_BRANCH_MANAGER, org.id, club.id),
                seedUser("reception@elixir.com", "Recept1234!", Roles.CLUB_RECEPTIONIST, org.id, club.id),
                seedUser("sales@elixir.com", "Sales1234!", Roles.CLUB_SALES_AGENT, org.id, club.id),
                seedUser("pt@elixir.com", "Trainer1234!", Roles.TRAINER_PT, org.id, club.id),
                seedUser("gx@elixir.com", "Trainer1234!", Roles.TRAINER_GX, org.id, club.id),
                seedUser("member@elixir.com", "Member1234!", Roles.MEMBER, org.id, club.id),
            )
        userRepository.saveAll(users)

        log.info("Seeded 1 organization, 1 club, and {} users.", users.size)
    }

    private fun seedUser(
        email: String,
        rawPassword: String,
        role: String,
        organizationId: Long?,
        clubId: Long?,
    ) = User(
        email = email,
        passwordHash = passwordEncoder.encode(rawPassword),
        role = role,
        organizationId = organizationId,
        clubId = clubId,
    )
}
