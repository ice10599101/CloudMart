package com.cloudmart.common.event;

import com.cloudmart.common.model.OperLogRecord;
import org.springframework.context.ApplicationEvent;

public class OperLogEvent extends ApplicationEvent {

    private final OperLogRecord record;

    public OperLogEvent(Object source, OperLogRecord record) {
        super(source);
        this.record = record;
    }

    public OperLogRecord getRecord() {
        return record;
    }
}
