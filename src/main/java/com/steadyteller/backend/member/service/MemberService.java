package com.steadyteller.backend.member.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.domain.MemberStatus;
import com.steadyteller.backend.member.dto.MemberResponse;
import com.steadyteller.backend.member.dto.MemberSignUpRequest;
import com.steadyteller.backend.member.dto.MemberUpdateRequest;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.LearningProfileRepository;
import com.steadyteller.backend.member.repository.MemberRepository;
import com.steadyteller.backend.member.domain.LearningProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final LearningProfileRepository learningProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse signUp(MemberSignUpRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.create(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname()
        );
        Member savedMember = memberRepository.save(member);
        learningProfileRepository.save(LearningProfile.createDefault(savedMember));
        return MemberResponse.from(savedMember);
    }

    public MemberResponse getMember(Long memberId) {
        return MemberResponse.from(getActiveMember(memberId));
    }

    @Transactional
    public MemberResponse updateMember(Long memberId, MemberUpdateRequest request) {
        Member member = getActiveMember(memberId);
        if (request.getNickname() != null) {
            member.updateNickname(request.getNickname());
        }
        if (request.getPassword() != null) {
            member.updatePassword(passwordEncoder.encode(request.getPassword()));
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        getActiveMember(memberId).withdraw();
    }

    public Member getActiveMember(Long memberId) {
        return memberRepository.findByIdAndStatus(memberId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
