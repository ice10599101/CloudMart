package com.cloudmart.job.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.job.dto.SysJobLogResponse;
import com.cloudmart.job.dto.SysJobRequest;
import com.cloudmart.job.dto.SysJobResponse;

public interface SysJobService {
    IPage<SysJobResponse> page(Integer page, Integer pageSize, String jobName, Integer status);
    SysJobResponse getById(Long id);
    Long create(SysJobRequest request);
    void update(Long id, SysJobRequest request);
    void delete(Long id);
    void changeStatus(Long id, Integer status);
    void runOnce(Long id);
    IPage<SysJobLogResponse> pageJobLogs(Long jobId, Integer page, Integer pageSize);
    void deleteJobLog(Long id);
    void cleanJobLogs();
}
