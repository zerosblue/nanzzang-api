package com.nanzzang.api.config;

import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.TopicRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 이미 데이터가 있다면 기존 유저 중 test1을 관리자로 승격하고 종료
        if (userRepository.count() > 0) {
            userRepository.findByEmail("test1@nanzzang.com")
                    .ifPresent(user -> {
                        if (!"ADMIN".equals(user.getRole())) {
                            user.promoteToAdmin();
                            userRepository.save(user);
                        }
                    });
            return;
        }

        // 1. 더미 유저 생성
        User user1 = User.builder()
                .email("test1@nanzzang.com")
                .nickname("키보드워리어")
                .role("ADMIN")
                .build();
        User user2 = User.builder()
                .email("test2@nanzzang.com")
                .nickname("팩트폭격기")
                .build();
        User user3 = User.builder()
                .email("test3@nanzzang.com")
                .nickname("지나가던선비")
                .build();

        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        // 2. 더미 토픽 생성 (사연/억울함 포함)
        Topic topic1 = Topic.builder()
                .author(user1)
                .title("탕수육, 부먹이냐 찍먹이냐 그것이 문제로다")
                .body("바삭함이 근본인가, 촉촉함이 진리인가? 여러분의 선택은?")
                .category("daily")
                .teamAName("부먹파")
                .teamBName("찍먹파")
                .imageUrls(null)
                .build();
        topic1.setExpiresAt(LocalDateTime.now().plusDays(4));
        topic1.updateHotScore(150.0);
        topic1.incrementViewCount();
        topic1.incrementViewCount();

        Topic topic2 = Topic.builder()
                .author(user2)
                .title("친구가 내 축의금으로 3만원 냈습니다. 손절해야 하나요?")
                .body("10년 지기 친구인데 제 결혼식에 와서 밥 먹고 3만원 내고 갔습니다. 제가 예민한 걸까요?")
                .category("story")
                .teamAName("글쓴이가 맞다")
                .teamBName("글쓴이가 틀리다")
                .imageUrls(null)
                .build();
        topic2.setExpiresAt(LocalDateTime.now().plusDays(7));
        topic2.updateHotScore(800.5);

        Topic topic3 = Topic.builder()
                .author(user3)
                .title("회사 막내가 에어팟 끼고 일하는데 꼰대인가요?")
                .body("요즘 MZ세대 특징이라는데, 부를 때마다 못 들어서 너무 답답합니다.")
                .category("work")
                .teamAName("막내 잘못")
                .teamBName("꼰대 맞음")
                .imageUrls(null)
                .build();
        // 곧 종료되는 데이터 테스트용
        topic3.setExpiresAt(LocalDateTime.now().plusHours(2));
        topic3.updateHotScore(30.0);

        Topic topic4 = Topic.builder()
                .author(user1)
                .title("남녀 데이트 비용, 정확히 5:5가 맞다 vs 아니다")
                .body("요즘 시대에 누가 더 내는 게 어딨습니까? 철저한 더치페이가 맞지 않나요?")
                .category("love")
                .teamAName("5:5가 맞다")
                .teamBName("상황에 따라")
                .imageUrls(null)
                .build();
        // 이미 종료된 데이터 테스트용
        topic4.setExpiresAt(LocalDateTime.now().minusDays(1));
        topic4.closeTopic("A"); // A팀 승리 가정

        topicRepository.save(topic1);
        topicRepository.save(topic2);
        topicRepository.save(topic3);
        topicRepository.save(topic4);
    }
}
