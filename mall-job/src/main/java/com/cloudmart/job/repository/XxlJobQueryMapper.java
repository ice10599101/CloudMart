package com.cloudmart.job.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * OPS-01：XXL-JOB 事实只读查询（跨库 xxl_job）。
 * 调度唯一事实来源是 XXL-JOB——本服务的任务管理接口只做只读适配展示，
 * 生命周期操作（新建/编辑/启停/Cron）一律走 XXL 调度中心控制台。
 */
@Mapper
public interface XxlJobQueryMapper {

    @Select("SELECT id, job_group AS jobGroup, job_desc AS jobDesc, "
            + "executor_handler AS executorHandler, executor_param AS executorParam, "
            + "schedule_conf AS scheduleConf, trigger_status AS triggerStatus, "
            + "author, alarm_email AS alarmEmail "
            + "FROM xxl_job.xxl_job_info "
            + "WHERE (#{jobDesc} IS NULL OR job_desc LIKE CONCAT('%', #{jobDesc}, '%')) "
            + "ORDER BY id LIMIT #{offset}, #{size}")
    List<Map<String, Object>> pageJobs(@Param("jobDesc") String jobDesc,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("SELECT COUNT(*) FROM xxl_job.xxl_job_info "
            + "WHERE (#{jobDesc} IS NULL OR job_desc LIKE CONCAT('%', #{jobDesc}, '%'))")
    long countJobs(@Param("jobDesc") String jobDesc);

    @Select("SELECT id, job_group AS jobGroup, job_desc AS jobDesc, "
            + "executor_handler AS executorHandler, executor_param AS executorParam, "
            + "schedule_conf AS scheduleConf, trigger_status AS triggerStatus, "
            + "author, alarm_email AS alarmEmail "
            + "FROM xxl_job.xxl_job_info WHERE id = #{id}")
    Map<String, Object> jobById(@Param("id") long id);

    @Select("SELECT executor_handler AS executorHandler FROM xxl_job.xxl_job_info WHERE id = #{id}")
    String handlerById(@Param("id") long id);

    @Select("SELECT log_id AS logId, job_group AS jobGroup, job_id AS jobId, "
            + "executor_address AS executorAddress, executor_handler AS executorHandler, "
            + "trigger_time AS triggerTime, trigger_code AS triggerCode, trigger_msg AS triggerMsg, "
            + "handle_time AS handleTime, handle_code AS handleCode, handle_msg AS handleMsg "
            + "FROM xxl_job.xxl_job_log WHERE job_id = #{jobId} "
            + "ORDER BY trigger_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> pageLogs(@Param("jobId") long jobId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("SELECT COUNT(*) FROM xxl_job.xxl_job_log WHERE job_id = #{jobId}")
    long countLogs(@Param("jobId") long jobId);
}
