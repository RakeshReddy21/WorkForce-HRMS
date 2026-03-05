package org.example.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpRangeRequest {

    @NotBlank(message = "IP range is required")
    @Size(max = 50, message = "IP range must not exceed 50 characters")
    private String ipRange;

    @NotBlank(message = "Description is required")
    @Size(max = 200, message = "Description must not exceed 200 characters")
    private String description;

    private Boolean isActive;
}
