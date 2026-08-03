package com.cloudmart.common.aspect;

import com.cloudmart.common.annotation.DataScope;
import com.cloudmart.common.datascope.DataScopeContext;
import com.cloudmart.common.datascope.DataScopeHandler;
import com.cloudmart.common.datascope.DataScopeResult;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DataScopeAspect {

    private final DataScopeHandler handler;

    public DataScopeAspect(@Autowired(required = false) DataScopeHandler handler) {
        this.handler = handler;
    }

    @Before("@annotation(dataScope)")
    public void doBefore(DataScope dataScope) {
        if (handler == null) {
            return;
        }
        DataScopeResult result = handler.resolveDataScope();
        if (result != null) {
            DataScopeContext.set(result);
        }
    }

    @After("@annotation(dataScope)")
    public void doAfter(DataScope dataScope) {
        DataScopeContext.clear();
    }
}
