package com.cloudmart.live.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.live.converter.LiveConverter;
import com.cloudmart.live.dto.CreateLiveRoomRequest;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.entity.LiveRoom;
import com.cloudmart.live.repository.LiveRoomMapper;
import com.cloudmart.live.service.LiveRoomService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class LiveRoomServiceImpl implements LiveRoomService {

    private final LiveRoomMapper roomMapper;
    private final LiveConverter converter;

    public LiveRoomServiceImpl(LiveRoomMapper roomMapper, LiveConverter converter) {
        this.roomMapper = roomMapper;
        this.converter = converter;
    }

    @Override
    @Transactional
    public LiveRoomDTO createRoom(CreateLiveRoomRequest request) {
        LiveRoom entity = converter.toEntity(request);
        if (request.maxViewers() == null) {
            entity.setMaxViewers(0);
        }
        roomMapper.insert(entity);
        return converter.toDTO(entity);
    }

    @Override
    @Transactional
    @SentinelResource(value = "startLive", fallback = "startLiveFallback")
    public LiveRoomDTO startLive(Long roomId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "直播间不存在");
        }
        if ("LIVE".equals(room.getStatus())) {
            throw new BusinessException("ROOM_ALREADY_LIVE", "直播间已在直播中");
        }
        room.setStatus("LIVE");
        room.setStartTime(LocalDateTime.now());
        room.setCurrentViewers(0);
        roomMapper.updateById(room);
        return converter.toDTO(room);
    }

    @Override
    @Transactional
    public LiveRoomDTO endLive(Long roomId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "直播间不存在");
        }
        room.setStatus("ENDED");
        room.setEndTime(LocalDateTime.now());
        room.setCurrentViewers(0);
        roomMapper.updateById(room);
        return converter.toDTO(room);
    }

    @Override
    public LiveRoomDTO getRoom(Long roomId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "直播间不存在");
        }
        return converter.toDTO(room);
    }

    @Override
    public IPage<LiveRoomDTO> listRooms(String status, int page, int size) {
        LambdaQueryWrapper<LiveRoom> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(LiveRoom::getStatus, status);
        }
        wrapper.orderByDesc(LiveRoom::getCreatedAt);
        IPage<LiveRoom> pageResult = roomMapper.selectPage(new Page<>(page, size), wrapper);
        Page<LiveRoomDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(converter.toDTOList(pageResult.getRecords()));
        return dtoPage;
    }

    @Override
    @Transactional
    public void incrementViewer(Long roomId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null || !"LIVE".equals(room.getStatus())) {
            return;
        }
        room.setCurrentViewers(room.getCurrentViewers() + 1);
        room.setTotalViewers(room.getTotalViewers() + 1);
        roomMapper.updateById(room);
    }

    @Override
    @Transactional
    public void decrementViewer(Long roomId) {
        LiveRoom room = roomMapper.selectById(roomId);
        if (room == null || !"LIVE".equals(room.getStatus())) {
            return;
        }
        room.setCurrentViewers(Math.max(0, room.getCurrentViewers() - 1));
        roomMapper.updateById(room);
    }

    public LiveRoomDTO startLiveFallback(Long roomId, Throwable throwable) {
        log.warn("startLive fallback triggered, roomId={}: {}", roomId, throwable.getMessage());
        return null;
    }
}
