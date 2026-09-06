package com.steadyteller.backend.member.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.LearningProfile;
import com.steadyteller.backend.member.dto.LearningProfileResponse;
import com.steadyteller.backend.member.dto.LearningProfileUpdateRequest;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.LearningProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningProfileService {

    private final MemberService memberService;
    private final LearningProfileRepository learningProfileRepository;

    public LearningProfileResponse getProfile(Long memberId) {
        memberService.getActiveMember(memberId);
        return LearningProfileResponse.from(getProfileEntity(memberId));
    }

    @Transactional
    public LearningProfileResponse updateProfile(Long memberId, LearningProfileUpdateRequest request) {
        memberService.getActiveMember(memberId);
        LearningProfile profile = getProfileEntity(memberId);
        profile.update(
                request.getDefaultLevel(),
                request.getPreferredStartTime(),
                request.isNotificationEnabled()
        );
        return LearningProfileResponse.from(profile);
    }

    private LearningProfile getProfileEntity(Long memberId) {
        return learningProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
