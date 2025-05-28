package com.playdata.orderingservice.ordering.controller;

import com.playdata.orderingservice.common.auth.TokenUserInfo;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Slf4j
public class SseController {

    @GetMapping("/subscribe")
    public void subscribe(@AuthenticationPrincipal TokenUserInfo userInfo) {
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L);
        log.info("Subscribe to {}", userInfo.getEmail());

        try {
            // 연결 성공 메세지 전송
            emitter.send(
                    SseEmitter.event()
                            .name("connect")
                            .data("connected!!")
            );

            // 30초 마다 heartbeat 메세지를 전송하여 연결 유지
            // 클라이언트에서 사용하는 EventSourcePolyfill 이 45초동안 활동이 없으면 지맘대로 연결 종료.
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
                try {
                    // 일정하게 동작시킬 로직 작성
                    emitter.send(
                            SseEmitter.event()
                                    .name("heartbeat")
                                    .data("keep-alive") // 클라이언트 단이 살아있는지 확인
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                    log.info("Failed to seand headtbate");
                }
            }, 30, 30, TimeUnit.SECONDS);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
