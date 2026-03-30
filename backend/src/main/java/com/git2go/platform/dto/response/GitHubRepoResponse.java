package com.git2go.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubRepoResponse {
    private Long id;
    private String name;
    private String fullName;
    private String description;
    private String htmlUrl;
    private String cloneUrl;
    private String language;
    private String defaultBranch;
    private boolean isPrivate;
    private int stargazersCount;
    private int forksCount;
}
