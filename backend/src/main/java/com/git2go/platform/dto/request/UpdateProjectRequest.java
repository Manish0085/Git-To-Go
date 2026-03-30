package com.git2go.platform.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateProjectRequest {

    private String name;
    private String branch;
    private Integer port;
    private Boolean autoDeployEnabled;

    // Env vars update — pura map replace hoga (PUT semantics)
    private Map<String, String> envVariables;
}
