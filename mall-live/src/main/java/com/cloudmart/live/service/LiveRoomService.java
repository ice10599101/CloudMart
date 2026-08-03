package com.cloudmart.live.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.live.dto.CreateLiveRoomRequest;
import com.cloudmart.live.dto.LiveRoomDTO;

public interface LiveRoomService {

    LiveRoomDTO createRoom(CreateLiveRoomRequest request);

    LiveRoomDTO startLive(Long roomId);

    LiveRoomDTO endLive(Long roomId);

    LiveRoomDTO getRoom(Long roomId);

    IPage<LiveRoomDTO> listRooms(String status, int page, int size);

    void incrementViewer(Long roomId);

    void decrementViewer(Long roomId);
}
