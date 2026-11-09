package com.requestrecorder.core.model;

/**
 * 项目信息，对应 http_request_project 表。
 */
public class ProjectInfo {

    private String projectCode;
    private String projectName;
    private String description;

    public ProjectInfo() {
    }

    public ProjectInfo(String projectCode, String projectName) {
        this.projectCode = projectCode;
        this.projectName = projectName;
    }

    public ProjectInfo(String projectCode, String projectName, String description) {
        this.projectCode = projectCode;
        this.projectName = projectName;
        this.description = description;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ProjectInfo{" + projectCode + "/" + projectName + "}";
    }
}
