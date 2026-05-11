package it.uniroma3.sii.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusResponse {
    private boolean localModeActive;
    private boolean onboardingCompleted;
    private boolean modelAvailable;
    private boolean diskOk;
    private long freeDiskMb;
    private long requiredDiskMb;
    private String model;
    private String dataRoot;
}
