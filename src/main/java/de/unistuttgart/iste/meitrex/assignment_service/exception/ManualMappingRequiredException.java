package de.unistuttgart.iste.meitrex.assignment_service.exception;

import org.json.JSONObject;

public class ManualMappingRequiredException extends Exception {
    private final JSONObject externalStudentInfo;

    public ManualMappingRequiredException(JSONObject externalStudentInfo) {
        super();
        this.externalStudentInfo = externalStudentInfo;
    }

    public JSONObject getExternalStudentInfo() {
        return externalStudentInfo;
    }
}
