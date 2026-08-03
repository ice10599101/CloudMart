package com.cloudmart.live.service;

import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;

import java.util.List;

public interface WebrtcService {

    void publishSignal(WebrtcSignalRequest request);

    List<WebrtcSignalResponse> getSignals(Long roomId, String role);

    void publishIceCandidate(WebrtcSignalRequest request);

    List<String> getIceCandidates(Long roomId, String role);

    void clearSignals(Long roomId);
}
