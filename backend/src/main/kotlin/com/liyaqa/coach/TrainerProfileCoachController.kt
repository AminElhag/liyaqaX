package com.liyaqa.coach

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.coach.dto.BranchSummary
import com.liyaqa.coach.dto.CertificationSummary
import com.liyaqa.coach.dto.ClubSummary
import com.liyaqa.coach.dto.TrainerMeResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.trainer.TrainerBranchAssignmentRepository
import com.liyaqa.trainer.TrainerCertificationRepository
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coach")
@Tag(name = "Coach Profile", description = "Trainer profile endpoints")
@Validated
class TrainerProfileCoachController(
    private val trainerRepository: TrainerRepository,
    private val trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository,
    private val trainerCertificationRepository: TrainerCertificationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository,
) {
    @GetMapping("/me")
    @Operation(summary = "Get current trainer profile with certifications and branch assignments")
    fun getMe(authentication: Authentication): ResponseEntity<TrainerMeResponse> {
        val claims = authentication.coachContext()
        val trainerPublicId = claims.requireTrainerId()

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val user =
            userRepository.findById(trainer.userId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "User not found.")
                }

        val club =
            clubRepository.findById(trainer.clubId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
                }

        val assignments = trainerBranchAssignmentRepository.findAllByTrainerId(trainer.id)
        val branchIds = assignments.map { it.branchId }
        val branches = branchRepository.findAllById(branchIds)

        val certifications = trainerCertificationRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)

        return ResponseEntity.ok(
            TrainerMeResponse(
                id = trainer.publicId,
                firstName = trainer.firstNameEn,
                lastName = trainer.lastNameEn,
                firstNameAr = trainer.firstNameAr,
                lastNameAr = trainer.lastNameAr,
                email = user.email,
                phone = trainer.phone,
                trainerTypes = claims.trainerTypes,
                club =
                    ClubSummary(
                        id = club.publicId,
                        name = club.nameEn,
                        nameAr = club.nameAr,
                    ),
                branches =
                    branches.map { branch ->
                        BranchSummary(
                            id = branch.publicId,
                            name = branch.nameEn,
                        )
                    },
                certifications =
                    certifications.map { cert ->
                        CertificationSummary(
                            id = cert.publicId,
                            name = cert.nameEn,
                            issuingOrganization = cert.issuingBody,
                            issueDate = cert.issuedAt,
                            expiryDate = cert.expiresAt,
                        )
                    },
            ),
        )
    }
}
