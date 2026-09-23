package com.company.projectmanagement.solution.domain;

public class DeploymentSolutionStep {
    private Long id;
    private Long solutionId;
    private Integer stepOrder;
    private String title;
    private String instructions;
    private Long deploymentAssetId;
    private String parametersTemplate;
    private String assetName;
    private Integer assetVersion;
    private String assetVersionLabel;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSolutionId() { return solutionId; }
    public void setSolutionId(Long solutionId) { this.solutionId = solutionId; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public Long getDeploymentAssetId() { return deploymentAssetId; }
    public void setDeploymentAssetId(Long deploymentAssetId) { this.deploymentAssetId = deploymentAssetId; }
    public String getParametersTemplate() { return parametersTemplate; }
    public void setParametersTemplate(String parametersTemplate) { this.parametersTemplate = parametersTemplate; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public Integer getAssetVersion() { return assetVersion; }
    public void setAssetVersion(Integer assetVersion) { this.assetVersion = assetVersion; }
    public String getAssetVersionLabel() { return assetVersionLabel; }
    public void setAssetVersionLabel(String assetVersionLabel) { this.assetVersionLabel = assetVersionLabel; }
}
