package com.cloudmart.live.service;

import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;

import java.util.List;

public interface WebrtcService {

    /** 为当前用户签发指定房间的信令票据（角色服务端派生，绑定 peerSessionId） */
    WebrtcSignalTicketService.IssuedTicket issueSignalTicket(Long userId, Long roomId);

    /** 发布信令（HOST 仅 OFFER 共享键 / VIEWER 仅 ANSWER 会话键，身份取自票据） */
    void publishSignal(WebrtcSignalRequest request);

    /**
     * 拉取信令。targetRole=HOST 为主播共享 OFFER（观众可读）；
     * targetRole=VIEWER 仅主播可聚合全部观众会话，观众只能读自身会话。
     */
    List<WebrtcSignalResponse> getSignals(Long roomId, String targetRole, String ticket);

    /** 发布 ICE 候选（HOST 共享键 / VIEWER 会话键，身份取自票据） */
    void publishIceCandidate(WebrtcSignalRequest request);

    /** 拉取 ICE 候选（授权规则同 {@link #getSignals}） */
    List<String> getIceCandidates(Long roomId, String targetRole, String ticket);

    /** 清除房间全部信令（仅房主票据；旧观众会话随 TTL 自然过期） */
    void clearSignals(Long roomId, String ticket);
}
