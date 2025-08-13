package com.example.acadlink;

import java.util.ArrayList;
import java.util.Map;

public class ProjectModel {
    private String id;
    private String title;
    private String projectType1;
    private String projectLevel;
    private String abstractText;
    private String methodology;
    private String similarity;
    private String aiGenerated;
    private ArrayList<String> fileInfoList;

    // store raw snapshot data for adapter fallback (optional)
    private Map<String, Object> extraData;

    // Empty constructor for Firebase
    public ProjectModel() {}

    // Full constructor (optional)
    public ProjectModel(String id, String title, String projectType1, String projectLevel,
                        String abstractText, String methodology, String similarity,
                        String aiGenerated, ArrayList<String> fileInfoList,
                        Map<String, Object> extraData) {
        this.id = id;
        this.title = title;
        this.projectType1 = projectType1;
        this.projectLevel = projectLevel;
        this.abstractText = abstractText;
        this.methodology = methodology;
        this.similarity = similarity;
        this.aiGenerated = aiGenerated;
        this.fileInfoList = fileInfoList;
        this.extraData = extraData;
    }

    // -------------------
    // Core getters / setters (canonical names)
    // -------------------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getProjectType1() { return projectType1; }
    public void setProjectType1(String projectType1) { this.projectType1 = projectType1; }

    public String getProjectLevel() { return projectLevel; }
    public void setProjectLevel(String projectLevel) { this.projectLevel = projectLevel; }

    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }

    public String getMethodology() { return methodology; }
    public void setMethodology(String methodology) { this.methodology = methodology; }

    public String getSimilarity() { return similarity; }
    public void setSimilarity(String similarity) { this.similarity = similarity; }

    public String getAiGenerated() { return aiGenerated; }
    public void setAiGenerated(String aiGenerated) { this.aiGenerated = aiGenerated; }

    public ArrayList<String> getFileInfoList() { return fileInfoList; }
    public void setFileInfoList(ArrayList<String> fileInfoList) { this.fileInfoList = fileInfoList; }

    public Map<String, Object> getExtraData() { return extraData; }
    public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }

    // -------------------
    // Alias getters/setters to match possible DB key names
    // (These let Firebase map keys like "projectTitle", "projectType2", "abstract" etc.)
    // -------------------

    // projectTitle -> title
    public String getProjectTitle() { return title; }
    public void setProjectTitle(String projectTitle) { this.title = projectTitle; }

    // projectType2 -> projectLevel (some entries use projectType2 as the level)
    public String getProjectType2() { return projectLevel; }
    public void setProjectType2(String projectType2) { this.projectLevel = projectType2; }

    // "abstract" key -> abstractText
    // method names like setAbstract(...) are valid identifiers (not the reserved word itself)
    public String getAbstract() { return abstractText; }
    public void setAbstract(String abstractText) { this.abstractText = abstractText; }

    // some databases may use "projectType" -> projectType1
    public String getProjectType() { return projectType1; }
    public void setProjectType(String projectType) { this.projectType1 = projectType; }

    // some DBs use "project_name" or "project_name" variants; Firebase maps keys to setters only if they match,
    // but we can't add every variant — adapter fallback will still handle other variants via extraData.
}
