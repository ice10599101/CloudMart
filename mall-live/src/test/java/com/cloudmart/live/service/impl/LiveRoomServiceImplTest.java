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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveRoomServiceImplTest {

    @Mock
    private LiveRoomMapper roomMapper;

    @Mock
    private LiveConverter converter;

    private LiveRoomServiceImpl liveRoomService;

    @BeforeEach
    void setUp() {
        liveRoomService = new LiveRoomServiceImpl(roomMapper, converter);
    }

    private static final Long ROOM_ID = 1L;
    private static final Long ANCHOR_USER_ID = 10L;

    @Nested
    @DisplayName("createRoom")
    class CreateRoomTests {

        @Test
        @DisplayName("should create room and return DTO")
        void createRoom_success_returnsDTO() {
            CreateLiveRoomRequest request = new CreateLiveRoomRequest(
                    "测试直播", "描述", ANCHOR_USER_ID, "主播A",
                    "cover.jpg", "rtmp://stream", 100L, 200L, 500);
            LiveRoom entity = new LiveRoom();
            entity.setId(ROOM_ID);
            entity.setTitle("测试直播");
            entity.setMaxViewers(500);

            LiveRoomDTO expectedDTO = new LiveRoomDTO(ROOM_ID, "测试直播", "描述",
                    ANCHOR_USER_ID, "主播A", "cover.jpg", "rtmp://stream",
                    100L, 200L, 500, 0, 0L, "OFFLINE", null, null, null);

            when(converter.toEntity(request)).thenReturn(entity);
            when(roomMapper.insert(entity)).thenReturn(1);
            when(converter.toDTO(entity)).thenReturn(expectedDTO);

            LiveRoomDTO result = liveRoomService.createRoom(request);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(ROOM_ID);
            assertThat(result.maxViewers()).isEqualTo(500);
            verify(roomMapper).insert(entity);
        }

        @Test
        @DisplayName("should default maxViewers to 0 when null")
        void createRoom_nullMaxViewers_defaultsToZero() {
            CreateLiveRoomRequest request = new CreateLiveRoomRequest(
                    "测试直播", "描述", ANCHOR_USER_ID, "主播A",
                    null, null, null, null, null);
            LiveRoom entity = new LiveRoom();
            entity.setTitle("测试直播");

            when(converter.toEntity(request)).thenReturn(entity);
            when(roomMapper.insert(entity)).thenReturn(1);
            when(converter.toDTO(entity)).thenReturn(
                    new LiveRoomDTO(ROOM_ID, "测试直播", "描述",
                            ANCHOR_USER_ID, "主播A", null, null,
                            null, null, 0, 0, 0L, "OFFLINE", null, null, null));

            liveRoomService.createRoom(request);

            assertThat(entity.getMaxViewers()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("startLive")
    class StartLiveTests {

        @Test
        @DisplayName("should start live for existing offline room")
        void startLive_existingRoom_startsLive() {
            LiveRoom room = new LiveRoom();
            room.setId(ROOM_ID);
            room.setStatus("OFFLINE");
            room.setCurrentViewers(5);

            LiveRoomDTO expectedDTO = new LiveRoomDTO(ROOM_ID, "测试直播", "描述",
                    ANCHOR_USER_ID, "主播A", null, null, null, null, 0, 0, 0L,
                    "LIVE", null, null, null);

            when(roomMapper.selectById(ROOM_ID)).thenReturn(room);
            when(roomMapper.updateById(room)).thenReturn(1);
            when(converter.toDTO(room)).thenReturn(expectedDTO);

            LiveRoomDTO result = liveRoomService.startLive(ROOM_ID);

            assertThat(result).isNotNull();
            assertThat(room.getStatus()).isEqualTo("LIVE");
            assertThat(room.getCurrentViewers()).isEqualTo(0);
            assertThat(room.getStartTime()).isNotNull();
            verify(roomMapper).updateById(room);
        }

        @Test
        @DisplayName("should throw when room not found")
        void startLive_nonExistentRoom_throwsException() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(null);

            assertThatThrownBy(() -> liveRoomService.startLive(ROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ROOM_NOT_FOUND");
        }

        @Test
        @DisplayName("should throw when room is already live")
        void startLive_alreadyLive_throwsException() {
            LiveRoom room = new LiveRoom();
            room.setId(ROOM_ID);
            room.setStatus("LIVE");

            when(roomMapper.selectById(ROOM_ID)).thenReturn(room);

            assertThatThrownBy(() -> liveRoomService.startLive(ROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ROOM_ALREADY_LIVE");
        }
    }

    @Nested
    @DisplayName("endLive")
    class EndLiveTests {

        @Test
        @DisplayName("should end live for existing room")
        void endLive_existingRoom_endsLive() {
            LiveRoom room = new LiveRoom();
            room.setId(ROOM_ID);
            room.setStatus("LIVE");
            room.setCurrentViewers(100);

            LiveRoomDTO expectedDTO = new LiveRoomDTO(ROOM_ID, "测试直播", "描述",
                    ANCHOR_USER_ID, "主播A", null, null, null, null, 0, 0, 0L,
                    "ENDED", null, null, null);

            when(roomMapper.selectById(ROOM_ID)).thenReturn(room);
            when(roomMapper.updateById(room)).thenReturn(1);
            when(converter.toDTO(room)).thenReturn(expectedDTO);

            LiveRoomDTO result = liveRoomService.endLive(ROOM_ID);

            assertThat(result).isNotNull();
            assertThat(room.getStatus()).isEqualTo("ENDED");
            assertThat(room.getCurrentViewers()).isEqualTo(0);
            assertThat(room.getEndTime()).isNotNull();
        }

        @Test
        @DisplayName("should throw when room not found")
        void endLive_nonExistentRoom_throwsException() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(null);

            assertThatThrownBy(() -> liveRoomService.endLive(ROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ROOM_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("getRoom")
    class GetRoomTests {

        @Test
        @DisplayName("should return room DTO when room exists")
        void getRoom_existingRoom_returnsDTO() {
            LiveRoom room = new LiveRoom();
            room.setId(ROOM_ID);
            room.setTitle("测试直播");

            LiveRoomDTO expectedDTO = new LiveRoomDTO(ROOM_ID, "测试直播", "描述",
                    ANCHOR_USER_ID, "主播A", null, null, null, null, 0, 0, 0L,
                    "OFFLINE", null, null, null);

            when(roomMapper.selectById(ROOM_ID)).thenReturn(room);
            when(converter.toDTO(room)).thenReturn(expectedDTO);

            LiveRoomDTO result = liveRoomService.getRoom(ROOM_ID);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(ROOM_ID);
        }

        @Test
        @DisplayName("should throw when room not found")
        void getRoom_nonExistentRoom_throwsException() {
            when(roomMapper.selectById(ROOM_ID)).thenReturn(null);

            assertThatThrownBy(() -> liveRoomService.getRoom(ROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ROOM_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("listRooms")
    class ListRoomsTests {

        @Test
        @DisplayName("should return filtered page when status provided")
        void listRooms_withStatus_returnsFilteredPage() {
            LiveRoom room = new LiveRoom();
            room.setId(ROOM_ID);
            room.setStatus("LIVE");

            Page<LiveRoom> pageResult = new Page<>(1, 10, 1);
            pageResult.setRecords(List.of(room));

            LiveRoomDTO dto = new LiveRoomDTO(ROOM_ID, "测试直播", "描述",
                    ANCHOR_USER_ID, "主播A", null, null, null, null, 0, 0, 0L,
                    "LIVE", null, null, null);

            when(roomMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageResult);
            when(converter.toDTOList(List.of(room))).thenReturn(List.of(dto));

            IPage<LiveRoomDTO> result = liveRoomService.listRooms("LIVE", 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).status()).isEqualTo("LIVE");
        }

        @Test
        @DisplayName("should return all rooms when status is null")
        void listRooms_nullStatus_returnsAllRooms() {
            LiveRoom room1 = new LiveRoom();
            room1.setId(1L);
            room1.setStatus("LIVE");
            LiveRoom room2 = new LiveRoom();
            room2.setId(2L);
            room2.setStatus("OFFLINE");

            Page<LiveRoom> pageResult = new Page<>(1, 10, 2);
            pageResult.setRecords(List.of(room1, room2));

            LiveRoomDTO dto1 = new LiveRoomDTO(1L, "直播1", null, ANCHOR_USER_ID, "主播A",
                    null, null, null, null, 0, 0, 0L, "LIVE", null, null, null);
            LiveRoomDTO dto2 = new LiveRoomDTO(2L, "直播2", null, ANCHOR_USER_ID, "主播B",
                    null, null, null, null, 0, 0, 0L, "OFFLINE", null, null, null);

            when(roomMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageResult);
            when(converter.toDTOList(List.of(room1, room2))).thenReturn(List.of(dto1, dto2));

            IPage<LiveRoomDTO> result = liveRoomService.listRooms(null, 1, 10);

            assertThat(result.getRecords()).hasSize(2);
        }
    }
}
