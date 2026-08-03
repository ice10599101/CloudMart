package com.cloudmart.common.model;

public class OperLogRecord {

    private String title;
    private int businessType;
    private int operatorType;
    private Long operUserId;
    private String operName;
    private String method;
    private String requestMethod;
    private String operUrl;
    private String operIp;
    private String operParam;
    private String jsonResult;
    private int status;
    private String errorMsg;
    private long costTime;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getBusinessType() { return businessType; }
    public void setBusinessType(int businessType) { this.businessType = businessType; }
    public int getOperatorType() { return operatorType; }
    public void setOperatorType(int operatorType) { this.operatorType = operatorType; }
    public Long getOperUserId() { return operUserId; }
    public void setOperUserId(Long operUserId) { this.operUserId = operUserId; }
    public String getOperName() { return operName; }
    public void setOperName(String operName) { this.operName = operName; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getOperUrl() { return operUrl; }
    public void setOperUrl(String operUrl) { this.operUrl = operUrl; }
    public String getOperIp() { return operIp; }
    public void setOperIp(String operIp) { this.operIp = operIp; }
    public String getOperParam() { return operParam; }
    public void setOperParam(String operParam) { this.operParam = operParam; }
    public String getJsonResult() { return jsonResult; }
    public void setJsonResult(String jsonResult) { this.jsonResult = jsonResult; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public long getCostTime() { return costTime; }
    public void setCostTime(long costTime) { this.costTime = costTime; }
}
